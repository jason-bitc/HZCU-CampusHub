import { computed, ref } from "vue";
import { adminRequest, adminSession } from "./client.ts";
import { ApiError, message } from "../lib/api.ts";

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}
export interface Article {
  id: number;
  title: string;
  state: string;
  revision: number;
  updatedAt?: string;
  versionId?: number;
  body?: string;
  bodyFormat?: string;
}
export interface Comment {
  id: number;
  articleId: number;
  body: string;
  state: string;
  revision: number;
  createdAt: string;
}
export interface Category {
  id: number;
  kind: string;
  name: string;
  enabled: boolean;
  displayOrder: number;
  revision: number;
}
export interface User {
  id: number;
  nickname: string;
  accountState: string;
}
export interface Restriction {
  id: number;
  userId: number;
  scope: string;
  enabled: boolean;
  revision: number;
  reason: string;
  updatedAt: string;
}
export interface Appeal {
  id: number;
  restrictionId: number;
  restrictionRevision: number;
  state: string;
  revision: number;
  reason: string;
  reply: string;
  createdAt: string;
  updatedAt: string;
}
export interface GovernanceEvent {
  id: number;
  adminId: number;
  targetType: string;
  targetId: number;
  action: string;
  beforeState: string;
  afterState: string;
  revision: number;
  reason: string;
  occurredAt: string;
}
export const stateLabels: Record<string, string> = {
  PUBLISHED: "公开",
  AUTHOR_OFFLINE: "作者下架",
  ADMIN_HIDDEN: "管理员隐藏",
  DELETED: "已删除",
  VISIBLE: "公开",
  OPEN: "待复核",
  ACCEPTED: "已接受",
  REJECTED: "已驳回",
  ENABLED: "启用",
  DISABLED: "停用",
};
export const kinds: Record<string, string> = {
  JOB: "岗位",
  MAJOR: "专业",
  COMPANY: "公司",
  TOPIC: "主题",
};
export interface Mutation {
  path: string;
  method: "POST" | "PUT";
  body: Record<string, string | number | boolean>;
  label: string;
}
const prefix = "campushub.web.community.private:";
function key(surface: string) {
  if (!adminSession.me) throw new Error("管理身份尚未核验");
  return `${prefix}${adminSession.me.adminId}:mutation:${surface}`;
}
export function useMutation(
  surface: string,
  capability: string,
  refresh: () => Promise<void>,
) {
  const pending = ref<Mutation | null>(null);
  const busy = ref(false),
    error = ref(""),
    notice = ref("");
  const allowed = computed(
    () =>
      adminSession.status === "ready" &&
      !!adminSession.me?.capabilities.includes(capability),
  );
  function restore() {
    try {
      const raw = sessionStorage.getItem(key(surface));
      pending.value = raw ? (JSON.parse(raw) as Mutation) : null;
    } catch {
      error.value = "无法读取本机待确认请求，请核对服务端记录。";
    }
  }
  async function submit() {
    if (!pending.value || busy.value || !allowed.value) return;
    const command = pending.value;
    busy.value = true;
    error.value = "";
    notice.value = "";
    try {
      await adminRequest(command.path, command.method, command.body);
      sessionStorage.removeItem(key(surface));
      pending.value = null;
      notice.value = `${command.label}：服务端回执已确认。`;
    } catch (e) {
      error.value = `${message(e)}${e instanceof ApiError ? ` [${e.code}]` : ""} 原请求已保留，请按原请求核对，或读取最新状态后结束本机待确认记录。`;
    } finally {
      busy.value = false;
    }
    if (!pending.value) await refresh();
  }
  async function start(
    path: string,
    method: Mutation["method"],
    body: Omit<Mutation["body"], "requestId">,
    label: string,
  ) {
    if (!allowed.value || busy.value || pending.value) return;
    error.value = "";
    const command: Mutation = {
      path,
      method,
      body: { ...body, requestId: crypto.randomUUID() },
      label,
    };
    try {
      sessionStorage.setItem(key(surface), JSON.stringify(command));
      pending.value = command;
      await submit();
    } catch (e) {
      error.value = message(e);
    }
  }
  async function reconcile() {
    if (busy.value) return;
    await refresh();
    if (
      confirm(
        "请核对最新状态和审计记录。结束本机待确认请求不会撤销服务端操作；继续编辑将使用最新修订和新请求键。确认结束？",
      )
    ) {
      sessionStorage.removeItem(key(surface));
      pending.value = null;
      error.value = "";
      notice.value = "已结束本机待确认记录，请根据最新状态重新确认。";
      await refresh();
    }
  }
  return {
    pending,
    busy,
    error,
    notice,
    allowed,
    restore,
    submit,
    start,
    reconcile,
  };
}
