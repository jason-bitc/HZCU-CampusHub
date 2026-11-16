import { defineConfig, loadEnv } from "vite";
import { fileURLToPath } from "node:url";
import vue from "@vitejs/plugin-vue";
import type { IncomingMessage } from "node:http";
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const surface = mode === "admin" ? "admin" : mode === "ops" ? "ops" : "user";
  const port = { user: 5173, admin: 5174, ops: 5175 }[surface];
  const proxy = {
    "/api/v1/ops": {
      target: env.CAMPUSHUB_OPS_TARGET || "http://127.0.0.1:8091",
      changeOrigin: true,
      bypass() {
        if (surface !== "ops") return false;
      },
    },
    "/api": {
      target: env.CAMPUSHUB_API_TARGET || "http://127.0.0.1:8080",
      changeOrigin: true,
      bypass(req: IncomingMessage) {
        const path = req.url?.split("?", 1)[0] || "";
        if (
          !path.startsWith("/api/v1/") ||
          /[%\\;]|\/\/|\/(?:\.|\.\.)(?:\/|$)/.test(path)
        )
          return false;
        const admin =
          path === "/api/v1/admin" || path.startsWith("/api/v1/admin/");
        if (
          surface === "ops" ||
          (surface === "user" && admin) ||
          (surface === "admin" && !admin)
        )
          return false;
      },
    },
  };
  return {
    resolve: {
      alias: {
        "@surface-routes": fileURLToPath(
          new URL(`./src/routes/${surface}.ts`, import.meta.url),
        ),
      },
    },
    plugins: [vue()],
    define: { __CAMPUSHUB_SURFACE__: JSON.stringify(surface) },
    build: { outDir: `dist/${surface}` },
    server: { port, strictPort: true, proxy },
    preview: { port: port - 1000, strictPort: true, proxy },
  };
});
