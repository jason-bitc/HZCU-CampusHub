import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./router";
import "./style.css";

const app = createApp(App).use(router);
// Resolve the initial identity domain before App starts any session recovery.
void router
  .isReady()
  .then(() => app.mount("#app"))
  .catch(() => {
    const root = document.getElementById("app");
    if (root) {
      root.setAttribute("role", "alert");
      root.textContent = "页面暂时无法载入，请检查网络后刷新重试。";
    }
  });
