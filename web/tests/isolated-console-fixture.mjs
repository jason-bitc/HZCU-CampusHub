import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { resolve, sep } from "node:path";

export async function isolatedConsole(root, records = []) {
  assert(root, "Set CAMPUSHUB_CONSOLE_FIXTURE to the isolated directory.");
  const config = JSON.parse(
    await readFile(resolve(root, "ops.private.json"), "utf8"),
  );
  const business = JSON.parse(
    await readFile(resolve(root, "business.private.json"), "utf8"),
  );
  assert.equal(config.databaseName, "campushub_test");
  assert.equal(business.CAMPUSHUB_DB_NAME, "campushub_test");
  assert.equal(business.CAMPUSHUB_RUNTIME_ENV, "test");
  assert.equal(config.healthUrl, "http://127.0.0.1:8082/actuator/health");
  assert.equal(config.port, 8092);
  for (const dir of ["runtimeRoot", "artifactRoot", "backupRoot", "mediaRoot"])
    assert(resolve(config[dir]).startsWith(resolve(root) + sep));
  const fixture = JSON.parse(
    await readFile(resolve(root, "fixture.private.json"), "utf8"),
  );
  async function request(path, token, method = "GET", body, expected) {
    const headers = token ? { Authorization: "Bearer " + token } : {};
    if (body && !(body instanceof FormData))
      headers["Content-Type"] = "application/json";
    const response = await fetch("http://127.0.0.1:8082/api/v1" + path, {
      method,
      headers,
      body:
        body instanceof FormData
          ? body
          : body === undefined
            ? undefined
            : JSON.stringify(body),
    });
    const value = await response.json();
    records.push({
      method,
      path: path.split("?")[0],
      status: response.status,
      code: value.code,
      traceId: value.traceId,
    });
    if (expected) assert.equal(response.status, expected);
    else
      assert(
        response.ok && value.code === "OK",
        method + " " + path.split("?")[0] + " " + value.code,
      );
    return value.data;
  }
  const admin = async () =>
    (
      await request("/admin/auth/login", null, "POST", {
        login: fixture.adminLogin,
        password: fixture.password,
      })
    ).accessToken;
  const member = async (email = fixture.email) =>
    (
      await request("/auth/login", null, "POST", {
        email,
        password: fixture.password,
      })
    ).accessToken;
  return { config, fixture, request, admin, member };
}

export async function publishIsolatedArticle(env) {
  const author = await env.member();
  let catalog = await env.request("/catalog");
  if (!catalog.jobs.length || !catalog.majors.length) {
    const token = await env.admin();
    for (const kind of ["JOB", "MAJOR"]) {
      await env.request("/admin/categories", token, "POST", {
        kind,
        name: "隔离验收 " + kind + " " + randomUUID().slice(0, 8),
        enabled: true,
        displayOrder: 0,
        expectedRevision: 0,
        requestId: randomUUID(),
        reason: "隔离验收目录",
      });
    }
    catalog = await env.request("/catalog");
  }
  const jobs = catalog.jobs;
  const majors = catalog.majors;
  const title = "【隔离验收】网页治理与恢复 " + randomUUID().slice(0, 8);
  assert(
    jobs.length && majors.length,
    "Test catalog needs an enabled job and major.",
  );
  const { articleId } = await env.request("/me/articles", author, "POST", {
    requestId: randomUUID(),
  });
  const image = new FormData();
  image.set("requestId", randomUUID());
  image.set(
    "file",
    new Blob(
      [
        await readFile(
          new URL("../public/campushub-logo.png", import.meta.url),
        ),
      ],
      { type: "image/png" },
    ),
    "acceptance.png",
  );
  const uploaded = await env.request(
    "/me/articles/" + articleId + "/images",
    author,
    "POST",
    image,
  );
  const draft = await env.request(
    "/me/articles/" + articleId + "/draft",
    author,
    "PUT",
    {
      expectedRevision: 0,
      title,
      summary: "完整测试数据恢复及固定版本核对",
      body:
        "## 真实接口验收\n\n本文章仅用于隔离测试。\n\n![城院帮项目示例图片](" +
        uploaded.url +
        ")",
      jobIds: [jobs[0].id],
      majorIds: [majors[0].id],
      companyIds: [],
      topicIds: [],
      experienceStage: "大三",
      sourceType: "DEMO",
      sourceNote: "",
      sourceLinks: [],
    },
  );
  await env.request("/me/articles/" + articleId + "/publish", author, "POST", {
    expectedRevision: draft.revision,
    requestId: randomUUID(),
  });
  return { articleId, title, image: uploaded.url };
}
