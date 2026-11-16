<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import AdminGate from "./AdminGate.vue";

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
  <h1 class="console-title">贡献治理与荣誉</h1>
  <AdminGate>
    <section class="console-panel">
      <h2>从文章举报查看投币</h2>
      <p>案件中可以查看关联文章的每笔投币、处置结果与审计。</p>
      <form class="console-filters" @submit.prevent="open">
        <label
          >案件编号<input
            v-model.trim="caseId"
            inputmode="numeric"
            pattern="[1-9][0-9]*"
            required
        /></label>
        <button class="console-primary">打开案件</button>
        <RouterLink
          class="button"
          :to="{ path: '/admin/cases', query: { kind: 'REPORT' } }"
          >查找举报</RouterLink
        >
      </form>
      <p class="hint">荣誉由服务端按实时有效贡献计算，称号以接口返回为准。</p>
    </section>
  </AdminGate>
</template>
