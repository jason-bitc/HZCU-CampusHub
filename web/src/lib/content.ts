import type { Draft, Form } from "../types";

export function fromDraft(draft: Draft): Form {
  const { tags, ...rest } = draft.content;
  return JSON.parse(JSON.stringify({ ...rest, ...tags })) as Form;
}
export function safeLink(raw: string): string {
  try {
    const url = new URL(raw);
    return ["http:", "https:"].includes(url.protocol) &&
      !url.username &&
      !url.password
      ? url.href
      : "";
  } catch {
    return "";
  }
}
export function imagePath(raw: string): string {
  return /^\/api\/v1\/media\/images\/[a-f0-9-]{36}$/.test(raw) ? raw : "";
}
const publishFields = {
  title: "填写标题",
  body: "添加正文或已上传图片",
  jobIds: "选择目标岗位",
  majorIds: "选择文章适用专业",
  experienceStage: "填写经历发生阶段",
  sourceType: "确认内容来源",
  sourceNote: "补充来源说明",
} as const;
type PublishField = keyof typeof publishFields;
export function publishChecklist(form: Form, serverFields: string[] = []) {
  const missing: Record<PublishField, boolean> = {
    title: !form.title.trim(),
    body: !form.body.trim(),
    jobIds: !form.jobIds.length,
    majorIds: !form.majorIds.length,
    experienceStage: !form.experienceStage.trim(),
    sourceType: !["FIRST_HAND", "CURATED", "DEMO"].includes(
      form.sourceType || "",
    ),
    sourceNote: form.sourceType === "CURATED" && !form.sourceNote.trim(),
  };
  return (Object.keys(publishFields) as PublishField[])
    .filter(
      (field) =>
        missing[field] ||
        serverFields.includes(field) ||
        (field === "body" && serverFields.includes("body或有效上传图片")),
    )
    .map((field) => ({ field, label: publishFields[field] }));
}
export function publishErrors(form: Form): string[] {
  return publishChecklist(form).map((issue) => issue.label);
}
export const stateName: Record<string, string> = {
  DRAFT: "草稿",
  PUBLISHED: "已发布",
  AUTHOR_OFFLINE: "作者已下架",
  ADMIN_HIDDEN: "管理员已隐藏",
  DELETED: "已删除",
};
export const sourceName: Record<string, string> = {
  FIRST_HAND: "亲历分享",
  CURATED: "资料整理",
  DEMO: "演示内容",
};
export function dateText(date: string) {
  return date
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(date))
    : "";
}
// 时间线等窄列使用的短格式：MM-DD HH:mm。
export function shortDateText(date: string | null | undefined) {
  if (!date) return "";
  const value = new Date(date);
  if (Number.isNaN(value.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(
    value.getHours(),
  )}:${pad(value.getMinutes())}`;
}
// 经验卡片使用的短日期：M月D日。
export function cardDate(date: string | null | undefined) {
  if (!date) return "";
  const value = new Date(date);
  if (Number.isNaN(value.getTime())) return "";
  return `${value.getMonth() + 1}月${value.getDate()}日`;
}
// 列表窄列使用的日期：YYYY-MM-DD HH:mm。
export function compactDateText(date: string | null | undefined) {
  if (!date) return "";
  const value = new Date(date);
  if (Number.isNaN(value.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(
    value.getDate(),
  )} ${pad(value.getHours())}:${pad(value.getMinutes())}`;
}
