import type { RouteRecordRaw } from "vue-router";
export const routes: RouteRecordRaw[] = [
  { path: "/", component: () => import("../pages/DiscoverPage.vue") },
  {
    path: "/articles/:id(\\d+)",
    component: () => import("../pages/ArticlePage.vue"),
  },
  { path: "/account", component: () => import("../pages/AccountPage.vue") },
  {
    path: "/authors/:id(\\d+)",
    component: () => import("../pages/AuthorPage.vue"),
  },
  { path: "/workspace", component: () => import("../pages/WorkspacePage.vue") },
  {
    path: "/editor/:id(\\d+)",
    component: () => import("../pages/EditorPage.vue"),
  },
  { path: "/favorites", component: () => import("../pages/FavoritesPage.vue") },
  { path: "/me", component: () => import("../pages/MePage.vue") },
  { path: "/me/wallet", component: () => import("../pages/WalletPage.vue") },
  {
    path: "/me/notifications",
    component: () => import("../pages/NotificationsPage.vue"),
  },
  {
    path: "/me/certification",
    component: () => import("../pages/CertificationPage.vue"),
  },
  {
    path: "/me/appeals/:id(\\d+)",
    component: () => import("../pages/CertificationPage.vue"),
  },
  { path: "/me/cases", component: () => import("../pages/CasesPage.vue") },
  { path: "/me/cases/new", component: () => import("../pages/CasesPage.vue") },
  {
    path: "/me/cases/:id(\\d+)",
    component: () => import("../pages/CasesPage.vue"),
  },
  { path: "/me/appeals", component: () => import("../pages/AppealsPage.vue") },
  { path: "/:pathMatch(.*)*", redirect: "/" },
];
