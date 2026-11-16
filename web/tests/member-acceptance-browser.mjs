import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { resolve } from "node:path";
import { chromium } from "playwright";

const root = process.env.CAMPUSHUB_CONSOLE_FIXTURE;
assert(root, "CAMPUSHUB_CONSOLE_FIXTURE is required");
const config = JSON.parse(
  await readFile(resolve(root, "ops.private.json"), "utf8"),
);
assert.equal(config.databaseName, "campushub_test");
assert.equal(config.healthUrl, "http://127.0.0.1:8082/actuator/health");
const fixture = JSON.parse(
  await readFile(resolve(root, "fixture.private.json"), "utf8"),
);
const { imported } = JSON.parse(
  await readFile(resolve(root, "demo-import.json"), "utf8"),
);
const [long, illustrated] = imported;
const out = resolve(root, "member-evidence");
await mkdir(out, { recursive: true });
const userUrl = "http://127.0.0.1:5173/#";
const adminUrl = "http://127.0.0.1:5174/#";
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
});
context.setDefaultTimeout(15000);
const page = await context.newPage();
const report = { checks: [], requests: [], errors: [] };
const apiRoot = "http://127.0.0.1:8082/api/v1";

function pass(text) {
  report.checks.push(text);
  console.log("PASS", text);
}
async function api(path, token, method = "GET", body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const response = await fetch(apiRoot + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json();
  report.requests.push({
    method,
    path,
    status: response.status,
    code: data.code,
    traceId: data.traceId,
  });
  assert.equal(
    data.code,
    "OK",
    `${method} ${path} ${response.status} ${data.code} ${data.traceId}`,
  );
  return data.data;
}
function record(p) {
  p.on("pageerror", (error) => report.errors.push(error.message));
  p.on("response", async (response) => {
    const path = new URL(response.url()).pathname;
    if (!path.startsWith("/api/v1/")) return;
    const responseType = response.headers()["content-type"];
    const data = responseType?.includes("application/json")
      ? await response.json().catch(() => ({}))
      : {};
    report.requests.push({
      method: response.request().method(),
      path,
      status: response.status(),
      code: data.code,
      traceId: data.traceId || response.headers()["x-trace-id"],
    });
  });
}
const loginApi = async (email) =>
  (
    await api("/auth/login", null, "POST", {
      email,
      password: fixture.password,
    })
  ).accessToken;
async function article(item) {
  await page.goto(`${userUrl}/articles/${item.articleId}`);
  await page.getByRole("heading", { name: item.title, exact: true }).waitFor();
}
const image = () => page.locator(".markdown img").first();
const contact = () =>
  page.getByRole("button", { name: "查看作者联系方式", exact: true });
record(page);
try {
  await article(long);
  assert.equal(
    await page.getByRole("heading", { name: "评论", exact: true }).count(),
    0,
  );
  await page
    .getByText("投递前可执行的检查清单", { exact: false })
    .last()
    .waitFor();
  await page.getByRole("button", { name: "目录", exact: true }).click();
  await page
    .locator(".toc-item")
    .filter({ hasText: "投递前可执行的检查清单" })
    .click();
  assert((await page.getByRole("link", { name: /牛客/ }).count()) >= 2);
  await page.screenshot({ path: resolve(out, "W02-long-reading.png") });
  await article(illustrated);
  await image().scrollIntoViewIfNeeded();
  await page.waitForFunction(
    () => document.querySelector(".markdown img")?.naturalWidth > 0,
  );
  await page.screenshot({ path: resolve(out, "W02-illustration.png") });
  pass("游客读取导入的长文末节、目录、来源和真实图片");

  const user = await loginApi(fixture.readerEmail);
  const author = await loginApi(fixture.email);
  const authorInfo = await api("/me", author);
  const profile = await api("/me/profile", user);
  await api("/me/profile", user, "PUT", {
    expectedRevision: profile.revision,
    nickname: "隔离演示读者",
    majorId: long.tags.majorIds[0],
    currentStage: "ENROLLED",
    bio: "",
  });
  const contactSettings = await api("/me/contact-settings", author);
  await api("/me/contact-settings", author, "PUT", {
    expectedRevision: contactSettings.revision,
    enabled: true,
    type: "WEB",
    value: "https://example.test/campushub-contact",
  });
  await api("/me/rewards/login", user, "POST", { requestId: randomUUID() });
  await page.goto(`${userUrl}/account?next=%2F`);
  await page.locator("input[name=email]").fill(fixture.readerEmail);
  await page.locator("input[name=password]").fill(fixture.password);
  await page.getByRole("button", { name: "登录校园账号", exact: true }).click();
  await page.waitForURL("**/#/");
  await page.goto(
    `${userUrl}/?keyword=${encodeURIComponent("Java 后端")}&jobId=${long.tags.jobIds[0]}&companyId=${long.tags.companyIds[0]}`,
  );
  await page.getByRole("button", { name: /同专业/ }).click();
  await page.waitForURL(new RegExp(`majorId=${long.tags.majorIds[0]}`));
  const filtered = page.url();
  await page
    .locator(".article-card h2 a")
    .filter({ hasText: long.title })
    .click();
  await page
    .locator(".breadcrumb")
    .getByRole("link", { name: "发现经验" })
    .click();
  await page.waitForURL(filtered);
  await page
    .locator(".article-card h2 a")
    .filter({ hasText: long.title })
    .click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("link", { name: /作者主页/ }).waitFor();
  assert(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page.screenshot({ path: resolve(out, "W03-member-narrow.png") });
  await page.setViewportSize({ width: 1440, height: 1000 });
  pass("岗位、公司和同专业组合筛选及详情返回保留筛选，窄屏无横向溢出");

  await page.getByRole("link", { name: /作者主页/ }).click();
  await contact().click();
  await page.getByRole("link", { name: "打开作者提供的站外链接" }).waitFor();
  await page.screenshot({ path: resolve(out, "W04-author-contact.png") });
  await api("/me/contact-settings", author, "PUT", {
    expectedRevision: contactSettings.revision + 1,
    enabled: false,
    type: "WEB",
    value: "https://example.test/campushub-contact",
  });
  await contact().click();
  await page.getByText("作者当前未公开联系方式。").waitFor();
  assert.equal(
    await page.getByRole("link", { name: "打开作者提供的站外链接" }).count(),
    0,
  );
  pass("认证作者主页按服务端实时开关显示站外联系方式");

  await article(long);
  await page.getByRole("button", { name: "赞同这篇经验" }).click();
  await page.getByRole("button", { name: "已点赞 · 取消" }).waitFor();
  await page.getByRole("button", { name: "收藏，稍后阅读" }).click();
  await page.getByRole("button", { name: "已收藏 · 取消" }).waitFor();
  await page
    .getByRole("textbox", { name: "写评论" })
    .fill("隔离验收：可以直接阅读长文末节。");
  await page.getByRole("button", { name: "提交评论", exact: true }).click();
  await page
    .getByText("隔离验收：可以直接阅读长文末节。", { exact: true })
    .waitFor();
  await page.getByRole("button", { name: "回复", exact: true }).first().click();
  await page
    .getByRole("textbox", { name: /回复/ })
    .fill("隔离验收：回复位置正确。");
  await page.getByRole("button", { name: "提交评论", exact: true }).click();
  await page
    .getByRole("button", { name: /查看 1 条回复/ })
    .first()
    .click();
  await page.getByText("隔离验收：回复位置正确。", { exact: true }).waitFor();
  await page.screenshot({ path: resolve(out, "W03-comments.png") });
  pass("认证读者实测点赞、收藏、提交文字评论和回复");

  page.on("dialog", (dialog) => dialog.accept());
  let dropped = false;
  const coinPattern = `**/api/v1/articles/${long.articleId}/coins`;
  await page.route(coinPattern, async (route) => {
    if (dropped || route.request().method() !== "POST") return route.continue();
    dropped = true;
    const original = await route.fetch();
    const data = await original.json();
    report.droppedCoin = {
      status: original.status(),
      code: data.code,
      traceId: data.traceId,
      eventId: data.data?.eventId,
    };
    return route.abort("failed");
  });
  await page.getByRole("button", { name: "投 2 币", exact: true }).click();
  await page.getByRole("button", { name: "核对上次投 2 币" }).click();
  await page.getByText("已投 2 币。", { exact: true }).waitFor();
  await page.unroute(coinPattern);
  assert.equal(
    (await api(`/articles/${long.articleId}/coin-state`, user)).spent,
    2,
  );
  assert.equal(fixture.additionalReaderEmails.length, 2);
  for (const [index, email] of fixture.additionalReaderEmails.entries()) {
    const amount = 2 - index;
    const reader = await loginApi(email);
    await api("/me/rewards/login", reader, "POST", { requestId: randomUUID() });
    await api(`/articles/${long.articleId}/coins`, reader, "POST", {
      amount,
      requestId: randomUUID(),
    });
  }
  assert.equal((await api("/me/coins", author)).honor.title, "经验分享者");
  await page.goto(`${userUrl}/authors/${authorInfo.userId}`);
  await page.getByText("经验分享者", { exact: true }).waitFor();
  await page.screenshot({ path: resolve(out, "W04-honor.png") });
  pass("网页实际投币、响应丢失按原键重试、作者达到 5 币后显示服务端荣誉");

  const userCase = await api("/me/cases", user, "POST", {
    requestId: randomUUID(),
    kind: "REPORT",
    category: "OTHER",
    targetType: "ARTICLE",
    targetId: long.articleId,
    description: "隔离验收：核对投币后演示贡献失效及审计。",
    imageIds: [],
  });
  const admin = await context.newPage();
  record(admin);
  admin.on("dialog", (dialog) => dialog.accept());
  await admin.goto(`${adminUrl}/admin/login`);
  await admin.locator('[name="admin-login"]').fill(fixture.adminLogin);
  await admin.locator('[name="admin-password"]').fill(fixture.password);
  await admin
    .getByRole("button", { name: "登录社区管理", exact: true })
    .click();
  await admin.waitForURL("**/admin/cases");
  await admin.goto(`${adminUrl}/admin/cases/${userCase.caseId}`);
  await admin.getByRole("button", { name: "受理此案件" }).click();
  const row = admin
    .getByRole("row")
    .filter({ hasText: report.droppedCoin.eventId });
  await row.getByRole("button", { name: "标记失效" }).click();
  await admin
    .getByRole("textbox", { name: "失效原因" })
    .fill("隔离演示：按举报案件复核有效贡献。");
  await admin.getByRole("button", { name: "确认失效", exact: true }).click();
  await row.getByText("已失效", { exact: true }).waitFor();
  await admin.getByText("投币失效", { exact: true }).waitFor();
  await admin.screenshot({
    path: resolve(out, "A07-contribution-audit.png"),
    fullPage: true,
  });
  assert.equal((await api("/me/coins", user)).balance, 0);
  const wallet = await api("/me/coins", author);
  assert.equal(wallet.receivedContribution, 3);
  assert.equal(wallet.honor, undefined);
  assert.equal(
    (await api(`/articles/${long.articleId}`)).counts.effectiveCoins,
    3,
  );
  pass("文章举报认领、填写原因、投币失效、案件审计及荣誉回落；投币者余额依旧");
  await admin.setViewportSize({ width: 390, height: 844 });
  await admin.screenshot({
    path: resolve(out, "A07-contribution-narrow.png"),
    fullPage: true,
  });
  assert(
    await admin.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  assert.equal(report.errors.length, 0);
} catch (e) {
  report.failure = e.message;
  throw e;
} finally {
  await writeFile(resolve(out, "report.json"), JSON.stringify(report, null, 2));
  await browser.close();
}
