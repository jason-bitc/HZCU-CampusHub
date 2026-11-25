<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";
import { clearSession, restoreSession, session } from "./lib/session";
import { get, message } from "./lib/api";
import type { Me } from "./types";
import { surface } from "./surface";
import BackToTop from "./components/BackToTop.vue";
const route = useRoute();
const consoleRoute = surface !== "user";
const siteHeader = ref<HTMLElement>();
let headerObserver: ResizeObserver | undefined;
let memberInitialized = false;
function focusMain() {
  document.getElementById("main")?.focus();
}
async function initializeMember() {
  if (consoleRoute || memberInitialized) return;
  memberInitialized = true;
  restoreSession();
  if (session.token) {
    try {
      session.me = await get<Me>("/me", true);
      session.status = "ready";
    } catch (error) {
      if (session.token && session.status !== "limited") {
        session.status = "error";
        session.message = message(error);
      }
    }
  }
}
onMounted(initializeMember);
onMounted(() => {
  const header = siteHeader.value;
  if (!header) return;
  const measure = () =>
    document.documentElement.style.setProperty(
      "--member-header-height",
      `${header.offsetHeight}px`,
    );
  measure();
  headerObserver = new ResizeObserver(measure);
  headerObserver.observe(header);
});
onBeforeUnmount(() => headerObserver?.disconnect());
</script>
<template>
  <RouterView v-if="consoleRoute" />
  <template v-else>
    <a class="skip-link" href="#main" @click.prevent="focusMain"
      >跳到主要内容</a
    >
    <header ref="siteHeader" class="site-header">
      <div class="header-inner">
        <RouterLink class="brand" to="/" aria-label="城院帮首页">
          <img
            class="brand-logo"
            src="/campushub-logo.png"
            alt=""
            width="34"
            height="33"
          />
          <span class="brand-copy">
            <span class="brand-name">城院帮</span>
          </span>
        </RouterLink>
        <nav aria-label="主导航">
          <RouterLink to="/" :class="{ active: route.path === '/' }"
            >发现</RouterLink
          ><RouterLink to="/favorites" active-class="active"
            >我的收藏</RouterLink
          ><RouterLink to="/workspace" active-class="active"
            >创作工作区</RouterLink
          ><RouterLink
            to="/me"
            :class="{
              active: route.path === '/me' || route.path.startsWith('/me/'),
            }"
            >个人中心</RouterLink
          >
        </nav>
        <div class="header-actions">
          <RouterLink
            class="button primary header-write"
            :to="
              session.status === 'ready'
                ? '/workspace?new=1'
                : '/account?next=%2Fworkspace'
            "
            >写经验</RouterLink
          ><RouterLink
            class="account-link"
            :to="
              session.status === 'ready'
                ? '/me'
                : session.status === 'limited'
                  ? '/me/certification'
                  : '/account'
            "
            :aria-label="
              ['ready', 'limited'].includes(session.status)
                ? '个人中心'
                : session.status === 'checking'
                  ? '正在核验会话'
                  : '校园账号登录'
            "
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle cx="12" cy="8" r="3.5" />
              <path d="M5 20c.7-4 3.1-6 7-6s6.3 2 7 6" />
            </svg>
            <span class="sr-only">{{
              ["ready", "limited"].includes(session.status)
                ? "个人中心"
                : session.status === "checking"
                  ? "核验会话…"
                  : "校园账号登录"
            }}</span>
          </RouterLink>
        </div>
      </div>
    </header>
    <div v-if="session.message" class="global-notice" role="status">
      {{ session.message }}
      <RouterLink v-if="session.status === 'limited'" to="/me/certification"
        >查看认证状态</RouterLink
      >
      <RouterLink v-if="session.status === 'limited'" to="/me/notifications"
        >查看通知</RouterLink
      >
    </div>
    <div v-if="session.status === 'error'" class="global-notice">
      <button
        class="text-button"
        @click="clearSession('已清除本机会话，请重新登录。')"
      >
        清除会话并重新登录</button
      ><button
        class="text-button"
        @click="
          get<Me>('/me', true)
            .then((me) => {
              session.me = me;
              session.status = 'ready';
              session.message = '';
            })
            .catch((e) => (session.message = message(e)))
        "
      >
        重新核验
      </button>
    </div>
    <main id="main" tabindex="-1">
      <RouterView v-slot="{ Component }"
        ><component
          :is="Component"
          :key="`${route.path === '/me/cases/new' ? route.fullPath : route.path}:${session.epoch}`"
      /></RouterView>
    </main>
    <footer class="site-footer">
      <span>城院帮 · 分享经历，找到自己的起点</span
      ><span>校园邮箱认证不等于经历真实性认证</span>
    </footer>
    <BackToTop />
  </template>
</template>
