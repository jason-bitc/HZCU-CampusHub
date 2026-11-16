<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  adminSession,
  clearAdmin,
  initializeAdmin,
  logoutAdmin,
} from "./client";
import { message } from "../lib/api";
import { opsSession, initializeOps, logoutOps, clearOps } from "./ops-client";
import "./management.css";
const route = useRoute();
const router = useRouter();
const error = ref("");
const menu = ref(false);
const ops = computed(() => route.path.startsWith("/ops"));
const preview = computed(() => route.query.preview === "1");
const adminLinks = [
  ["待办案件", "/admin/cases"],
  ["内容巡查", "/admin/content"],
  ["账号与申诉", "/admin/accounts"],
  ["分类维护", "/admin/categories"],
  ["贡献与荣誉", "/admin/contributions"],
  ["治理审计", "/admin/audit"],
];
const opsLinks = [
  ["服务总览", "/ops/overview"],
  ["日志与任务", "/ops/logs"],
  ["版本部署", "/ops/deploy"],
  ["业务重启", "/ops/restart"],
  ["备份与恢复", "/ops/backups"],
  ["操作手册", "/ops/manual"],
];
const links = computed(() => (ops.value ? opsLinks : adminLinks));
function link(path: string) {
  return { path, query: preview.value ? { preview: "1" } : {} };
}
function focusMain() {
  document.getElementById("console-main")?.focus();
}
function active(path: string) {
  if (route.path.endsWith("/login")) return path === links.value[0]?.[1];
  return (
    route.path.startsWith(path) ||
    (path === "/ops/logs" && /\/ops\/(tasks|exceptions)/.test(route.path)) ||
    (path === "/ops/backups" && route.path === "/ops/restore")
  );
}
async function init() {
  if (preview.value) return;
  if (ops.value) await initializeOps();
  else await initializeAdmin();
}
async function logout() {
  error.value = "";
  if (preview.value) {
    await router.push(link(ops.value ? "/ops/login" : "/admin/login"));
    return;
  }
  try {
    if (ops.value) await logoutOps();
    else await logoutAdmin();
    await router.push(ops.value ? "/ops/login" : "/admin/login");
  } catch (e) {
    error.value = message(e);
  }
}
watch(
  () => [route.path, route.query.preview],
  () => {
    menu.value = false;
    void init();
  },
);
onMounted(init);
</script>
<template>
  <div
    class="console-shell"
    :class="{ 'ops-shell': ops, 'console-preview': preview }"
  >
    <a class="skip-link" href="#console-main" @click.prevent="focusMain"
      >跳到主要内容</a
    >
    <aside class="console-sidebar" :class="{ expanded: menu }">
      <RouterLink
        class="console-brand"
        :to="ops ? '/ops/overview' : '/admin/cases'"
        >城院帮</RouterLink
      >
      <p class="console-domain">{{ ops ? "独立运维" : "社区管理" }}</p>
      <nav :aria-label="ops ? '运维导航' : '社区管理导航'">
        <RouterLink
          v-for="(item, index) in links"
          :key="item[1]"
          :to="link(item[1] || '/')"
          :class="{ active: active(item[1] || '/') }"
          ><svg viewBox="0 0 20 20" aria-hidden="true">
            <g
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <template v-if="index % 2 === 0">
                <rect x="4" y="2" width="12" height="16" rx="1.5" />
                <path d="M7 6h6M7 10h6M7 14h3" />
              </template>
              <path
                v-else
                d="M4 3h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-5 3v-3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z"
              />
            </g></svg
          >{{ item[0] }}</RouterLink
        >
      </nav>
      <div class="console-sidebar-footer">
        <span>{{ preview ? "原型示例 · 非真实授权" : "独立授权身份" }}</span
        ><button @click="logout">
          {{ preview ? "返回登录页" : ops ? "退出运维账号" : "退出管理账号" }}
        </button>
      </div>
    </aside>
    <header class="console-top">
      <button class="console-menu" :aria-expanded="menu" @click="menu = !menu">
        菜单</button
      ><span>城院帮 / {{ ops ? "独立运维" : "社区管理" }}</span
      ><RouterLink
        class="console-mode"
        :to="{ path: route.path, query: preview ? {} : { preview: '1' } }"
        >{{ preview ? "原型示例 · 只读" : "查看原型示例" }}</RouterLink
      ><span class="console-identity">{{
        preview
          ? "示例身份"
          : ops
            ? opsSession.status === "ready"
              ? "已授权运维账号"
              : "未登录运维账号"
            : adminSession.status === "ready"
              ? "已授权管理员"
              : "未登录管理账号"
      }}</span>
    </header>
    <main id="console-main" class="console-main" tabindex="-1">
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p
        v-if="adminSession.error && !preview && !ops"
        class="error"
        role="alert"
      >
        {{ adminSession.error }}
        <button
          v-if="adminSession.status === 'error'"
          @click="
            adminSession.status = 'checking';
            initializeAdmin();
          "
        >
          重新核验</button
        ><button
          v-if="adminSession.token"
          @click="clearAdmin('已清除本机管理会话。服务端退出尚未确认。')"
        >
          清除本机管理会话
        </button>
      </p>
      <p v-if="opsSession.error && !preview && ops" class="error" role="alert">
        {{ opsSession.error }}
        <button
          v-if="opsSession.status === 'error'"
          @click="
            opsSession.status = 'checking';
            initializeOps();
          "
        >
          重新核验</button
        ><button
          v-if="opsSession.token"
          @click="clearOps('已清除本机运维会话，服务端退出尚未确认。')"
        >
          清除本机运维会话
        </button>
      </p>
      <RouterView v-slot="{ Component }"
        ><component
          :is="Component"
          :key="`${route.path}:${preview}:${ops ? opsSession.epoch : adminSession.epoch}`"
      /></RouterView>
      <p v-if="preview" class="console-preview-note">
        原型示例模式：人物、数字和运行状态均来自原型，不代表真实系统；不会执行治理或运维操作。<RouterLink
          :to="{ path: route.path }"
          >查看实际接入状态</RouterLink
        >
      </p>
    </main>
  </div>
</template>
