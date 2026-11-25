<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import AdminGate from "./AdminGate.vue";
import ConsolePageHeader from "./ConsolePageHeader.vue";

const router = useRouter();
const caseId = ref("");
function open() {
  if (
    /^[1-9]\d*$/.test(caseId.value) &&
    Number.isSafeInteger(Number(caseId.value))
  )
    void router.push(`/admin/cases/${caseId.value}`);
}
</script>
<template>
  <AdminGate>
    <div class="console-page">
      <ConsolePageHeader
        title="贡献治理与荣誉"
        description="投币处置从文章举报案件进入；荣誉由服务端按实时有效贡献计算。"
      />
      <section class="console-panel" aria-labelledby="contribution-title">
        <h2 id="contribution-title" class="console-card-title">
          从案件查看关联投币
        </h2>
        <p class="console-card-desc">
          案件详情内可以查看关联文章的每笔投币、处置结果与审计。
        </p>
        <form class="console-inline-filters" @submit.prevent="open">
          <label class="console-filter"
            >案件编号<input
              v-model.trim="caseId"
              inputmode="numeric"
              pattern="[1-9][0-9]*"
              required
              aria-label="案件编号"
          /></label>
          <div class="console-filter-actions">
            <button class="console-primary" type="submit">打开案件</button>
            <RouterLink
              class="button"
              :to="{ path: '/admin/cases', query: { kind: 'REPORT' } }"
              >查找举报</RouterLink
            >
          </div>
        </form>
        <p class="console-form-note">
          荣誉称号以接口返回为准，不提供荣誉配置入口。
        </p>
      </section>
    </div>
  </AdminGate>
</template>
