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
export function publishErrors(form: Form): string[] {
  const errors: string[] = [];
  if (!form.title.trim()) errors.push("填写标题");
  if (!form.body.trim()) errors.push("添加正文或已上传图片");
  if (!form.jobIds.length) errors.push("选择目标岗位");
  if (!form.majorIds.length) errors.push("选择文章适用专业");
  if (!form.experienceStage.trim()) errors.push("填写经历发生阶段");
  if (!["FIRST_HAND", "CURATED", "DEMO"].includes(form.sourceType || ""))
    errors.push("确认内容来源");
  if (form.sourceType === "CURATED" && !form.sourceNote.trim())
    errors.push("补充来源说明");
  return errors;
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
