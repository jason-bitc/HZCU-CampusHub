import type { RouteRecordRaw } from "vue-router";
export const routes: RouteRecordRaw[] = [
  { path: "/", component: () => import("../pages/DiscoverPage.vue") },
  {
    path: "/articles/:id(\\d+)",
    component: () => import("../pages/ArticlePage.vue"),
  },
  { path: "/account", component: () => import("../pages/AccountPage.vue") },
  { path: "/workspace", component: () => import("../pages/WorkspacePage.vue") },
  {
    path: "/editor/:id(\\d+)",
    component: () => import("../pages/EditorPage.vue"),
  },
  { path: "/favorites", component: () => import("../pages/FavoritesPage.vue") },
  { path: "/me", component: () => import("../pages/MePage.vue") },
  { path: "/me/appeals", component: () => import("../pages/AppealsPage.vue") },
  { path: "/:pathMatch(.*)*", redirect: "/" },
];
