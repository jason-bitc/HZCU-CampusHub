export interface OpsMe {
  actorDomain: "OPS_ADMIN";
  capabilities: string[];
}
export type OpsAction = "DEPLOY" | "RESTART" | "BACKUP" | "RESTORE";
export interface Artifact {
  id: string;
  sha256: string;
  schemaFingerprint: string;
}
export interface Backup {
  id: string;
  createdAt: string;
  complete: boolean;
  schemaFingerprint: string;
}
export interface OpsStatus {
  target: string;
  healthy: boolean;
  managedProcessRunning: boolean;
  release: Partial<Artifact>;
  recoveryRequired: boolean;
}
export interface Operation {
  action: OpsAction;
  target: "business";
  referenceId: string;
}
export interface Confirmation extends Operation {
  confirmationId: string;
  expiresAt: string;
  impact: string;
}
export interface JobInput extends Operation {
  requestId: string;
  confirmationId: string;
}
export interface Job {
  id: string;
  requestId: string;
  action: OpsAction;
  target: string;
  referenceId: string;
  state: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "INTERRUPTED";
  createdAt: string;
  startedAt?: string;
  finishedAt?: string;
  events: { phase: string; occurredAt: string }[];
  errorCode?: string;
  result?: {
    healthy?: boolean;
    backupId?: string;
    protectionBackupId?: string;
    referenceId?: string;
  };
}
export const actionLabels: Record<OpsAction, string> = {
  DEPLOY: "部署",
  RESTART: "重启",
  BACKUP: "完整备份",
  RESTORE: "恢复备份",
};
export const jobLabels: Record<Job["state"], string> = {
  QUEUED: "排队中",
  RUNNING: "执行中",
  SUCCEEDED: "执行成功",
  FAILED: "执行失败",
  INTERRUPTED: "中断待核查",
};
export const phases: Record<string, string> = {
  PREFLIGHT: "预检查",
  STOPPING: "停止受控业务",
  PROTECTING: "保护当前状态",
  INSTALLING: "安装已登记版本",
  RESTORING: "恢复数据库与图片",
  STARTING: "启动并等待健康检查",
  VERIFIED: "核对通过",
  ROLLING_BACK: "保护副本回退",
  ROLLBACK_VERIFIED: "回退核对通过",
  MANUAL_RECOVERY_REQUIRED: "需要人工恢复",
};
