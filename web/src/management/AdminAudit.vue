<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import type { GovernanceEvent, Page } from "./governance";
import { stateLabels } from "./governance";
import { dateText } from "../lib/content";
import { message } from "../lib/api";
import AdminGate from "./AdminGate.vue";
import ConsolePageHeader from "./ConsolePageHeader.vue";
import ConsolePager from "./ConsolePager.vue";
const route = useRoute(),
  router = useRouter(),
  data = ref<Page<GovernanceEvent>>(),
  error = ref(""),
  busy = ref(false);
const draft = ref({ keyword: "", action: "", target: "" }),
  applied = ref({ ...draft.value });
let generation = 0;
const actions = computed(() => [
  ...new Set((data.value?.items || []).map((row) => row.action)),
]);
const targets = computed(() => [
  ...new Set((data.value?.items || []).map((row) => row.targetType)),
]);
const rows = computed(() =>
  (data.value?.items || []).filter((row) => {
    const text = applied.value.keyword.trim().toLowerCase();
    if (
      text &&
      !`${row.id} ${row.targetType} ${row.targetId} ${row.canonicalId || ""} ${row.caseId || ""} ${row.action} ${row.reason}`
        .toLowerCase()
        .includes(text)
    )
      return false;
    if (applied.value.action && row.action !== applied.value.action)
      return false;
    if (applied.value.target && row.targetType !== applied.value.target)
      return false;
    return true;
  }),
);
async function load() {
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  data.value = undefined;
  try {
    const result = await adminRequest<Page<GovernanceEvent>>(
      `/admin/governance-events?page=${Math.max(1, Number(route.query.page) || 1)}&size=20`,
    );
    if (current === generation) data.value = result;
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
function apply() {
  applied.value = { ...draft.value };
}
function reset() {
  draft.value = { keyword: "", action: "", target: "" };
  applied.value = { ...draft.value };
}
function turn(page: number) {
  void router.push({ query: { ...route.query, page: String(page) } });
}
onMounted(load);
watch(() => route.query, load);
watch(() => adminSession.status, load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <AdminGate>
    <div class="console-page">
      <ConsolePageHeader
        title="审计记录"
        description="文章、评论和分类的治理记录；账号限制、申诉与案件历史在各自页面核对。"
      />
      <p v-if="error" class="console-banner is-error" role="alert">
        {{ error }}
        <span class="console-banner-actions"
          ><button type="button" :disabled="busy" @click="load">
            重新读取</button
          ></span
        >
      </p>
      <form
        class="console-panel console-filter-card"
        aria-label="筛选审计记录"
        @submit.prevent="apply"
      >
        <label class="console-filter"
          >关键词<input
            v-model="draft.keyword"
            placeholder="记录、对象、案件或理由"
            aria-label="关键词" /></label
        ><label class="console-filter"
          >动作<select v-model="draft.action" aria-label="动作">
            <option value="">全部</option>
            <option v-for="item in actions" :key="item" :value="item">
              {{ item }}
            </option></select></label
        ><label class="console-filter"
          >对象<select v-model="draft.target" aria-label="对象类型">
            <option value="">全部</option>
            <option v-for="item in targets" :key="item" :value="item">
              {{ item }}
            </option></select></label
        >
        <div class="console-filter-actions">
          <button class="console-primary" type="submit">查询</button>
          <button type="button" :disabled="busy" @click="reset">重置</button>
          <button type="button" :disabled="busy" @click="load">
            刷新治理记录
          </button>
        </div>
        <p class="console-form-note" style="flex-basis: 100%">
          动作与对象选项来自当前页记录。
        </p>
      </form>
      <section class="console-panel" aria-labelledby="audit-list-title">
        <div class="console-card-head">
          <div>
            <h2 id="audit-list-title" class="console-card-title">治理审计</h2>
            <p class="console-card-desc">
              每条记录保留操作人、前后状态、修订与理由。
            </p>
          </div>
        </div>
        <p v-if="busy" class="console-muted" role="status">
          正在读取审计记录…
        </p>
        <div v-if="data" class="console-table-wrap">
          <table class="console-table">
            <thead>
              <tr>
                <th>发生时间</th>
                <th>操作人</th>
                <th>对象</th>
                <th>动作 · 修订</th>
                <th>前后状态</th>
                <th>理由</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td>
                  {{ dateText(row.occurredAt)
                  }}<small class="console-inline-code">记录 {{ row.id }}</small>
                </td>
                <td>管理 {{ row.adminId }}</td>
                <td>
                  {{ row.targetType }} {{ row.targetId }}
                  <small v-if="row.canonicalId">归并至分类 {{ row.canonicalId }}</small>
                  <small v-if="row.caseId">
                    <RouterLink :to="`/admin/cases/${row.caseId}`"
                      >案件 {{ row.caseId }}</RouterLink
                    >
                    · 修订 {{ row.caseRevision }}
                  </small>
                </td>
                <td class="num">{{ row.action }} · {{ row.revision }}</td>
                <td>
                  {{ stateLabels[row.beforeState] || row.beforeState }} →
                  {{ stateLabels[row.afterState] || row.afterState }}
                </td>
                <td class="console-preserve">{{ row.reason }}</td>
              </tr>
              <tr v-if="!busy && !rows.length">
                <td colspan="6" class="console-table-empty">
                  {{ data.items.length ? "当前页没有匹配的记录。" : "暂无治理记录。" }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <ConsolePager
          v-if="data"
          :page="data.page"
          :size="data.size"
          :total="data.total"
          :busy="busy"
          @turn="turn"
        />
      </section>
    </div>
  </AdminGate>
</template>
