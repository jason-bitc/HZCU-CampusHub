import assert from "node:assert/strict";
import test from "node:test";
import { request } from "../src/lib/api.ts";
import { clearSession, session } from "../src/lib/session.ts";

const values = new Map<string, string>();
Object.defineProperty(globalThis, "sessionStorage", {
  value: {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  },
  configurable: true,
});
function login() {
  session.token = "unit-test-token";
  session.expiresAt = new Date(Date.now() + 60000).toISOString();
  session.status = "ready";
}
test("interaction requests omit the body and attach Bearer only to private calls", async () => {
  const original = globalThis.fetch;
  const calls: RequestInit[] = [];
  login();
  globalThis.fetch = async (_url, init) => {
    calls.push(init || {});
    return Response.json({ code: "OK", data: {} });
  };
  try {
    await request("/articles/1/like", { method: "PUT", auth: true });
    await request("/articles/1");
    assert.equal(calls[0]?.body, undefined);
    assert.deepEqual(calls[0]?.headers, {
      Authorization: "Bearer unit-test-token",
    });
    assert.deepEqual(calls[1]?.headers, {});
  } finally {
    globalThis.fetch = original;
    clearSession();
  }
});
test("a delayed private JSON response cannot survive logout", async () => {
  const original = globalThis.fetch;
  login();
  let release: (value: unknown) => void = () => {};
  const delayed = new Promise<unknown>((resolve) => (release = resolve));
  globalThis.fetch = async () =>
    ({ ok: true, status: 200, json: () => delayed }) as Response;
  try {
    const result = request("/me/profile", { auth: true });
    await new Promise((resolve) => setTimeout(resolve, 0));
    clearSession();
    release({ code: "OK", data: { nickname: "private" } });
    await assert.rejects(result, { code: "SESSION_CHANGED" });
  } finally {
    globalThis.fetch = original;
    clearSession();
  }
});
test("401 clears authentication and does not retry the mutation", async () => {
  const original = globalThis.fetch;
  let count = 0;
  login();
  globalThis.fetch = async () => {
    count++;
    return Response.json(
      { code: "UNAUTHORIZED", message: "expired" },
      { status: 401 },
    );
  };
  try {
    await assert.rejects(
      request("/me/articles", {
        method: "POST",
        auth: true,
        data: { requestId: "original" },
      }),
      { status: 401 },
    );
    assert.equal(count, 1);
    assert.equal(session.token, "");
    assert.equal(session.status, "guest");
  } finally {
    globalThis.fetch = original;
    clearSession();
  }
});
test("restricted sessions discard old private responses and keep the inbox available", async () => {
  const original = globalThis.fetch;
  login();
  let release: (response: Response) => void = () => {};
  const profile = new Promise<Response>((resolve) => (release = resolve));
  const calls: string[] = [];
  globalThis.fetch = async (url, init) => {
    const path = String(url);
    calls.push(path);
    assert.equal(
      (init?.headers as Record<string, string>).Authorization,
      "Bearer unit-test-token",
    );
    if (path === "/api/v1/me/profile") return profile;
    if (path === "/api/v1/me")
      return Response.json({ code: "ACCESS_RESTRICTED" }, { status: 403 });
    return Response.json({ code: "OK", data: { unreadCount: 1 } });
  };
  try {
    const old = request("/me/profile", { auth: true });
    await assert.rejects(request("/me", { auth: true }), {
      code: "ACCESS_RESTRICTED",
    });
    release(Response.json({ code: "OK", data: { nickname: "private" } }));
    await assert.rejects(old, { code: "SESSION_CHANGED" });
    await assert.rejects(request("/me/notifications/1/other", { auth: true }), {
      code: "ACCESS_RESTRICTED",
    });
    assert.deepEqual(
      await request("/me/notifications?page=1", { auth: true }),
      { unreadCount: 1 },
    );
    assert.deepEqual(calls, [
      "/api/v1/me/profile",
      "/api/v1/me",
      "/api/v1/me/notifications?page=1",
    ]);
    assert.equal(session.status, "limited");
  } finally {
    globalThis.fetch = original;
    clearSession();
  }
});
test("an empty gateway failure becomes an actionable service error", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async () => new Response(null, { status: 503 });
  try {
    await assert.rejects(request("/articles"), {
      status: 503,
      code: "HTTP_503",
      message: "服务暂时不可用，请稍后重试。",
    });
  } finally {
    globalThis.fetch = original;
  }
});
test("an empty 401 response still clears a private session", async () => {
  const original = globalThis.fetch;
  login();
  globalThis.fetch = async () => new Response(null, { status: 401 });
  try {
    await assert.rejects(request("/me/profile", { auth: true }), {
      status: 401,
      code: "HTTP_401",
    });
    assert.equal(session.token, "");
    assert.equal(session.status, "guest");
  } finally {
    globalThis.fetch = original;
    clearSession();
  }
});
test("private image download returns a Blob rather than parsing JSON", async () => {
  const original = globalThis.fetch;
  login();
  globalThis.fetch = async () =>
    new Response(new Uint8Array([1, 2, 3]), {
      headers: { "Content-Type": "image/png" },
    });
  try {
    const result = await request<Blob>("/media/images/test", {
      auth: true,
      blob: true,
    });
    assert.equal(result.size, 3);
  } finally {
    globalThis.fetch = original;
    clearSession();
  }
});
