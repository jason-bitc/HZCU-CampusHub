import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { chromium } from "playwright";

const root = process.env.CAMPUSHUB_CONSOLE_FIXTURE;
assert(
  root,
  "Set CAMPUSHUB_CONSOLE_FIXTURE to the isolated fixture directory.",
);
const ops = JSON.parse(
  await readFile(resolve(root, "ops.private.json"), "utf8"),
);
assert.equal(ops.databaseName, "campushub_test");
assert.equal(ops.healthUrl, "http://127.0.0.1:8082/actuator/health");
const fixture = JSON.parse(
  await readFile(resolve(root, "closure.private.json"), "utf8"),
);
const { imported } = JSON.parse(
  await readFile(resolve(root, "demo-import.json"), "utf8"),
);
const long = imported.find((item) => item.sourceId === "A01");
const illustrated = imported.find((item) => item.sourceId === "A03");
const out = resolve(root, "frontend-closure");
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: "chrome", headless: true });
const report = { completed: [], requests: [], errors: [] };
const userUrl = "http://127.0.0.1:5173/#";
const adminUrl = "http://127.0.0.1:5174/#";
const apiUrl = "http://127.0.0.1:8082/api/v1";

async function newPage() {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1000 },
  });
  page.setDefaultTimeout(20000);
  page.on("pageerror", (e) => report.errors.push(e.message));
  page.on("response", async (response) => {
    const path = new URL(response.url()).pathname;
    if (!path.startsWith("/api/v1/")) return;
    const value = await response.json().catch(() => ({}));
    report.requests.push({
      path,
      status: response.status(),
      method: response.request().method(),
      code: value.code,
      traceId: value.traceId || response.headers()["x-trace-id"],
    });
  });
  page.on("dialog", (dialog) => dialog.accept());
  return page;
}
async function api(path, token, method = "GET", body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers["Content-Type"] = "application/json";
  const response = await fetch(apiUrl + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const result = await response.json();
  report.requests.push({
    path,
    status: response.status,
    method,
    code: result.code,
    traceId: result.traceId,
  });
  assert.equal(
    result.code,
    "OK",
    `${method} ${path} ${result.code} ${result.traceId}`,
  );
  return result.data;
}
const campusToken = async (email) =>
  (
    await api("/auth/login", null, "POST", {
      email,
      password: fixture.password,
    })
  ).accessToken;
async function signIn(page, email, destination) {
  await page.goto(`${userUrl}/account?next=${encodeURIComponent(destination)}`);
  await page.locator("input[name=email]").fill(email);
  await page.locator("input[name=password]").fill(fixture.password);
  await page.getByRole("button", { name: "登录校园账号", exact: true }).click();
  await page.waitForURL(`${userUrl}${destination}`);
}
const shot = (page, file) =>
  page.screenshot({ path: resolve(out, file), fullPage: true });
function pass(name) {
  report.completed.push(name);
  console.log("PASS", name);
}

try {
  const reader = await newPage(),
    admin = await newPage(),
    author = await newPage(),
    candidate = await newPage();
  const starting = reader.waitForResponse(
    (res) =>
      res.url().endsWith(`/articles/${long.articleId}/reading-session`) &&
      res.request().method() === "POST",
  );
  await signIn(reader, fixture.readerEmail, `/articles/${long.articleId}`);
  const reading = await (await starting).json();
  assert.equal(reading.code, "OK");
  await reader.goto(`${userUrl}/me/wallet`);
  const login = reader.getByRole("button", {
    name: /^(领取登录奖励|今日登录奖励已领)$/,
  });
  await login.waitFor();
  if ((await login.innerText()).trim() === "领取登录奖励") {
    await login.click();
    await reader.getByText(/已领取登录奖励/).waitFor();
  }
  await reader
    .getByText(/^登录奖励 · \+/)
    .first()
    .waitFor();
  await shot(reader, "W09-wallet-in-progress.png");
  pass("网页开始真实阅读计时、领取登录奖励并写入本人账目");

  await signIn(author, fixture.email, `/editor/${illustrated.articleId}`);
  const contentBefore = await author
    .locator(".writing-grid textarea")
    .first()
    .inputValue();
  await author.getByText("写作提示（可跳过）", { exact: true }).click();
  await author.locator("details[open] li").first().waitFor();
  assert.equal(
    await author.locator(".writing-grid textarea").first().inputValue(),
    contentBefore,
  );
  await shot(author, "W05-writing-prompts.png");
  pass("网页作者打开服务器写作提示，原 Markdown 保留");

  await reader.goto(`${userUrl}/articles/${long.articleId}`);
  await reader.getByRole("link", { name: "举报这篇经验" }).click();
  const file = await fetch(`http://127.0.0.1:8082${illustrated.assets[0]}`);
  assert.equal(file.status, 200);
  await reader
    .getByRole("textbox", { name: "说明" })
    .fill("隔离验收文章举报与证据回读");
  await reader
    .locator("input[type=file]")
    .setInputFiles({
      name: "fixture.png",
      mimeType: "image/png",
      buffer: Buffer.from(await file.arrayBuffer()),
    });
  await reader.getByRole("img", { name: "案件证据图片" }).waitFor();
  await reader.getByRole("button", { name: "提交举报", exact: true }).click();
  await reader.waitForURL(/\/me\/cases\/\d+$/);
  const reportId = Number(reader.url().split("/").at(-1));
  await reader
    .getByText("隔离验收文章举报与证据回读", { exact: true })
    .waitFor();
  await reader.getByRole("img", { name: "案件证据图片" }).waitFor();
  await shot(reader, "W10-report-detail.png");
  await reader.goto(`${userUrl}/me/cases/new`);
  await reader
    .getByRole("textbox", { name: "说明" })
    .fill("隔离验收普通反馈，期待回到本人处理记录");
  await reader.getByRole("button", { name: "提交反馈", exact: true }).click();
  await reader.waitForURL(/\/me\/cases\/\d+$/);
  const feedbackId = Number(reader.url().split("/").at(-1));
  await reader.goto(`${userUrl}/me/cases`);
  await reader.getByRole("link", { name: `内容举报 ${reportId}` }).waitFor();
  await reader.getByRole("link", { name: `使用反馈 ${feedbackId}` }).waitFor();
  pass("网页文章举报上传与读取证据，普通反馈进入本人列表");

  await admin.goto(`${adminUrl}/admin/login`);
  await admin.locator('[name="admin-login"]').fill(fixture.adminLogin);
  await admin.locator('[name="admin-password"]').fill(fixture.password);
  await admin
    .getByRole("button", { name: "登录社区管理", exact: true })
    .click();
  await admin.waitForURL("**/admin/cases");
  const adminToken = (
    await api("/admin/auth/login", null, "POST", {
      login: fixture.adminLogin,
      password: fixture.password,
    })
  ).accessToken;
  await admin.goto(`${adminUrl}/admin/cases/${feedbackId}`);
  await admin.getByRole("button", { name: "受理此案件" }).click();
  await admin
    .getByRole("combobox", { name: "处理结论" })
    .selectOption("ANSWERED");
  await admin
    .getByRole("textbox", { name: /公开答复/ })
    .fill("隔离验收反馈答复");
  await admin.getByRole("button", { name: "确认处理并留存记录" }).click();
  await admin
    .getByRole("dialog")
    .getByRole("button", { name: "提交处理决定" })
    .click();
  await admin.getByText("隔离验收反馈答复", { exact: false }).last().waitFor();
  await reader.goto(`${userUrl}/me/notifications`);
  const notice = reader
    .locator("article")
    .filter({ has: reader.locator(`a[href$="/me/cases/${feedbackId}"]`) });
  await notice.getByRole("button", { name: "标记已读" }).click();
  await notice.getByText(/· 已读/).waitFor();
  await notice.getByRole("link", { name: "查看相关内容" }).click();
  await reader.waitForURL(`${userUrl}/me/cases/${feedbackId}`);
  await reader.getByText("隔离验收反馈答复", { exact: true }).waitFor();
  pass("社区决定生成站内通知，网页标记已读后进入本人反馈详情");

  const stamp = randomUUID().slice(0, 8);
  const sourceName = `验收旧标签 ${stamp}`;
  const targetName = `验收保留标签 ${stamp}`;
  const category = (name) =>
    api("/admin/categories", adminToken, "POST", {
      kind: "JOB",
      name,
      enabled: true,
      displayOrder: -10,
      expectedRevision: 0,
      requestId: randomUUID(),
      reason: "隔离环境用于分类归并验收",
    });
  await category(sourceName);
  await category(targetName);
  await admin.goto(`${adminUrl}/admin/categories?kind=JOB`);
  const source = admin.getByRole("row").filter({ hasText: sourceName });
  await source.getByRole("button", { name: "归并同义标签" }).click();
  const targetOptions = admin.getByRole("combobox", { name: "保留的同类标签" });
  const targetOption = targetOptions.getByRole("option", {
    name: new RegExp(targetName),
  });
  await targetOption.waitFor({ state: "attached" });
  await targetOptions.selectOption(await targetOption.getAttribute("value"));
  await admin
    .getByRole("textbox", { name: "归并理由" })
    .fill("验收验证同类型启用标签归并");
  await admin.getByRole("button", { name: "确认归并", exact: true }).click();
  await admin
    .getByRole("dialog")
    .getByRole("button", { name: /^归并到/ })
    .click();
  await source.getByText(/已归并至分类/).waitFor();
  await shot(admin, "A06-merge.png");
  pass("社区分类真实归并并重新读取来源、目标及状态");

  const candidateId =
    fixture.candidateId ??
    (await api("/me", await campusToken(fixture.candidateEmail))).userId;
  await admin.goto(`${adminUrl}/admin/accounts`);
  const selected = admin
    .getByRole("row")
    .filter({ has: admin.getByText(`用户 ${candidateId}`, { exact: true }) });
  await selected.getByRole("button", { name: "查看限制" }).click();
  const state = await api(
    `/admin/users/${candidateId}/certification`,
    adminToken,
  );
  if (state.state === "VALID") {
    await admin
      .getByRole("textbox", { name: "撤销理由" })
      .fill("隔离验收测试撤销与本人申诉");
    await admin.getByRole("button", { name: "确认撤销认证" }).click();
    await admin
      .getByRole("dialog")
      .getByRole("button", { name: "撤销校园认证" })
      .click();
  }
  await admin.getByText(/已撤销 · 修订/).waitFor();
  await signIn(candidate, fixture.candidateEmail, "/me/certification");
  await candidate.getByRole("heading", { name: "校园认证已撤销" }).waitFor();
  await candidate
    .getByText("撤销理由 隔离验收测试撤销与本人申诉", { exact: true })
    .waitFor();
  await candidate.getByRole("link", { name: "站内通知", exact: true }).click();
  const revoked = candidate
    .locator("article")
    .filter({ hasText: /撤销|重新认证/ })
    .first();
  await revoked.waitFor();
  const markRevoked = revoked.getByRole("button", { name: "标记已读" });
  if (await markRevoked.isVisible()) await markRevoked.click();
  await revoked.getByText(/· 已读/).waitFor();
  await revoked.getByRole("link", { name: "查看相关内容" }).click();
  const appealAction = candidate
    .getByRole("button", { name: "提交认证申诉" })
    .or(candidate.getByRole("link", { name: "查看本次认证申诉" }));
  await appealAction.waitFor();
  if (await candidate.getByRole("textbox", { name: "申诉理由" }).isVisible()) {
    await candidate
      .getByRole("textbox", { name: "申诉理由" })
      .fill("隔离验收申请复核校园认证");
  }
  await appealAction.click();
  await candidate.waitForURL(/\/me\/appeals\/\d+$/);
  const appealId = Number(candidate.url().split("/").at(-1));
  await admin.reload();
  const appeal = admin
    .getByRole("row")
    .filter({ hasText: new RegExp(`^\\s*${appealId}\\s*/\\s*校园认证`) });
  await appeal.getByRole("button", { name: "查看与复核" }).click();
  const outcome = admin.getByRole("combobox", { name: "复核结论" });
  if ((await outcome.isVisible()) && (await outcome.isEnabled())) {
    await outcome.selectOption("ACCEPTED");
    await admin
      .getByRole("textbox", { name: "公开复核答复" })
      .fill("认证申诉已复核，请使用原校园邮箱重新认证。");
    await admin.getByRole("button", { name: "保存复核决定" }).click();
  }
  await appeal.getByText("已接受", { exact: true }).waitFor();
  await candidate.reload();
  await candidate.getByText(/复核答复 认证申诉已复核/).waitFor();
  await shot(candidate, "W08-certification-appeal.png");
  report.appealId = appealId;
  pass("管理员撤销认证，受限账号登录、读通知、提交认证申诉并查看管理员答复");

  await reader.goto(`${userUrl}/me/wallet`);
  const button = reader.getByRole("button", {
    name: /^(领取阅读奖励|今日阅读奖励已领)$/,
  });
  const remaining = Math.max(
    0,
    Date.parse(reading.data.claimableAt) - Date.now(),
  );
  await button.waitFor();
  if ((await button.innerText()).trim() === "领取阅读奖励") {
    await button.click({ timeout: remaining + 20000 });
    await reader.getByText(/已领取阅读奖励/).waitFor();
  }
  await reader
    .getByText(/^阅读奖励 · \+/)
    .first()
    .waitFor();
  await reader.setViewportSize({ width: 390, height: 844 });
  assert(
    await reader.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await shot(reader, "W09-reading-reward-narrow.png");
  pass("真实计时到期后领取阅读奖励，窄屏核对余额与账目");

  await admin.goto(`${adminUrl}/admin/cases/${reportId}`);
  await admin.getByRole("button", { name: "受理此案件" }).click();
  const linked = admin.getByRole("link", { name: "前往对应文章与评论巡查" });
  await admin.waitForFunction(
    (id) =>
      [...document.links].some(
        (link) =>
          link.href.includes(`/admin/content?`) &&
          link.href.includes(`case=${id}`),
      ),
    reportId,
  );
  await linked.click();
  await admin.getByText(`举报案件 ${reportId}`, { exact: true }).waitFor();
  const moderate = async (action, reason) => {
    const received = admin.waitForResponse(
      (response) =>
        response
          .url()
          .endsWith(`/admin/articles/${long.articleId}/moderation`) &&
        response.request().method() === "POST",
    );
    await admin.getByRole("textbox", { name: "必填理由" }).fill(reason);
    await admin.getByRole("button", { name: action }).click();
    await admin.getByRole("dialog").getByRole("button", { name: /选定内容$/ }).click();
    const receipt = await (await received).json();
    assert.equal(receipt.code, "OK", receipt.traceId);
    return receipt.data;
  };
  const hidden = await moderate("确认隐藏", "隔离验收案件关联内容隐藏");
  let restored = false;
  try {
    assert.equal(hidden.caseId, reportId);
    assert.equal(hidden.state, "ADMIN_HIDDEN");
    const visible = await moderate("确认恢复", "隔离验收已恢复原公开文章");
    restored = visible.state === "PUBLISHED";
    assert(restored);
    assert.equal(visible.caseId, reportId);
    await admin.goto(`${adminUrl}/admin/audit`);
    const recorded = admin
      .getByRole("row")
      .filter({ hasText: "隔离验收已恢复原公开文章" })
      .first();
    await recorded.getByRole("link", { name: `案件 ${reportId}` }).waitFor();
    await shot(admin, "A04-case-moderation-audit.png");
  } finally {
    if (!restored) {
      const current = await api(
        `/admin/articles/${long.articleId}`,
        adminToken,
      );
      if (current.state === "ADMIN_HIDDEN")
        await api(
          `/admin/articles/${long.articleId}/moderation`,
          adminToken,
          "POST",
          {
            action: "RESTORE",
            expectedRevision: current.revision,
            requestId: randomUUID(),
            reason: "隔离验收中断后恢复公开文章",
          },
        );
    }
  }
  pass("案件发起隐藏与恢复，真实回执和治理审计均保留举报编号");
  assert.equal(report.errors.length, 0);
} catch (error) {
  report.finishingError = error.message;
  throw error;
} finally {
  await writeFile(resolve(out, "report.json"), JSON.stringify(report, null, 2));
  await browser.close();
}
