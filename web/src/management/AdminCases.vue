<script lang="ts">
let listOrigin:
  | { path: string; id: string; top: number; y: number; epoch: number }
  | undefined;
</script>
<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import type { LocationQueryRaw } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { caseCategories, caseStates } from "./types";
import type { CasePage, CaseRow } from "./types";
import { dateText } from "../lib/content";
import { message } from "../lib/api";
import AdminGate from "./AdminGate.vue";
import { consoleError } from "./console-ux";
import ConsoleReadState from "./ConsoleReadState.vue";
import ConsolePager from "./ConsolePager.vue";
import ConsoleTable from "./ConsoleTable.vue";
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
const owner = computed(() => value("owner") || "all");
const listPanel = ref<HTMLElement>();
const hasFilters = computed(
  () => owner.value !== "all" || !!value("kind") || !!value("state"),
);
let generation = 0;
function value(name: string) {
  return typeof route.query[name] === "string" ? route.query[name] : "";
}
const rows = computed(
  () =>
    data.value?.items.filter(
      (row) =>
        owner.value === "all" ||
        (owner.value === "mine" &&
          row.assigneeId === adminSession.me?.adminId) ||
        (owner.value === "free" && row.assigneeId === null),
    ) || [],
);
function change(key: string, text: string) {
  const q: LocationQueryRaw = { ...route.query, page: "1" };
  delete q.snapshot;
  if (text) Object.assign(q, { [key]: text });
  else delete q[key];
  void router.push({ path: "/admin/cases", query: q });
}
function target(row: CaseRow) {
  return row.targetType === "COMMENT"
    ? `评论 ${row.commentId}`
    : row.targetType === "ARTICLE"
      ? `文章 ${row.articleId}`
      : "无关联目标";
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
    if (cases.status === "fulfilled") {
      data.value = cases.value;
      await nextTick();
      if (current === generation) restoreOrigin();
    } else error.value = consoleError(cases.reason);
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
    if (current === generation) error.value = consoleError(e);
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
function restoreOrigin() {
  const origin = listOrigin;
  if (
    !origin ||
    origin.epoch !== adminSession.epoch ||
    origin.path !== route.fullPath
  )
    return;
  listOrigin = undefined;
  requestAnimationFrame(() => {
    if (route.fullPath !== origin.path) return;
    const link = document.getElementById(origin.id);
    if (!link) {
      listPanel.value?.focus({ preventScroll: true });
      window.scrollTo(0, origin.y);
      return;
    }
    link.focus({ preventScroll: true });
    window.scrollBy(0, link.getBoundingClientRect().top - origin.top);
  });
}
onBeforeRouteLeave((to) => {
  const id = to.params.id;
  if (!id || !to.path.startsWith("/admin/cases/")) return;
  const key = "case-link-" + id;
  const link = document.getElementById(key);
  if (link)
    listOrigin = {
      path: route.fullPath,
      id: key,
      top: link.getBoundingClientRect().top,
      y: window.scrollY,
      epoch: adminSession.epoch,
    };
});
onMounted(load);
watch(() => [route.query, adminSession.status], load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <h1 class="console-title case-list-title">待办案件</h1>
  <AdminGate :preview="preview"
    ><p class="console-note case-list-note">
      举报属于待核查线索，不会自动认定违规。案件处理需查看对应文章、评论及必要证据。
    </p>
    <p v-if="statsError && !preview" class="error" role="alert">
      总览读取失败。{{ statsError }}
    </p>
    <div class="console-stat-grid" :class="{ 'console-live-stats': !preview }">
      <section
        v-for="(label, i) in preview
          ? ['待受理', '处理中', '待后续治理']
          : ['待受理', '处理中', '待后续治理', '隐藏文章']"
        :key="label"
        class="console-panel console-stat"
      >
        <span>{{ label }}</span
        ><strong>{{ preview ? [4, 2, 1][i] : (stats[i] ?? "—") }}</strong>
      </section>
    </div>
    <section
      ref="listPanel"
      tabindex="-1"
      class="console-panel console-case-list"
    >
      <div class="console-filterbar">
        <label class="console-inline-label"
          >类型<select
            aria-label="案件类型"
            :value="value('kind')"
            @change="change('kind', ($event.target as HTMLSelectElement).value)"
          >
            <option value="">全部类型</option>
            <option value="REPORT">举报</option>
            <option value="FEEDBACK">使用反馈</option>
          </select></label
        ><label class="console-inline-label"
          >状态<select
            aria-label="案件状态"
            :value="value('state')"
            @change="
              change('state', ($event.target as HTMLSelectElement).value)
            "
          >
            <option value="">全部状态</option>
            <option
              v-for="(label, state) in caseStates"
              :key="state"
              :value="state"
            >
              {{ label }}
            </option>
          </select></label
        ><label class="console-inline-label"
          >负责人（当前页）<select
            :value="owner"
            aria-label="当前页负责人"
            @change="
              change('owner', ($event.target as HTMLSelectElement).value)
            "
          >
            <option value="all">全部</option>
            <option value="mine">仅本人</option>
            <option value="free">未分配</option>
          </select></label
        ><button class="console-link" :disabled="busy" @click="load">
          刷新当前状态
        </button>
      </div>
      <ConsoleReadState
        :loading="busy"
        :error="error"
        :stale="!!data"
        label="正在读取案件快照…"
        @retry="load"
      />
      <ConsoleTable
        v-if="preview"
        :columns="[
          '案件 / 类型',
          '关联对象',
          '提交时间',
          '状态 / 负责人',
          '操作',
        ]"
        :rows="[
          [
            'DEMO-C102 · 广告举报',
            '文章 DEMO-001',
            '11月4日 09:20',
            '待受理 / 未分配',
            '查看上下文',
          ],
          [
            'DEMO-C098 · 内容举报',
            '评论 DEMO-092',
            '11月4日 08:55',
            '处理中 / 管理员甲',
            '继续处理',
          ],
          [
            'DEMO-F090 · 使用反馈',
            '图片上传问题',
            '11月3日 20:10',
            '已完成 / 管理员乙',
            '查看处理记录',
          ],
        ]"
        :targets="['/admin/cases/102', '/admin/cases/102', '/admin/cases/102']"
      />
      <div v-else class="console-table-wrap">
        <table class="console-table console-responsive-table" role="table">
          <thead role="rowgroup">
            <tr role="row">
              <th scope="col" role="columnheader">案件 / 类型</th>
              <th scope="col" role="columnheader">关联对象</th>
              <th scope="col" role="columnheader">提交时间</th>
              <th scope="col" role="columnheader">状态 / 负责人</th>
              <th scope="col" role="columnheader">操作</th>
            </tr>
          </thead>
          <tbody role="rowgroup">
            <tr v-for="row in rows" :key="row.caseId" role="row">
              <td data-label="案件 / 类型" role="cell">
                {{ row.caseId }} ·
                {{ caseCategories[row.category] || row.category }}
              </td>
              <td data-label="关联对象" role="cell">{{ target(row) }}</td>
              <td data-label="提交时间" role="cell">
                {{ dateText(row.createdAt) }}
              </td>
              <td data-label="状态 / 负责人" role="cell">
                {{ caseStates[row.state] }} /
                {{
                  row.assigneeId === null
                    ? "未分配"
                    : row.assigneeId === adminSession.me?.adminId
                      ? "本人"
                      : `管理员 ${row.assigneeId}`
                }}
              </td>
              <td data-label="操作" role="cell">
                <RouterLink
                  :id="'case-link-' + row.caseId"
                  :to="{
                    path: `/admin/cases/${row.caseId}`,
                    query: { from: route.fullPath },
                  }"
                  >{{
                    row.state === "CLOSED" ? "查看处理记录" : "查看上下文"
                  }}</RouterLink
                >
              </td>
            </tr>
            <tr v-if="!busy && !error && data && !rows.length" role="row">
              <td colspan="5" class="console-table-empty">
                {{
                  hasFilters
                    ? "当前筛选没有匹配案件。可调整筛选或刷新。"
                    : "尚无案件，收到举报或反馈后将在这里显示。"
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <ConsolePager
        v-if="data && data.total > data.size"
        :page="data.page"
        :total="data.total"
        :size="data.size"
        :busy="busy || !!error"
        @turn="turn"
      />
      <p class="hint">
        列表按同一快照翻页；详情显示当前状态。处理后刷新列表以获取新快照。
      </p>
    </section></AdminGate
  >
</template>
