import type { RouteRecordRaw } from "vue-router";
export const routes: RouteRecordRaw[] = [
  {
    path: "/admin",
    component: () => import("../management/ManagementLayout.vue"),
    redirect: "/admin/login",
    children: [
      {
        path: "login",
        component: () => import("../management/AdminLogin.vue"),
      },
      {
        path: "cases",
        component: () => import("../management/AdminCases.vue"),
      },
      {
        path: "cases/:id(\\d+)",
        component: () => import("../management/AdminCase.vue"),
      },
      ...["content", "accounts", "categories", "contributions", "audit"].map(
        (path) => ({
          path,
          component: () => import("../management/AdminAdditional.vue"),
        }),
      ),
    ],
  },
  { path: "/:pathMatch(.*)*", redirect: "/admin/login" },
];
