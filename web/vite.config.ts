import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxy = {
    "/api/v1/ops": {
      target: env.CAMPUSHUB_OPS_TARGET || "http://127.0.0.1:8091",
      changeOrigin: true,
    },
    "/api": {
      target: env.CAMPUSHUB_API_TARGET || "http://127.0.0.1:8080",
      changeOrigin: true,
    },
  };
  return {
    plugins: [vue()],
    server: { port: 5173, strictPort: true, proxy },
    preview: { port: 4173, strictPort: true, proxy },
  };
});
