import { createRouter, createWebHashHistory } from "vue-router";

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: "/admin",
      component: () => import("./management/ManagementLayout.vue"),
      redirect: "/admin/login",
      children: [
        {
          path: "login",
          component: () => import("./management/AdminLogin.vue"),
        },
        {
          path: "cases",
          component: () => import("./management/AdminCases.vue"),
        },
        {
          path: "cases/:id(\\d+)",
          component: () => import("./management/AdminCase.vue"),
        },
        ...["content", "accounts", "categories", "contributions", "audit"].map(
          (path) => ({
            path,
            component: () => import("./management/AdminAdditional.vue"),
          }),
        ),
      ],
    },
    {
      path: "/ops",
      component: () => import("./management/ManagementLayout.vue"),
      redirect: "/ops/login",
      children: [
        ...[
          "login",
          "overview",
          "logs",
          "deploy",
          "restart",
          "backups",
          "restore",
          "exceptions",
          "manual",
        ].map((path) => ({
          path,
          component: () => import("./management/OpsPages.vue"),
        })),
        {
          path: "tasks/:id([A-Za-z0-9_-]+)",
          component: () => import("./management/OpsPages.vue"),
        },
      ],
    },
    { path: "/", component: () => import("./pages/DiscoverPage.vue") },
    {
      path: "/articles/:id(\\d+)",
      component: () => import("./pages/ArticlePage.vue"),
    },
    { path: "/account", component: () => import("./pages/AccountPage.vue") },
    {
      path: "/workspace",
      component: () => import("./pages/WorkspacePage.vue"),
    },
    {
      path: "/editor/:id(\\d+)",
      component: () => import("./pages/EditorPage.vue"),
    },
    {
      path: "/favorites",
      component: () => import("./pages/FavoritesPage.vue"),
    },
    { path: "/me", component: () => import("./pages/MePage.vue") },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
  scrollBehavior(to, from, saved) {
    if (to.path === from.path) return;
    return saved || { top: 0 };
  },
});
