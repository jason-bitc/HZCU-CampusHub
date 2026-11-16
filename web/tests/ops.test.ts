import test from "node:test";
import assert from "node:assert/strict";
import {
  opsRequest,
  opsSession,
  clearOps,
} from "../src/management/ops-client.ts";
import { adminSession } from "../src/management/client.ts";
import { session } from "../src/lib/session.ts";
const data: Record<string, string> = {};
const storage = Object.create(null) as Storage;
Object.defineProperties(storage, {
  getItem: { value: (k: string) => data[k] ?? null },
  setItem: {
    value: (k: string, v: string) => {
      data[k] = v;
      Object.defineProperty(storage, k, {
        value: v,
        configurable: true,
        enumerable: true,
      });
    },
  },
  removeItem: {
    value: (k: string) => {
      delete data[k];
      delete (storage as unknown as Record<string, unknown>)[k];
    },
  },
});
Object.defineProperty(globalThis, "sessionStorage", {
  value: storage,
  configurable: true,
});
function auth() {
  opsSession.token = "ops_unit";
  opsSession.expiresAt = new Date(Date.now() + 60000).toISOString();
  opsSession.me = {
    actorDomain: "OPS_ADMIN",
    capabilities: ["STATUS_READ", "RESTART"],
  };
  opsSession.status = "ready";
}
test("ops uses its own Bearer, bodyless logout, and rejects other domains", async () => {
  const old = fetch;
  auth();
  session.token = "campus_unit";
  adminSession.token = "adm_unit";
  let input: RequestInit | undefined;
  globalThis.fetch = async (_url, init) => {
    input = init;
    return Response.json({ code: "OK", data: { loggedOut: true } });
  };
  try {
    await opsRequest("/ops/auth/logout", "POST");
    assert.equal(input?.body, undefined);
    assert.deepEqual(input?.headers, { Authorization: "Bearer ops_unit" });
    await assert.rejects(opsRequest("/admin/me"), /路径无效/);
    await assert.rejects(opsRequest("/me"), /路径无效/);
  } finally {
    globalThis.fetch = old;
    clearOps();
    session.token = "";
    adminSession.token = "";
  }
});
test("ops 401 clears only ops data even for malformed gateway response", async () => {
  const old = fetch;
  auth();
  session.token = "campus_unit";
  adminSession.token = "adm_unit";
  sessionStorage.setItem("campushub.web.ops.private:operation", "private");
  sessionStorage.setItem("campushub.web.community.private:case", "keep");
  globalThis.fetch = async () => new Response("unauthorized", { status: 401 });
  try {
    await assert.rejects(opsRequest("/ops/me"));
    assert.equal(opsSession.token, "");
    assert.equal(session.token, "campus_unit");
    assert.equal(adminSession.token, "adm_unit");
    assert.equal(
      sessionStorage.getItem("campushub.web.ops.private:operation"),
      null,
    );
    assert.equal(
      sessionStorage.getItem("campushub.web.community.private:case"),
      "keep",
    );
  } finally {
    globalThis.fetch = old;
    clearOps();
    session.token = "";
    adminSession.token = "";
  }
});
test("old ops 401 cannot clear a new session", async () => {
  const old = fetch;
  auth();
  let finish: (r: Response) => void = () => {};
  globalThis.fetch = () => new Promise((r) => (finish = r));
  try {
    const request = opsRequest("/ops/status");
    opsSession.epoch++;
    opsSession.token = "ops_new";
    finish(
      Response.json(
        { code: "OPS_UNAUTHORIZED", message: "expired" },
        { status: 401 },
      ),
    );
    await assert.rejects(request, { code: "SESSION_CHANGED" });
    assert.equal(opsSession.token, "ops_new");
  } finally {
    globalThis.fetch = old;
    clearOps();
  }
});
test("invalid ops expiry never sends a privileged request", async () => {
  const old = fetch;
  auth();
  opsSession.expiresAt = "invalid";
  let count = 0;
  globalThis.fetch = async () => {
    count++;
    return Response.json({});
  };
  try {
    await assert.rejects(opsRequest("/ops/status"), { status: 401 });
    assert.equal(count, 0);
  } finally {
    globalThis.fetch = old;
    clearOps();
  }
});
