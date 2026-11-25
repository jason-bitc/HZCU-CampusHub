import { caseListPath, opsLogQuery } from "../src/management/console-ux.ts";
import assert from "node:assert/strict";
import test from "node:test";
import {
  adminRequest,
  adminSession,
  clearAdmin,
  storeCommand,
  pendingCommand,
} from "../src/management/client.ts";
import { session } from "../src/lib/session.ts";
const storage: Record<string, string> = {};
const shim = Object.create(null) as Storage;
Object.defineProperties(shim, {
  getItem: { value: (key: string) => storage[key] ?? null },
  setItem: {
    value: (key: string, value: string) => {
      storage[key] = value;
      Object.defineProperty(shim, key, {
        value,
        enumerable: true,
        configurable: true,
        writable: true,
      });
    },
  },
  removeItem: {
    value: (key: string) => {
      delete storage[key];
      delete (shim as unknown as Record<string, unknown>)[key];
    },
  },
});
Object.defineProperty(globalThis, "sessionStorage", {
  value: shim,
  configurable: true,
});
function auth() {
  adminSession.token = "adm_unit";
  adminSession.expiresAt = new Date(Date.now() + 60000).toISOString();
  adminSession.me = {
    adminId: 1,
    actorDomain: "COMMUNITY_ADMIN",
    capabilities: ["CASE_READ", "CASE_HANDLE"],
  };
  adminSession.status = "ready";
}
test("management login never inherits campus Bearer", async () => {
  const old = globalThis.fetch;
  let headers: HeadersInit | undefined;
  session.token = "campus_unit";
  globalThis.fetch = async (_url, init) => {
    headers = init?.headers;
    return Response.json({ code: "OK", data: {} });
  };
  try {
    await adminRequest(
      "/admin/auth/login",
      "POST",
      { login: "unit", password: "unit-password" },
      true,
    );
    assert.deepEqual(headers, { "Content-Type": "application/json" });
  } finally {
    globalThis.fetch = old;
    clearAdmin();
    session.token = "";
  }
});
test("admin logout is bodyless and only uses its own identity domain", async () => {
  const old = globalThis.fetch;
  let options: RequestInit | undefined;
  auth();
  globalThis.fetch = async (_url, init) => {
    options = init;
    return Response.json({ code: "OK", data: {} });
  };
  try {
    await adminRequest("/admin/auth/logout", "POST");
    assert.equal(options?.body, undefined);
    assert.deepEqual(options?.headers, { Authorization: "Bearer adm_unit" });
    await assert.rejects(adminRequest("/me"), /管理请求路径无效/);
  } finally {
    globalThis.fetch = old;
    clearAdmin();
  }
});
test("admin 401 clears only management storage and leaves campus login intact", async () => {
  const old = globalThis.fetch;
  auth();
  session.token = "campus_unit";
  sessionStorage.setItem("campushub.web.session.v1", "campus-record");
  sessionStorage.setItem(
    "campushub.web.community.private:1:case:10",
    "private-request",
  );
  globalThis.fetch = async () =>
    Response.json(
      { code: "ADMIN_UNAUTHORIZED", message: "expired" },
      { status: 401 },
    );
  try {
    await assert.rejects(adminRequest("/admin/me"), { status: 401 });
    assert.equal(adminSession.token, "");
    assert.equal(session.token, "campus_unit");
    assert.equal(
      sessionStorage.getItem("campushub.web.session.v1"),
      "campus-record",
    );
    assert.equal(
      sessionStorage.getItem("campushub.web.community.private:1:case:10"),
      null,
    );
  } finally {
    globalThis.fetch = old;
    clearAdmin();
    session.token = "";
  }
});
test("late admin response cannot survive logout or clear a new management login", async () => {
  const old = globalThis.fetch;
  auth();
  let release: () => void = () => {};
  const gate = new Promise<void>((resolve) => (release = resolve));
  globalThis.fetch = async () => {
    await gate;
    return Response.json(
      { code: "ADMIN_UNAUTHORIZED", message: "old" },
      { status: 401 },
    );
  };
  try {
    const result = adminRequest("/admin/me");
    clearAdmin();
    auth();
    adminSession.token = "adm_new";
    release();
    await assert.rejects(result, { code: "SESSION_CHANGED" });
    assert.equal(adminSession.token, "adm_new");
  } finally {
    globalThis.fetch = old;
    clearAdmin();
  }
});
test("pending case action retains the original key revision and text across reload reads", () => {
  auth();
  try {
    const command = {
      action: "decisions" as const,
      caseId: 10,
      body: {
        expectedRevision: 3,
        requestId: "01234567-89ab-cdef-0123-456789abcdef",
        outcome: "NO_VIOLATION",
        reply: "original reply",
        internalNote: "",
      },
    };
    storeCommand(command);
    command.body.reply = "later input";
    const restored = pendingCommand(10);
    assert.equal(restored?.body.reply, "original reply");
    assert.equal(restored?.body.expectedRevision, 3);
    assert.equal(
      restored?.body.requestId,
      "01234567-89ab-cdef-0123-456789abcdef",
    );
  } finally {
    clearAdmin();
  }
});

test("case return paths retain list filters without entering another surface", () => {
  const list = "/admin/cases?kind=REPORT&state=OPEN&page=2&snapshot=41";
  assert.equal(caseListPath(list), list);
  for (const path of [
    "/ops/restore",
    "//other.invalid/admin/cases",
    "/admin/cases/41",
    "/admin/cases#target",
  ])
    assert.equal(caseListPath(path), "/admin/cases");
});

test("ops log retries serialize the selected safe query fields", () => {
  const id = "7e6955fe-8c45-41eb-aa0d-0b27851e92e9";
  const query = new URLSearchParams(opsLogQuery(" " + id + " ", true, 20));
  assert.equal(query.get("traceId"), id);
  assert.equal(query.get("errorsOnly"), "true");
  assert.equal(query.get("limit"), "20");
  assert(!new URLSearchParams(opsLogQuery("", false, 100)).has("traceId"));
});
