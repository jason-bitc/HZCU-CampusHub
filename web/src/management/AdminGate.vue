<script setup lang="ts">
import { adminSession } from "./client";
defineProps<{ preview?: boolean }>();
</script>
<template>
  <slot v-if="preview || adminSession.status === 'ready'" />
  <section v-else class="console-panel console-gate">
    <h2>
      {{
        adminSession.status === "checking"
          ? "正在核验管理会话"
          : "请先登录社区管理"
      }}
    </h2>
    <p>普通校园账号与运维账号不能代替社区管理授权。</p>
    <RouterLink
      v-if="adminSession.status === 'guest'"
      class="button console-primary"
      to="/admin/login"
      >登录社区管理</RouterLink
    >
  </section>
</template>
