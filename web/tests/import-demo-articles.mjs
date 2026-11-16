// Imports two source-indexed articles through the same API used by the editors.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const [sourceDir, fixtureDir] = process.argv.slice(2);
assert(
  sourceDir && fixtureDir,
  "Pass the demo-article directory and the isolated fixture directory.",
);
const config = JSON.parse(
  await readFile(resolve(fixtureDir, "ops.private.json"), "utf8"),
);
assert.equal(config.databaseName, "campushub_test");
assert.equal(config.healthUrl, "http://127.0.0.1:8082/actuator/health");
const fixture = JSON.parse(
  await readFile(resolve(fixtureDir, "fixture.private.json"), "utf8"),
);
const reportPath = resolve(fixtureDir, "demo-import.json");
const index = JSON.parse(
  await readFile(resolve(sourceDir, "article-index.json"), "utf8"),
);
const evidence = [];
const imported = [];

async function api(path, { token, method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body && !(body instanceof FormData))
    headers["Content-Type"] = "application/json";
  const response = await fetch(`http://127.0.0.1:8082/api/v1${path}`, {
    method,
    headers,
    body:
      body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  const json = await response.json();
  evidence.push({
    method,
    path,
    status: response.status,
    code: json.code,
    traceId: json.traceId,
  });
  assert(
    response.ok && json.code === "OK",
    `${method} ${path} ${response.status} ${json.code} ${json.traceId}`,
  );
  return json.data;
}

try {
  const author = (
    await api("/auth/login", {
      method: "POST",
      body: { email: fixture.email, password: fixture.password },
    })
  ).accessToken;
  const admin = (
    await api("/admin/auth/login", {
      method: "POST",
      body: { login: fixture.adminLogin, password: fixture.password },
    })
  ).accessToken;
  const catalog = await api("/catalog");
  const groups = [
    ["job", "jobs", "JOB"],
    ["major", "majors", "MAJOR"],
    ["company", "companies", "COMPANY"],
    ["topic", "topics", "TOPIC"],
  ];
  for (const item of index.filter(({ id }) => ["A01", "A03"].includes(id))) {
    const tags = {};
    for (const [key, group, kind] of groups) {
      tags[`${key}Ids`] = [];
      for (const name of item[`${key}Tags`]) {
        let category = catalog[group].find((row) => row.name === name);
        if (!category) {
          await api("/admin/categories", {
            token: admin,
            method: "POST",
            body: {
              kind,
              name,
              enabled: true,
              displayOrder: 50,
              expectedRevision: 0,
              requestId: randomUUID(),
              reason: "隔离演示环境文章索引标签",
            },
          });
          catalog[group] = (await api("/catalog"))[group];
          category = catalog[group].find((row) => row.name === name);
        }
        assert(category, `Missing ${kind} ${name}`);
        tags[`${key}Ids`].push(category.id);
      }
    }
    const title = `【验收演示】${item.title}`;
    const existing = (
      await api("/me/articles?page=1&size=100&state=PUBLISHED", {
        token: author,
      })
    ).items.find((row) => row.title === title);
    if (existing) {
      imported.push({
        sourceId: item.id,
        articleId: existing.articleId,
        title,
        tags: tags,
        existing: true,
      });
      continue;
    }
    const { articleId } = await api("/me/articles", {
      token: author,
      method: "POST",
      body: { requestId: randomUUID() },
    });
    const endpoint = `/me/articles/${articleId}`;
    let body = (await readFile(resolve(sourceDir, item.file), "utf8")).replace(
      /^\uFEFF/,
      "",
    );
    const assets = [];
    for (const match of body.matchAll(/\]\(\.\.\/assets\/([^()]+\.png)\)/g)) {
      const bytes = await readFile(resolve(sourceDir, "assets", match[1]));
      const form = new FormData();
      form.set("requestId", randomUUID());
      form.set(
        "file",
        new Blob([bytes], { type: "image/png" }),
        match[1].split("/").pop(),
      );
      const uploaded = await api(`${endpoint}/images`, {
        token: author,
        method: "POST",
        body: form,
      });
      body = body.replace(`../assets/${match[1]}`, uploaded.url);
      assets.push(uploaded.url);
    }
    const draft = await api(`${endpoint}/draft`, { token: author });
    const saved = await api(`${endpoint}/draft`, {
      token: author,
      method: "PUT",
      body: {
        expectedRevision: draft.revision,
        title,
        summary: item.summary,
        body,
        ...tags,
        experienceStage: item.experienceStage,
        sourceType: item.sourceType,
        sourceNote: `隔离演示数据。 ${item.sourceNote}`,
        sourceLinks: item.sourceLinks,
      },
    });
    const preview = await api(
      `${endpoint}/preview?expectedRevision=${saved.revision}`,
      { token: author },
    );
    assert.equal(
      preview.canPublish,
      true,
      JSON.stringify(preview.missingFields),
    );
    const receipt = await api(`${endpoint}/publish`, {
      token: author,
      method: "POST",
      body: {
        expectedRevision: saved.revision,
        requestId: randomUUID(),
      },
    });
    const published = await api(`/articles/${articleId}`);
    const editable = await api(`${endpoint}/draft`, { token: author });
    assert.equal(published.body, body);
    assert.equal(editable.content.body, body);
    assert.deepEqual(published.sourceLinks, item.sourceLinks);
    for (const asset of assets) {
      const response = await fetch(`http://127.0.0.1:8082${asset}`);
      assert.equal(response.status, 200);
      assert.equal(response.headers.get("content-type"), "image/png");
      evidence.push({
        method: "GET",
        path: asset,
        status: response.status,
        traceId: response.headers.get("x-trace-id"),
      });
    }
    imported.push({
      sourceId: item.id,
      articleId,
      versionId: receipt.versionId,
      title,
      tags,
      assets,
    });
  }
  assert.equal(imported.length, 2);
  console.log(
    imported
      .map(({ sourceId, articleId }) => `${sourceId} -> ${articleId}`)
      .join("\n"),
  );
} finally {
  await writeFile(reportPath, JSON.stringify({ imported, evidence }, null, 2));
}
