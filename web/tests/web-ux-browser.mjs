import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { chromium } from "playwright";

const fixtureDir = process.env.CAMPUSHUB_WEB_UX_FIXTURE;
const evidenceDir = process.env.CAMPUSHUB_WEB_SCREENSHOT_DIR;
assert(
  fixtureDir && evidenceDir,
  "Set the isolated fixture and screenshot directories.",
);
const fixture = JSON.parse(
  await readFile(resolve(fixtureDir, "closure.private.json"), "utf8"),
);
const environment = JSON.parse(
  await readFile(resolve(fixtureDir, "business.private.json"), "utf8"),
);
assert.equal(environment.CAMPUSHUB_DB_NAME, "campushub_test");
assert.equal(environment.CAMPUSHUB_RUNTIME_ENV, "test");
const { imported } = JSON.parse(
  await readFile(resolve(fixtureDir, "demo-import.json"), "utf8"),
);
const longId = imported.find(({ sourceId }) => sourceId === "A01").articleId;
const imageId = imported.find(({ sourceId }) => sourceId === "A03").articleId;
const host = "http://127.0.0.1:5173";
const base = host + "/#";
const real = "http://127.0.0.1:8080/api/v1";
const report = { checks: [], backend: [], browserErrors: [] };
await mkdir(evidenceDir, { recursive: true });
const browser = await chromium.launch({ channel: "chrome", headless: true });
let article;
let author;
let reader;

async function api(path, token, method = "GET", data, expected = 200) {
  const headers = token ? { Authorization: "Bearer " + token } : {};
  if (data !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(real + path, {
    method,
    headers,
    body: data === undefined ? undefined : JSON.stringify(data),
  });
  const result = await response.json();
  report.backend.push({
    from: "direct",
    method,
    path: path.split("?")[0],
    status: response.status,
    code: result.code,
    traceId: result.traceId,
  });
  assert.equal(
    response.status,
    expected,
    method + " " + path.split("?")[0] + " " + result.code,
  );
  return result.data;
}
async function token(email) {
  return (
    await api("/auth/login", null, "POST", {
      email,
      password: fixture.password,
    })
  ).accessToken;
}
async function newPage(width = 1440) {
  const page = await browser.newPage({
    viewport: { width, height: width === 390 ? 844 : 1000 },
  });
  page.setDefaultTimeout(10000);
  page.on("pageerror", (e) => report.browserErrors.push(e.message));
  page.on("response", async (response) => {
    const path = new URL(response.url()).pathname;
    if (!path.startsWith("/api/v1/") || path.includes("/media/")) return;
    const value = await response.json().catch(() => ({}));
    report.backend.push({
      from: "browser",
      method: response.request().method(),
      path,
      status: response.status(),
      code: value.code,
      traceId: value.traceId,
    });
  });
  return page;
}
async function signIn(page, email, next) {
  await page.goto(base + "/account?next=" + encodeURIComponent(next));
  await fillLogin(page, email);
  await page.waitForURL((url) => url.hash === "#" + next);
}
async function fillLogin(page, email) {
  await page.locator("input[name=email]").fill(email);
  await page.locator("input[name=password]").fill(fixture.password);
  await page.getByRole("button", { name: "登录校园账号", exact: true }).click();
}
async function shot(page, name) {
  await page.screenshot({ path: resolve(evidenceDir, name + ".png") });
}
async function check(name, callback) {
  try {
    await callback();
    report.checks.push({ name, passed: true });
    console.log("PASS", name);
  } catch (error) {
    report.checks.push({ name, passed: false, error: error.message });
    console.error("FAIL", name, error.message);
  }
}
const button = (page, name) => page.getByRole("button", { name, exact: true });
const noOverflow = async (page) =>
  assert(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  );

try {
  author = await token(fixture.email);
  reader = await token(fixture.readerEmail);
  const full = await api("/articles/" + longId);
  const imageArticle = await api("/articles/" + imageId);
  const mediaPath = imageArticle.body.match(
    /\/api\/v1\/media\/images\/[a-f0-9-]{36}/,
  )?.[0];
  assert(mediaPath);
  const media = await fetch("http://127.0.0.1:8080" + mediaPath);
  assert(media.ok);
  const mimeType = media.headers.get("content-type").split(";")[0];
  const image = {
    name: mimeType === "image/png" ? "acceptance.png" : "acceptance.jpg",
    mimeType,
    buffer: Buffer.from(await media.arrayBuffer()),
  };

  await check("390px 真实岗位筛选、跳章焦点、收藏回读与返回滚动", async () => {
    const page = await newPage(390);
    const initial = await api(
      "/articles/" + longId + "/interaction-state",
      reader,
    );
    const selector = "#experience-" + longId + " h2 a";
    try {
      await signIn(page, fixture.readerEmail, "/");
      await button(page, "组合筛选").click();
      await page.getByRole("combobox", { name: /^目标岗位/ }).click();
      await page
        .getByRole("option", {
          name: full.categories.jobs[0].name,
          exact: true,
        })
        .click();
      await page
        .getByRole("list", { name: "已应用的条件" })
        .getByText(full.categories.jobs[0].name, { exact: false })
        .first()
        .waitFor();
      const card = page.locator(selector);
      await card.waitFor();
      await card.evaluate((el) => el.scrollIntoView({ block: "center" }));
      const top = await card.evaluate(
        (el) => el.closest("article").getBoundingClientRect().top,
      );
      const listing = page.url();
      await card.press("Enter");
      await page.locator(".reading-article h1").waitFor();
      await noOverflow(page);
      const toc = page.getByRole("navigation", { name: "文章目录" });
      await button(page, "目录").click();
      const section = toc.getByRole("button", {
        name: /投递前可执行的检查清单/,
      });
      const heading = (await section.innerText()).trim();
      await section.press("Enter");
      assert.equal(
        await page.evaluate(() => document.activeElement.textContent.trim()),
        heading,
      );
      const headingId = await page.evaluate(() => document.activeElement.id);
      const chapterOffset = await page.evaluate(() => window.scrollY);
      const favorite = button(
        page,
        initial.favorited ? "已收藏 · 取消" : "收藏，稍后阅读",
      );
      let submissions = 0;
      const recover = "**/api/v1/articles/" + longId + "/favorite";
      await page.route(recover, async (route) => {
        submissions++;
        const receipt = await route.fetch();
        const data = await receipt.json();
        report.backend.push({
          from: "withheld-response",
          method: route.request().method(),
          path: "/articles/" + longId + "/favorite",
          status: receipt.status(),
          code: data.code,
          traceId: data.traceId,
        });
        await route.abort("failed");
      });
      await favorite.evaluate((element) => {
        element.click();
        element.click();
      });
      await page
        .getByText(initial.favorited ? "已确认取消收藏。" : "已确认收藏。", {
          exact: true,
        })
        .waitFor();
      assert.equal(
        (await api("/articles/" + longId + "/interaction-state", reader))
          .favorited,
        !initial.favorited,
      );
      await page.unroute(recover);
      assert.equal(submissions, 1);
      assert(
        Math.abs((await page.evaluate(() => window.scrollY)) - chapterOffset) <
          40,
      );
      assert(
        await page
          .locator("#" + headingId)
          .evaluate(
            (el) =>
              el.getBoundingClientRect().top >=
              document.querySelector(".reading-actions").getBoundingClientRect()
                .bottom,
          ),
      );
      await button(page, "目录").click();
      await toc
        .locator('[aria-current="location"]')
        .filter({ hasText: heading })
        .waitFor();
      await noOverflow(page);
      const positions = await page.evaluate(() => {
        const box = (selector) =>
          document.querySelector(selector).getBoundingClientRect();
        return {
          actionBottom: box(".reading-actions").bottom,
          tocTop: box(".toc-trigger").top,
          tocBottom: box(".toc-panel").bottom,
          topButton: box(".back-to-top").top,
        };
      });
      assert(positions.actionBottom <= positions.tocTop);
      assert(positions.tocBottom <= positions.topButton);
      await shot(page, "after-reading-390");
      await page.keyboard.press("Escape");
      await page.locator(".breadcrumb a").click();
      await page.waitForURL(listing);
      await page.waitForFunction(
        (id) =>
          document.activeElement?.closest("article")?.id === "experience-" + id,
        longId,
      );
      assert(
        Math.abs(
          (await card.evaluate(
            (el) => el.closest("article").getBoundingClientRect().top,
          )) - top,
        ) <= 65,
      );
      await page.route("**/api/v1/articles?*", (route) =>
        route.abort("failed"),
      );
      await button(page, "最新发布").click();
      await page.getByText(/下方显示的是上次读取成功的结果/).waitFor();
      assert(await card.isVisible());
      await page.unroute("**/api/v1/articles?*");
      await button(page, "重试当前条件").click();
      await page.getByText(/找到 .* 篇经验/).waitFor();
      assert(new URL(page.url()).hash.includes("sort=latest"));
    } finally {
      await api(
        "/articles/" + longId + "/favorite",
        reader,
        initial.favorited ? "PUT" : "DELETE",
      );
      await page.close();
    }
  });

  await check("游客从目录登录并恢复原章节", async () => {
    const page = await newPage(390);
    try {
      await page.goto(base + "/articles/" + longId);
      await button(page, "目录").click();
      const toc = page.getByRole("navigation", { name: "文章目录" });
      await toc.getByRole("button", { name: /投递前可执行的检查清单/ }).click();
      const section = await page.evaluate(() => document.activeElement.id);
      await button(page, "目录").click();
      await button(page, "登录后继续阅读").click();
      await fillLogin(page, fixture.readerEmail);
      await page.waitForFunction(
        (id) => document.activeElement?.id === id,
        section,
      );
      assert(new URL(page.url()).hash.includes("reading=" + section));
      await noOverflow(page);
    } finally {
      await page.close();
    }
  });

  await check("图片位置错误反馈和真实资源重试", async () => {
    const page = await newPage(390);
    try {
      await page.route("**" + mediaPath, (route) => route.abort("failed"), {
        times: 1,
      });
      await page.goto(base + "/articles/" + imageId);
      const image = page.locator(
        '.markdown-image img[src="' + mediaPath + '"]',
      );
      await image
        .first()
        .evaluate((el) => el.parentElement.scrollIntoView({ block: "center" }));
      const retry = image
        .first()
        .locator("..")
        .getByRole("button", { name: "重试图片" });
      await retry.waitFor();
      await shot(page, "after-image-error-390");
      await retry.click();
      await page.waitForFunction(
        (path) =>
          [...document.images].some(
            (img) => img.getAttribute("src") === path && img.naturalWidth > 0,
          ),
        mediaPath,
      );
      assert(!(await retry.isVisible()));
      await noOverflow(page);
    } finally {
      await page.close();
    }
  });

  await check("评论真实提交、键盘取消删除和删除回执", async () => {
    const page = await newPage();
    let commentId;
    try {
      await signIn(page, fixture.readerEmail, "/articles/" + longId);
      const name = "用于网页交互验收的临时评论";
      await page.locator("#comment-body").fill(name);
      const sent = page.waitForResponse(
        (res) =>
          res.url().endsWith("/articles/" + longId + "/comments") &&
          res.request().method() === "POST",
      );
      await button(page, "提交评论").click();
      const receipt = await (await sent).json();
      assert.equal(receipt.code, "OK");
      commentId = receipt.data.rootCommentId;
      const comment = page.locator("#comment-" + commentId);
      await comment.getByText(name, { exact: true }).waitFor();
      const remove = button(comment, "删除本人评论");
      await remove.press("Enter");
      const dialog = page.getByRole("dialog", {
        name: "删除评论 " + commentId + "？",
      });
      await dialog.waitFor();
      await page.keyboard.press("Escape");
      await dialog.waitFor({ state: "hidden" });
      assert.equal(
        await page.evaluate(() => document.activeElement.textContent.trim()),
        "删除本人评论",
      );
      await remove.click();
      await button(dialog, "删除评论").click();
      await page
        .getByText("已删除评论 " + commentId + "。", { exact: true })
        .waitFor();
      await comment
        .getByText(name, { exact: true })
        .waitFor({ state: "hidden" });
      commentId = undefined;
    } finally {
      if (commentId) await api("/comments/" + commentId, reader, "DELETE");
      await page.close();
    }
  });

  await check("通知刷新失败保留旧通知和未读数，重试与已读回执", async () => {
    const page = await newPage();
    try {
      await signIn(page, fixture.readerEmail, "/me/notifications");
      const rows = page.locator(".notification-row");
      await rows.first().waitFor();
      const before = await rows.allTextContents();
      let count = await page.locator(".page-heading p").innerText();
      await page.route(
        "**/api/v1/me/notifications?*",
        (route) => route.abort("failed"),
        { times: 1 },
      );
      await button(page, "刷新通知").click();
      await page
        .getByText("当前显示的是上次读取结果。", { exact: true })
        .waitFor();
      assert.deepEqual(await rows.allTextContents(), before);
      assert.equal(await page.locator(".page-heading p").innerText(), count);
      await shot(page, "after-notifications-error-1440");
      await button(page, "重试读取").click();
      await page
        .getByText("当前显示的是上次读取结果。", { exact: true })
        .waitFor({ state: "hidden" });
      const unreadRow = page.locator(".notification-unread").first();
      if (await unreadRow.count()) {
        await page.route(
          "**/api/v1/me/notifications/*/read",
          (route) => route.abort("failed"),
          { times: 1 },
        );
        await button(unreadRow, "标记已读").click();
        await unreadRow.getByText(/标记结果待确认/).waitFor();
        const mark = page
          .locator(".notification-row")
          .filter({ has: page.getByText(/标记结果待确认/) });
        await button(mark, "标记已读").click();
        await page
          .getByText("已标记为已读。", { exact: true })
          .first()
          .waitFor();
        const receipt = await api("/me/notifications?page=1&size=20", reader);
        count = receipt.unreadCount + " 条未读";
        await page.getByText(count, { exact: true }).waitFor();
      }
    } finally {
      await page.close();
    }
  });

  await check("空草稿缺项定位、图片回执丢失时续写、同一请求重试", async () => {
    article = (
      await api(
        "/me/articles",
        author,
        "POST",
        { requestId: randomUUID() },
        201,
      )
    ).articleId;
    const page = await newPage(390);
    try {
      await signIn(page, fixture.email, "/editor/" + article);
      await button(page, "预览并发布").click();
      const checklist = page.getByRole("region", { name: "发布前检查" });
      const all = await checklist.getByRole("button").allTextContents();
      assert(all.length >= 5);
      for (const label of all) {
        await button(checklist, label.trim()).click();
        assert(
          await page.evaluate(
            () =>
              document.activeElement.id?.startsWith("draft-") ||
              document.activeElement.closest('fieldset[id^="draft-"]'),
          ),
        );
      }
      await checklist.scrollIntoViewIfNeeded();
      await shot(page, "after-editor-checklist-390");
      const body = page.locator("#draft-body");
      await body.fill("## 网页验收\n\n上传前写入");
      let firstKey, secondKey, resolveArrival, release;
      const reached = new Promise((done) => {
        resolveArrival = done;
      });
      const held = new Promise((done) => {
        release = done;
      });
      const upload = "**/api/v1/me/articles/" + article + "/images";
      const routeHandler = async (route) => {
        const key = route
          .request()
          .postDataBuffer()
          .toString("latin1")
          .match(/name="requestId"\r\n\r\n([a-f0-9-]+)/)?.[1];
        if (!firstKey) {
          firstKey = key;
          resolveArrival();
          await held;
          const response = await route.fetch();
          const data = await response.json();
          report.backend.push({
            from: "withheld-response",
            path: "/me/articles/" + article + "/images",
            method: "POST",
            status: response.status(),
            code: data.code,
            traceId: data.traceId,
          });
          await route.abort("failed");
        } else {
          secondKey = key;
          await route.continue();
        }
      };
      await page.route(upload, routeHandler);
      try {
        await page.locator('input[type="file"]').setInputFiles(image);
        await Promise.race([
          reached,
          new Promise((_, reject) =>
            setTimeout(() => reject(new Error("上传请求未发出")), 9000),
          ),
        ]);
        assert(await body.isEnabled());
        await body.fill("## 网页验收\n\n上传中继续输入，本句必须保留。");
      } finally {
        release();
      }
      await button(page, "重试这张图片").click();
      await page.getByText(/图片已添加到当前正文末尾/).waitFor();
      assert(firstKey && firstKey === secondKey);
      assert.match(
        await body.inputValue(),
        /上传中继续输入，本句必须保留。.*\n+!\[/,
      );
      assert.equal(
        (await body.inputValue()).match(/\/api\/v1\/media\/images/g).length,
        1,
      );
      await page.unroute(upload, routeHandler);
      const leave = page.locator(".breadcrumb a");
      await leave.press("Enter");
      const leaveDialog = page.getByRole("dialog", { name: "离开创作页？" });
      await leaveDialog.waitFor();
      assert.equal(await page.getByRole("dialog").count(), 1);
      await page.keyboard.press("Escape");
      await leaveDialog.waitFor({ state: "hidden" });
      assert.match(await body.inputValue(), /上传中继续输入/);
      await body.evaluate((el) => el.setSelectionRange(4, 8));
      await button(page, "看预览").click();
      await button(page, "写正文").click();
      assert.deepEqual(
        await body.evaluate((el) => [el.selectionStart, el.selectionEnd]),
        [4, 8],
      );
      await button(page, "保存草稿").click();
      await page.getByText("草稿已保存到服务器。", { exact: true }).waitFor();
    } finally {
      await page.close();
    }
  });

  await check(
    "真实 409 保留当前正文、新输入与图片，合并后真实发布",
    async () => {
      assert(article, "缺少上一项的草稿");
      const page = await newPage();
      try {
        await signIn(page, fixture.email, "/editor/" + article);
        const body = page.locator("#draft-body");
        await body.waitFor();
        const original = await api(
          "/me/articles/" + article + "/draft",
          author,
        );
        const local = (await body.inputValue()) + "\n\n冲突前本机新输入";
        await body.fill(local);
        const { tags, ...content } = original.content;
        await api("/me/articles/" + article + "/draft", author, "PUT", {
          ...content,
          ...tags,
          title: "另一标签页已保存的标题",
          expectedRevision: original.revision,
        });
        const response = page.waitForResponse(
          (res) =>
            res.status() === 409 &&
            res.url().endsWith("/draft") &&
            res.request().method() === "PUT",
        );
        await button(page, "保存草稿").click();
        assert.equal((await (await response).json()).code, "REVISION_CONFLICT");
        await page
          .getByRole("heading", { name: "草稿版本有冲突，请手动合并" })
          .waitFor();
        assert.equal(await body.inputValue(), local);
        assert(local.includes("/api/v1/media/images/"));
        assert.equal(
          await page
            .locator(".compare-grid dt")
            .filter({ hasText: "摘要" })
            .count(),
          0,
        );
        await button(page, "查看全部字段").click();
        assert.equal(
          await page
            .locator(".compare-grid dt")
            .filter({ hasText: "摘要" })
            .count(),
          2,
        );
        await button(page, "只看差异").click();
        await shot(page, "after-editor-conflict-1440");
        const useServer = button(page, "使用服务器版本");
        await useServer.press("Enter");
        const confirm = page.getByRole("dialog", { name: "使用服务器版本？" });
        await confirm.waitFor();
        assert.equal(
          await page.evaluate(() => document.activeElement.textContent.trim()),
          "取消",
        );
        await page.keyboard.press("Escape");
        await confirm.waitFor({ state: "hidden" });
        assert.equal(
          await page.evaluate(() => document.activeElement.textContent.trim()),
          "使用服务器版本",
        );
        assert.equal(await body.inputValue(), local);
        await button(page, "保留本机内容，按新修订手动合并").click();
        await page.locator("#draft-title").fill("第三轮交互验收临时演示文章");
        await page.locator("#draft-jobIds input").first().check();
        await page.locator("#draft-majorIds input").first().check();
        await page.locator("#draft-experienceStage").fill("大三");
        await page.locator("#draft-sourceType").selectOption("DEMO");
        await button(page, "预览并发布").click();
        await page.getByRole("dialog", { name: "确认公开这篇经验" }).waitFor();
        await button(page, "确认发布").click();
        await page.getByText(/发布回执已确认/).waitFor();
        const published = await api("/articles/" + article);
        assert.equal(published.body, local.trimEnd());
        await shot(page, "after-editor-published-1440");
      } finally {
        await page.close();
      }
    },
  );

  await check("工作区键盘取消、下架恢复和文章终态", async () => {
    assert(article, "缺少上一项的文章");
    const page = await newPage();
    try {
      await signIn(page, fixture.email, "/workspace");
      const row = page
        .locator(".workspace-card")
        .filter({ has: page.locator("#work-title-" + article) });
      const offline = button(row, "下架");
      await offline.press("Enter");
      await page.getByRole("dialog").waitFor();
      await page.keyboard.press("Escape");
      await page.getByRole("dialog").waitFor({ state: "hidden" });
      assert.equal(
        await page.evaluate(() => document.activeElement.textContent.trim()),
        "下架",
      );
      await offline.click();
      await shot(page, "after-workspace-confirm-1440");
      await button(page, "确认下架").click();
      await row.getByText("当前状态为作者已下架。", { exact: true }).waitFor();
      await api("/articles/" + article, null, "GET", undefined, 404);
      await button(row, "恢复上次公开版本").click();
      await button(page, "确认恢复上次公开版本").click();
      await row.getByText("当前状态为已发布。", { exact: true }).waitFor();
      await api("/articles/" + article);
    } finally {
      await page.close();
    }
  });

  await check("个人资料与联系设置独立保存和回读", async () => {
    const page = await newPage();
    const profile = await api("/me/profile", reader);
    const contact = await api("/me/contact-settings", reader);
    try {
      await signIn(page, fixture.readerEmail, "/me");
      await page.getByLabel("昵称").fill("第三轮验收读者");
      const profileSave = button(page, "保存资料");
      const contactSave = button(page, "保存联系设置");
      await profileSave.click();
      await page.getByText("个人资料已保存。", { exact: true }).waitFor();
      await page.getByLabel("联系方式类型").selectOption("QQ");
      await page.getByLabel("联系信息").fill("12345678");
      await page.getByLabel("向已认证用户开放联系入口").uncheck();
      await contactSave.click();
      await page.getByText("联系设置已保存。", { exact: true }).waitFor();
      await button(page, "重新读取个人资料").click();
      await page.getByText("已重新读取个人资料。", { exact: true }).waitFor();
      await button(page, "重新读取联系设置").click();
      await page.getByText("已重新读取联系设置。", { exact: true }).waitFor();
      assert.equal(
        await page.getByLabel("昵称").inputValue(),
        "第三轮验收读者",
      );
      assert.equal(await page.getByLabel("联系信息").inputValue(), "12345678");
      assert.equal((await api("/me/contact-settings", reader)).enabled, false);
      assert.deepEqual(
        await page.locator(".profile-vertical h2").allTextContents(),
        ["个人资料", "联系设置", "我的硬币"],
      );
      await shot(page, "after-profile-1440");
    } finally {
      for (const [path, snapshot] of [
        ["/me/profile", profile],
        ["/me/contact-settings", contact],
      ]) {
        const { revision } = await api(path, reader);
        const { revision: oldRevision, ...values } = snapshot;
        if (revision !== oldRevision)
          await api(path, reader, "PUT", {
            ...values,
            expectedRevision: revision,
          });
      }
      await page.close();
    }
  });

  await check("反馈举报列表、处理记录、申诉与认证撤销答复", async () => {
    const page = await newPage(390);
    try {
      await signIn(page, fixture.readerEmail, "/me/cases");
      const records = await api("/me/cases?page=1&size=20", reader);
      const answered =
        records.items.find(
          (item) => item.state === "ANSWERED" || item.state === "RESOLVED",
        ) || records.items[0];
      assert(answered);
      await page
        .locator('a[href="#/me/cases/' + answered.caseId + '"]')
        .click();
      await page.getByRole("heading", { name: "处理记录" }).waitFor();
      assert(await page.locator(".record-timeline li").count());
      await noOverflow(page);
      const timeline = page.locator(".record-timeline");
      const timelineBefore = await timeline.textContent();
      await page.route(
        "**/api/v1/me/cases/" + answered.caseId,
        (route) => route.abort("failed"),
        { times: 1 },
      );
      await button(page, "重新读取").click();
      await page
        .getByText("当前显示的是上次读取结果。", { exact: true })
        .waitFor();
      assert.equal(await timeline.textContent(), timelineBefore);
      const reread = page.waitForResponse(
        (res) => res.url().endsWith("/me/cases/" + answered.caseId) && res.ok(),
      );
      await button(page, "重试读取").click();
      await reread;
      await page
        .getByText("当前显示的是上次读取结果。", { exact: true })
        .waitFor({ state: "hidden" });
      await shot(page, "after-case-timeline-390");
      const appealResponse = page.waitForResponse(
        (res) => res.url().includes("/me/appeals?") && res.ok(),
      );
      await page.goto(base + "/me/appeals");
      await page.getByRole("heading", { name: "申诉记录" }).waitFor();
      await appealResponse;
      await noOverflow(page);
      await page.close();
      const limited = await newPage(390);
      try {
        await signIn(limited, fixture.candidateEmail, "/me/certification");
        await limited.getByText(/撤销理由/).waitFor();
        await limited.getByRole("link", { name: "查看本次认证申诉" }).click();
        await limited
          .getByText(/复核答复|申诉已驳回|申诉已接受/)
          .first()
          .waitFor();
        await shot(limited, "after-certification-appeal-390");
        await noOverflow(limited);
      } finally {
        await limited.close();
      }
    } finally {
      if (!page.isClosed()) await page.close();
    }
  });
} finally {
  try {
    if (author && article) {
      const draft = await api("/me/articles/" + article + "/draft", author);
      await api(
        "/me/articles/" + article + "?expectedRevision=" + draft.revision,
        author,
        "DELETE",
      );
    }
  } finally {
    await browser.close();
    await writeFile(
      resolve(evidenceDir, "web-ux-results.json"),
      JSON.stringify(report, null, 2),
    );
  }
}
assert(
  report.checks.every((check) => check.passed),
  "One or more acceptance scenarios failed.",
);
assert.deepEqual(report.browserErrors, []);
