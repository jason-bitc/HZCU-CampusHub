// Explicit opt-in real local-backend verification. Never sends verification mail or spends coins.
import { chromium } from "playwright";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { resolve, dirname } from "node:path";
import assert from "node:assert/strict";
import { deflateSync } from "node:zlib";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const out = resolve(
  root,
  process.env.CAMPUSHUB_WEB_SCREENSHOT_DIR ||
    "docs/screenshots/20261106-web-foundation",
);
const base = process.env.CAMPUSHUB_WEB_URL || "http://127.0.0.1:5173";
const credentialsPath = process.env.CAMPUSHUB_WEB_CREDENTIAL_FILE;
if (!credentialsPath)
  throw new Error(
    "Set CAMPUSHUB_WEB_CREDENTIAL_FILE to an ignored private JSON file (email/password).",
  );
const raw = JSON.parse(await readFile(credentialsPath, "utf8"));
const credentials = Array.isArray(raw) ? raw[0] : raw;
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: "chrome", headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
  deviceScaleFactor: 1,
});
const page = await context.newPage();
const evidence = [];
const assertions = [];
const errors = [];
let fixtureId = null;
page.on("response", (response) => {
  const url = new URL(response.url());
  if (!url.pathname.startsWith("/api/v1/")) return;
  evidence.push({
    method: response.request().method(),
    path: url.pathname,
    status: response.status(),
    traceId: response.headers()["x-trace-id"] || "",
  });
});
page.on("pageerror", () => errors.push("uncaught page error"));
async function api(path, method = "GET", data) {
  return page.evaluate(
    async ({ path, method, data }) => {
      const s = JSON.parse(
        sessionStorage.getItem("campushub.web.session.v1") || "{}",
      );
      const headers = { Authorization: `Bearer ${s.accessToken}` };
      if (data !== undefined) headers["Content-Type"] = "application/json";
      const r = await fetch(`/api/v1${path}`, {
        method,
        headers,
        body: data === undefined ? undefined : JSON.stringify(data),
        cache: "no-store",
      });
      const value = await r.json();
      if (!r.ok) throw new Error(`${r.status} ${value.code}`);
      return value.data;
    },
    { path, method, data },
  );
}
async function shot(name, width = 1440, height = 1000, selector = "") {
  await page.setViewportSize({ width, height });
  if (selector) await page.locator(selector).first().scrollIntoViewIfNeeded();
  else await page.evaluate(() => window.scrollTo(0, 0));
  await page.evaluate(
    () =>
      new Promise((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(resolve)),
      ),
  );
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: resolve(out, name), fullPage: false });
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
    `horizontal overflow at ${name}`,
  );
}
function testPng() {
  const table = Array.from({ length: 256 }, (_, n) => {
    for (let k = 0; k < 8; k++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1;
    return n >>> 0;
  });
  function chunk(type, bytes) {
    const data = Buffer.concat([Buffer.from(type), bytes]);
    let crc = 0xffffffff;
    for (const n of data) crc = table[(crc ^ n) & 255] ^ (crc >>> 8);
    const length = Buffer.alloc(4);
    length.writeUInt32BE(bytes.length);
    const tail = Buffer.alloc(4);
    tail.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
    return Buffer.concat([length, data, tail]);
  }
  const h = Buffer.alloc(13);
  h.writeUInt32BE(240);
  h.writeUInt32BE(100, 4);
  h[8] = 8;
  h[9] = 2;
  const pixels = Buffer.alloc(100 * (1 + 240 * 3));
  for (let y = 0; y < 100; y++)
    for (let x = 0; x < 240; x++) {
      const i = y * 721 + 1 + x * 3;
      const color =
        x < 80 ? [13, 107, 87] : x < 160 ? [151, 183, 166] : [232, 242, 236];
      pixels.set(color, i);
    }
  return Buffer.concat([
    Buffer.from("89504e470d0a1a0a", "hex"),
    chunk("IHDR", h),
    chunk("IDAT", deflateSync(pixels)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}
const report = {
  checkedAt: new Date().toISOString(),
  status: "RUNNING",
  base,
  browser: "Installed Google Chrome, Playwright",
  backend: "real local Java/MySQL",
  assertions,
  evidence,
  emailsSent: 0,
  coinsSpent: 0,
  realDevice: "NOT_RUN",
};
try {
  await page.goto(base);
  await page.locator(".article-card").first().waitFor();
  await shot("W01-discover-desktop.png");
  await shot("W01-discover-mobile.png", 390, 844);
  await page
    .getByRole("searchbox")
    .fill("本轮无结果验证-不存在的经验-20261106");
  await page.getByRole("button", { name: "搜索", exact: true }).click();
  await page.getByRole("heading", { name: "当前条件下还没有经验" }).waitFor();
  assertions.push("real empty-result and clear-filter recovery");
  await page.getByRole("button", { name: "清除所有条件", exact: true }).click();
  await page.locator(".article-card").first().waitFor();
  await page.goto(`${base}/#/articles/2005`);
  await page.locator(".markdown h1").waitFor();
  await page.getByRole("button", { name: "目录", exact: true }).click();
  assert.ok(await page.locator(".toc-item").count());
  await page.locator(".toc-item").first().click();
  assertions.push(
    "guest full article, safe Markdown and compact TOC navigation",
  );
  await shot("W02-guest-reading.png");
  await page.goto(`${base}/#/account`);
  await page.locator("input[name=email]").fill(credentials.email);
  await page.locator("input[name=password]").fill(credentials.password);
  await page.getByRole("button", { name: "登录校园账号", exact: true }).click();
  await page.waitForURL("**/#/workspace");
  await page.locator(".workspace-card").first().waitFor();
  await page.reload();
  await page.locator(".workspace-card").first().waitFor();
  assertions.push("real login and session recovery after browser reload");
  if (process.env.CAMPUSHUB_WEB_FIXTURE_ID) {
    fixtureId = Number(process.env.CAMPUSHUB_WEB_FIXTURE_ID);
    const existing = await api(`/me/articles/${fixtureId}/draft`);
    assert.ok(
      ["DRAFT", "AUTHOR_OFFLINE"].includes(existing.state) &&
        (!existing.content.title ||
          existing.content.title.startsWith("【DEMO】网页原型接入验证")),
    );
    await page.goto(`${base}/#/editor/${fixtureId}`);
  } else {
    await page
      .getByRole("button", { name: "＋ 写一篇经验", exact: true })
      .click();
    await page.waitForURL(/#\/editor\/\d+$/);
    fixtureId = Number(page.url().split("/").pop());
  }
  await page
    .getByLabel("标题", { exact: true })
    .fill("【DEMO】网页原型接入验证 2026-11-06");
  await page
    .getByLabel("摘要", { exact: true })
    .fill("网页端真实保存、冲突、上传和发布验证；非真实求职经历。");
  const body =
    "# 当时的基础\n\n这是一篇网页联调演示，不代表真实求职经历。\n\n## 准备过程\n\n- 复习基础\n- 整理项目\n\n```cpp\nint main() { return 0; }\n```\n\n| 阶段 | 记录 |\n| --- | --- |\n| 复盘 | 保留完整 Markdown |\n\n<script>window.__unsafe = true</script>\n\n## 下一步\n\n继续核对接口与原型。";
  await page.getByLabel("Markdown 编辑", { exact: true }).fill(body);
  await page.getByLabel("Java后端开发（演示）", { exact: true }).check();
  await page.getByLabel("计算机科学与技术", { exact: true }).check();
  await page.getByLabel("经历发生阶段", { exact: true }).fill("DEMO 网页联调");
  await page.getByLabel("内容来源", { exact: true }).selectOption("DEMO");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.getByText("草稿已保存到服务器。", { exact: true }).waitFor();
  assert.equal(
    (await api(`/me/articles/${fixtureId}/draft`)).content.body,
    body,
  );
  assert.equal(await page.evaluate(() => window.__unsafe === true), false);
  assertions.push(
    "complete Markdown/table/code retained and HTML script remains literal",
  );
  const imagesBefore = (await api(`/me/articles/${fixtureId}/draft`)).images
    .length;
  let firstUpload;
  await page.route(
    `**/api/v1/me/articles/${fixtureId}/images`,
    async (route) => {
      const response = await route.fetch();
      assert.equal(response.status(), 200);
      firstUpload = (await response.json()).data;
      evidence.push({
        method: "POST",
        path: `/api/v1/me/articles/${fixtureId}/images`,
        status: response.status(),
        traceId: response.headers()["x-trace-id"],
        testCondition:
          "real backend response deliberately dropped before browser receipt",
      });
      await route.abort("failed");
    },
    { times: 1 },
  );
  const png = {
    name: "DEMO-test-colors.png",
    mimeType: "image/png",
    buffer: testPng(),
  };
  await page.locator("input[type=file]").setInputFiles(png);
  await page
    .getByRole("button", { name: "选择原图重试", exact: true })
    .waitFor();
  await page.waitForFunction(
    () => !document.querySelector("fieldset")?.disabled,
  );
  await page.locator("input[type=file]").setInputFiles(png);
  await page
    .getByText("图片已上传并插入正文，保存草稿后保留引用。", { exact: true })
    .waitFor();
  await page.waitForFunction(() => {
    const image = document.querySelector(".preview-column .markdown img");
    return (
      image &&
      image.src.startsWith("blob:") &&
      image.complete &&
      image.naturalWidth > 0
    );
  });
  assertions.push(
    "real multipart image upload and Bearer Blob preview without token URL",
  );
  const uploaded = await api(`/me/articles/${fixtureId}/draft`);
  assert.equal(uploaded.images.length, imagesBefore + 1);
  assert.ok(
    (
      await page.getByLabel("Markdown 编辑", { exact: true }).inputValue()
    ).includes(firstUpload.url),
  );
  assertions.push(
    "lost upload response retries same file/key and creates exactly one image",
  );
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.getByText("草稿已保存到服务器。", { exact: true }).waitFor();
  await shot("W05-editor-desktop.png");
  await shot("W05-editor-mobile.png", 390, 844);
  await shot("W05-mobile-preview.png", 390, 844, ".preview-column");
  await shot(
    "W05-mobile-publish-info.png",
    390,
    844,
    ".editor-sidebar > .panel",
  );
  await page.setViewportSize({ width: 1440, height: 1000 });
  const current = await api(`/me/articles/${fixtureId}/draft`);
  const { tags, ...content } = current.content;
  await api(`/me/articles/${fixtureId}/draft`, "PUT", {
    ...content,
    ...tags,
    expectedRevision: current.revision,
    summary: "服务器并发编辑版本，仅用于冲突验证",
  });
  await page
    .getByLabel("摘要", { exact: true })
    .fill("本机编辑版本，冲突后必须由用户选择");
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page
    .getByRole("heading", { name: "草稿版本有冲突，请手动合并" })
    .waitFor();
  assert.equal(
    await page.getByLabel("摘要", { exact: true }).inputValue(),
    "本机编辑版本，冲突后必须由用户选择",
  );
  await shot("W07-revision-conflict.png");
  await page
    .locator(".conflict-panel")
    .screenshot({ path: resolve(out, "W07-revision-conflict-full.png") });
  await shot("W07-conflict-mobile.png", 390, 844, ".conflict-panel");
  await shot(
    "W07-conflict-mobile-server.png",
    390,
    844,
    ".compare-grid > div:nth-child(2)",
  );
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page
    .getByRole("button", {
      name: "保留本机内容，按新修订手动合并",
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  await page.getByText("草稿已保存到服务器。", { exact: true }).waitFor();
  assertions.push(
    "real 409 preserves local edits and shows server/local versions; explicit merge required",
  );
  await page.getByRole("button", { name: "预览并发布", exact: true }).click();
  await page.getByRole("dialog").waitFor();
  assert.ok(
    await page.evaluate(
      () => document.activeElement?.closest("dialog") !== null,
    ),
  );
  const publishAttempts = [];
  page.on("request", (request) => {
    if (
      request.method() === "POST" &&
      new URL(request.url()).pathname ===
        `/api/v1/me/articles/${fixtureId}/publish`
    )
      publishAttempts.push(request.postDataJSON());
  });
  await page.route(
    `**/api/v1/me/articles/${fixtureId}/publish`,
    async (route) => {
      const response = await route.fetch();
      assert.equal(response.status(), 200);
      evidence.push({
        method: "POST",
        path: `/api/v1/me/articles/${fixtureId}/publish`,
        status: response.status(),
        traceId: response.headers()["x-trace-id"],
        testCondition:
          "real backend response deliberately dropped before browser receipt",
      });
      await route.abort("failed");
    },
    { times: 1 },
  );
  await page.getByRole("button", { name: "确认发布", exact: true }).click();
  await page.getByText(/尚未确认发布结果/).waitFor();
  await page.getByRole("button", { name: "返回编辑", exact: true }).click();
  page.once("dialog", (dialog) => dialog.accept());
  await page.reload();
  await page
    .getByRole("heading", { name: "发现本标签页保存的副本", exact: true })
    .waitFor();
  await page
    .getByRole("button", { name: "恢复本机编辑与待确认请求", exact: true })
    .click();
  await page.getByRole("button", { name: "核对上次发布", exact: true }).click();
  await page
    .getByText("发布回执已确认；当前状态：已发布。", { exact: true })
    .waitFor();
  assertions.push("explicit publish confirmation and real publish receipt");
  assert.equal(publishAttempts.length, 2);
  assert.deepEqual(publishAttempts[0], publishAttempts[1]);
  assertions.push(
    "lost publish response survives reload and retries original key/revision without saving again",
  );
  await api(`/articles/${fixtureId}/like`, "DELETE");
  await api(`/articles/${fixtureId}/favorite`, "DELETE");
  await page.goto(`${base}/#/articles/${fixtureId}`);
  await page
    .getByRole("button", { name: "赞同这篇经验", exact: true })
    .waitFor();
  await page.getByRole("button", { name: "赞同这篇经验", exact: true }).click();
  await page
    .getByRole("button", { name: "已点赞 · 取消", exact: true })
    .waitFor();
  await page
    .getByRole("button", { name: "收藏，稍后阅读", exact: true })
    .click();
  await page
    .getByRole("button", { name: "已收藏 · 取消", exact: true })
    .waitFor();
  await page
    .getByRole("button", { name: "已点赞 · 取消", exact: true })
    .click();
  await page
    .getByRole("button", { name: "赞同这篇经验", exact: true })
    .waitFor();
  await page.goto(`${base}/#/favorites`);
  await page
    .getByRole("link", {
      name: "【DEMO】网页原型接入验证 2026-11-06",
      exact: true,
    })
    .waitFor();
  assertions.push("real bodyless like/favorite and visible favorites list");
  const d = await api(`/me/articles/${fixtureId}/draft`);
  await api(`/me/articles/${fixtureId}/unpublish`, "POST", {
    expectedRevision: d.revision,
  });
  await page.getByRole("button", { name: "刷新收藏", exact: true }).click();
  await page
    .getByRole("heading", { name: "此收藏暂不可用", exact: true })
    .waitFor();
  assert.ok(
    !(await page.locator("main").innerText()).includes(
      "【DEMO】网页原型接入验证",
    ),
  );
  assertions.push("unavailable favorite does not reveal historical title/body");
  await page.getByRole("button", { name: "取消收藏", exact: true }).click();
  await page.goto(`${base}/#/workspace`);
  await page.locator(".workspace-card").first().waitFor();
  const card = page.locator(".workspace-card").filter({
    has: page.getByRole("heading", {
      name: "【DEMO】网页原型接入验证 2026-11-06",
      exact: true,
    }),
  });
  page.once("dialog", (dialog) => dialog.accept());
  await card
    .getByRole("button", { name: "恢复上次公开版本", exact: true })
    .click();
  await card.getByRole("button", { name: "下架", exact: true }).waitFor();
  page.once("dialog", (dialog) => dialog.accept());
  await card.getByRole("button", { name: "下架", exact: true }).click();
  await card
    .getByRole("button", { name: "恢复上次公开版本", exact: true })
    .waitFor();
  assertions.push(
    "workspace restore and unpublish use current revision; final fixture offline",
  );
  await shot("W06-workspace-desktop.png");
  await shot("W06-workspace-mobile.png", 390, 844);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(`${base}/#/me`);
  await page.getByRole("heading", { name: "我的硬币", exact: true }).waitFor();
  assert.ok(await page.getByLabel("昵称", { exact: true }).inputValue());
  assertions.push(
    "real profile/contact/wallet reads without changing account data",
  );
  await page.getByRole("button", { name: "退出当前账号", exact: true }).click();
  await page.waitForURL("**/#/");
  assert.equal(
    await page.evaluate(
      () =>
        Object.keys(sessionStorage).filter((k) =>
          k.startsWith("campushub.web."),
        ).length,
    ),
    0,
  );
  assertions.push(
    "real logout revokes current token and removes private browser records",
  );
  assert.equal(errors.length, 0);
  report.status = "PASS";
} catch (error) {
  report.status = "FAIL";
  report.failure =
    error instanceof Error
      ? error.message
          .replaceAll(credentials.email || "", "[private-email]")
          .replaceAll(credentials.password || "", "[private-password]")
      : "browser test failed";
  process.exitCode = 1;
} finally {
  if (fixtureId) {
    try {
      const d = await api(`/me/articles/${fixtureId}/draft`);
      if (d.state === "PUBLISHED")
        await api(`/me/articles/${fixtureId}/unpublish`, "POST", {
          expectedRevision: d.revision,
        });
      report.fixtureFinalState = (
        await api(`/me/articles/${fixtureId}/draft`)
      ).state;
    } catch {
      report.fixtureFinalState ||=
        report.status === "PASS"
          ? "AUTHOR_OFFLINE (verified before logout)"
          : "CHECK_REQUIRED";
    }
  }
  try {
    await api("/auth/logout", "POST");
    await page.evaluate(() => sessionStorage.clear());
  } catch {
    /* Already logged out, or no valid token remains. */
  }
  report.fixtureArticleId = fixtureId;
  report.uncaughtPageErrors = errors.length;
  await writeFile(
    resolve(
      root,
      process.env.CAMPUSHUB_WEB_REPORT || "docs/REAL-BROWSER-20261106.json",
    ),
    JSON.stringify(report, null, 2) + "\n",
  );
  await browser.close();
  console.log(
    JSON.stringify({
      status: report.status,
      assertions: assertions.length,
      fixtureArticleId: fixtureId,
      fixtureFinalState: report.fixtureFinalState,
      failure: report.failure,
    }),
  );
}
