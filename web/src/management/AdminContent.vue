<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { stateLabels, useMutation } from "./governance";
import type { Article, Comment, Page } from "./governance";
import type { CaseDetail } from "./types";
import { dateText } from "../lib/content";
import MarkdownView from "../components/MarkdownView.vue";
import AdminGate from "./AdminGate.vue";
import ConsolePager from "./ConsolePager.vue";
import ConsoleConfirm from "./ConsoleConfirm.vue";
import { caseListPath, consoleError } from "./console-ux";
import MutationNotice from "./MutationNotice.vue";
const route = useRoute(),
  router = useRouter();
const data = ref<Page<Article>>(),
  article = ref<Article>(),
  comments = ref<Page<Comment>>(),
  linkedCase = ref<CaseDetail>();
const selected = ref<{
  type: "articles" | "comments";
  id: number;
  revision: number;
  state: string;
}>();
const reason = ref(""),
  action = ref("HIDE"),
  error = ref(""),
  busy = ref(false),
  commentPage = ref(1);
const contextError = ref("");
let generation = 0;
const page = computed(() => Math.max(1, Number(route.query.page) || 1));
const caseId = computed(() => Number(route.query.case) || 0);
const returnId = computed(() => {
  const input = route.query.returnCase || route.query.case;
  return typeof input === "string" && /^[1-9]\d*$/.test(input) ? input : "";
});
const caseRevision = computed(() => {
  const item = linkedCase.value;
  const target = selected.value;
  if (
    !item ||
    item.kind !== "REPORT" ||
    !target ||
    item.assigneeId !== adminSession.me?.adminId ||
    !["PROCESSING", "NEEDS_ACTION"].includes(item.state)
  )
    return undefined;
  const matches = item.commentId
    ? target.type === "comments" && target.id === item.commentId
    : target.type === "articles" && target.id === item.articleId;
  return matches ? item.revision : undefined;
});
const mutation = useMutation("content", "CONTENT_MODERATE", load);
const frozen = computed(
  () => busy.value || mutation.busy.value || !!mutation.pending.value,
);
async function load() {
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  try {
    const query = new URLSearchParams({ page: String(page.value), size: "20" });
    if (typeof route.query.state === "string" && route.query.state)
      query.set("state", route.query.state);
    contextError.value = "";
    const [result, item] = await Promise.allSettled([
      adminRequest<Page<Article>>(`/admin/articles?${query}`),
      caseId.value
        ? adminRequest<CaseDetail>(`/admin/cases/${caseId.value}`)
        : undefined,
    ]);
    if (current !== generation) return;
    if (result.status === "fulfilled") data.value = result.value;
    else error.value = consoleError(result.reason);
    if (item.status === "fulfilled") linkedCase.value = item.value;
    else {
      linkedCase.value = undefined;
      contextError.value = consoleError(item.reason);
    }
    mutation.restore();
    if (/^[1-9]\d*$/.test(String(route.query.article || "")))
      await open(Number(route.query.article));
  } catch (e) {
    if (current === generation) error.value = consoleError(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function open(id: number) {
  const current = generation;
  try {
    const [a, c] = await Promise.all([
      adminRequest<Article>(`/admin/articles/${id}`),
      adminRequest<Page<Comment>>(
        `/admin/articles/${id}/comments?page=${commentPage.value}&size=10`,
      ),
    ]);
    if (current !== generation) return;
    article.value = a;
    comments.value = c;
    const commentId = Number(route.query.comment);
    const comment = c.items.find((row) => row.id === commentId);
    if (comment) select("comments", comment);
    else if (!commentId) select("articles", a);
    else {
      selected.value = undefined;
      error.value = `案件关联评论 ${commentId} 不在当前页，请翻页后核对并选择目标。`;
    }
  } catch (e) {
    if (current === generation) error.value = consoleError(e);
  }
}
function select(type: "articles" | "comments", row: Article | Comment) {
  if (mutation.pending.value) return;
  const sameTarget =
    selected.value?.type === type && selected.value.id === row.id;
  selected.value = {
    type,
    id: row.id,
    revision: row.revision,
    state: row.state,
  };
  action.value = row.state === "ADMIN_HIDDEN" ? "RESTORE" : "HIDE";
  if (!sameTarget) reason.value = "";
}
async function moderate() {
  const target = selected.value;
  const linked = caseRevision.value;
  if (!target || frozen.value || error.value) return;
  if (caseId.value && linked === undefined) {
    error.value = "请选择该举报的对象，并核对案件仍由你处理。";
    return;
  }
  if (!reason.value.trim()) {
    error.value = "请填写处置依据。";
    return;
  }
  const label = action.value === "HIDE" ? "隐藏" : "恢复";
  if (
    !(await modal.value?.ask({
      title: `${label}${target.type === "articles" ? "文章" : "评论"} ${target.id}？`,
      detail: `治理修订 ${target.revision}。${action.value === "HIDE" ? "当前公开内容将被隐藏。" : "恢复内容治理状态，保留作者原下架状态。"}该决定会记录在审计中。\n\n理由：${reason.value.trim()}`,
      action: label + "选定内容",
      danger: action.value === "HIDE",
    }))
  )
    return;
  await mutation.start(
    `/admin/${target.type}/${target.id}/moderation`,
    "POST",
    {
      action: action.value,
      expectedRevision: target.revision,
      reason: reason.value,
      ...(linked === undefined
        ? {}
        : { caseId: caseId.value, expectedCaseRevision: linked }),
    },
    action.value === "HIDE" ? "隐藏内容" : "恢复内容",
  );
}
function turn(p: number) {
  void router.push({ query: { ...route.query, page: String(p) } });
}
async function turnComments(p: number) {
  commentPage.value = p;
  if (article.value) await open(article.value.id);
}
onMounted(load);
watch(() => [route.query, adminSession.status], load);
onBeforeUnmount(() => generation++);
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
</script>
<template>
  <h1 class="console-title">内容巡查</h1>
  <AdminGate>
    <ConsoleConfirm ref="modal" />
    <p class="console-note">
      读取已有公开版本，作者草稿不进入巡查。隐藏与恢复均保留治理审计；恢复保留作者原下架状态。
    </p>
    <p v-if="returnId" class="console-note">
      <RouterLink
        :to="{
          path: '/admin/cases/' + returnId,
          query: { from: caseListPath(route.query.from), resume: 'target' },
        }"
        >返回原案件 {{ returnId }}</RouterLink
      >
    </p>
    <p v-if="caseId" class="console-note">
      本次治理关联<RouterLink :to="`/admin/cases/${caseId}`"
        >举报案件 {{ caseId }}</RouterLink
      >。
      <span v-if="caseRevision !== undefined"
        >案件修订 {{ caseRevision }}。</span
      >
    </p>
    <p v-if="contextError" class="error" role="alert">
      {{ contextError }}
      <button :disabled="busy" @click="load">重读关联案件</button>
    </p>
    <p v-if="error" class="error" role="alert">
      {{ error }} <span v-if="data">当前显示的是上次读取结果。</span>
      <button :disabled="busy" @click="load">重新读取</button>
    </p>
    <section class="console-panel console-patrol">
      <div class="console-filterbar">
        <label class="console-inline-label"
          >文章状态<select
            aria-label="巡查状态"
            :value="route.query.state || ''"
            :disabled="frozen"
            @change="
              router.push({
                query: {
                  ...route.query,
                  page: '1',
                  state: ($event.target as HTMLSelectElement).value,
                },
              })
            "
          >
            <option value="">全部</option>
            <option
              v-for="state in [
                'PUBLISHED',
                'AUTHOR_OFFLINE',
                'ADMIN_HIDDEN',
                'DELETED',
              ]"
              :key="state"
              :value="state"
            >
              {{ stateLabels[state] }}
            </option>
          </select></label
        ><button :disabled="frozen" @click="load">刷新当前状态</button>
      </div>
      <p v-if="busy" role="status">正在读取巡查内容…</p>
      <div v-if="data" class="console-table-wrap">
        <table class="console-table console-responsive-table" role="table">
          <thead role="rowgroup">
            <tr role="row">
              <th scope="col" role="columnheader">内容 / 对象</th>
              <th scope="col" role="columnheader">状态</th>
              <th scope="col" role="columnheader">治理修订</th>
              <th scope="col" role="columnheader">更新时间</th>
              <th scope="col" role="columnheader">操作</th>
            </tr>
          </thead>
          <tbody role="rowgroup">
            <tr role="row" v-for="row in data.items" :key="row.id">
              <td data-label="内容 / 对象" role="cell">
                {{ row.title }}<small>文章 {{ row.id }}</small>
              </td>
              <td data-label="状态" role="cell">
                {{ stateLabels[row.state] || row.state }}
              </td>
              <td data-label="治理修订" role="cell">{{ row.revision }}</td>
              <td data-label="更新时间" role="cell">
                {{ dateText(row.updatedAt || "") }}
              </td>
              <td data-label="操作" role="cell">
                <button
                  :disabled="frozen"
                  @click="
                    router.push({
                      query: {
                        ...route.query,
                        article: String(row.id),
                        comment: undefined,
                      },
                    })
                  "
                >
                  查看与处置
                </button>
              </td>
            </tr>
            <tr role="row" v-if="!data.items.length">
              <td role="cell" colspan="5">没有符合条件的公开版本。</td>
            </tr>
          </tbody>
        </table>
      </div>
      <ConsolePager
        v-if="data"
        :page="data.page"
        :total="data.total"
        :size="data.size"
        :busy="frozen"
        @turn="turn"
      />
    </section>
    <section
      v-if="article"
      class="console-panel console-followup console-patrol-context"
    >
      <h2>{{ article.title }} · 公开版本 {{ article.versionId }}</h2>
      <p>
        文章 {{ article.id }} ·
        {{ stateLabels[article.state] || article.state }} · 治理修订
        {{ article.revision }}
      </p>
      <MarkdownView
        v-if="article.bodyFormat === 'MARKDOWN'"
        :body="article.body || ''"
        block-images
      />
      <p v-else class="console-preserve">{{ article.body }}</p>
      <p class="hint">
        巡查合同未提供独立鉴权图片接口，此处不加载图片。案件证据图片在对应案件中读取。
      </p>
      <button :disabled="frozen" @click="select('articles', article)">
        选择此文章处置
      </button>
      <h3>文章评论</h3>
      <div class="console-table-wrap">
        <table class="console-table console-responsive-table" role="table">
          <thead role="rowgroup">
            <tr role="row">
              <th scope="col" role="columnheader">评论 / 内容</th>
              <th scope="col" role="columnheader">状态</th>
              <th scope="col" role="columnheader">修订</th>
              <th scope="col" role="columnheader">操作</th>
            </tr>
          </thead>
          <tbody role="rowgroup">
            <tr role="row" v-for="row in comments?.items" :key="row.id">
              <td data-label="评论 / 内容" role="cell" class="console-preserve">
                {{ row.id }} · {{ row.body }}
              </td>
              <td data-label="状态" role="cell">
                {{ stateLabels[row.state] || row.state }}
              </td>
              <td data-label="修订" role="cell">{{ row.revision }}</td>
              <td data-label="操作" role="cell">
                <button :disabled="frozen" @click="select('comments', row)">
                  选择此评论处置
                </button>
              </td>
            </tr>
            <tr role="row" v-if="!comments?.items.length">
              <td role="cell" colspan="4">这篇文章暂无评论。</td>
            </tr>
          </tbody>
        </table>
      </div>
      <ConsolePager
        v-if="comments"
        :page="comments.page"
        :total="comments.total"
        :size="comments.size"
        :busy="frozen"
        @turn="turnComments"
      />
    </section>
    <div class="console-split console-followup">
      <form class="console-panel" @submit.prevent="moderate">
        <h2>处置确认</h2>
        <MutationNotice
          :pending="mutation.pending.value"
          :busy="mutation.busy.value"
          :error="mutation.error.value"
          :notice="mutation.notice.value"
          @retry="mutation.submit"
          @reconcile="mutation.reconcile"
        />

        <button
          v-if="mutation.error.value && !mutation.pending.value"
          type="button"
          :disabled="busy"
          @click="load"
        >
          读取最新治理修订
        </button>
        <p>
          对象：{{
            selected
              ? `${selected.type === "articles" ? "文章" : "评论"} ${selected.id} · 修订 ${selected.revision}`
              : "待选择"
          }}
        </p>
        <fieldset
          :disabled="
            frozen ||
            !selected ||
            !mutation.allowed.value ||
            (!!caseId && caseRevision === undefined)
          "
        >
          <label
            >动作<select v-model="action" aria-label="治理动作">
              <option v-if="selected?.state === 'ADMIN_HIDDEN'" value="RESTORE">
                恢复
              </option>
              <option v-else value="HIDE">隐藏</option>
            </select></label
          ><label
            >必填理由<textarea
              v-model="reason"
              required
              maxlength="2000"
              rows="3"
              placeholder="写明已核对的事实及关联案件。"
            ></textarea></label
          ><button
            class="console-danger"
            :disabled="
              ![
                'PUBLISHED',
                'AUTHOR_OFFLINE',
                'VISIBLE',
                'ADMIN_HIDDEN',
              ].includes(selected?.state || '')
            "
          >
            {{ action === "HIDE" ? "确认隐藏" : "确认恢复" }}
          </button>
        </fieldset>
      </form>
      <p class="console-note">
        409冲突后读取当前修订，由操作者重新确认。隐藏文章不会自动限制账号，解除账号限制也不会自动恢复内容。
      </p>
    </div>
  </AdminGate>
</template>
