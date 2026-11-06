<script setup lang="ts">
import { useRoute } from "vue-router";
import { session } from "../lib/session";
defineProps<{ title?: string }>();
const route = useRoute();
</script>
<template>
  <slot v-if="session.status === 'ready'" />
  <section v-else class="panel state-panel" aria-live="polite">
    <h1>
      {{
        session.status === "checking"
          ? "正在核验校园账号"
          : title || "登录后继续"
      }}
    </h1>
    <p>网页与小程序使用同一校园账号，两个客户端的会话各自独立。</p>
    <RouterLink
      v-if="session.status === 'guest'"
      class="button primary"
      :to="{ path: '/account', query: { next: route.fullPath } }"
      >登录校园账号</RouterLink
    >
    <p v-else-if="session.status === 'error'">请在页面顶部重试会话核验。</p>
  </section>
</template>
