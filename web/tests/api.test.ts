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
