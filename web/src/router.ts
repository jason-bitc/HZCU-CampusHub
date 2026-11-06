import { createRouter, createWebHashHistory } from "vue-router";

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
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
