import { createRouter, createWebHashHistory } from "vue-router";
import { routes } from "@surface-routes";
export const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    if (to.path === from.path) return;
    return saved || { top: 0 };
  },
});
