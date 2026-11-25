import assert from "node:assert/strict";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { resolve } from "node:path";
import { chromium } from "playwright";
import {
  isolatedConsole,
  publishIsolatedArticle,
} from "./isolated-console-fixture.mjs";

const root = process.env.CAMPUSHUB_CONSOLE_FIXTURE;
assert(root, "CAMPUSHUB_CONSOLE_FIXTURE is required");
const config = JSON.parse(
  await readFile(resolve(root, "ops.private.json"), "utf8"),
);
assert.equal(config.databaseName, "campushub_test");
assert.equal(config.healthUrl, "http://127.0.0.1:8082/actuator/health");
assert.equal(config.port, 8092);
const fixture = JSON.parse(
  await readFile(resolve(root, "fixture.private.json"), "utf8"),
);
const packaged = process.argv[2] === "--packaged";
const restoreOnly = packaged ? undefined : process.argv[2];
assert(
  !restoreOnly || /^[a-f0-9-]{36}$/.test(restoreOnly),
  "Pass the isolated backup identifier.",
);
const out = resolve(
  root,
  packaged
    ? "ops-evidence-packaged-failed"
    : restoreOnly
      ? "ops-restore-repeat"
      : "ops-evidence",
);
await mkdir(out, { recursive: true });
const base = "http://127.0.0.1:5175/#/ops";
const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.setDefaultTimeout(20000);
const report = { operations: [], checks: [], requests: [], errors: [] };
let seededArticle;
page.on("pageerror", (e) => report.errors.push(e.message));
page.on("response", async (response) => {
  const path = new URL(response.url()).pathname;
  if (!path.startsWith("/api/v1/ops/")) return;
  const { code, traceId } = await response.json().catch(() => ({}));
  report.requests.push({
    path,
    method: response.request().method(),
    status: response.status(),
    code,
    traceId,
  });
});

async function open(path) {
  await page.goto(`${base}/${path}`);
  await page.locator("h1").waitFor();
}
async function operation(path, reference) {
  await open(path);
  if (reference) {
    await page
      .getByRole("row")
      .filter({ hasText: reference })
      .getByRole("button", { name: /选择/ })
      .click();
  }
  await page.getByRole("button", { name: "读取操作确认", exact: true }).click();
  if (path === "restore") {
    await page
      .getByText("服务端已返回备份预检结果，请核对覆盖范围。")
      .waitFor();
    await page.getByText("已完成文件核对", { exact: true }).waitFor();
    await page.screenshot({
      path: resolve(out, "O07-restore-preflight.png"),
      fullPage: true,
    });
  }
  await page.getByRole("checkbox").check();
  let polls = 0;
  const pollingTarget = "**/api/v1/ops/jobs/*";
  const interrupt = async (route) => {
    if (route.request().method() === "GET" && ++polls === 2)
      await route.abort("failed");
    else await route.continue();
  };
  if (path === "deploy" && !packaged)
    await page.route(pollingTarget, interrupt);
  const received = page.waitForResponse(
    (r) =>
      r.url().endsWith("/api/v1/ops/jobs") && r.request().method() === "POST",
  );
  await page.getByRole("button", { name: /^确认.+并创建任务$/ }).click();
  const dialog = page.getByRole("dialog");
  await dialog.waitFor();
  assert.equal(
    await page.evaluate(() => document.activeElement?.textContent?.trim()),
    "取消",
  );
  if (path === "deploy") {
    await page.keyboard.press("Escape");
    await dialog.waitFor({ state: "hidden" });
    assert.match(
      await page.evaluate(() => document.activeElement?.textContent || ""),
      /创建任务/,
    );
    await page.getByRole("button", { name: /^确认.+并创建任务$/ }).click();
  }
  await dialog.getByRole("button", { name: /^执行/ }).click();
  const result = await (await received).json();
  assert.equal(result.code, "OK");
  const id = result.data.id;
  await page.waitForURL(`**/ops/tasks/${id}`);
  if (path === "deploy" && !packaged) {
    await page.getByText(/当前进度待确认/).waitFor({ timeout: 25000 });
    assert(await page.locator(".ops-task-summary").isVisible());
    assert(await page.getByText(id, { exact: true }).first().isVisible());
    await page.setViewportSize({ width: 390, height: 844 });
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    await page.screenshot({
      path: resolve(out, "O08-polling-interrupted-390.png"),
    });
    await page.unroute(pollingTarget, interrupt);
    report.checks.push("自动轮询失败保留已读取任务，继续查询原任务");
    await page.setViewportSize({ width: 1440, height: 1000 });
  }
  const terminal = page
    .locator(".ops-task-result")
    .getByRole("heading", { name: /^(执行成功|执行失败|中断待核查)$/ });
  await terminal.waitFor({ timeout: 120000 });
  assert.equal(
    await terminal.innerText(),
    packaged ? "执行失败" : "执行成功",
    await page.locator(".ops-task-result").innerText(),
  );
  const job = await page.evaluate(async (jobId) => {
    const token = JSON.parse(
      sessionStorage.getItem("campushub.web.ops.session.v1"),
    ).accessToken;
    const response = await fetch(`/api/v1/ops/jobs/${jobId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return (await response.json()).data;
  }, id);
  assert.equal(job.state, packaged ? "FAILED" : "SUCCEEDED");
  if (packaged) {
    assert.equal(job.errorCode, "OPS_EXECUTION_FAILED");
    assert.equal(job.events.at(-1).phase, "PREFLIGHT");
    await page.getByText(/执行阶段 PREFLIGHT/).waitFor();
    await page.getByRole("button", { name: "复制追踪号" }).waitFor();
  }
  report.operations.push(job);
  await page.screenshot({
    path: resolve(out, `O08-task-${path}.png`),
    fullPage: true,
  });
  console.log(`${job.action} ${job.state} ${id}`);
  return job;
}
try {
  if (!restoreOnly) {
    const business = await fetch(config.healthUrl).catch(() => undefined);
    assert(
      !business?.ok,
      "The fixture must start with its business process unavailable",
    );
  }
  await open("login");
  await page.locator('[name="ops-login"]').fill(fixture.opsLogin);
  await page.locator('[name="ops-password"]').fill(fixture.password);
  await page.getByRole("button", { name: "登录独立运维", exact: true }).click();
  await page.waitForURL("**/ops/overview");
  await page.getByText("已授权运维账号", { exact: true }).waitFor();
  let backupId = restoreOnly;
  if (!backupId) {
    await page.getByRole("link", { name: /日志/ }).first().click();
    await page
      .getByRole("heading", { name: /日志与任务/ })
      .first()
      .waitFor();
    await page.screenshot({
      path: resolve(out, "O03-business-stopped.png"),
      fullPage: true,
    });
    report.checks.push("独立运维在业务不可用时成功登录并打开日志");
    await page.getByRole("checkbox", { name: "仅错误请求" }).check();
    await page.route("**/api/v1/ops/logs?*", (r) => r.abort("failed"), {
      times: 1,
    });
    await page.getByRole("button", { name: "查询日志" }).click();
    await page.getByText("下方记录来自上次成功查询。").waitFor();
    assert(
      await page.getByRole("checkbox", { name: "仅错误请求" }).isChecked(),
    );
    await page.getByRole("button", { name: "重试当前查询" }).click();
    await page
      .getByText("下方记录来自上次成功查询。")
      .waitFor({ state: "hidden" });
    await page.setViewportSize({ width: 390, height: 844 });
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    await page.screenshot({ path: resolve(out, "O03-logs-390.png") });
    await page.setViewportSize({ width: 1440, height: 1000 });
    report.checks.push("日志查询失败保留旧内容和错误筛选，按原条件重试");
    const deployed = await operation("deploy", "acceptance-69b2f3a");
    if (packaged) {
      report.checks.push("独立复现打包启动部署预检失败");
    } else {
      const seed = await isolatedConsole(root, report.requests);
      seededArticle = await publishIsolatedArticle(seed);
      await operation("restart");
      backupId = (await operation("backups")).result.backupId;
    }
  }
  if (!packaged) {
    const restored = await operation("restore", backupId);
    assert.equal(restored.result.businessVerification.status, "VERIFIED");
    assert(
      Number.isSafeInteger(restored.result.verifiedMediaReferences) &&
        restored.result.verifiedMediaReferences >= 0,
    );
    if (seededArticle) {
      assert(restored.result.verifiedMediaReferences > 0);
      const publicArticle = await fetch(
        "http://127.0.0.1:8082/api/v1/articles/" + seededArticle.articleId,
      );
      assert(
        (await publicArticle.json()).data.body.includes(seededArticle.image),
      );
      assert((await fetch("http://127.0.0.1:8082" + seededArticle.image)).ok);
      report.checks.push("恢复后回读同一篇隔离文章及媒体文件");
    }
    const health = await fetch(config.healthUrl);
    assert.equal((await health.json()).data.status, "UP");
    assert.equal(report.errors.length, 0);
    await page.setViewportSize({ width: 390, height: 844 });
    await open("overview");
    await page.getByText("受控进程运行中", { exact: false }).first().waitFor();
    assert(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    );
    await page.screenshot({
      path: resolve(out, "O02-overview-narrow.png"),
      fullPage: true,
    });
  }
} catch (e) {
  report.failure = e.message;
  await page
    .screenshot({ path: resolve(out, "failure.png"), fullPage: true })
    .catch(() => {});
  throw e;
} finally {
  await writeFile(resolve(out, "report.json"), JSON.stringify(report, null, 2));
  await browser.close();
}
