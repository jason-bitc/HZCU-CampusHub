<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { LocationQueryRaw } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { caseCategories, caseStates } from "./types";
import type { CasePage, CaseRow } from "./types";
import { dateText } from "../lib/content";
import { message } from "../lib/api";
import AdminGate from "./AdminGate.vue";
import ConsoleTable from "./ConsoleTable.vue";
const route = useRoute();
const router = useRouter();
const preview = computed(() => route.query.preview === "1");
const data = ref<CasePage>();
const stats = ref<number[]>([]);
const busy = ref(false);
const error = ref("");
const owner = ref("all");
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
  data.value = undefined;
  try {
    const p = new URLSearchParams({ page: value("page") || "1", size: "20" });
    for (const k of ["kind", "state", "snapshot"])
      if (value(k)) p.set(k, value(k));
    const result = await adminRequest<CasePage>(`/admin/cases?${p}`);
    if (current !== generation) return;
    data.value = result;
    const s = await Promise.all(
      ["OPEN", "PROCESSING", "NEEDS_ACTION"].map((state) =>
        adminRequest<CasePage>(
          `/admin/cases?page=1&size=1&state=${state}&snapshot=${result.snapshot}`,
        ),
      ),
    );
    if (current === generation) stats.value = s.map((r) => r.total);
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
watch(() => [route.query, adminSession.status], load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <h1 class="console-title case-list-title">待办案件</h1>
  <AdminGate :preview="preview"
    ><p class="console-note case-list-note">
      举报属于待核查线索，不会自动认定违规。案件处理需查看对应文章、评论及必要证据。
    </p>
    <div class="console-stat-grid">
      <section
        v-for="(label, i) in ['待受理', '处理中', '待后续治理']"
        :key="label"
        class="console-panel console-stat"
      >
        <span>{{ label }}</span
        ><strong>{{ preview ? [4, 2, 1][i] : (stats[i] ?? "—") }}</strong>
      </section>
    </div>
    <section class="console-panel console-case-list">
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
          >负责人（当前页）<select v-model="owner" aria-label="当前页负责人">
            <option value="all">全部</option>
            <option value="mine">仅本人</option>
            <option value="free">未分配</option>
          </select></label
        ><button
          class="console-link"
          :disabled="busy"
          @click="change('snapshot', '')"
        >
          刷新当前状态
        </button>
      </div>
      <p v-if="busy" role="status">正在读取案件快照…</p>
      <p v-if="error" class="error" role="alert">
        {{ error }} <button @click="load">重试当前查询</button>
      </p>
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
        <table class="console-table">
          <thead>
            <tr>
              <th>案件 / 类型</th>
              <th>关联对象</th>
              <th>提交时间</th>
              <th>状态 / 负责人</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.caseId">
              <td>
                {{ row.caseId }} ·
                {{ caseCategories[row.category] || row.category }}
              </td>
              <td>{{ target(row) }}</td>
              <td>{{ dateText(row.createdAt) }}</td>
              <td>
                {{ caseStates[row.state] }} /
                {{
                  row.assigneeId === null
                    ? "未分配"
                    : row.assigneeId === adminSession.me?.adminId
                      ? "本人"
                      : `管理员 ${row.assigneeId}`
                }}
              </td>
              <td>
                <RouterLink :to="`/admin/cases/${row.caseId}`">{{
                  row.state === "CLOSED" ? "查看处理记录" : "查看上下文"
                }}</RouterLink>
              </td>
            </tr>
            <tr v-if="!busy && !error && !rows.length">
              <td colspan="5" class="console-table-empty">
                当前条件下没有案件。可调整筛选或刷新。
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <nav
        v-if="data && data.total > data.size"
        class="pagination"
        aria-label="案件分页"
      >
        <button :disabled="busy || data.page <= 1" @click="turn(data.page - 1)">
          上一页</button
        ><span
          >第 {{ data.page }} / {{ Math.ceil(data.total / data.size) }} 页</span
        ><button
          :disabled="busy || data.page * data.size >= data.total"
          @click="turn(data.page + 1)"
        >
          下一页
        </button>
      </nav>
      <p class="hint">
        列表按同一快照翻页；详情显示当前状态。处理后刷新列表以获取新快照。
      </p>
    </section></AdminGate
  >
</template>
