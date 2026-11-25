import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { chromium } from "playwright";
import {
  isolatedConsole,
  publishIsolatedArticle,
} from "./isolated-console-fixture.mjs";

const root = process.env.CAMPUSHUB_CONSOLE_FIXTURE;
const output = resolve(root || ".", "console-ux-evidence");
const report = { checks: [], requests: [], errors: [] };
const env = await isolatedConsole(root, report.requests);
const { fixture, request } = env;
const apiToken = await env.admin();

const base = "http://127.0.0.1:5174/#";
const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
page.setDefaultTimeout(12000);
page.on("pageerror", (e) => report.errors.push(e.message));
page.on("response", async (res) => {
  const path = new URL(res.url()).pathname;
  if (!path.startsWith("/api/v1/admin/")) return;
  const { code, traceId } = await res.json().catch(() => ({}));
  report.requests.push({
    via: "browser",
    path,
    method: res.request().method(),
    status: res.status(),
    code,
    traceId,
  });
});
const button = (scope, name) =>
  scope.getByRole("button", { name, exact: true });
const shot = (name) =>
  page.screenshot({ path: resolve(output, name + ".png") });
const check = (name) => {
  report.checks.push(name);
  console.log("PASS", name);
};
const goto = (path) => page.goto(base + path);
async function fitTable(locator) {
  assert(await locator.evaluate((el) => el.scrollWidth <= el.clientWidth));
  assert(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
}
async function confirm(action) {
  const dialog = page.getByRole("dialog");
  await dialog.waitFor();
  await button(dialog, action).click();
}

await mkdir(output, { recursive: true });
try {
  const { articleId, title } = await publishIsolatedArticle(env);
  const articlePath = "/articles/" + articleId;
  const author = await env.member();
  let reader;
  for (const email of [
    fixture.readerEmail,
    "fixture.reader3@stu.hzcu.edu.cn",
  ]) {
    const token = await env.member(email);
    const rewards = await request("/me/rewards", token);
    if (!rewards.login.claimed)
      await request("/me/rewards/login", token, "POST", {
        requestId: randomUUID(),
      });
    if ((await request("/me/coins", token)).balance >= 1) {
      reader = token;
      break;
    }
  }
  assert(
    reader,
    "Fresh isolated reader identities are needed for another real contribution.",
  );
  const coin = await request(articlePath + "/coins", reader, "POST", {
    amount: 1,
    requestId: randomUUID(),
  });
  const credited = await request("/me/coins", author);
  const { caseId } = await request("/me/cases", reader, "POST", {
    kind: "REPORT",
    category: "MISLEADING_CONTENT",
    targetType: "ARTICLE",
    targetId: articleId,
    description: "隔离案例。固定文章版本、关联内容与真实投币核对。",
    imageIds: [],
    requestId: randomUUID(),
  });
  const casePath = "/admin/cases/" + caseId;

  await goto("/admin/login");
  await page.locator("[name=admin-login]").fill(fixture.adminLogin);
  await page.locator("[name=admin-password]").fill(fixture.password);
  await button(page, "登录社区管理").click();
  await page.waitForURL("**/admin/cases");
  await page.getByRole("combobox", { name: "案件类型" }).selectOption("REPORT");
  await page.getByRole("combobox", { name: "案件状态" }).selectOption("OPEN");
  const origin = page.url();
  const link = page.locator("#case-link-" + caseId);
  await link.waitFor();
  await fitTable(page.locator(".console-case-list .console-table-wrap"));
  await shot("after-admin-cases-390");
  await link.evaluate((el) => el.scrollIntoView({ block: "center" }));
  const start = await link.evaluate((el) => el.getBoundingClientRect().top);
  await link.press("Enter");
  await page.getByText(/本案固定公开版本/).waitFor();
  await page.getByRole("link", { name: "前往对应文章与评论巡查" }).click();
  await page
    .getByRole("heading", { name: new RegExp(title.replace(/[【】]/g, ".")) })
    .waitFor();
  await page.getByRole("link", { name: "返回原案件 " + caseId }).click();
  await page.getByRole("link", { name: "返回原案件列表" }).click();
  await page.waitForURL(origin);
  await page.waitForFunction(
    (id) => document.activeElement?.id === "case-link-" + id,
    caseId,
  );
  assert(
    Math.abs(
      (await link.evaluate((el) => el.getBoundingClientRect().top)) - start,
    ) < 55,
  );
  check("390px 带筛选打开固定文章，再回案件、原列表和行焦点");

  const stable = await page.locator(".console-case-list tbody").innerText();
  await page.route("**/api/v1/admin/cases?*", (r) => r.abort("failed"), {
    times: 1,
  });
  await button(page, "刷新当前状态").click();
  await page.getByText("当前显示的是上次读取结果。").waitFor();
  assert.equal(
    await page.locator(".console-case-list tbody").innerText(),
    stable,
  );
  assert.equal(page.url(), origin);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await shot("after-admin-refresh-failure-1440");
  await button(page, "重试当前查询").click();
  await page
    .getByText("当前显示的是上次读取结果。")
    .waitFor({ state: "hidden" });
  check("案件刷新失败保留列表、筛选和重试目标");

  await link.click();
  await button(page, "受理此案件").click();
  const decision = page.getByRole("combobox", { name: "处理结论" });
  const reply = page.getByRole("textbox", { name: /公开答复/ });
  await decision.selectOption("NO_VIOLATION");
  await reply.fill("本地处理理由，修订冲突后须保留。");
  const claimed = await request(casePath, apiToken);
  assert.equal(claimed.state, "PROCESSING");
  await request(casePath + "/decisions", apiToken, "POST", {
    expectedRevision: claimed.revision,
    requestId: randomUUID(),
    outcome: "REQUIRES_GOVERNANCE",
    reply: "另一会话推进了案件处理。",
    internalNote: "隔离验收",
  });
  const conflict = page.waitForResponse(
    (res) => res.url().endsWith("/decisions") && res.status() === 409,
  );
  await button(page, "确认处理并留存记录").click();
  const confirmDialog = page.getByRole("dialog");
  await confirmDialog.waitFor();
  assert.equal(
    await page.evaluate(() => document.activeElement?.textContent?.trim()),
    "取消",
  );
  await page.keyboard.press("Escape");
  assert.equal(
    await page.evaluate(() => document.activeElement?.textContent?.trim()),
    "确认处理并留存记录",
  );
  await button(page, "确认处理并留存记录").click();
  await confirm("提交处理决定");
  assert.equal((await (await conflict).json()).code, "REVISION_CONFLICT");
  await button(page, "读取并复核最新修订").waitFor();
  assert.equal(await reply.inputValue(), "本地处理理由，修订冲突后须保留。");
  await shot("after-admin-revision-conflict-1440");
  await button(page, "读取并复核最新修订").click();
  await confirm("使用最新修订");
  assert.equal(await decision.inputValue(), "NO_VIOLATION");
  assert.equal(await reply.inputValue(), "本地处理理由，修订冲突后须保留。");
  check("真实认领与 409 修订冲突保留决定、理由和复核焦点");

  const coinRow = page.getByRole("row").filter({ hasText: coin.eventId });
  await button(coinRow, "标记失效").click();
  await page
    .getByRole("textbox", { name: "失效原因" })
    .fill("隔离验收反查原流水及有效贡献");
  await button(page, "确认失效").click();
  await confirm("标记投币失效");
  await coinRow.getByText("已失效", { exact: true }).waitFor();
  await page.getByRole("heading", { name: "案件审计", exact: true }).waitFor();
  const contributions = await request(
    casePath + "/contributions?page=1&size=20",
    apiToken,
  );
  assert.equal(
    contributions.items.find((e) => e.eventId === coin.eventId).effective,
    false,
  );
  assert.equal(
    (await request("/me/coins", author)).receivedContribution,
    credited.receivedContribution - 1,
  );
  assert.equal((await request(articlePath)).counts.effectiveCoins, 0);
  const audit = await request(casePath + "/audit?page=1&size=20", apiToken);
  assert(audit.items.some((entry) => entry.source === "CONTRIBUTION"));
  await shot("after-admin-contribution-1440");
  check("真实投币失效、贡献减少、公开计数及案件审计");

  await button(page, "确认处理并留存记录").click();
  await confirm("提交处理决定");
  await page.getByText(/当前状态为已完成/).waitFor();
  const closed = await request(casePath, apiToken);
  assert.equal(closed.state, "CLOSED");
  assert(
    closed.history.some(
      (entry) =>
        entry.reply === "本地处理理由，修订冲突后须保留。" &&
        entry.state === "CLOSED",
    ),
  );
  await page.getByRole("link", { name: "返回原案件列表" }).click();
  await page.waitForURL(origin);
  await page.getByText(/当前筛选没有匹配案件/).waitFor();
  await page.getByRole("combobox", { name: "案件状态" }).selectOption("CLOSED");
  await button(page, "刷新当前状态").waitFor();
  await page.locator("#case-link-" + caseId).waitFor();
  assert.equal(
    (await page.locator("#case-link-" + caseId).innerText()).trim(),
    "查看处理记录",
  );
  check("重新确认真实闭案，列表、详情和时间线一致");

  const stamp = randomUUID().slice(0, 8);
  const category = (name) =>
    request("/admin/categories", apiToken, "POST", {
      kind: "JOB",
      name,
      enabled: true,
      displayOrder: -100,
      expectedRevision: 0,
      reason: "隔离验收",
      requestId: randomUUID(),
    });
  const sourceName = "临时同义 " + stamp;
  const targetName = "临时标准 " + stamp;
  await category(sourceName);
  await category(targetName);
  const beforeMerge = await request(
    "/admin/categories?kind=JOB&page=1&size=100",
    apiToken,
  );
  const source = beforeMerge.items.find((row) => row.name === sourceName);
  const target = beforeMerge.items.find((row) => row.name === targetName);
  assert(source && target);
  await goto("/admin/categories?kind=JOB");
  const sourceRow = page
    .getByRole("row")
    .filter({ has: page.getByText(sourceName, { exact: false }) });
  await button(sourceRow, "归并同义标签").click();
  const select = page.getByRole("combobox", { name: "保留的同类标签" });
  await select.selectOption(String(target.id));
  await page
    .getByRole("textbox", { name: "归并理由" })
    .fill("核对同义来源、目标、影响及移动端反馈");
  await button(page, "确认归并").click();
  await confirm("归并到 " + target.name);
  await sourceRow.getByText(/已归并至分类/).waitFor();
  const categoryPage = await request(
    "/admin/categories?kind=JOB&page=1&size=100",
    apiToken,
  );
  assert.equal(
    categoryPage.items.find((row) => row.id === source.id).canonicalId,
    target.id,
  );
  check("真实归并同义来源和目标并回读关联");

  const candidate = await env.member("fixture.reader2@stu.hzcu.edu.cn");
  const identity = await request("/me", candidate);
  await goto("/admin/accounts");
  const candidateRow = page.getByRole("row").filter({
    has: page.getByText("用户 " + identity.userId, { exact: true }),
  });
  await button(candidateRow, "查看限制").click();
  const revocation = "隔离验收撤销理由保留，并检查本人申诉路径。";
  await page.getByRole("textbox", { name: "撤销理由" }).fill(revocation);
  await button(page, "确认撤销认证").click();
  await confirm("撤销校园认证");
  await page.getByText(/已撤销 · 修订/).waitFor();
  const certification = await request("/me/certification", candidate);
  assert.equal(certification.revocationReason, revocation);
  const notifications = await request("/me/notifications", candidate);
  assert(
    notifications.items.some((item) => item.target?.type === "CERTIFICATION"),
  );
  await request("/me", candidate, "GET", undefined, 403);
  const appeal = await request("/me/appeals", candidate, "POST", {
    certificationRevision: certification.revision,
    reason: "隔离测试复核本次认证决定",
    requestId: randomUUID(),
  });
  assert.equal(
    (await request("/me/appeals/" + appeal.id, candidate)).state,
    "OPEN",
  );
  check("真实认证撤销、本人原因、通知入口及申诉建单");

  assert.deepEqual(report.errors, []);
} catch (e) {
  report.failure = e.message;
  await shot("admin-failure").catch(() => {});
  throw e;
} finally {
  await writeFile(
    resolve(output, "report.json"),
    JSON.stringify(report, null, 2),
  );
  await browser.close();
}
