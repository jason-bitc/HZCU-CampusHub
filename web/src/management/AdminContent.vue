<script setup lang="ts">
import ConsoleConfirm from "./ConsoleConfirm.vue";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { stateLabels, useMutation } from "./governance";
import type { Article, Comment, Page } from "./governance";
import type { CaseDetail } from "./types";
import { message } from "../lib/api";
import MarkdownView from "../components/MarkdownView.vue";
import AdminGate from "./AdminGate.vue";
import ConsolePageHeader from "./ConsolePageHeader.vue";
import ConsolePager from "./ConsolePager.vue";
import ConsoleSteps from "./ConsoleSteps.vue";
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
  commentPage = ref(1),
  keyword = ref(""),
  formState = ref("");
let generation = 0;
const page = computed(() => Math.max(1, Number(route.query.page) || 1));
const caseId = computed(() => Number(route.query.case) || 0);
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
const rows = computed(() =>
  (data.value?.items || []).filter((row) => {
    const text = keyword.value.trim().toLowerCase();
    return (
      !text ||
      row.title.toLowerCase().includes(text) ||
      `art-${row.id}`.includes(text)
    );
  }),
);
const tone = (state: string) =>
  ({
    PUBLISHED: "is-positive",
    VISIBLE: "is-positive",
    ADMIN_HIDDEN: "is-negative",
    AUTHOR_OFFLINE: "is-neutral",
    DELETED: "is-neutral",
  })[state] || "is-neutral";
const targetLabel = computed(() =>
  selected.value
    ? `${selected.value.type === "articles" ? "文章" : "评论"} ${
        selected.value.type === "articles" ? "ART" : "CMT"
      }-${selected.value.id}`
    : "待选择",
);
const back = computed(() =>
  caseId.value
    ? { label: "返回原案件", to: `/admin/cases/${caseId.value}` }
    : { label: "返回治理列表", to: "/admin/content" },
);
function syncForm() {
  formState.value = typeof route.query.state === "string" ? route.query.state : "";
}
function apply() {
  const q = { ...route.query } as Record<string, string | undefined>;
  delete q.article;
  delete q.comment;
  q.page = "1";
  q.state = formState.value || undefined;
  const same = (formState.value || "") === (route.query.state || "");
  if (same) void load();
  else void router.push({ query: q });
}
function reset() {
  formState.value = "";
  keyword.value = "";
  if (!route.query.state) void load();
  else
    void router.push({
      query: { case: route.query.case, page: "1", state: undefined },
    });
}
async function load() {
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  data.value = undefined;
  article.value = undefined;
  comments.value = undefined;
  selected.value = undefined;
  linkedCase.value = undefined;
  try {
    const query = new URLSearchParams({ page: String(page.value), size: "20" });
    if (typeof route.query.state === "string" && route.query.state)
      query.set("state", route.query.state);
    const [result, item] = await Promise.all([
      adminRequest<Page<Article>>(`/admin/articles?${query}`),
      caseId.value
        ? adminRequest<CaseDetail>(`/admin/cases/${caseId.value}`)
        : undefined,
    ]);
    if (current !== generation) return;
    data.value = result;
    linkedCase.value = item;
    mutation.restore();
    if (/^[1-9]\d*$/.test(String(route.query.article || "")))
      await open(Number(route.query.article));
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function open(id: number) {
  const current = generation;
  error.value = "";
  article.value = undefined;
  comments.value = undefined;
  selected.value = undefined;
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
    else
      error.value = `案件关联评论 ${commentId} 不在当前页，请翻页后核对并选择目标。`;
  } catch (e) {
    if (current === generation) error.value = message(e);
  }
}
function select(type: "articles" | "comments", row: Article | Comment) {
  if (mutation.pending.value) return;
  selected.value = {
    type,
    id: row.id,
    revision: row.revision,
    state: row.state,
  };
  action.value = row.state === "ADMIN_HIDDEN" ? "RESTORE" : "HIDE";
  reason.value = "";
}
function pick(event: Event) {
  const [type, raw] = (event.target as HTMLSelectElement).value.split(":");
  const id = Number(raw);
  const row =
    type === "articles"
      ? article.value
      : comments.value?.items.find((item) => item.id === id);
  if (row) select(type as "articles" | "comments", row);
}
async function moderate() {
  const target = selected.value;
  const linked = caseRevision.value;
  if (!target || frozen.value) return;
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
watch(
  () => route.query,
  () => {
    syncForm();
    void load();
  },
);
watch(() => adminSession.status, load);
onBeforeUnmount(() => generation++);
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
</script>
<template>
  <AdminGate>
    <div class="console-page">
      <ConsoleConfirm ref="modal" />
      <template v-if="article">
        <ConsolePageHeader
          variant="plain"
          :back="back"
          :title="`关联内容 · 确认${action === 'HIDE' ? '隐藏' : '恢复'}`"
          :meta="`来源案件 CASE-${caseId || '—'} · 目标文章 ART-${article.id} · 当前公开版本 v${article.versionId ?? article.revision}`"
        />
        <ConsoleSteps
          v-if="caseId"
          :steps="['事实与证据', '处理决定', '复核提交']"
          :current="1"
        />
        <p v-if="caseId" class="console-banner is-info" role="status">
          本次治理关联举报案件 CASE-{{ caseId }}<template v-if="caseRevision !== undefined">
            · 案件修订 {{ caseRevision }}</template
          >。完成后返回案件，后端核对治理事件后才允许闭案。
        </p>
      </template>
      <ConsolePageHeader
        v-else
        title="文章与评论治理"
        description="读取已有公开版本，作者草稿不进入治理。隐藏与恢复均保留治理审计。"
      />
      <p v-if="error" class="console-banner is-error" role="alert">
        {{ error }}
        <span class="console-banner-actions"
          ><button type="button" :disabled="busy" @click="load">
            重新读取</button
          ></span
        >
      </p>
      <MutationNotice
        :pending="mutation.pending.value"
        :busy="mutation.busy.value"
        :error="mutation.error.value"
        :notice="mutation.notice.value"
        @retry="mutation.submit"
        @reconcile="mutation.reconcile"
      />
      <p v-if="busy" class="console-muted" role="status">
        正在读取治理内容…
      </p>
      <template v-if="!article">
        <form class="console-panel console-filter-card" @submit.prevent="apply">
          <label class="console-filter"
            >关键词<input
              v-model="keyword"
              placeholder="标题或文章编号"
              aria-label="关键词" /></label
          ><label class="console-filter"
            >状态<select v-model="formState" aria-label="治理状态">
              <option value="">全部状态</option>
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
              </option></select></label
          >
          <div class="console-filter-actions">
            <button class="console-primary" type="submit" :disabled="frozen">
              查询
            </button>
            <button type="button" :disabled="frozen" @click="reset">
              重置
            </button>
          </div>
        </form>
        <section class="console-panel" aria-labelledby="governance-list-title">
          <div class="console-card-head">
            <div>
              <h2 id="governance-list-title" class="console-card-title">
                治理对象
              </h2>
              <p class="console-card-desc">
                从案件进入时保留原案件上下文。<template v-if="caseId">
                  当前关联案件 CASE-{{ caseId }}。</template
                >
              </p>
            </div>
          </div>
          <div v-if="data" class="console-table-wrap">
            <table class="console-table">
              <thead>
                <tr>
                  <th>对象编号</th>
                  <th>内容</th>
                  <th>类型</th>
                  <th>状态</th>
                  <th>关联案件</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.id">
                  <td class="num">ART-{{ row.id }}</td>
                  <td class="wrap">{{ row.title }}</td>
                  <td>文章</td>
                  <td>
                    <span class="console-tag" :class="tone(row.state)">{{
                      stateLabels[row.state] || row.state
                    }}</span>
                  </td>
                  <td class="num">
                    {{ caseId ? `CASE-${caseId}` : "—" }}
                  </td>
                  <td>
                    <button
                      class="console-link"
                      type="button"
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
                      查看并处置
                    </button>
                  </td>
                </tr>
                <tr v-if="!busy && !rows.length">
                  <td colspan="6" class="console-table-empty">
                    没有符合条件的公开版本。
                  </td>
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
          <p class="console-footnote">
            读取已有公开版本，作者草稿不进入治理。隐藏与恢复均保留治理审计；恢复保留作者原下架状态。关键词仅过滤当前页结果。
          </p>
        </section>
      </template>
      <div v-else class="console-split">
        <section class="console-panel" aria-label="治理对象">
          <h2 class="console-card-title">治理对象</h2>
          <p class="console-card-desc">
            {{ caseId ? "源自案件固定快照。" : "读取当前公开版本。" }}
          </p>
          <dl class="console-facts">
            <div>
              <dt>目标文章</dt>
              <dd>ART-{{ article.id }} · {{ article.title }}</dd>
            </div>
            <div>
              <dt>当前公开版本</dt>
              <dd>
                v{{ article.versionId ?? article.revision }} · 治理修订
                {{ article.revision }}
              </dd>
            </div>
            <div>
              <dt>当前状态</dt>
              <dd>
                <span class="console-tag" :class="tone(article.state)">{{
                  stateLabels[article.state] || article.state
                }}</span>
              </dd>
            </div>
            <div v-if="caseId">
              <dt>关联案件</dt>
              <dd>
                <RouterLink :to="`/admin/cases/${caseId}`"
                  >CASE-{{ caseId }}</RouterLink
                >
              </dd>
            </div>
          </dl>
          <p class="console-callout">
            <strong>影响 · </strong>隐藏后公开阅读入口不可用。案件快照及处理依据仍供有权管理员复核。
          </p>
          <div class="console-case-content">
            <MarkdownView
              v-if="article.bodyFormat === 'MARKDOWN'"
              :body="article.body || ''"
              block-images
            />
            <p v-else class="console-preserve">{{ article.body }}</p>
          </div>
          <p class="console-footnote">
            巡查合同未提供独立鉴权图片接口，此处不加载图片。案件证据图片在对应案件中读取。
          </p>
          <h3 class="console-sub-title">文章评论</h3>
          <div class="console-table-wrap" style="margin-top: 8px">
            <table class="console-table">
              <thead>
                <tr>
                  <th>评论</th>
                  <th>内容</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in comments?.items" :key="row.id">
                  <td class="num">CMT-{{ row.id }}</td>
                  <td class="wrap console-preserve">{{ row.body }}</td>
                  <td>
                    <span class="console-tag" :class="tone(row.state)">{{
                      stateLabels[row.state] || row.state
                    }}</span>
                  </td>
                  <td>
                    <button
                      class="console-link"
                      type="button"
                      :disabled="frozen"
                      @click="select('comments', row)"
                    >
                      选择此评论处置
                    </button>
                  </td>
                </tr>
                <tr v-if="!comments?.items.length">
                  <td colspan="4" class="console-table-empty">
                    这篇文章暂无评论。
                  </td>
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
        <form
          id="content-moderation-form"
          class="console-panel"
          @submit.prevent="moderate"
        >
          <h2 class="console-card-title">
            确认{{ action === "HIDE" ? "隐藏" : "恢复"
            }}{{ selected?.type === "comments" ? "这条评论" : "这篇文章" }}
          </h2>
          <p class="console-card-desc">将记录理由、操作者和治理事件。</p>
          <fieldset
            class="console-form"
            :disabled="
              frozen ||
              !selected ||
              !mutation.allowed.value ||
              (!!caseId && caseRevision === undefined)
            "
          >
            <label class="console-field"
              ><span>治理对象</span
              ><select
                aria-label="治理对象"
                :value="selected ? `${selected.type}:${selected.id}` : ''"
                @change="pick"
              >
                <option :value="`articles:${article.id}`">
                  文章 ART-{{ article.id }}
                </option>
                <option
                  v-for="row in comments?.items"
                  :key="row.id"
                  :value="`comments:${row.id}`"
                >
                  评论 CMT-{{ row.id }}
                </option>
              </select></label
            ><label class="console-field"
              ><span>治理动作</span
              ><select v-model="action" aria-label="治理动作">
                <option
                  v-if="selected?.state === 'ADMIN_HIDDEN'"
                  value="RESTORE"
                >
                  恢复公开
                </option>
                <option v-else value="HIDE">隐藏内容</option>
              </select></label
            ><label class="console-field"
              ><span>必填理由</span
              ><textarea
                v-model="reason"
                required
                maxlength="2000"
                rows="3"
                placeholder="写明已核对的事实及关联案件。"
              ></textarea></label
            >
            <p class="console-form-note">
              对象：{{ targetLabel }} · 修订 {{ selected?.revision ?? "—" }}
            </p>
          </fieldset>
          <p class="console-card-foot">
            409冲突后读取当前修订，由操作者重新确认。隐藏文章不会自动限制账号，解除账号限制也不会自动恢复内容。
          </p>
        </form>
      </div>
      <footer v-if="article && !mutation.pending.value" class="console-footer">
        <div class="console-footer-actions">
          <RouterLink class="button console-secondary" :to="back.to">{{
            back.label
          }}</RouterLink>
          <button
            class="console-danger"
            type="submit"
            form="content-moderation-form"
            :disabled="
              frozen ||
              !selected ||
              !mutation.allowed.value ||
              (!!caseId && caseRevision === undefined) ||
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
        </div>
      </footer>
    </div>
  </AdminGate>
</template>
