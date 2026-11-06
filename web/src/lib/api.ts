import { clearSession, session } from "./session.ts";

export class ApiError extends Error {
  status: number;
  code: string;
  traceId: string;
  constructor(status: number, code: string, message: string, traceId = "") {
    super(message);
    this.status = status;
    this.code = code;
    this.traceId = traceId;
  }
}
export function message(error: unknown) {
  if (error instanceof ApiError)
    return `${error.message}${error.traceId ? `（追踪 ${error.traceId}）` : ""}`;
  return error instanceof Error ? error.message : "请求未完成，请重试";
}
interface Options {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  data?: unknown;
  auth?: boolean;
  form?: FormData;
  blob?: boolean;
}
export async function request<T>(
  path: string,
  options: Options = {},
): Promise<T> {
  const { auth = false, method = "GET", data, form, blob = false } = options;
  const epoch = session.epoch;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), form ? 60000 : 20000);
  try {
    if (
      auth &&
      (!session.token || Date.parse(session.expiresAt) <= Date.now())
    ) {
      clearSession("请重新登录校园账号。");
      throw new ApiError(401, "UNAUTHORIZED", "请重新登录校园账号");
    }
    const headers: Record<string, string> = {};
    if (auth) headers.Authorization = `Bearer ${session.token}`;
    if (data !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      body: form ?? (data === undefined ? undefined : JSON.stringify(data)),
      cache: "no-store",
      signal: controller.signal,
    });
    if (auth && epoch !== session.epoch)
      throw new ApiError(409, "SESSION_CHANGED", "会话已变化，已忽略旧请求");
    if (blob && response.ok) {
      const value = await response.blob();
      if (auth && epoch !== session.epoch)
        throw new ApiError(409, "SESSION_CHANGED", "会话已变化，已忽略旧请求");
      return value as T;
    }
    const value: unknown = await response.json();
    if (auth && epoch !== session.epoch)
      throw new ApiError(409, "SESSION_CHANGED", "会话已变化，已忽略旧请求");
    if (!value || typeof value !== "object" || !("code" in value))
      throw new Error("服务响应格式不正确");
    if (!response.ok || value.code !== "OK") {
      if (
        auth &&
        (response.status === 401 ||
          (response.status === 403 && value.code === "ACCESS_RESTRICTED"))
      )
        clearSession(
          response.status === 403
            ? "账号或校园认证受限。"
            : "会话已失效，请重新登录。",
        );
      throw new ApiError(
        response.status,
        String(value.code),
        "message" in value ? String(value.message) : "请求未完成",
        "traceId" in value ? String(value.traceId) : "",
      );
    }
    if (!("data" in value)) throw new Error("服务响应缺少数据");
    return value.data as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof Error && error.name === "AbortError")
      throw new Error(
        "请求超时，结果尚未确认。创建、上传或发布请用原请求重试。",
      );
    if (error instanceof TypeError)
      throw new Error("无法连接服务，请检查网络并重试。");
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
export function get<T>(path: string, auth = false) {
  return request<T>(path, { auth });
}
export function send<T>(
  path: string,
  method: "POST" | "PUT" | "DELETE",
  data?: unknown,
) {
  return request<T>(path, { method, data, auth: true });
}
