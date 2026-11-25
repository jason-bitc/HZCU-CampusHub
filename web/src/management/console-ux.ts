import { ApiError, message } from "../lib/api.ts";

export interface ConfirmationCopy {
  title: string;
  detail: string;
  action: string;
  danger?: boolean;
}

export function consoleError(error: unknown) {
  if (!(error instanceof ApiError)) return message(error);
  return (
    (error.status === 403 ? "权限不足。 " : "") +
    message(error) +
    " [" +
    error.code +
    "]"
  );
}

export function caseListPath(value: unknown) {
  return typeof value === "string" && /^\/admin\/cases(?:\?[^#]*)?$/.test(value)
    ? value
    : "/admin/cases";
}

export function opsLogQuery(trace: string, onlyErrors: boolean, limit: number) {
  const query = new URLSearchParams({
    limit: String(limit),
    errorsOnly: String(onlyErrors),
  });
  if (trace.trim()) query.set("traceId", trace.trim());
  return query.toString();
}
