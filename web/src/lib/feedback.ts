import { ApiError, message } from "./api.ts";

export type Feedback = {
  kind: "success" | "error" | "pending" | "denied";
  text: string;
};

export function operationFailure(error: unknown, uncertain?: string): Feedback {
  if (error instanceof ApiError && [401, 403].includes(error.status))
    return { kind: "denied", text: "权限不足。" + message(error) };
  if (uncertain && (!(error instanceof ApiError) || error.status >= 500))
    return { kind: "pending", text: uncertain + message(error) };
  return { kind: "error", text: message(error) };
}
