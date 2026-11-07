<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import type { GovernanceEvent, Page } from "./governance";
import { stateLabels } from "./governance";
import { dateText } from "../lib/content";
import { message } from "../lib/api";
import AdminGate from "./AdminGate.vue";
import ConsolePager from "./ConsolePager.vue";
const route = useRoute(),
  router = useRouter(),
  data = ref<Page<GovernanceEvent>>(),
  error = ref(""),
  busy = ref(false);
let generation = 0;
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
onMounted(load);
watch(() => [route.query, adminSession.status], load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <h1 class="console-title">治理审计与操作结果</h1>
  <AdminGate
    ><p class="console-note">
      此列表为文章、评论和分类的真实事务治理记录。账号限制和申诉通过对应页面读取；案件处理历史在案件详情中核对。
    </p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <section class="console-panel console-audit">
      <button :disabled="busy" @click="load">刷新治理记录</button>
      <p v-if="busy" role="status">正在读取审计记录…</p>
      <div v-if="data" class="console-table-wrap">
        <table class="console-table">
          <thead>
            <tr>
              <th>时间 / 操作人</th>
              <th>对象</th>
              <th>前后状态</th>
              <th>动作 / 修订</th>
              <th>理由</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data.items" :key="row.id">
              <td>
                {{ dateText(row.occurredAt)
                }}<small>管理 {{ row.adminId }} · 记录 {{ row.id }}</small>
              </td>
              <td>{{ row.targetType }} {{ row.targetId }}</td>
              <td>
                {{ stateLabels[row.beforeState] || row.beforeState }} →
                {{ stateLabels[row.afterState] || row.afterState }}
              </td>
              <td>{{ row.action }} / {{ row.revision }}</td>
              <td class="console-preserve">{{ row.reason }}</td>
            </tr>
            <tr v-if="!data.items.length">
              <td colspan="5">暂无治理记录。</td>
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
        @turn="router.push({ query: { page: String($event) } })"
      /></section
  ></AdminGate>
</template>
