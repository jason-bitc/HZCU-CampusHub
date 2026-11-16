// Opt-in real local-backend checks. Restores original favorite states on exit.
import { chromium } from "playwright";
import assert from "node:assert/strict";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const out = resolve(root, "docs/screenshots/20261106-management");
await mkdir(out, { recursive: true });
if (!process.env.CAMPUSHUB_WEB_CREDENTIAL_FILE)
  throw new Error("Set private credential JSON path first");
const raw = JSON.parse(
  await readFile(process.env.CAMPUSHUB_WEB_CREDENTIAL_FILE, "utf8"),
);
const credentials = Array.isArray(raw) ? raw[0] : raw;
const base = process.env.CAMPUSHUB_WEB_URL || "http://127.0.0.1:5173";
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 960 },
});
const page = await context.newPage();
const evidence = [];
const assertions = [];
let originals = [];
let initialTotal;
let uncaught = 0;
page.on("pageerror", () => uncaught++);
page.on("response", (r) => {
  const path = new URL(r.url()).pathname;
  if (path.startsWith("/api/v1/"))
    evidence.push({
      method: r.request().method(),
      path,
      status: r.status(),
      traceId: r.headers()["x-trace-id"] || "",
    });
});
const report = {
  checkedAt: new Date().toISOString(),
  status: "RUNNING",
  mode: "real local Java backend + one explicit simulated 503 transport response",
  assertions,
  evidence,
  realEmailsSent: 0,
  realCoinsSpent: 0,
  realAdminMutation: false,
  restoredOriginalFavoriteStates: false,
};
async function api(path, method = "GET") {
  return page.evaluate(
    async ({ path, method }) => {
      const s = JSON.parse(
        sessionStorage.getItem("campushub.web.session.v1") || "{}",
      );
      const r = await fetch(`/api/v1${path}`, {
        method,
        headers: { Authorization: `Bearer ${s.accessToken}` },
        cache: "no-store",
      });
      const v = await r.json();
      if (!r.ok) throw new Error(`${r.status} ${v.code}`);
      return v.data;
    },
    { path, method },
  );
}
async function shot(name, mobile = false) {
  await page.setViewportSize(
    mobile ? { width: 390, height: 844 } : { width: 1440, height: 960 },
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.evaluate(
    () =>
      new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))),
  );
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page.screenshot({ path: resolve(out, name) });
}
try {
  await page.goto(`${base}/#/account`);
  await page.locator("input[name=email]").fill(credentials.email);
  await page.locator("input[name=password]").fill(credentials.password);
  await page.getByRole("button", { name: "登录校园账号", exact: true }).click();
  await page.waitForURL("**/#/workspace");
  await page.locator(".workspace-card").first().waitFor();
  const initial = await api("/me/favorites?page=1&size=100");
  initialTotal = initial.total;
  const articles = await page.evaluate(async () => {
    const r = await fetch("/api/v1/articles?page=1&size=100");
    return (await r.json()).data.items
      .filter((x) => x.sourceType === "DEMO")
      .slice(0, 6)
      .map((x) => ({ id: x.id, title: x.title }));
  });
  assert.equal(
    articles.length,
    6,
    "need six existing DEMO articles for the reversible favorite test",
  );
  for (const row of articles) {
    const state = await api(`/articles/${row.id}/interaction-state`);
    originals.push({ id: row.id, favorited: state.favorited });
  }
  await page.goto(base);
  await page.locator(".article-card").first().waitFor();
  assert.equal(await page.locator(".discovery-sidebar").count(), 0);
  for (const row of articles) {
    const card = page.locator(".article-card").filter({
      has: page.getByRole("link", { name: row.title, exact: true }),
    });
    await card
      .getByRole("button", { name: /^(收藏经验|已收藏 · 取消)$/ })
      .waitFor();
    if (!originals.find((x) => x.id === row.id).favorited) {
      await card.getByRole("button", { name: "收藏经验", exact: true }).click();
      await card
        .getByRole("button", { name: "已收藏 · 取消", exact: true })
        .waitFor();
    }
  }
  assertions.push(
    "real bodyless favorite actions directly from six public DEMO cards; no discovery right sidebar",
  );
  await page.goto(`${base}/#/favorites`);
  await page.locator(".favorites-table tbody tr").first().waitFor();
  await page
    .getByRole("combobox", { name: "每页收藏条数", exact: true })
    .selectOption("5");
  await page.waitForFunction(
    () => document.querySelectorAll(".favorites-table tbody tr").length === 5,
  );
  await shot("F01-favorites-actual.png");
  await shot("F01-favorites-actual-mobile.png", true);
  await page.setViewportSize({ width: 1440, height: 960 });
  assertions.push(
    "real current-public collection table, five-row size choice and responsive horizontal table containment",
  );
  if (initialTotal === 0) {
    await page.getByRole("button", { name: "下一页", exact: true }).click();
    await page.waitForFunction(
      () => document.querySelectorAll(".favorites-table tbody tr").length === 1,
    );
    const href = await page
      .locator(".favorites-table tbody tr td:first-child a")
      .getAttribute("href");
    await page.locator(".favorites-table tbody tr td:first-child a").click();
    await page.waitForURL(/#\/articles\/\d+$/);
    await page.locator(".markdown").waitFor();
    await page.goBack();
    await page.waitForURL(/page=2/);
    await page.waitForFunction(
      () => document.querySelectorAll(".favorites-table tbody tr").length === 1,
    );
    await shot("F02-last-page-actual.png");
    await page.getByRole("button", { name: "取消收藏", exact: true }).click();
    await page.waitForFunction(
      () =>
        location.hash.includes("page=1") &&
        document.querySelectorAll(".favorites-table tbody tr").length === 5,
    );
    assert.equal((await api("/me/favorites?page=1&size=100")).total, 5);
    assert.ok(href);
    assertions.push(
      "browser Back retains collection page/size; removing sole final-page row returns to page one with real new total",
    );
  } else
    report.lastPageRemoval = "NOT_RUN: preexisting user favorites preserved";
  await page.route(
    "**/api/v1/me/favorites?*",
    (r) =>
      r.fulfill({
        status: 503,
        contentType: "application/json",
        body: JSON.stringify({
          code: "SERVICE_UNAVAILABLE",
          message: "Explicit browser fault injection",
          traceId: "test-injected-503",
        }),
      }),
    { times: 1 },
  );
  await page.getByRole("button", { name: "刷新收藏", exact: true }).click();
  await page
    .getByRole("heading", { name: "收藏暂时读取或操作失败", exact: true })
    .waitFor();
  assert.equal(
    await page
      .getByRole("heading", { name: "还没有收藏的经验", exact: true })
      .count(),
    0,
  );
  await page.getByRole("button", { name: "重试当前页", exact: true }).click();
  await page.locator(".favorites-table tbody tr").first().waitFor();
  assertions.push(
    "explicit simulated 503 remains an error, retry uses real backend without empty-state fallback",
  );
  const domain = await page.evaluate(async () => {
    const s = JSON.parse(sessionStorage.getItem("campushub.web.session.v1"));
    const a = await fetch("/api/v1/admin/me", {
      headers: { Authorization: `Bearer ${s.accessToken}` },
      cache: "no-store",
    });
    const m = await fetch("/api/v1/me", {
      headers: { Authorization: `Bearer ${s.accessToken}` },
      cache: "no-store",
    });
    return { admin: a.status, member: m.status };
  });
  assert.deepEqual(domain, { admin: 404, member: 200 });
  const adminBase = process.env.CAMPUSHUB_ADMIN_URL || "http://127.0.0.1:5174";
  const campusToken = await page.evaluate(
    () =>
      JSON.parse(sessionStorage.getItem("campushub.web.session.v1"))
        .accessToken,
  );
  const crossDomain = await fetch(`${adminBase}/api/v1/admin/me`, {
    headers: { Authorization: `Bearer ${campusToken}` },
  });
  assert.equal(crossDomain.status(), 401);
  assertions.push(
    "public proxy rejects privileged route and community backend rejects campus Bearer while ordinary session remains valid",
  );
  assert.equal(uncaught, 0);
  report.status = "PASS";
} catch (error) {
  report.status = "FAIL";
  report.failure = error instanceof Error ? error.message : String(error);
  for (const secret of [credentials.email, credentials.password])
    if (secret) report.failure = report.failure.replaceAll(secret, "[private]");
  process.exitCode = 1;
} finally {
  try {
    for (const row of originals)
      await api(
        `/articles/${row.id}/favorite`,
        row.favorited ? "PUT" : "DELETE",
      );
    for (const row of originals) {
      const state = await api(`/articles/${row.id}/interaction-state`);
      assert.equal(state.favorited, row.favorited);
    }
    report.restoredOriginalFavoriteStates = true;
    if (initialTotal === 0) {
      await page.goto(`${base}/#/favorites`);
      await page
        .getByRole("heading", { name: "还没有收藏的经验", exact: true })
        .waitFor();
      await shot("F03-empty-actual.png");
    }
    await api("/auth/logout", "POST");
    await page.evaluate(() => {
      sessionStorage.removeItem("campushub.web.session.v1");
      Object.keys(sessionStorage)
        .filter((k) => k.startsWith("campushub.web.private:"))
        .forEach((k) => sessionStorage.removeItem(k));
    });
  } catch {
    report.status = "FAIL";
    report.cleanup =
      "CHECK_REQUIRED: original favorite/session state could not be fully verified";
    process.exitCode = 1;
  }
  report.uncaughtPageErrors = uncaught;
  await writeFile(
    resolve(root, "docs/FAVORITES-BROWSER-20261106.json"),
    JSON.stringify(report, null, 2) + "\n",
  );
  await browser.close();
  console.log(
    JSON.stringify({
      status: report.status,
      assertions: assertions.length,
      restored: report.restoredOriginalFavoriteStates,
      failure: report.failure,
      cleanup: report.cleanup,
    }),
  );
}
