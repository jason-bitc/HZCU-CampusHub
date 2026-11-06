import { reactive } from "vue";
import type { Login, Me } from "../types";

const KEY = "campushub.web.session.v1";
const PRIVATE = "campushub.web.private:";
export const session = reactive({
  token: "",
  expiresAt: "",
  me: null as Me | null,
  status: "checking",
  epoch: 0,
  message: "",
});
let timer: ReturnType<typeof setTimeout> | undefined;
export function clearSession(message = "") {
  clearTimeout(timer);
  session.epoch += 1;
  session.token = "";
  session.expiresAt = "";
  session.me = null;
  session.status = "guest";
  session.message = message;
  try {
    sessionStorage.removeItem(KEY);
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith(PRIVATE))
      .forEach((k) => sessionStorage.removeItem(k));
  } catch {
    /* In-memory session is already cleared. */
  }
}
function schedule() {
  clearTimeout(timer);
  const remaining = Date.parse(session.expiresAt) - Date.now();
  if (remaining <= 0) {
    clearSession("会话已过期，请重新登录。");
    return;
  }
  timer = setTimeout(
    () => clearSession("会话已过期，请重新登录。"),
    Math.min(remaining, 2147483647),
  );
}
export function acceptSession(value: Login, persist = true) {
  if (
    value.tokenType !== "Bearer" ||
    !value.accessToken ||
    !Number.isFinite(Date.parse(value.expiresAt))
  )
    throw new Error("登录响应无效");
  clearSession();
  if (persist) sessionStorage.setItem(KEY, JSON.stringify(value));
  session.token = value.accessToken;
  session.expiresAt = value.expiresAt;
  session.status = "checking";
  schedule();
}
export function restoreSession() {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (raw) {
      const value: unknown = JSON.parse(raw);
      if (
        typeof value === "object" &&
        value &&
        "accessToken" in value &&
        typeof value.accessToken === "string" &&
        "expiresAt" in value &&
        typeof value.expiresAt === "string" &&
        "tokenType" in value &&
        value.tokenType === "Bearer"
      ) {
        // Do not erase recovery/idempotency records during a same-session reload.
        session.token = value.accessToken;
        session.expiresAt = value.expiresAt;
        schedule();
        return;
      }
    }
  } catch {
    /* Unavailable/invalid session storage means guest access. */
  }
  clearSession();
}
export function privateKey(name: string) {
  if (!session.me) throw new Error("请先完成登录核验");
  return `${PRIVATE}${session.me.userId}:${name}`;
}
export function readPrivate<T>(name: string): T | null {
  const raw = sessionStorage.getItem(privateKey(name));
  return raw ? (JSON.parse(raw) as T) : null;
}
export function writePrivate(name: string, value: unknown) {
  sessionStorage.setItem(privateKey(name), JSON.stringify(value));
}
export function removePrivate(name: string) {
  sessionStorage.removeItem(privateKey(name));
}
