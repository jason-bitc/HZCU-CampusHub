<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch, watchEffect } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  adminSession,
  initializeAdmin,
  logoutAdmin,
} from "./client";
import { opsSession, initializeOps, logoutOps } from "./ops-client";
import { message } from "../lib/api";
import ConsoleIcon from "./ConsoleIcon.vue";
import "./management.css";
type Icon =
  | "queue"
  | "content"
  | "accounts"
  | "category"
  | "cert"
  | "audit"
  | "overview"
  | "tasks"
  | "logs"
  | "deploy"
  | "backup"
  | "rollback";
type Item = { label: string; to: string; icon: Icon; match?: string[] };
type Group = { label: string; items: Item[] };
const adminGroups: Group[] = [
  {
    label: "案件处置",
    items: [
      { label: "案件队列", to: "/admin/cases", icon: "queue" },
      { label: "内容治理", to: "/admin/content", icon: "content" },
      { label: "账号与申诉", to: "/admin/accounts", icon: "accounts" },
    ],
  },
  {
    label: "治理与审计",
    items: [
      { label: "分类治理", to: "/admin/categories", icon: "category" },
      { label: "认证管理", to: "/admin/certification", icon: "cert" },
      { label: "审计记录", to: "/admin/audit", icon: "audit" },
    ],
  },
];
const opsGroups: Group[] = [
  {
    label: "监控与任务",
    items: [
      { label: "运行总览", to: "/ops/overview", icon: "overview" },
      {
        label: "任务记录",
        to: "/ops/tasks",
        icon: "tasks",
        match: ["/ops/exceptions"],
      },
      { label: "受控日志", to: "/ops/logs", icon: "logs" },
    ],
  },
  {
    label: "受控操作",
    items: [
      {
        label: "部署与重启",
        to: "/ops/deploy",
        icon: "deploy",
        match: ["/ops/restart"],
      },
      {
        label: "备份与恢复",
        to: "/ops/backups",
        icon: "backup",
        match: ["/ops/restore"],
      },
      { label: "回退与交接", to: "/ops/manual", icon: "rollback" },
    ],
  },
];
const route = useRoute(),
  router = useRouter(),
  menu = ref(false),
  error = ref("");
const menuButton = ref<HTMLButtonElement>();
const sidebar = ref<HTMLElement>();
const ops = computed(() => route.path.startsWith("/ops"));
const login = computed(() => route.path === "/ops/login" || route.path === "/admin/login");
const preview = computed(() => route.query.preview === "1");
const groups = computed(() => (ops.value ? opsGroups : adminGroups));
const role = computed(() =>
  ops.value
    ? { name: "运维管理员", area: "独立运维", brand: "城院帮独立运维" }
    : { name: "社区管理员", area: "社区管理", brand: "城院帮社区管理" },
);
const current = computed(() => {
  for (const group of groups.value)
    for (const item of group.items)
      if (isActive(item)) return item.label;
  return login.value ? "登录" : "";
});
const session = computed(() =>
  ops.value
    ? { status: opsSession.status, error: opsSession.error }
    : { status: adminSession.status, error: adminSession.error },
);
const notice = computed(() => error.value || session.value.error);
onMounted(init);
function isActive(item: Item) {
  const paths = [item.to, ...(item.match || [])];
  return paths.some(
    (path) => route.path === path || route.path.startsWith(`${path}/`),
  );
}
const previewLink = computed(() => ({
  path: route.path,
  query: preview.value
    ? Object.fromEntries(
        Object.entries(route.query).filter(([key]) => key !== "preview"),
      )
    : { ...route.query, preview: "1" },
}));
async function init() {
  if (preview.value) return;
  if (ops.value) await initializeOps();
  else await initializeAdmin();
}
function focusMain() {
  document.getElementById("console-main")?.focus();
}
function closeMenu() {
  if (!menu.value) return;
  menu.value = false;
  menuButton.value?.focus();
}
async function toggleMenu() {
  if (menu.value) {
    closeMenu();
    return;
  }
  menu.value = true;
  await nextTick();
  sidebar.value?.querySelector<HTMLElement>("a")?.focus();
}
async function signOut() {
  error.value = "";
  if (preview.value) {
    await router.push(ops.value ? "/ops/login" : "/admin/login");
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
  () => route.fullPath,
  () => {
    menu.value = false;
    error.value = "";
    void init();
  },
);
watchEffect(() => {
  document.title = current.value
    ? `${current.value} · ${role.value.brand}`
    : role.value.brand;
});
</script>
<template>
  <div
    class="console-shell"
    :class="{ 'ops-shell': ops, 'console-preview': preview }"
    @keydown.esc="closeMenu"
  >
    <a class="skip-link" href="#console-main" @click.prevent="focusMain">跳到主要内容</a>
    <header class="console-topbar" :class="{ 'is-bare': login }">
      <RouterLink class="console-brand" :to="ops ? '/ops/overview' : '/admin/cases'">
        <img src="/campushub-logo.png" alt="" width="30" height="30" />
        <span class="console-brand-text">
          <strong>城院帮</strong>
          <span>{{ role.area }}</span>
        </span>
      </RouterLink>
      <button
        v-if="!login"
        ref="menuButton"
        class="console-menu"
        type="button"
        :aria-expanded="menu"
        aria-controls="console-sidebar"
        @click="toggleMenu"
      >
        <ConsoleIcon :name="menu ? 'close' : 'menu'" :size="18" />
        <span class="sr-only">{{ menu ? "关闭菜单" : "打开菜单" }}</span>
      </button>
      <nav v-if="!login" class="console-breadcrumb" aria-label="面包屑">
        <RouterLink :to="ops ? '/ops/overview' : '/admin/cases'">城院帮</RouterLink>
        <span class="console-breadcrumb-sep" aria-hidden="true">/</span>
        <span>{{ role.area }}</span>
        <span class="console-breadcrumb-sep" aria-hidden="true">/</span>
        <span aria-current="page">{{ current }}</span>
      </nav>
      <div v-if="!login" class="console-topbar-right">
        <RouterLink class="console-mode" :to="previewLink">
          {{ preview ? "返回实际接入" : "查看原型示例" }}
        </RouterLink>
        <span class="console-identity">{{ role.name }}</span>
        <button class="console-exit" type="button" @click="signOut">
          退出
        </button>
      </div>
    </header>
    <div class="console-body" :class="{ 'is-bare': login }">
      <aside
        v-if="!login"
        id="console-sidebar"
        ref="sidebar"
        class="console-sidebar"
        :class="{ expanded: menu }"
      >
        <nav aria-label="控制台导航">
          <div
            v-for="group in groups"
            :key="group.label"
            class="console-nav-group"
          >
            <span class="console-nav-label">{{ group.label }}</span>
            <div class="console-menu-list">
              <RouterLink
                v-for="item in group.items"
                :key="item.to"
                :to="item.to"
                :class="{ active: isActive(item) }"
                :aria-current="isActive(item) ? 'page' : undefined"
                @click="closeMenu"
              >
                <span class="console-nav-icon">
                  <ConsoleIcon :name="item.icon" :size="20" />
                </span>
                <span>{{ item.label }}</span>
              </RouterLink>
            </div>
          </div>
        </nav>
        <div v-if="ops" class="console-sidebar-footer">
          <p>固定目标 · business</p>
          <p>独立端口 8091</p>
        </div>
      </aside>
      <main id="console-main" class="console-main" tabindex="-1">
        <div class="console-main-inner">
          <p
            v-if="notice"
            class="console-banner is-error console-global-notice"
            role="alert"
          >
            {{ notice }}
          </p>
          <RouterView :key="route.path" />
        </div>
      </main>
    </div>
  </div>
</template>
