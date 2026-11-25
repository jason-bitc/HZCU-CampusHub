<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { LocationQueryRaw } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { caseCategories, caseKinds, caseStates } from "./types";
import type { CasePage, CaseRow } from "./types";
import { dateText } from "../lib/content";
import { message } from "../lib/api";
import AdminGate from "./AdminGate.vue";
import ConsolePageHeader from "./ConsolePageHeader.vue";
import ConsolePager from "./ConsolePager.vue";
const route = useRoute();
const router = useRouter();
const preview = computed(() => route.query.preview === "1");
const data = ref<CasePage>();
interface Overview {
  openCases: number;
  processingCases: number;
  needsActionCases: number;
  hiddenArticles: number;
}
const stats = ref<number[]>([]);
const statsError = ref("");
const busy = ref(false);
const error = ref("");
const owner = ref("all");
const keyword = ref("");
const formState = ref("");
const formKind = ref("");
let generation = 0;
function value(name: string) {
  return typeof route.query[name] === "string" ? route.query[name] : "";
}
function assignee(row: CaseRow) {
  return row.assigneeId === null
    ? "未认领"
    : row.assigneeId === adminSession.me?.adminId
      ? "本人"
      : `管理员 ${row.assigneeId}`;
}
function target(row: CaseRow) {
  return row.targetType === "COMMENT"
    ? `评论 CMT-${row.commentId}`
    : row.targetType === "ARTICLE"
      ? `文章 ART-${row.articleId}`
      : "无关联对象";
}
function tone(state: string) {
  return (
    {
      OPEN: "is-warning",
      PROCESSING: "is-positive",
      NEEDS_ACTION: "is-neutral",
      CLOSED: "is-positive",
    }[state] || "is-neutral"
  );
}
const keywordMatch = (row: CaseRow) => {
  const text = keyword.value.trim().toLowerCase();
  if (!text) return true;
  return [
    `case-${row.caseId}`,
    target(row),
    caseCategories[row.category] || row.category,
    caseStates[row.state] || row.state,
    assignee(row),
  ].some((part) => part.toLowerCase().includes(text));
};
const rows = computed(
  () =>
    data.value?.items.filter(
      (row) =>
        keywordMatch(row) &&
        (owner.value === "all" ||
          (owner.value === "mine" &&
            row.assigneeId === adminSession.me?.adminId) ||
          (owner.value === "free" && row.assigneeId === null)),
    ) || [],
);
const previewRows = [
  {
    caseId: 21,
    category: "ADVERTISEMENT",
    targetType: "ARTICLE",
    articleId: 18,
    commentId: null,
    state: "OPEN",
    assigneeId: null,
    createdAt: "2026-11-24T09:20:00+08:00",
  },
  {
    caseId: 24,
    category: "MISLEADING_CONTENT",
    targetType: "ARTICLE",
    articleId: 18,
    commentId: null,
    state: "PROCESSING",
    assigneeId: 1,
    createdAt: "2026-11-23T16:40:00+08:00",
  },
  {
    caseId: 19,
    category: "ADVERTISEMENT",
    targetType: "COMMENT",
    articleId: 18,
    commentId: 112,
    state: "CLOSED",
    assigneeId: 2,
    createdAt: "2026-11-19T10:05:00+08:00",
  },
] as CaseRow[];
const visible = computed(() =>
  preview.value ? previewRows.filter((r) => keywordMatch(r)) : rows.value,
);
const condition = computed(() =>
  [
    formState.value ? caseStates[formState.value] : "",
    formKind.value ? caseKinds[formKind.value] : "",
  ]
    .filter(Boolean)
    .join("、") || "全部案件",
);
const summary = computed(() =>
  preview.value
    ? [4, 2, 1]
    : stats.value.length
      ? stats.value
      : ["—", "—", "—", "—"],
);
function sync() {
  formState.value = value("state");
  formKind.value = value("kind");
  keyword.value = value("keyword");
  owner.value = value("owner") || "all";
}
function apply() {
  const q: LocationQueryRaw = {};
  if (formKind.value) q.kind = formKind.value;
  if (formState.value) q.state = formState.value;
  if (keyword.value.trim()) q.keyword = keyword.value.trim();
  if (owner.value !== "all") q.owner = owner.value;
  const same =
    (formKind.value || "") === value("kind") &&
    (formState.value || "") === value("state") &&
    keyword.value.trim() === value("keyword") &&
    (owner.value === "all" ? "" : owner.value) === value("owner");
  if (same) void load();
  else void router.push({ path: "/admin/cases", query: q });
}
function reset() {
  formKind.value = "";
  formState.value = "";
  keyword.value = "";
  owner.value = "all";
  if (!["kind", "state", "keyword", "owner", "page", "snapshot"].some(value)) void load();
  else void router.push("/admin/cases");
}
async function load() {
  if (preview.value || adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  statsError.value = "";
  try {
    const p = new URLSearchParams({ page: value("page") || "1", size: "20" });
    for (const k of ["kind", "state", "snapshot"])
      if (value(k)) p.set(k, value(k));
    const [cases, overview] = await Promise.allSettled([
      adminRequest<CasePage>(`/admin/cases?${p}`),
      adminRequest<Overview>("/admin/overview"),
    ]);
    if (current !== generation) return;
    if (cases.status === "fulfilled") data.value = cases.value;
    else error.value = message(cases.reason);
    if (overview.status === "fulfilled") {
      const v = overview.value;
      stats.value = [
        v.openCases,
        v.processingCases,
        v.needsActionCases,
        v.hiddenArticles,
      ];
    } else statsError.value = message(overview.reason);
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
function turn(page: number) {
  void router.push({
    path: "/admin/cases",
    query: {
      ...route.query,
      page: String(page),
      snapshot: String(data.value?.snapshot || 0),
    },
  });
}
onMounted(load);
watch(
  () => route.query,
  () => {
    sync();
    void load();
  },
);
watch(() => adminSession.status, load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <AdminGate :preview="preview">
    <div class="console-page">
      <ConsolePageHeader
        title="案件队列"
        description="按状态、类型或内容定位案件，进入详情核对举报依据。"
      />
      <form class="console-panel console-filter-card" @submit.prevent="apply">
        <label class="console-filter"
          >关键词<input
            v-model="keyword"
            placeholder="案件编号或对象"
            aria-label="关键词" /></label
        ><label class="console-filter"
          >状态<select v-model="formState" aria-label="案件状态">
            <option value="">全部状态</option>
            <option
              v-for="(label, state) in caseStates"
              :key="state"
              :value="state"
            >
              {{ label }}
            </option></select></label
        ><label class="console-filter"
          >类型<select v-model="formKind" aria-label="案件类型">
            <option value="">全部类型</option>
            <option v-for="(label, kind) in caseKinds" :key="kind" :value="kind">
              {{ label }}
            </option></select></label
        ><label class="console-filter"
          >认领人（当前页）<select v-model="owner" aria-label="当前页认领人">
            <option value="all">全部</option>
            <option value="mine">仅本人</option>
            <option value="free">未认领</option>
          </select></label
        >
        <div class="console-filter-actions">
          <button class="console-primary" type="submit" :disabled="busy">
            查询
          </button>
          <button type="button" :disabled="busy" @click="reset">重置</button>
        </div>
      </form>
      <p v-if="statsError && !preview" class="console-banner is-warning" role="status">
        总览读取失败。{{ statsError }}
      </p>
      <section class="console-panel" aria-labelledby="case-result-title">
        <div class="console-card-head">
          <div>
            <h2 id="case-result-title" class="console-card-title">案件结果</h2>
            <p class="console-card-desc">
              当前条件：{{ condition }} · 待受理 {{ summary[0] }} · 处理中
              {{ summary[1] }} · 待后续治理 {{ summary[2] }}<template
                v-if="!preview"
              >
                · 隐藏文章 {{ summary[3] }}</template
              >
            </p>
          </div>
        </div>
        <p v-if="busy" class="console-muted" role="status" style="margin-top: 12px">
          正在读取案件快照…
        </p>
        <p v-if="error" class="console-banner is-error" role="alert" style="margin-top: 12px">
          {{ error }}<span v-if="data">。当前显示上次读取的结果。</span>
          <span class="console-banner-actions"
            ><button type="button" @click="load">重试当前查询</button></span
          >
        </p>
        <div class="console-table-wrap">
          <table class="console-table">
            <thead>
              <tr>
                <th>案件编号</th>
                <th>举报对象</th>
                <th>类型</th>
                <th>状态</th>
                <th>认领人</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in visible" :key="row.caseId">
                <td class="num">CASE-{{ row.caseId }}</td>
                <td>{{ target(row) }}</td>
                <td>{{ caseCategories[row.category] || row.category }}</td>
                <td>
                  <span class="console-tag" :class="tone(row.state)">{{
                    caseStates[row.state] || row.state
                  }}</span>
                </td>
                <td>
                  {{ assignee(row) }}
                  <small v-if="!preview">{{ dateText(row.createdAt) }}</small>
                </td>
                <td>
                  <RouterLink :to="{ path: `/admin/cases/${row.caseId}`, query: { from: route.fullPath } }">查看案件</RouterLink>
                </td>
              </tr>
              <tr v-if="!busy && !error && !visible.length">
                <td colspan="6" class="console-table-empty">
                  当前条件下没有案件。可调整筛选或刷新。
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
          :busy="busy"
          @turn="turn"
        />
        <p class="console-footnote">
          列表按同一快照翻页；详情显示当前状态。处理后刷新列表以获取新快照。关键词与认领人仅过滤当前页结果。
        </p>
      </section>
    </div>
  </AdminGate>
</template>
