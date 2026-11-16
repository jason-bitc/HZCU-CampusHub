import { reactive } from "vue";
import { ApiError } from "../lib/api.ts";
import type { Login } from "../types";
import type { AdminMe, CaseCommand } from "./types";
const KEY = "campushub.web.community.session.v1";
const PRIVATE = "campushub.web.community.private:";
export const adminSession = reactive({
  token: "",
  expiresAt: "",
  me: null as AdminMe | null,
  status: "checking",
  epoch: 0,
  error: "",
});
let expiry: ReturnType<typeof setTimeout> | undefined;
export function clearAdmin(message = "") {
  clearTimeout(expiry);
  adminSession.token = "";
  adminSession.me = null;
  adminSession.expiresAt = "";
  adminSession.status = "guest";
  adminSession.error = message;
  adminSession.epoch++;
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
  const ms = Date.parse(adminSession.expiresAt) - Date.now();
  if (!Number.isFinite(ms) || ms <= 0) {
    clearAdmin("管理会话已过期，请重新登录。");
    return;
  }
  clearTimeout(expiry);
  expiry = setTimeout(
    () => clearAdmin("管理会话已过期，请重新登录。"),
    Math.min(ms, 2147483647),
  );
}
export async function adminRequest<T>(
  path: string,
  method = "GET",
  body?: unknown,
  publicLogin = false,
  binary = false,
): Promise<T> {
  if (!path.startsWith("/admin/")) throw new Error("管理请求路径无效");
  const epoch = adminSession.epoch;
  if (
    !publicLogin &&
    (!adminSession.token ||
      !Number.isFinite(Date.parse(adminSession.expiresAt)) ||
      Date.parse(adminSession.expiresAt) <= Date.now())
  ) {
    clearAdmin("请登录独立社区管理账号。");
    throw new ApiError(401, "ADMIN_UNAUTHORIZED", "请登录独立社区管理账号");
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20000);
  try {
    const headers: Record<string, string> = {};
    if (!publicLogin) headers.Authorization = `Bearer ${adminSession.token}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
      signal: controller.signal,
    });
    if (!publicLogin && epoch !== adminSession.epoch)
      throw new ApiError(
        409,
        "SESSION_CHANGED",
        "管理会话已变化，旧响应已丢弃",
      );
    if (!publicLogin && response.status === 401)
      clearAdmin("管理会话失效，请重新登录。");
    if (
      !(binary && response.ok) &&
      !response.headers.get("content-type")?.includes("application/json")
    )
      throw new ApiError(
        response.status || 503,
        "SERVICE_UNAVAILABLE",
        "管理服务没有返回有效接口响应，请核对服务与代理目标。",
      );
    const value: unknown =
      binary && response.ok ? await response.blob() : await response.json();
    if (!publicLogin && response.status !== 401 && epoch !== adminSession.epoch)
      throw new ApiError(
        409,
        "SESSION_CHANGED",
        "管理会话已变化，旧响应已丢弃",
      );
    if (binary && response.ok) return value as T;
    if (!value || typeof value !== "object" || !("code" in value))
      throw new Error("管理服务响应格式不正确");
    if (!response.ok || value.code !== "OK") {
      throw new ApiError(
        response.status,
        String(value.code),
        "message" in value ? String(value.message) : "请求未完成",
        "traceId" in value ? String(value.traceId) : "",
      );
    }
    if (!("data" in value)) throw new Error("管理响应缺少数据");
    return value.data as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof Error && error.name === "AbortError")
      throw new Error("请求超时，结果未确认。请保留当前输入并核对原请求。");
    if (error instanceof TypeError)
      throw new Error("无法连接社区管理服务，请重试。");
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
export async function initializeAdmin() {
  if (adminSession.status !== "checking") return;
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) {
      clearAdmin();
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
      throw new Error("管理会话无效");
    adminSession.token = data.accessToken;
    adminSession.expiresAt = data.expiresAt;
    schedule();
    if (!adminSession.token) return;
    adminSession.me = await adminRequest<AdminMe>("/admin/me");
    if (adminSession.me.actorDomain !== "COMMUNITY_ADMIN")
      throw new Error("管理身份域不正确");
    adminSession.status = "ready";
  } catch (error) {
    if (adminSession.token) {
      adminSession.status = "error";
      adminSession.error =
        error instanceof Error ? error.message : "会话核验失败";
    } else clearAdmin();
  }
}
export async function loginAdmin(login: string, password: string) {
  const value = await adminRequest<Login>(
    "/admin/auth/login",
    "POST",
    { login, password },
    true,
  );
  if (
    value.tokenType !== "Bearer" ||
    !value.accessToken.startsWith("adm_") ||
    !Number.isFinite(Date.parse(value.expiresAt))
  )
    throw new Error("管理登录响应域不正确");
  clearAdmin();
  sessionStorage.setItem(KEY, JSON.stringify(value));
  adminSession.token = value.accessToken;
  adminSession.expiresAt = value.expiresAt;
  schedule();
  try {
    adminSession.me = await adminRequest<AdminMe>("/admin/me");
    if (adminSession.me.actorDomain !== "COMMUNITY_ADMIN")
      throw new Error("管理身份域不正确");
    adminSession.status = "ready";
  } catch (e) {
    if (adminSession.token) adminSession.status = "error";
    throw e;
  }
}
export async function logoutAdmin() {
  await adminRequest("/admin/auth/logout", "POST");
  clearAdmin();
}
export function commandKey(caseId: number) {
  if (!adminSession.me) throw new Error("管理身份尚未核验");
  return `${PRIVATE}${adminSession.me.adminId}:case:${caseId}`;
}
export function storeCommand(value: CaseCommand) {
  sessionStorage.setItem(commandKey(value.caseId), JSON.stringify(value));
}
export function pendingCommand(caseId: number): CaseCommand | null {
  const value = sessionStorage.getItem(commandKey(caseId));
  return value ? (JSON.parse(value) as CaseCommand) : null;
}
export function forgetCommand(caseId: number) {
  sessionStorage.removeItem(commandKey(caseId));
}
