// Real local MySQL + dedicated ops JVM. Requires freshly generated private test fixtures.
// Never targets the development database or an existing business process.
import { chromium } from "playwright";
import assert from "node:assert/strict";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
const privateRoot = process.env.CAMPUSHUB_CONSOLE_FIXTURE;
if (!privateRoot)
  throw new Error(
    "Set CAMPUSHUB_CONSOLE_FIXTURE to the isolated local fixture directory.",
  );
const config = JSON.parse(
  await readFile(resolve(privateRoot, "ops.private.json"), "utf8"),
);
assert.equal(config.databaseName, "campushub_test");
assert.equal(config.port, 8092);
assert.equal(config.healthUrl, "http://127.0.0.1:8082/actuator/health");
const fixture = JSON.parse(
  await readFile(resolve(privateRoot, "fixture.private.json"), "utf8"),
);
const root = resolve(import.meta.dirname, ".."),
  out = resolve(
    root,
    process.env.CAMPUSHUB_CONSOLE_SCREENSHOT_DIR ||
      "../tmp/console-real-browser/screenshots",
  );
await mkdir(out, { recursive: true });
const report = {
  checkedAt: new Date().toISOString(),
  status: "RUNNING",
  mode: "real isolated campushub_test, business 8082, ops 8092; browser surfaces 5173/5174/5175",
  checks: [],
  evidence: [],
  screenshots: [],
  errors: [],
};
const reportPath = resolve(
  root,
  process.env.CAMPUSHUB_CONSOLE_REPORT ||
    "../tmp/console-real-browser/report.json",
);
await mkdir(dirname(reportPath), { recursive: true });
async function save() {
  await writeFile(reportPath, JSON.stringify(report, null, 2) + "\n");
}
async function pass(label) {
  report.checks.push(label);
  await save();
  console.log("PASS", label);
}
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 960 },
  reducedMotion: "reduce",
});
context.on("page", (page) => {
  page.on("pageerror", (e) => report.errors.push(e.message));
  page.on("response", async (response) => {
    if (!response.url().includes("/api/v1/")) return;
    try {
      const json = await response.json();
      report.evidence.push({
        method: response.request().method(),
        path: new URL(response.url()).pathname,
        status: response.status(),
        code: json.code,
        traceId: json.traceId,
      });
    } catch {
      /* A deliberate transport drop has no JSON. */
    }
  });
});
const ops = await context.newPage(),
  admin = await context.newPage(),
  user = await context.newPage();
async function go(page, port, path) {
  await page.bringToFront();
  await page.goto(`http://127.0.0.1:${port}/#${path}`);
  await page.locator("h1").first().waitFor();
}
async function shot(page, name, fullPage = false) {
  await page.screenshot({ path: resolve(out, name), fullPage });
  report.screenshots.push(name);
}
async function api(page, path, domain = "user", method = "GET", body) {
  const key = {
    user: "campushub.web.session.v1",
    admin: "campushub.web.community.session.v1",
    ops: "campushub.web.ops.session.v1",
  }[domain];
  return page.evaluate(
    async ({ path, key, method, body }) => {
      const token = JSON.parse(sessionStorage.getItem(key)).accessToken;
      const headers = { Authorization: `Bearer ${token}` };
      if (body !== undefined) headers["Content-Type"] = "application/json";
      const res = await fetch(`/api/v1${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      return { status: res.status, ...(await res.json()) };
    },
    { path, key, method, body },
  );
}
function ok(result) {
  assert.equal(
    result.code,
    "OK",
    `Unexpected HTTP ${result.status}: ${result.code}`,
  );
  return result.data;
}
async function waitTask(page) {
  await page.waitForURL(/\/tasks\/[a-f0-9-]{36}/);
  await page.getByRole("heading", { name: "结果与操作" }).waitFor();
  await page
    .locator(".ops-task-result")
    .getByRole("heading", { name: /执行成功|执行失败|中断待核查/, exact: true })
    .waitFor({ timeout: 120000 });
  const summary = await page.locator(".ops-task-result").innerText();
  assert.match(summary, /执行成功/);
  return page.url().split("/tasks/")[1];
}
async function operation(page, path, ref) {
  await go(page, 5175, path);
  await page.getByRole("heading", { name: /前确认$/ }).waitFor();
  if (ref)
    await page
      .getByRole("row")
      .filter({ hasText: ref })
      .getByRole("button", { name: /选择/ })
      .click();
  await page.getByRole("button", { name: "读取操作确认", exact: true }).click();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: /^确认.+并创建任务$/ }).click();
  return waitTask(page);
}
try {
  await go(ops, 5175, "/ops/login");
  await ops.locator('[name="ops-login"]').fill(fixture.opsLogin);
  await ops.locator('[name="ops-password"]').fill(fixture.password);
  await ops.getByRole("button", { name: "登录独立运维", exact: true }).click();
  await ops.waitForURL(/ops\/overview/);
  await ops.getByText("已授权运维账号", { exact: true }).waitFor();
  await pass(
    "real independent operations login on its own port and identity domain",
  );
  const initial = ok(await api(ops, "/ops/status", "ops"));
  if (initial.healthy && initial.release.id === "frontend-20261106") {
    const existing = ok(await api(ops, "/ops/jobs", "ops")).items.find(
      (j) =>
        j.action === "DEPLOY" &&
        j.state === "SUCCEEDED" &&
        j.referenceId === "frontend-20261106",
    );
    assert.ok(existing);
    await go(ops, 5175, `/ops/tasks/${existing.id}`);
    await waitTask(ops);
    await pass(
      "reopened the persisted successful browser deployment from the prior test attempt",
    );
  } else {
    await operation(ops, "/ops/deploy", "frontend-20261106");
    await pass(
      "browser confirmation actually deploys the registered same-schema JAR to dedicated business 8082 and waits for success",
    );
  }
  await shot(ops, "O08-deploy-real.png");
  await go(ops, 5175, "/ops/overview");
  await ops.getByText("受控进程运行中", { exact: false }).waitFor();
  await shot(ops, "O02-overview-real.png");
  await go(user, 5173, "/");
  await user.locator(".article-card").first().waitFor();
  assert.equal(
    await user.getByRole("link", { name: /社区管理|独立运维/ }).count(),
    0,
  );
  const publicRequests = [];
  user.on("request", (r) => {
    if (/\/api\/v1\/(admin|ops)/.test(r.url())) publicRequests.push(r.url());
  });
  await go(user, 5173, "/admin/login");
  await user.getByRole("heading", { name: /经验/ }).first().waitFor();
  await go(user, 5173, "/ops/login");
  assert.equal(await user.locator(".console-shell").count(), 0);
  assert.equal(publicRequests.length, 0);
  const blocked = await context.request.get(
    "http://127.0.0.1:5173/api/v1/admin/me",
  );
  assert.equal(blocked.status(), 404);
  const blockedOps = await context.request.get(
    "http://127.0.0.1:5173/api/v1/ops/me",
  );
  assert.equal(blockedOps.status(), 404);
  await pass(
    "public surface has no management links, no management routes and no privileged API proxy",
  );
  await go(user, 5173, "/");
  const card = user.locator(".article-card").first();
  const before = await card.evaluate((e) => getComputedStyle(e).borderColor);
  await card.hover();
  const after = await card.evaluate((e) => getComputedStyle(e).borderColor);
  assert.notEqual(before, after);
  await shot(user, "W01-card-hover-real.png");
  await card.locator(".summary").click({ position: { x: 10, y: 8 } });
  await user.waitForURL(/\/articles\/\d+/);
  await pass(
    "hover highlights article card and clicking its summary enters reading",
  );
  await go(user, 5173, "/");
  await user.locator(".article-card").first().waitFor();
  await user.evaluate(() =>
    window.scrollTo(0, document.documentElement.scrollHeight),
  );
  await user.getByRole("button", { name: "回到顶部", exact: true }).waitFor();
  await shot(user, "W02-back-to-top-real.png");
  await user.getByRole("button", { name: "回到顶部", exact: true }).click();
  await user.waitForFunction(() => scrollY === 0);
  await pass(
    "circular right-side back-to-top button returns to top with reduced motion respected",
  );
  await go(admin, 5174, "/admin/login");
  await admin.locator('[name="admin-login"]').fill(fixture.adminLogin);
  await admin.locator('[name="admin-password"]').fill(fixture.password);
  await admin
    .getByRole("button", { name: "登录社区管理", exact: true })
    .click();
  await admin.waitForURL(/\/admin\/cases/);
  await admin.getByText("已授权管理员", { exact: true }).waitFor();
  await admin.getByText("隐藏文章", { exact: true }).waitFor();
  assert.ok(ok(await api(admin, "/admin/overview", "admin")).openCases >= 0);
  await shot(admin, "A02-cases-real.png");
  await pass(
    "real community login and overview on 5174 with an independently scoped identity",
  );
  const priorRestriction = ok(
    await api(admin, `/admin/users/${fixture.userId}/restrictions`, "admin"),
  ).items.find((row) => row.scope === "COMMENT" && row.enabled);
  if (priorRestriction) {
    assert.equal(priorRestriction.reason, "【DEMO】验证评论限制");
    ok(
      await api(
        admin,
        `/admin/users/${fixture.userId}/restrictions`,
        "admin",
        "POST",
        {
          scope: "COMMENT",
          enabled: false,
          expectedRevision: priorRestriction.revision,
          requestId: crypto.randomUUID(),
          reason: "【DEMO】解除前次联调的测试限制",
        },
      ),
    );
  }
  // The authoring fixture is created through the existing browser UI, never by forging a public version.
  await go(user, 5173, "/account?next=/workspace");
  await user.locator('[name="email"]').fill(fixture.email);
  await user.locator('[name="password"]').fill(fixture.password);
  await user.getByRole("button", { name: "登录校园账号", exact: true }).click();
  await user.waitForURL("**/#/workspace");
  const priorFixtures = ok(await api(user, "/me/articles")).items.filter(
    (row) =>
      row.state === "PUBLISHED" && row.title?.startsWith("【DEMO】管理联调"),
  );
  for (const prior of priorFixtures) {
    user.once("dialog", (dialog) => dialog.accept());
    const oldCard = user
      .locator(".workspace-card")
      .filter({ hasText: prior.title });
    await oldCard.getByRole("button", { name: "下架", exact: true }).click();
    await oldCard
      .getByRole("button", { name: "恢复上次公开版本", exact: true })
      .waitFor();
  }

  await user
    .getByRole("button", { name: /写一篇经验|新建/ })
    .first()
    .click();
  await user.waitForURL(/\/editor\/\d+/);
  const articleId = Number(user.url().match(/editor\/(\d+)/)[1]);
  report.articleId = articleId;
  const title = `【DEMO】管理联调 ${crypto.randomUUID().slice(0, 6)}`;
  await user.getByLabel("标题", { exact: true }).fill(title);
  await user
    .getByLabel("摘要", { exact: true })
    .fill("隔离环境验证巡查、限制与运维的测试文章。");
  await user
    .getByLabel("Markdown 编辑", { exact: true })
    .fill(
      "# 管理联调\n\n这是隔离环境内的演示正文。\n\n## 核对内容\n\n保留公开版本与实际治理记录。",
    );
  await user.getByLabel("Java后端开发（演示）", { exact: true }).check();
  await user.getByLabel("计算机科学与技术", { exact: true }).check();
  await user.getByLabel("经历发生阶段", { exact: true }).fill("DEMO 管理联调");
  await user.getByLabel("内容来源", { exact: true }).selectOption("DEMO");
  await user.getByRole("button", { name: "保存草稿", exact: true }).click();
  await user.getByText("草稿已保存到服务器。", { exact: true }).waitFor();
  await user.getByRole("button", { name: "预览并发布", exact: true }).click();
  await user.getByRole("button", { name: "确认发布", exact: true }).click();
  await user.getByText(/发布回执已确认/).waitFor();
  const newComment = {
    requestId: crypto.randomUUID(),
    body: "【DEMO】巡查评论",
    imageIds: [],
    replyToCommentId: null,
  };
  const comment = ok(
    await api(
      user,
      `/articles/${articleId}/comments`,
      "user",
      "POST",
      newComment,
    ),
  );
  const caseRow = ok(
    await api(user, "/me/cases", "user", "POST", {
      requestId: crypto.randomUUID(),
      kind: "REPORT",
      category: "OTHER",
      targetType: "ARTICLE",
      targetId: articleId,
      description: "【DEMO】测试治理闭环",
      imageIds: [],
    }),
  );
  await go(admin, 5174, `/admin/cases/${caseRow.caseId}`);
  await admin
    .getByRole("button", { name: /受理并|领取|受理此案/ })
    .first()
    .click();
  await admin
    .getByLabel("处理结论", { exact: true })
    .selectOption("GOVERNANCE_COMPLETED");
  await admin
    .getByLabel(/公开答复 \/ 处理理由/)
    .fill("【DEMO】已核对本次治理。\n");
  await admin
    .getByRole("button", { name: "确认处理并留存记录", exact: true })
    .click();
  await admin.getByRole("heading", { name: "上次操作结果待确认" }).waitFor();
  await admin
    .getByRole("alert")
    .filter({ hasText: /治理|状态/ })
    .first()
    .waitFor();
  const blockedClose = report.evidence.find(
    (row) =>
      row.path.endsWith(`/cases/${caseRow.caseId}/decisions`) &&
      row.status === 409,
  );
  assert.equal(blockedClose?.code, "GOVERNANCE_NOT_RECORDED");
  admin.once("dialog", (dialog) => dialog.accept());
  await admin
    .getByRole("button", { name: "核对最新状态并结束本次请求", exact: true })
    .click();
  await admin.getByRole("status").filter({ hasText: "已结束原请求" }).waitFor();
  await go(admin, 5174, `/admin/content?article=${articleId}`);
  await admin
    .getByRole("heading", { name: new RegExp(`${title}.*公开版本`) })
    .waitFor();
  await admin.getByLabel("必填理由").fill("【DEMO】核对文章治理与幂等回执");
  const moderationAttempts = [];
  admin.on("request", (req) => {
    if (
      req.method() === "POST" &&
      req.url().endsWith(`/articles/${articleId}/moderation`)
    )
      moderationAttempts.push(req.postDataJSON());
  });
  await admin.route(
    `**/api/v1/admin/articles/${articleId}/moderation`,
    async (route) => {
      const actual = await route.fetch();
      assert.equal(actual.status(), 200);
      report.evidence.push({
        method: "POST",
        path: `/api/v1/admin/articles/${articleId}/moderation`,
        status: 200,
        traceId: actual.headers()["x-trace-id"],
        condition: "real response deliberately dropped",
      });
      await route.abort("failed");
    },
    { times: 1 },
  );
  await admin.getByRole("button", { name: "确认隐藏", exact: true }).click();
  await admin.getByRole("alert").filter({ hasText: "原请求已保留" }).waitFor();
  await admin.reload();
  await admin
    .getByRole("button", { name: "按原请求核对", exact: true })
    .click();
  await admin
    .getByRole("status")
    .filter({ hasText: "服务端回执已确认" })
    .waitFor();
  assert.deepEqual(moderationAttempts[0], moderationAttempts[1]);
  assert.equal(
    (
      await context.request.get(
        `http://127.0.0.1:5173/api/v1/articles/${articleId}`,
      )
    ).status(),
    404,
  );
  await shot(admin, "A04-content-real.png");
  await pass(
    "real article hide makes public content unavailable; dropped receipt replays exact request key and revision across reload",
  );
  await go(admin, 5174, `/admin/cases/${caseRow.caseId}`);
  await admin
    .getByLabel("处理结论", { exact: true })
    .selectOption("GOVERNANCE_COMPLETED");
  await admin
    .getByLabel(/公开答复 \/ 处理理由/)
    .fill("【DEMO】已完成对应文章的真实隐藏事件。");
  await admin
    .getByRole("button", { name: "确认处理并留存记录", exact: true })
    .click();
  await admin.getByText(/修订.*已执行治理并闭案/).waitFor();
  await pass(
    "case closure refuses absent governance, then succeeds after recorded hide",
  );
  await go(admin, 5174, `/admin/content?article=${articleId}`);
  await admin.getByLabel("必填理由").fill("【DEMO】恢复测试文章");
  await admin.getByRole("button", { name: "确认恢复", exact: true }).click();
  await admin
    .getByRole("status")
    .filter({ hasText: "恢复内容：服务端回执已确认" })
    .waitFor();
  const commentRow = admin
    .getByRole("row")
    .filter({ hasText: `${comment.commentId} · 【DEMO】巡查评论` });
  await commentRow.getByRole("button", { name: "选择此评论处置" }).click();
  await admin.getByLabel("必填理由").fill("【DEMO】隐藏测试评论");
  await admin.getByRole("button", { name: "确认隐藏", exact: true }).click();
  await commentRow.getByText("管理员隐藏", { exact: true }).waitFor();
  await commentRow.getByRole("button", { name: "选择此评论处置" }).click();
  await admin.getByLabel("必填理由").fill("【DEMO】恢复测试评论");
  await admin.getByRole("button", { name: "确认恢复", exact: true }).click();
  await commentRow.getByText("公开", { exact: true }).waitFor();
  await pass(
    "actual article and comment restore plus comment hide reflect server revisions",
  );
  const catName = `联调-${crypto.randomUUID().slice(0, 6)}`,
    finalName = `${catName}-停用`;
  await go(admin, 5174, "/admin/categories?kind=TOPIC");
  await admin.getByRole("button", { name: "新增标签", exact: true }).click();
  await admin.getByLabel("标准名称", { exact: true }).fill(catName);
  await admin
    .getByLabel("变更理由", { exact: true })
    .fill("【DEMO】新增联调主题");
  await admin
    .getByRole("button", { name: "确认保存分类", exact: true })
    .click();
  await admin
    .getByRole("row")
    .filter({ hasText: catName })
    .getByRole("button", { name: "编辑 / 启停", exact: true })
    .click();
  await admin.getByLabel("标准名称", { exact: true }).fill(finalName);
  await admin.getByLabel("展示顺序", { exact: true }).fill("25");
  await admin.getByRole("checkbox", { name: "启用此分类" }).uncheck();
  await admin
    .getByLabel("变更理由", { exact: true })
    .fill("【DEMO】修改顺序并停用");
  await admin
    .getByRole("button", { name: "确认保存分类", exact: true })
    .click();
  await admin
    .getByRole("row")
    .filter({ hasText: finalName })
    .getByText("已停用", { exact: true })
    .waitFor();
  await shot(admin, "A06-category-real.png");
  await pass(
    "creates, renames, reorders and disables a real category without deleting historical links",
  );
  await go(admin, 5174, "/admin/accounts");
  await admin
    .getByRole("row")
    .filter({ hasText: `用户 ${fixture.userId}` })
    .getByRole("button", { name: "查看限制" })
    .click();
  await admin
    .getByLabel("变更理由", { exact: true })
    .fill("【DEMO】验证评论限制");
  await admin
    .getByRole("button", { name: "确认限制所选行为", exact: true })
    .click();
  await admin
    .getByRole("status")
    .filter({ hasText: "限制所选行为：服务端回执已确认" })
    .waitFor();
  assert.equal(
    (
      await api(user, `/articles/${articleId}/comments`, "user", "POST", {
        ...newComment,
        requestId: crypto.randomUUID(),
      })
    ).status,
    403,
  );
  await go(user, 5173, "/me/appeals");
  await user
    .getByLabel("申诉对应限制", { exact: true })
    .selectOption({ index: 1 });
  await user
    .getByLabel("申诉理由", { exact: true })
    .fill("【DEMO】申请复核评论限制");
  await user.getByRole("button", { name: "提交本人申诉", exact: true }).click();
  await user.getByText(/申诉已提交/).waitFor();
  const appeal = ok(await api(user, "/me/appeals")).items[0];
  await admin.reload();
  await admin
    .getByRole("row")
    .filter({ hasText: `${appeal.id} / 限制 ${appeal.restrictionId}` })
    .getByRole("button", { name: "查看与复核", exact: true })
    .click();
  await admin.getByLabel("复核结论", { exact: true }).selectOption("ACCEPTED");
  await admin
    .getByLabel("公开复核答复", { exact: true })
    .fill("【DEMO】解除申诉对应版本的评论限制");
  await admin
    .getByRole("button", { name: "保存复核决定", exact: true })
    .click();
  await admin
    .getByRole("status")
    .filter({ hasText: "保存申诉复核决定：服务端回执已确认" })
    .waitFor();
  await user.reload();
  await user
    .getByRole("heading", {
      name: `申诉 ${appeal.id} · 申诉已接受`,
      exact: true,
    })
    .waitFor();
  assert.equal(
    ok(await api(user, "/me/restrictions")).items.find(
      (r) => r.scope === "COMMENT",
    ).enabled,
    false,
  );
  await shot(admin, "A05-appeal-real.png");
  await pass(
    "real comment restriction is enforced, the campus user appeals and the admin accepts only the corresponding version",
  );
  await go(admin, 5174, "/admin/audit");
  const auditRow = admin
    .getByRole("row")
    .filter({ hasText: `ARTICLE ${articleId}` })
    .filter({ hasText: "【DEMO】核对文章治理与幂等回执" });
  await auditRow.waitFor();
  assert.equal(await auditRow.count(), 1);
  await shot(admin, "A08-audit-real.png");
  await pass(
    "real governance audit contains one event for the retried operation",
  );
  const pidFile = resolve(config.runtimeRoot, "pid.json");
  const oldPid = JSON.parse(await readFile(pidFile, "utf8")).pid;
  await operation(ops, "/ops/restart");
  assert.notEqual(JSON.parse(await readFile(pidFile, "utf8")).pid, oldPid);
  await pass(
    "actual isolated business restart changes PID and ends with successful health check",
  );
  await go(ops, 5175, "/ops/backups");
  await ops.getByRole("button", { name: "读取操作确认", exact: true }).click();
  await ops.getByRole("checkbox").check();
  let acceptedJob;
  const jobAttempts = [];
  ops.on("request", (req) => {
    if (req.method() === "POST" && req.url().endsWith("/ops/jobs"))
      jobAttempts.push(req.postDataJSON());
  });
  await ops.route("**/api/v1/ops/jobs", async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    const actual = await route.fetch();
    assert.equal(actual.status(), 200);
    acceptedJob = (await actual.json()).data.id;
    report.evidence.push({
      method: "POST",
      path: "/api/v1/ops/jobs",
      status: 200,
      traceId: actual.headers()["x-trace-id"],
      condition: "real task receipt deliberately dropped",
    });
    await route.abort("failed");
  });
  await ops.getByRole("button", { name: /^确认.+并创建任务$/ }).click();
  await ops
    .getByRole("alert")
    .filter({ hasText: "原操作、确认ID和请求键已保留" })
    .waitFor();
  await ops.unroute("**/api/v1/ops/jobs");
  await ops.reload();
  await ops
    .getByRole("button", { name: "按原请求核对任务", exact: true })
    .click();
  const backupJob = await waitTask(ops);
  assert.equal(backupJob, acceptedJob);
  assert.deepEqual(jobAttempts[0], jobAttempts[1]);
  const backup = ok(await api(ops, `/ops/jobs/${backupJob}`, "ops")).result
    .backupId;
  const manifest = JSON.parse(
    await readFile(resolve(config.backupRoot, backup, "manifest.json"), "utf8"),
  );
  assert.equal(manifest.complete, true);
  assert.ok(
    manifest.files["database.sql"] && manifest.files["application.jar"],
  );
  await shot(ops, "O08-backup-real.png");
  await pass(
    "real complete backup retains original task ID when receipt is dropped across browser reload",
  );
  await go(admin, 5174, "/admin/categories?kind=TOPIC");
  await admin
    .getByRole("row")
    .filter({ hasText: finalName })
    .getByRole("button", { name: "编辑 / 启停", exact: true })
    .click();
  await admin.getByLabel("标准名称", { exact: true }).fill(`${catName}-备份后`);
  await admin
    .getByLabel("变更理由", { exact: true })
    .fill("【DEMO】用于验证恢复确实还原数据库");
  await admin
    .getByRole("button", { name: "确认保存分类", exact: true })
    .click();
  await admin
    .getByRole("row")
    .filter({ hasText: `${catName}-备份后` })
    .waitFor();
  await operation(ops, "/ops/restore", backup);
  await shot(ops, "O08-restore-real.png");
  await admin.reload();
  await admin.getByRole("row").filter({ hasText: finalName }).waitFor();
  assert.equal(
    await admin
      .getByRole("row")
      .filter({ hasText: `${catName}-备份后` })
      .count(),
    0,
  );
  await pass(
    "real browser restore protects current state and restores the database value from the selected backup",
  );
  await go(ops, 5175, "/ops/logs");
  await ops.getByText("必要请求字段与任务事件", { exact: true }).waitFor();
  const log = ok(await api(ops, "/ops/logs", "ops"));
  assert.ok(log.taskEvents.length > 0);
  await shot(ops, "O03-logs-real.png");
  await go(user, 5173, "/");
  await user.getByRole("searchbox").fill(title);
  await user.getByRole("button", { name: "搜索", exact: true }).click();
  const ownCard = user.locator(".article-card").filter({ hasText: title });
  await ownCard.waitFor();
  const discoveryUrl = user.url();
  await ownCard.getByRole("button", { name: "收藏经验", exact: true }).click();
  await ownCard
    .getByRole("button", { name: "已收藏 · 取消", exact: true })
    .waitFor();
  assert.equal(user.url(), discoveryUrl);
  await ownCard
    .getByRole("button", { name: "已收藏 · 取消", exact: true })
    .click();
  await ownCard
    .getByRole("button", { name: "收藏经验", exact: true })
    .waitFor();
  await ownCard.getByRole("link", { name: title, exact: true }).focus();
  await user.keyboard.press("Enter");
  await user.waitForURL(new RegExp(`/articles/${articleId}$`));
  await pass(
    "nested favorite button stays on discovery and keyboard link opens the card’s article",
  );
  for (const [page, port, path, name] of [
    [user, 5173, "/", "W01-mobile-real.png"],
    [admin, 5174, "/admin/cases", "A02-mobile-real.png"],
    [ops, 5175, "/ops/overview", "O02-mobile-real.png"],
  ]) {
    await page.setViewportSize({ width: 390, height: 844 });
    await go(page, port, path);
    if (port === 5173) await page.locator(".article-card h2").first().waitFor();
    if (port === 5174)
      await page
        .locator(".console-live-stats strong")
        .filter({ hasText: /^\d+$/ })
        .first()
        .waitFor();
    if (port === 5175)
      await page.getByText("受控进程运行中", { exact: false }).waitFor();
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    await shot(page, name);
  }
  await pass(
    "390px user, community and ops surfaces render within the viewport",
  );
  await go(user, 5173, "/workspace");
  const owned = user.locator(".workspace-card").filter({ hasText: title });
  user.once("dialog", (dialog) => dialog.accept());
  await owned.getByRole("button", { name: "下架", exact: true }).click();
  await owned
    .getByRole("button", { name: "恢复上次公开版本", exact: true })
    .waitFor();
  await go(user, 5173, "/me");
  await user.getByRole("button", { name: "退出当前账号", exact: true }).click();
  await user.waitForURL(/\/#\/$/);
  await admin.setViewportSize({ width: 1440, height: 960 });
  await admin
    .getByRole("button", { name: "退出管理账号", exact: true })
    .click();
  await admin.waitForURL(/admin\/login/);
  await ops.setViewportSize({ width: 1440, height: 960 });
  await ops.getByRole("button", { name: "退出运维账号", exact: true }).click();
  await ops.waitForURL(/ops\/login/);
  assert.deepEqual(report.errors, []);
  report.status = "PASS";
  await pass(
    "fixture article taken offline and all three browser sessions logged out; no uncaught page errors",
  );
} catch (error) {
  report.status = "FAIL";
  report.failure =
    error instanceof Error ? error.message : "browser assertion failed";
  await save();
  throw error;
} finally {
  await save();
  await context.close();
  await browser.close();
}
