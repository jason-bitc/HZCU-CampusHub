import { reactive } from "vue";
import { ApiError } from "../lib/api.ts";
import type { Login } from "../types";
import type { OpsMe } from "./ops-types";
const KEY = "campushub.web.ops.session.v1";
const PRIVATE = "campushub.web.ops.private:";
export const opsSession = reactive({
  token: "",
  expiresAt: "",
  me: null as OpsMe | null,
  status: "checking",
  epoch: 0,
  error: "",
});
let expiry: ReturnType<typeof setTimeout> | undefined;
export function clearOps(message = "") {
  clearTimeout(expiry);
  opsSession.token = "";
  opsSession.me = null;
  opsSession.expiresAt = "";
  opsSession.status = "guest";
  opsSession.error = message;
  opsSession.epoch++;
  try {
    sessionStorage.removeItem(KEY);
    Object.keys(sessionStorage)
      .filter((key) => key.startsWith(PRIVATE))
      .forEach((key) => sessionStorage.removeItem(key));
  } catch {
    /* Memory is already cleared. */
  }
}
function schedule() {
  const ms = Date.parse(opsSession.expiresAt) - Date.now();
  if (!Number.isFinite(ms) || ms <= 0) {
    clearOps("运维会话已过期，请重新登录。");
    return;
  }
  clearTimeout(expiry);
  expiry = setTimeout(
    () => clearOps("运维会话已过期，请重新登录。"),
    Math.min(ms, 2147483647),
  );
}
export async function opsRequest<T>(
  path: string,
  method = "GET",
  body?: unknown,
  publicLogin = false,
  binary = false,
): Promise<T> {
  if (!path.startsWith("/ops/")) throw new Error("运维请求路径无效");
  const epoch = opsSession.epoch;
  if (
    !publicLogin &&
    (!opsSession.token ||
      !Number.isFinite(Date.parse(opsSession.expiresAt)) ||
      Date.parse(opsSession.expiresAt) <= Date.now())
  ) {
    clearOps("请登录独立独立运维账号。");
    throw new ApiError(401, "OPS_UNAUTHORIZED", "请登录独立独立运维账号");
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20000);
  try {
    const headers: Record<string, string> = {};
    if (!publicLogin) headers.Authorization = `Bearer ${opsSession.token}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
      signal: controller.signal,
    });
    if (!publicLogin && epoch !== opsSession.epoch)
      throw new ApiError(
        409,
        "SESSION_CHANGED",
        "运维会话已变化，旧响应已丢弃",
      );
    if (!publicLogin && response.status === 401)
      clearOps("运维会话失效，请重新登录。");
    if (
      !(binary && response.ok) &&
      !response.headers.get("content-type")?.includes("application/json")
    )
      throw new ApiError(
        response.status || 503,
        "SERVICE_UNAVAILABLE",
        "运维服务没有返回有效接口响应，请核对服务与代理目标。",
      );
    const value: unknown =
      binary && response.ok ? await response.blob() : await response.json();
    if (!publicLogin && response.status !== 401 && epoch !== opsSession.epoch)
      throw new ApiError(
        409,
        "SESSION_CHANGED",
        "运维会话已变化，旧响应已丢弃",
      );
    if (binary && response.ok) return value as T;
    if (!value || typeof value !== "object" || !("code" in value))
      throw new Error("运维服务响应格式不正确");
    if (!response.ok || value.code !== "OK") {
      throw new ApiError(
        response.status,
        String(value.code),
        "message" in value ? String(value.message) : "请求未完成",
        "traceId" in value ? String(value.traceId) : "",
      );
    }
    if (!("data" in value)) throw new Error("运维响应缺少数据");
    return value.data as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof Error && error.name === "AbortError")
      throw new Error("请求超时，结果未确认。请保留当前输入并核对原请求。");
    if (error instanceof TypeError)
      throw new Error("无法连接独立运维服务，请重试。");
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
export async function initializeOps() {
  if (opsSession.status !== "checking") return;
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) {
      clearOps();
      return;
    }
    const data: unknown = JSON.parse(raw);
    if (
      !data ||
      typeof data !== "object" ||
      !("accessToken" in data) ||
      typeof data.accessToken !== "string" ||
      !("expiresAt" in data) ||
      typeof data.expiresAt !== "string"
    )
      throw new Error("运维会话无效");
    opsSession.token = data.accessToken;
    opsSession.expiresAt = data.expiresAt;
    schedule();
    if (!opsSession.token) return;
    opsSession.me = await opsRequest<OpsMe>("/ops/me");
    if (opsSession.me.actorDomain !== "OPS_ADMIN")
      throw new Error("运维身份域不正确");
    opsSession.status = "ready";
  } catch (error) {
    if (opsSession.token) {
      opsSession.status = "error";
      opsSession.error =
        error instanceof Error ? error.message : "会话核验失败";
    } else clearOps();
  }
}
export async function loginOps(login: string, password: string) {
  const value = await opsRequest<Login>(
    "/ops/auth/login",
    "POST",
    { login, password },
    true,
  );
  if (
    value.tokenType !== "Bearer" ||
    !value.accessToken.startsWith("ops_") ||
    !Number.isFinite(Date.parse(value.expiresAt))
  )
    throw new Error("运维登录响应域不正确");
  clearOps();
  sessionStorage.setItem(KEY, JSON.stringify(value));
  opsSession.token = value.accessToken;
  opsSession.expiresAt = value.expiresAt;
  schedule();
  try {
    opsSession.me = await opsRequest<OpsMe>("/ops/me");
    if (opsSession.me.actorDomain !== "OPS_ADMIN")
      throw new Error("运维身份域不正确");
    opsSession.status = "ready";
  } catch (e) {
    if (opsSession.token) opsSession.status = "error";
    throw e;
  }
}
export async function logoutOps() {
  await opsRequest("/ops/auth/logout", "POST");
  clearOps();
}
