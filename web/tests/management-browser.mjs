// Visual prototype comparison + explicitly simulated management contract tests.
// This does not create real admins, handle real cases, or execute any operations.
import { chromium } from "playwright";
import assert from "node:assert/strict";
import { mkdir, writeFile, readFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const out = resolve(root, "docs/screenshots/20261106-management");
await mkdir(out, { recursive: true });
const base = process.env.CAMPUSHUB_WEB_URL || "http://127.0.0.1:5173";
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 960 },
});
const page = await context.newPage();
const screens = [
  ["A01", "/admin/login"],
  ["A02", "/admin/cases"],
  ["A03", "/admin/cases/102"],
  ["A04", "/admin/content"],
  ["A05", "/admin/accounts"],
  ["A06", "/admin/categories"],
  ["A07", "/admin/contributions"],
  ["A08", "/admin/audit"],
  ["O01", "/ops/login"],
  ["O02", "/ops/overview"],
  ["O03", "/ops/logs"],
  ["O04", "/ops/deploy"],
  ["O05", "/ops/restart"],
  ["O06", "/ops/backups"],
  ["O07", "/ops/restore"],
  ["O08", "/ops/tasks/DEMO-T108"],
  ["O09", "/ops/exceptions"],
  ["O10", "/ops/manual"],
];
const headings = {
  A01: "社区管理登录",
  A02: "待办案件",
  A03: "案件 DEMO-C102 · 上下文与处理",
  A04: "内容巡查",
  A05: "账号限制与申诉复核",
  A06: "岗位、专业、公司与主题",
  A07: "贡献治理与荣誉规则",
  A08: "治理审计与操作结果",
  O01: "独立运维登录",
  O02: "服务总览",
  O03: "业务停止期间的日志与任务",
  O04: "部署已准备的项目版本",
  O05: "重启业务服务",
  O06: "一致性备份",
  O07: "备份校验与恢复确认",
  O08: "任务 DEMO-T108 · 持久进度",
  O09: "任务异常与冲突操作",
  O10: "运维手册与交接",
};
try {
  const prior = JSON.parse(
    await readFile(
      resolve(root, "docs/MANAGEMENT-BROWSER-20261106.json"),
      "utf8",
    ),
  );
  if (!prior.captureMethod) {
    prior.prototypeCaptureValidation =
      "NOT_ACCEPTED: reviewer found A03 desktop captured the previous page; replaced after route-specific readiness checks";
    await writeFile(
      resolve(root, "docs/MANAGEMENT-BROWSER-20261106-before-capture-fix.json"),
      JSON.stringify(prior, null, 2) + "\n",
    );
  }
} catch {
  /* No previous capture report exists. */
}
const report = {
  checkedAt: new Date().toISOString(),
  status: "RUNNING",
  prototypeScreens: [],
  actualReadOnlyScreens: [],
  simulatedContractChecks: [],
  realAdminLogin: "NOT_RUN_NO_PRIVATE_ADMIN_CREDENTIALS",
  opsBackend: "NOT_IMPLEMENTED_USER_CONFIRMED",
  realOperationsExecuted: 0,
  uncaughtPageErrors: 0,
  captureMethod:
    "expected route-specific h1 + key content readiness, not generic h1",
};
let apiCount = 0;
page.on("request", (r) => {
  if (new URL(r.url()).pathname.startsWith("/api/")) apiCount++;
});
page.on("pageerror", () => report.uncaughtPageErrors++);
async function capture(code, path, preview = true, mobile = false) {
  await page.setViewportSize(
    mobile ? { width: 390, height: 844 } : { width: 1440, height: 960 },
  );
  const before = apiCount;
  await page.goto(`${base}/#${path}${preview ? "?preview=1" : ""}`);
  await page
    .getByRole("heading", { level: 1, name: headings[code], exact: true })
    .waitFor();
  if (code === "A03") {
    await page.locator(".console-context-preview").waitFor();
    await page
      .getByRole("heading", { name: "处理决定", exact: true })
      .waitFor();
  }
  if (code === "A02" && !preview)
    await page
      .getByRole("heading", { name: "请先登录社区管理", exact: true })
      .waitFor();
  if (code === "A06" && !preview)
    await page
      .getByRole("cell", { name: "Java后端开发（演示）", exact: true })
      .waitFor();
  if (preview) await page.locator(".console-preview-note").waitFor();
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(
    () =>
      new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))),
  );
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
    `${code} page overflow`,
  );
  const name = `${code}-${preview ? "prototype" : "actual"}${mobile ? "-mobile" : ""}.png`;
  await page.screenshot({ path: resolve(out, name) });
  if (preview) {
    assert.equal(apiCount, before, `${code} prototype must not call an API`);
    report.prototypeScreens.push({
      code,
      path,
      mobile,
      file: name,
      visibleHeading: await page.locator("h1").innerText(),
      mode: "explicit prototype fixtures; not real data",
    });
  } else report.actualReadOnlyScreens.push({ code, path, mobile, file: name });
  if (!mobile) {
    const geometry = await page.locator(".console-sidebar").boundingBox();
    assert.equal(geometry?.width, 208);
    assert.equal(
      await page
        .locator(".console-top")
        .evaluate((el) => el.getBoundingClientRect().height),
      66,
    );
  }
}
try {
  for (const [code, path] of screens) await capture(code, path);
  for (const code of ["A01", "A02", "A03", "O02", "O07", "O10"]) {
    const item = screens.find((s) => s[0] === code);
    await capture(code, item[1], true, true);
  }
  for (const [code, path] of [
    ["A01", "/admin/login"],
    ["A02", "/admin/cases"],
    ["A06", "/admin/categories"],
    ["O01", "/ops/login"],
    ["O02", "/ops/overview"],
  ])
    await capture(code, path, false);
  await page.goto(`${base}/#/`);
  await page.locator(".article-card").first().waitFor();
  assert.equal(await page.locator(".discovery-sidebar").count(), 0);
  assert.equal(
    await page
      .locator(".discovery-single")
      .evaluate(
        (el) =>
          getComputedStyle(el).gridTemplateColumns.trim().split(" ").length,
      ),
    1,
  );
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.screenshot({ path: resolve(out, "W01-single-column-actual.png") });
  report.actualReadOnlyScreens.push({
    code: "W01",
    file: "W01-single-column-actual.png",
    mode: "real public backend",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: resolve(out, "W01-single-column-actual-mobile.png"),
  });
  const mockContext = await browser.newContext({
    viewport: { width: 1440, height: 960 },
  });
  const p = await mockContext.newPage();
  p.on("pageerror", () => report.uncaughtPageErrors++);
  let data = {
    caseId: 901,
    kind: "REPORT",
    category: "ADVERTISEMENT",
    state: "OPEN",
    revision: 1,
    createdAt: "2026-11-06T01:00:00Z",
    updatedAt: "2026-11-06T01:00:00Z",
    targetType: "ARTICLE",
    articleId: 1001,
    commentId: null,
    assigneeId: null,
    description: "CONTRACT MOCK: not a real report",
    submitterId: 5,
    articleVersionId: 1,
    images: [],
    history: [],
  };
  const commands = [];
  const receipts = new Map();
  let dropped = false;
  let ordinaryRequests = 0;
  await mockContext.route("**/api/v1/**", async (route) => {
    const r = route.request();
    const url = new URL(r.url());
    const path = url.pathname.slice("/api/v1".length);
    const method = r.method();
    const ok = (value) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          code: "OK",
          data: value,
          traceId: "contract-mock-only",
        }),
      });
    if (!path.startsWith("/admin/")) {
      ordinaryRequests++;
      await route.fulfill({
        status: 401,
        contentType: "application/json",
        body: JSON.stringify({
          code: "UNAUTHORIZED",
          message: "mock unexpected ordinary request",
        }),
      });
      return;
    }
    if (path === "/admin/auth/login") {
      assert.equal(r.headers().authorization, undefined);
      return ok({
        tokenType: "Bearer",
        accessToken: "adm_contract_unit",
        expiresAt: new Date(Date.now() + 3600000).toISOString(),
      });
    }
    assert.equal(r.headers().authorization, "Bearer adm_contract_unit");
    if (path === "/admin/me")
      return ok({
        adminId: 1,
        actorDomain: "COMMUNITY_ADMIN",
        capabilities: ["CASE_READ", "CASE_HANDLE"],
      });
    if (path === "/admin/auth/logout") {
      assert.equal(r.postData(), null);
      return ok({ loggedOut: true });
    }
    if (path === "/admin/cases") {
      const matches =
        !url.searchParams.get("state") ||
        url.searchParams.get("state") === data.state;
      return ok({
        items: matches ? [data] : [],
        total: matches ? 1 : 0,
        page: 1,
        size: Number(url.searchParams.get("size") || 20),
        snapshot: 10,
      });
    }
    if (path === "/admin/cases/901") return ok(data);
    if (path === "/admin/cases/901/context")
      return ok({
        caseId: 901,
        articleAvailable: true,
        article: {
          versionId: 1,
          articleId: 1001,
          title: "CONTRACT MOCK private context",
          summary: "Contract test only",
          body: "# Context\n\n![private](/api/v1/media/images/01234567-89ab-cdef-0123-456789abcdef)",
          bodyFormat: "MARKDOWN",
          sourceType: "DEMO",
          publishedAt: data.createdAt,
          currentState: "ADMIN_HIDDEN",
        },
        comments: [],
        media: [],
      });
    if (method === "POST") {
      const body = r.postDataJSON();
      const action = path.split("/").pop();
      commands.push({ action, body });
      if (receipts.has(body.requestId)) return ok(receipts.get(body.requestId));
      assert.equal(body.expectedRevision, data.revision);
      if (action === "claim") {
        assert.deepEqual(Object.keys(body).sort(), [
          "expectedRevision",
          "requestId",
        ]);
        data = {
          ...data,
          state: "PROCESSING",
          revision: data.revision + 1,
          assigneeId: 1,
        };
      } else if (action === "decisions") {
        assert.deepEqual(Object.keys(body).sort(), [
          "expectedRevision",
          "internalNote",
          "outcome",
          "reply",
          "requestId",
        ]);
        data = { ...data, state: "CLOSED", revision: data.revision + 1 };
        data.history.push({
          revision: data.revision,
          action: "CASE_DECISION",
          state: "CLOSED",
          reply: body.reply,
          outcome: body.outcome,
          occurredAt: data.updatedAt,
          actorDomain: "COMMUNITY_ADMIN",
          actorId: 1,
          assigneeId: 1,
          internalNote: body.internalNote,
          reason: "",
        });
      }
      const receipt = {
        caseId: 901,
        revision: data.revision,
        state: data.state,
      };
      receipts.set(body.requestId, receipt);
      if (action === "decisions" && !dropped) {
        dropped = true;
        await route.abort("failed");
        return;
      }
      return ok(receipt);
    }
    throw new Error(`Unhandled contract mock path ${path}`);
  });
  await p.goto(`${base}/#/admin/login`);
  await p.locator("input[name=admin-login]").waitFor();
  await p.evaluate(() =>
    sessionStorage.setItem(
      "campushub.web.session.v1",
      "campus-domain-test-marker",
    ),
  );
  await p.locator("input[name=admin-login]").fill("contract.admin");
  await p.locator("input[name=admin-password]").fill("contract-password");
  await p.getByRole("button", { name: "登录社区管理", exact: true }).click();
  await p.waitForURL("**/#/admin/cases");
  await p.getByRole("link", { name: "查看上下文", exact: true }).click();
  await p.waitForURL("**/#/admin/cases/901");
  await p.getByRole("button", { name: "受理此案件", exact: true }).click();
  await p
    .getByRole("combobox", { name: "处理结论", exact: true })
    .waitFor({ state: "visible" });
  await p.waitForFunction(
    () => !document.querySelector('select[aria-label="处理结论"]')?.disabled,
  );
  assert.equal(await p.locator('img[src*="/api/v1/media/images/"]').count(), 0);
  report.simulatedContractChecks.push(
    "isolated admin login, claim and no public-media fallback for hidden context",
  );
  await p
    .getByRole("combobox", { name: "处理结论", exact: true })
    .selectOption("NO_VIOLATION");
  await p
    .getByPlaceholder("写明事实、依据与影响范围。")
    .fill("CONTRACT MOCK public reply");
  await p
    .getByPlaceholder("仅管理处理范围可见")
    .fill("CONTRACT MOCK internal note");
  await p
    .getByRole("button", { name: "确认处理并留存记录", exact: true })
    .click();
  await p.getByText(/原请求与输入已保留/).waitFor();
  await p.reload();
  await p.getByRole("button", { name: "按原请求核对", exact: true }).waitFor();
  await p.getByRole("button", { name: "按原请求核对", exact: true }).click();
  await p
    .getByText("操作回执已确认，当前状态以最新读取为准。", { exact: true })
    .waitFor();
  const decisions = commands.filter((c) => c.action === "decisions");
  assert.equal(decisions.length, 2);
  assert.deepEqual(decisions[0], decisions[1]);
  assert.equal(data.history.length, 1);
  report.simulatedContractChecks.push(
    "simulated lost management receipt survives reload and keeps original request/revision/text",
  );
  await p.getByRole("button", { name: "退出管理账号", exact: true }).click();
  await p.waitForURL("**/#/admin/login");
  assert.equal(
    await p.evaluate(() => sessionStorage.getItem("campushub.web.session.v1")),
    "campus-domain-test-marker",
  );
  assert.equal(
    await p.evaluate(() =>
      Object.keys(sessionStorage).some((k) =>
        k.startsWith("campushub.web.community."),
      ),
    ),
    false,
  );
  assert.equal(ordinaryRequests, 0);
  report.simulatedContractChecks.push(
    "admin logout leaves ordinary session and never sends its token to ordinary API",
  );
  assert.equal(report.uncaughtPageErrors, 0);
  report.status = "PASS";
  await mockContext.close();
} catch (error) {
  report.status = "FAIL";
  report.failure = error instanceof Error ? error.message : String(error);
  process.exitCode = 1;
} finally {
  await writeFile(
    resolve(root, "docs/MANAGEMENT-BROWSER-20261106.json"),
    JSON.stringify(report, null, 2) + "\n",
  );
  await browser.close();
  console.log(
    JSON.stringify({
      status: report.status,
      prototypeScreens: report.prototypeScreens.length,
      actualReadOnlyScreens: report.actualReadOnlyScreens.length,
      contractMockChecks: report.simulatedContractChecks.length,
      failure: report.failure,
    }),
  );
}
