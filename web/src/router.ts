import { createRouter, createWebHashHistory } from "vue-router";
import { routes } from "@surface-routes";
export const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    if (to.hash) return { el: to.hash, top: 24 };
    if (to.path === from.path) return;
    return saved || { top: 0 };
  },
});
