export interface AdminMe {
  adminId: number;
  actorDomain: "COMMUNITY_ADMIN";
  capabilities: string[];
}
export interface CaseRow {
  caseId: number;
  kind: "REPORT" | "FEEDBACK";
  category: string;
  state: string;
  revision: number;
  createdAt: string;
  updatedAt: string;
  targetType: string | null;
  articleId: number | null;
  commentId: number | null;
  assigneeId: number | null;
}
export interface CasePage {
  items: CaseRow[];
  total: number;
  page: number;
  size: number;
  snapshot: number;
}
export interface CaseHistory {
  revision: number;
  action: string;
  state: string;
  reply: string;
  outcome: string | null;
  occurredAt: string;
  actorDomain: string;
  actorId: number;
  assigneeId: number | null;
  internalNote: string;
  reason: string;
}
export interface CaseDetail extends CaseRow {
  description: string;
  submitterId: number;
  articleVersionId: number | null;
  images: { id: string; mime: string; width: number; height: number }[];
  history: CaseHistory[];
}
export interface CaseContext {
  caseId: number;
  articleAvailable: boolean;
  article: {
    versionId: number;
    articleId: number;
    title: string;
    summary: string;
    body: string;
    bodyFormat: string;
    sourceType: string;
    publishedAt: string;
    currentState: string;
  } | null;
  comments: {
    role: string;
    commentId: number;
    available: boolean;
    body: string;
    createdAt: string | null;
  }[];
  media: { id: string; sourceKind: string }[];
}
export interface CaseCommand {
  action: "claim" | "release" | "decisions";
  caseId: number;
  body: {
    expectedRevision: number;
    requestId: string;
    reason?: string;
    outcome?: string;
    reply?: string;
    internalNote?: string;
  };
}
export const caseStates: Record<string, string> = {
  OPEN: "待受理",
  PROCESSING: "处理中",
  NEEDS_ACTION: "待后续治理",
  CLOSED: "已完成",
};
export const caseKinds: Record<string, string> = {
  REPORT: "举报",
  FEEDBACK: "使用反馈",
};
export const caseCategories: Record<string, string> = {
  ADVERTISEMENT: "广告举报",
  MISLEADING_CONTENT: "内容举报",
  BUG: "使用问题",
  SUGGESTION: "建议",
  OTHER: "其他",
};
export const outcomes: Record<string, string> = {
  NO_VIOLATION: "未发现违规",
  DUPLICATE: "重复举报",
  REQUIRES_GOVERNANCE: "交接后续治理",
  GOVERNANCE_COMPLETED: "已执行治理并闭案",
  ANSWERED: "已答复",
  RECORDED: "已记录",
};
