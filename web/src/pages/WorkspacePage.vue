<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { get, message, send } from "../lib/api";
import {
  readPrivate,
  removePrivate,
  session,
  writePrivate,
} from "../lib/session";
import { compactDateText as dateText, stateName } from "../lib/content";
import type { Owned, Page } from "../types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import OperationFeedback from "../components/OperationFeedback.vue";
import { operationFailure, type Feedback } from "../lib/feedback";
import MemberGate from "../components/MemberGate.vue";
const router = useRouter();
const route = useRoute();
const result = ref<Page<Owned> & { publishingRestricted?: boolean }>();
const page = ref(1);
const busy = ref(false);
const error = ref("");
const filter = ref("ALL");
const pending = ref("");
const confirmation = ref<InstanceType<typeof ConfirmDialog>>();
const working = ref<Record<number, boolean>>({});
const rowFeedback = ref<Record<number, Feedback>>({});
const creating = ref(false);
const createFeedback = ref<Feedback>();
let loadVersion = 0;
let asking = false;
const transitions = {
  unpublish: {
    label: "下架",
    detail: "下架后读者将无法访问这篇文章，草稿仍可编辑。",
  },
  restore: {
    label: "恢复上次公开版本",
    detail: "读者将可以访问上次公开的版本。当前草稿会继续单独保存。",
  },
  delete: {
    label: "删除",
    detail: "删除后，文章无法公开访问，也无法继续编辑或恢复。",
  },
};
const states = [
  "ALL",
  "DRAFT",
  "PUBLISHED",
  "AUTHOR_OFFLINE",
  "ADMIN_HIDDEN",
  "DELETED",
];
function restoreFilter() {
  const requested =
    typeof route.query.state === "string" ? route.query.state : "ALL";
  filter.value = states.includes(requested) ? requested : "ALL";
  page.value = 1;
}
restoreFilter();
watch(
  () => route.query.state,
  () => {
    restoreFilter();
    result.value = undefined;
    void load();
  },
);
function chooseFilter(state: string) {
  void router.replace({ query: state === "ALL" ? {} : { state } });
}
async function load() {
  if (session.status !== "ready") return;
  const version = ++loadVersion;
  busy.value = true;
  error.value = "";
  try {
    pending.value = readPrivate<string>("create") || "";
    const requested = filter.value === "ALL" ? "" : `&state=${filter.value}`;
    let listing = await get<NonNullable<typeof result.value>>(
      `/me/articles?page=${page.value}&size=20${requested}`,
      true,
    );
    if (version !== loadVersion) return;
    if (!listing.items.length && page.value > 1) {
      const lastPage = Math.max(1, Math.ceil(listing.total / listing.size));
      if (lastPage < page.value) {
        page.value = lastPage;
        listing = await get(
          `/me/articles?page=${page.value}&size=20${requested}`,
          true,
        );
      }
    }
    if (version === loadVersion) result.value = listing;
  } catch (e) {
    if (version === loadVersion) error.value = message(e);
  } finally {
    if (version === loadVersion) busy.value = false;
  }
}
async function create() {
  if (creating.value) return;
  creating.value = true;
  createFeedback.value = undefined;
  try {
    if (!pending.value) {
      const key = crypto.randomUUID();
      writePrivate("create", key);
      pending.value = key;
    }
    const r = await send<{ articleId: number }>("/me/articles", "POST", {
      requestId: pending.value,
    });
    removePrivate("create");
    pending.value = "";
    await router.push(`/editor/${r.articleId}`);
  } catch (e) {
    createFeedback.value = operationFailure(
      e,
      "创建结果待确认，请沿用原请求重试。",
    );
  } finally {
    creating.value = false;
  }
}
async function transition(
  item: Owned,
  action: keyof typeof transitions,
  origin: Event,
) {
  const id = item.articleId;
  if (busy.value || asking || working.value[id]) return;
  const change = transitions[action];
  const title = item.title || "未命名草稿";
  const previousButton = origin.currentTarget as HTMLElement;
  asking = true;
  try {
    if (
      !(await confirmation.value?.ask(
        {
          title: change.label + "《" + title + "》？",
          detail: change.detail,
          action: "确认" + change.label,
        },
        previousButton,
      ))
    )
      return;
  } finally {
    asking = false;
  }
  working.value[id] = true;
  delete rowFeedback.value[id];
  try {
    const receipt =
      action === "delete"
        ? await send<Pick<Owned, "state" | "revision">>(
            `/me/articles/${id}?expectedRevision=${item.revision}`,
            "DELETE",
          )
        : await send<Pick<Owned, "state" | "revision">>(
            `/me/articles/${id}/${action}`,
            "POST",
            { expectedRevision: item.revision },
          );
    if (receipt.state === "DELETED") removePrivate(`draft:${id}`);
    const row = result.value?.items.find((entry) => entry.articleId === id);
    if (!row) return;
    row.state = receipt.state;
    row.revision = receipt.revision;
    const actions = new Set(row.allowedActions || []);
    actions.delete("UNPUBLISH");
    actions.delete("RESTORE");
    actions.delete("VIEW_PUBLIC");
    if (row.state === "PUBLISHED") {
      actions.add("VIEW_PUBLIC");
      actions.add("UNPUBLISH");
    } else if (
      row.state === "AUTHOR_OFFLINE" &&
      !result.value?.publishingRestricted
    ) {
      actions.add("RESTORE");
    }
    row.allowedActions = row.state === "DELETED" ? [] : [...actions];
    rowFeedback.value[id] = {
      kind: "success",
      text: `当前状态为${stateName[receipt.state]}。`,
    };
    await nextTick();
    const focus = document.getElementById("work-title-" + id);
    if (!previousButton.isConnected && document.activeElement === document.body)
      focus?.focus({ preventScroll: true });
  } catch (e) {
    rowFeedback.value[id] = operationFailure(
      e,
      "结果待确认。请重新读取文章状态。",
    );
  } finally {
    working.value[id] = false;
  }
}
onMounted(() => {
  void load();
  autoCreate();
});
function permits(
  item: Owned,
  action: NonNullable<Owned["allowedActions"]>[number],
  fallback: boolean,
) {
  return item.allowedActions?.includes(action) ?? fallback;
}
watch(() => session.status, () => {
  void load();
  autoCreate();
});
// 顶栏“写经验”带 ?new=1 进入时直接创建新草稿。
function autoCreate() {
  if (route.query.new !== "1" || session.status !== "ready" || creating.value)
    return;
  void router.replace({ path: "/workspace", query: {} });
  void create();
}
</script>
<template>
  <MemberGate title="登录后管理你的文章"
    ><ConfirmDialog ref="confirmation" />
    <div class="page-heading workspace-heading">
      <div>
        <h1>我的作品</h1>
        <p>草稿与已公开的经验都在这里管理。</p>
      </div>
      <button class="primary" :disabled="creating" @click="create">
        {{ pending ? "继续上次创建" : "写一篇经验" }}
      </button>
    </div>
    <OperationFeedback
      :feedback="createFeedback"
      :processing="creating ? '正在创建草稿…' : undefined"
    />
    <p v-if="pending && !creating" class="notice">
      上次创建结果尚未确认，再次点击会沿用原请求。
    </p>
    <section class="panel workspace-card" aria-label="作品列表">
      <div class="workspace-tabs" aria-label="文章状态">
        <button
          v-for="state in states"
          :key="state"
          :aria-pressed="filter === state"
          :disabled="busy"
          @click="chooseFilter(state)"
        >
          {{ state === "ALL" ? "全部" : stateName[state] }}
        </button>
      </div>
      <p v-if="error" class="error workspace-inline-note" role="alert">
        {{ error }} <button @click="load">重新读取状态</button>
      </p>
      <p v-if="busy" class="workspace-inline-note" role="status">
        正在读取文章列表…
      </p>
      <p v-if="result?.publishingRestricted" class="hint workspace-inline-note">
        当前账号发布受限。可保存草稿并查看<RouterLink to="/me/appeals"
          >限制与申诉</RouterLink
        >。
      </p>
      <table v-if="result?.items.length" class="workspace-table">
        <thead>
          <tr>
            <th scope="col">作品</th>
            <th scope="col">状态</th>
            <th scope="col">最近修订</th>
            <th scope="col">可用操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="item in result.items" :key="item.articleId">
            <tr>
              <td>
                <h2 :id="'work-title-' + item.articleId" tabindex="-1">
                  <RouterLink
                    v-if="permits(item, 'VIEW_DRAFT', item.state !== 'DELETED')"
                    :to="`/editor/${item.articleId}`"
                    >{{ item.title || "未命名草稿" }}</RouterLink
                  >
                  <RouterLink
                    v-else-if="permits(item, 'VIEW_PUBLIC', false)"
                    :to="`/articles/${item.articleId}`"
                    >{{ item.title }}</RouterLink
                  >
                  <span v-else>{{ item.title }}</span>
                </h2>
              </td>
              <td>
                <p class="state-label" :data-state="item.state">
                  {{ stateName[item.state] }}
                </p>
              </td>
              <td>
                <time :datetime="item.updatedAt">{{
                  dateText(item.updatedAt)
                }}</time
                ><span class="workspace-revision"
                  >修订 {{ item.revision }}</span
                >
              </td>
              <td>
                <div class="workspace-row-actions">
                  <RouterLink
                    v-if="
                      item.allowedActions?.includes('VIEW_DRAFT') ??
                      item.state !== 'DELETED'
                    "
                    :to="`/editor/${item.articleId}`"
                    >{{
                      item.state === "ADMIN_HIDDEN" ? "查看草稿" : "编辑草稿"
                    }}</RouterLink
                  ><RouterLink
                    v-if="
                      item.allowedActions?.includes('VIEW_PUBLIC') ??
                      item.state === 'PUBLISHED'
                    "
                    :to="`/articles/${item.articleId}`"
                    >查看公开文章</RouterLink
                  >
                  <button
                    v-if="
                      item.allowedActions?.includes('UNPUBLISH') ??
                      item.state === 'PUBLISHED'
                    "
                    class="text-button workspace-danger"
                    :disabled="busy || working[item.articleId]"
                    @click="transition(item, 'unpublish', $event)"
                  >
                    下架</button
                  ><button
                    v-if="
                      item.allowedActions?.includes('RESTORE') ??
                      item.state === 'AUTHOR_OFFLINE'
                    "
                    class="text-button"
                    :disabled="busy || working[item.articleId]"
                    @click="transition(item, 'restore', $event)"
                  >
                    恢复上次公开版本
                  </button>
                  <button
                    v-if="item.allowedActions?.includes('DELETE')"
                    class="text-button workspace-danger"
                    :disabled="busy || working[item.articleId]"
                    @click="transition(item, 'delete', $event)"
                  >
                    删除
                  </button>
                </div>
                <OperationFeedback
                  :feedback="rowFeedback[item.articleId]"
                  :processing="
                    working[item.articleId] ? '正在处理文章状态…' : undefined
                  "
                />
                <button
                  v-if="
                    rowFeedback[item.articleId] &&
                    rowFeedback[item.articleId]?.kind !== 'success'
                  "
                  class="text-button"
                  :disabled="busy"
                  @click="load"
                >
                  重新读取状态
                </button>
                <p v-if="item.state === 'ADMIN_HIDDEN'" class="hint">
                  管理员隐藏的文章不能自行修改、发布或恢复。
                </p>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <section
        v-else-if="!busy && result"
        class="workspace-empty"
      >
        <h2>还没有此状态的文章</h2>
        <p>可以切换状态或写下第一篇经验。</p>
      </section>
      <div v-if="result" class="workspace-foot">
        <span aria-live="polite">当前 {{ result.total }} 条作品</span>
        <nav
          v-if="result.total > result.size"
          class="pagination"
          aria-label="工作区翻页"
        >
          <button
            aria-label="上一页"
            :disabled="busy || page <= 1"
            @click="
              page--;
              load();
            "
          >
            ‹</button
          ><span aria-current="page">{{ page }}</span
          ><button
            aria-label="下一页"
            :disabled="busy || page * result.size >= result.total"
            @click="
              page++;
              load();
            "
          >
            ›
          </button>
        </nav>
      </div>
    </section></MemberGate
  >
</template>
