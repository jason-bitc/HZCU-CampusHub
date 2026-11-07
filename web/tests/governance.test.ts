import test from "node:test";
import assert from "node:assert/strict";
import { useMutation } from "../src/management/governance.ts";
import { adminSession, clearAdmin } from "../src/management/client.ts";
const data: Record<string, string> = {};
Object.defineProperty(globalThis, "sessionStorage", {
  value: {
    getItem: (k: string) => data[k] ?? null,
    setItem: (k: string, v: string) => {
      data[k] = v;
    },
    removeItem: (k: string) => {
      delete data[k];
    },
  },
  configurable: true,
});
test("uncertain governance write restores the exact original body and key after reload", async () => {
  const old = fetch;
  adminSession.token = "adm_unit";
  adminSession.expiresAt = new Date(Date.now() + 60000).toISOString();
  adminSession.me = {
    adminId: 13,
    actorDomain: "COMMUNITY_ADMIN",
    capabilities: ["CONTENT_MODERATE"],
  };
  adminSession.status = "ready";
  let refresh = 0;
  const bodies: string[] = [];
  globalThis.fetch = async (_u, i) => {
    bodies.push(String(i?.body));
    if (bodies.length === 1) throw new TypeError("receipt lost");
    return Response.json({
      code: "OK",
      data: { targetId: 42, revision: 1, state: "ADMIN_HIDDEN" },
    });
  };
  try {
    const first = useMutation("content", "CONTENT_MODERATE", async () => {
      refresh++;
    });
    await first.start(
      "/admin/articles/42/moderation",
      "POST",
      { action: "HIDE", expectedRevision: 0, reason: "核对事实" },
      "隐藏内容",
    );
    assert.ok(first.pending.value);
    const restored = useMutation("content", "CONTENT_MODERATE", async () => {
      refresh++;
    });
    restored.restore();
    assert.deepEqual(restored.pending.value, first.pending.value);
    await restored.start(
      "/admin/articles/99/moderation",
      "POST",
      { action: "HIDE", expectedRevision: 9, reason: "changed" },
      "changed",
    );
    assert.equal(bodies.length, 1);
    await restored.submit();
    assert.equal(bodies[0], bodies[1]);
    assert.equal(restored.pending.value, null);
    assert.equal(refresh, 1);
  } finally {
    globalThis.fetch = old;
    clearAdmin();
  }
});
test("missing governance capability prevents a local write", async () => {
  const old = fetch;
  adminSession.status = "ready";
  adminSession.me = {
    adminId: 14,
    actorDomain: "COMMUNITY_ADMIN",
    capabilities: ["CASE_READ"],
  };
  let count = 0;
  globalThis.fetch = async () => {
    count++;
    return Response.json({});
  };
  try {
    const mutation = useMutation(
      "categories",
      "CATEGORY_MANAGE",
      async () => {},
    );
    await mutation.start("/admin/categories", "POST", {}, "创建");
    assert.equal(count, 0);
    assert.equal(mutation.pending.value, null);
  } finally {
    globalThis.fetch = old;
    clearAdmin();
  }
});
