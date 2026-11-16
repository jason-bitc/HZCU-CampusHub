import type { RouteRecordRaw } from "vue-router";
export const routes: RouteRecordRaw[] = [
  {
    path: "/ops",
    component: () => import("../management/ManagementLayout.vue"),
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
        component: () => import("../management/OpsPages.vue"),
      })),
      {
        path: "tasks/:id([A-Za-z0-9_-]+)",
        component: () => import("../management/OpsPages.vue"),
      },
    ],
  },
  { path: "/:pathMatch(.*)*", redirect: "/ops/login" },
];
