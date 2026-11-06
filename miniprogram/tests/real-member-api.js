// Local, explicitly invoked integration. Never sends mail, changes passwords or profile/contact settings.
// Creates only clearly marked DEMO fixtures; leaves the created article AUTHOR_OFFLINE.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const backendRoot = process.argv[2];
if (!backendRoot || !path.isAbsolute(backendRoot)) throw new Error('Provide the existing backend server directory');
const base = 'http://127.0.0.1:8080';
const storage = new Map();
const evidence = [];
const tempFiles = new Set();
const temporaryRoot = path.resolve(__dirname, '../../tmp');
fs.mkdirSync(temporaryRoot, { recursive: true });
function response(statusCode, data) { return { statusCode, header: {}, data }; }
global.wx = {
  getStorageSync: (k) => storage.get(k), setStorageSync: (k, v) => storage.set(k, v), removeStorageSync: (k) => storage.delete(k), getStorageInfoSync: () => ({ keys: [...storage.keys()] }),
  request(o) {
    const abort = new AbortController(); const method = o.method || 'GET';
    const url = new URL(o.url); if (method === 'GET' && o.data) Object.entries(o.data).forEach(([k, v]) => url.searchParams.set(k, String(v)));
    const input = { method, headers: o.header, signal: abort.signal }; if (method !== 'GET' && Object.prototype.hasOwnProperty.call(o, 'data')) input.body = JSON.stringify(o.data);
    fetch(url, input).then(async (r) => { const body = await r.json(); evidence.push({ method, path: url.pathname, status: r.status, code: body.code, traceId: body.traceId }); o.success(response(r.status, body)); }).catch(() => o.fail({ errMsg: 'request:fail local connection' })); return { abort: () => abort.abort() };
  },
  uploadFile(o) {
    const abort = new AbortController(); const data = new FormData(); data.append('requestId', o.formData.requestId); data.append('file', new Blob([fs.readFileSync(o.filePath)], { type: 'image/png' }), 'frontend-fixture.png');
    fetch(o.url, { method: 'POST', headers: o.header, body: data, signal: abort.signal }).then(async (r) => { const payload = await r.text(); const body = JSON.parse(payload); evidence.push({ method: 'POST', path: new URL(o.url).pathname, status: r.status, code: body.code, traceId: body.traceId }); o.success(response(r.status, payload)); }).catch(() => o.fail({ errMsg: 'uploadFile:fail' })).finally(() => o.complete()); return { abort: () => abort.abort() };
  },
  downloadFile(o) {
    const abort = new AbortController(); fetch(o.url, { headers: o.header, signal: abort.signal }).then(async (r) => { const file = path.join(temporaryRoot, crypto.randomUUID() + '.download'); fs.writeFileSync(file, Buffer.from(await r.arrayBuffer())); tempFiles.add(file); evidence.push({ method: 'GET', path: new URL(o.url).pathname, status: r.status, binary: true }); o.success({ statusCode: r.status, tempFilePath: file }); }).catch(() => o.fail({ errMsg: 'downloadFile:fail' })).finally(() => o.complete()); return { abort: () => abort.abort() };
  },
  getFileSystemManager: () => ({ unlink(o) { if (tempFiles.has(o.filePath)) { try { fs.unlinkSync(o.filePath); } catch (_) {} tempFiles.delete(o.filePath); } } })
};
const config = require('../config/env');
config.apiBaseUrl = base; config.demoMode = false; config.debug = false;
const account = require('../services/account');
const api = require('../services/member');
const media = require('../services/media');
const session = require('../services/session');
const checks = [];
let articleId, createdComment, replyComment, articleRevision, completed = false;
async function check(name, run) { await run(); checks.push({ name, result: 'PASS' }); }
async function raw(pathname, status) { const r = await fetch(base + pathname); assert.equal(r.status, status); return r; }
const digest = (value) => crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
async function main() {
  const password = JSON.parse(fs.readFileSync(path.join(backendRoot, '.env.account-test.json'), 'utf8')).password;
  const script = fs.readFileSync(path.join(backendRoot, 'scripts/Verify-Authoring.ps1'), 'utf8');
  const matched = /\$email\s*=\s*'([^']+)'/.exec(script); if (!matched || !password) throw new Error('Existing local test credentials unavailable');
  const me = await account.login(matched[1], password); assert.equal(me.role, 'USER');
  const profileBefore = digest(await api.profile()); const contactBefore = digest(await api.contactSettings());
  await check('前端登录适配器与/me核验成功', async () => { assert.equal(session.userId(), me.userId); });
  await check('互动无正文，空对象会被真实后端拒绝', async () => { const { request } = require('../services/request'); await assert.rejects(request({ path: '/api/v1/articles/1001/like', auth: true, method: 'PUT', data: {} }).promise, (e) => e.status === 400); });
  const createKey = crypto.randomUUID(); articleId = (await api.create(createKey)).articleId;
  await check('相同创建键不重复创建草稿', async () => { assert.equal((await api.create(createKey)).articleId, articleId); });
  const draft = await api.draft(articleId); const catalog = await api.call('/catalog');
  let form = { expectedRevision: draft.revision, title: '【DEMO】原生前端接管联调 2026-11-06', summary: '前端真实服务适配器的本机验证内容，非真实求职经历。', body: '# 联调正文\n\n验证原生前端请求合同，不代表经历已核验。', jobIds: [catalog.jobs[0].id], majorIds: [catalog.majors[0].id], companyIds: [], topicIds: [], experienceStage: 'DEMO接口验证阶段', sourceType: 'DEMO', sourceNote: '本机Java/MySQL接口验证，无真实亲历声明。', sourceLinks: [] };
  let saved = await api.saveDraft(articleId, form); articleRevision = saved.revision;
  await check('旧revision不会覆盖较新草稿', async () => { await assert.rejects(api.saveDraft(articleId, form), (e) => e.status === 409 && e.code === 'REVISION_CONFLICT'); assert.equal((await api.draft(articleId)).content.body, form.body); });
  const imageFile = path.join(backendRoot, 'target/authoring-fixture.png'); if (!fs.existsSync(imageFile)) throw new Error('Existing PNG fixture unavailable');
  const uploadKey = crypto.randomUUID(); const image = await api.uploadArticle(articleId, imageFile, uploadKey);
  await check('私有图片真实上传、同键重试及带鉴权下载', async () => { assert.equal((await api.uploadArticle(articleId, imageFile, uploadKey)).imageId, image.imageId); await raw(image.url, 404); const file = await media.download(image.url); assert.equal(crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'), crypto.createHash('sha256').update(fs.readFileSync(imageFile)).digest('hex')); media.release(file); });
  form = { ...form, expectedRevision: saved.revision, body: form.body + `\n\n![联调图片](${image.url})` }; saved = await api.saveDraft(articleId, form); articleRevision = saved.revision;
  const publish = { expectedRevision: saved.revision, requestId: crypto.randomUUID() }; const receipt = await api.publish(articleId, publish); articleRevision = receipt.revision;
  await check('同一发布键返回原公开版本且正文图片公开可读', async () => { assert.equal((await api.publish(articleId, publish)).versionId, receipt.versionId); const r = await raw(`/api/v1/articles/${articleId}`, 200); const detail = (await r.json()).data; assert.equal(detail.body, form.body); assert.equal('author' in detail, false); await raw(image.url, 200); });
  await check('点赞与收藏真实设置、读取和取消', async () => { assert.equal((await api.like(articleId, true)).liked, true); assert.equal((await api.favorite(articleId, true)).favorited, true); const state = await api.interaction(articleId); assert.equal(state.liked, true); assert.equal(state.favorited, true); assert.equal((await api.like(articleId, false)).liked, false); });
  await check('作者主页与联系开关遵守当前合同', async () => { const author = await api.articleAuthor(articleId); assert.equal(author.authorId, me.userId); const profile = await api.author(author.authorId); assert.equal('email' in profile, false); const contact = await api.authorContact(author.authorId); if (!contact.enabled) assert.deepEqual(Object.keys(contact), ['enabled']); assert.ok((await api.authorArticles(author.authorId, 1)).items.some((a) => a.id === articleId)); });
  const commentImageKey = crypto.randomUUID(); const commentImage = await api.uploadComment(articleId, imageFile, commentImageKey);
  const comment = { requestId: crypto.randomUUID(), body: '<script>按普通文字保留</script>\n真实接口联调评论', imageIds: [commentImage.imageId], replyToCommentId: null };
  const commentReceipt = await api.comment(articleId, comment); createdComment = commentReceipt.commentId;
  await check('评论与图片、同键回执和直接回复', async () => { assert.equal((await api.comment(articleId, comment)).commentId, createdComment); const rows = await api.comments(articleId, 1); assert.equal(rows.items.find((r) => r.commentId === createdComment).body, comment.body); const file = await media.download(commentImage.url); media.release(file); const reply = await api.comment(articleId, { requestId: crypto.randomUUID(), body: '联调回复', imageIds: [], replyToCommentId: createdComment }); replyComment = reply.commentId; assert.equal((await api.replies(createdComment, 1)).items.some((r) => r.commentId === replyComment), true); });
  await check('本人评论删除后不泄露正文，正常回复保留', async () => { await api.deleteComment(createdComment); createdComment = null; const thread = await api.replies(commentReceipt.rootCommentId, 1); assert.equal(thread.root.available, false); assert.equal('body' in thread.root, false); assert.equal(thread.items.some((r) => r.commentId === replyComment), true); await api.deleteComment(replyComment); replyComment = null; });
  await check('钱包、账目快照、奖励及阅读计时真实可读', async () => { const wallet = await api.wallet(); assert.ok(wallet.balance >= 0); const ledger = await api.ledger(1); assert.equal((await api.ledger(2, ledger.snapshot)).snapshot, ledger.snapshot); const rewards = await api.rewards(); assert.ok(rewards.rules.readingSeconds > 0); const reading = await api.startReading(articleId); assert.equal(reading.articleId, articleId); assert.equal((await api.startReading(articleId)).sessionId, reading.sessionId); });
  await check('本人文章和无真实作者演示文投币边界', async () => { assert.equal((await api.coinState(articleId)).reason, 'SELF_ARTICLE'); assert.equal((await api.coinState(1001)).reason, 'NO_REAL_AUTHOR'); await assert.rejects(api.coin(articleId, 1, crypto.randomUUID()), (e) => e.status === 409 && e.code === 'SELF_ARTICLE'); });
  const offline = await api.articleState(articleId, 'unpublish', articleRevision); articleRevision = offline.revision;
  await check('下架后收藏只留允许字段，仍可无正文取消', async () => { await raw(`/api/v1/articles/${articleId}`, 404); const favorites = await api.favorites(1); const row = favorites.items.find((r) => r.articleId === articleId); assert.deepEqual(Object.keys(row).sort(), ['articleId', 'available']); assert.equal(row.available, false); assert.equal((await api.favorite(articleId, false)).favorited, false); await raw(image.url, 404); });
  const restored = await api.articleState(articleId, 'restore', articleRevision); articleRevision = restored.revision;
  await check('恢复公开版本不会发布未保存内容', async () => { const r = await raw(`/api/v1/articles/${articleId}`, 200); assert.equal((await r.json()).data.body, form.body); });
  const final = await api.articleState(articleId, 'unpublish', articleRevision); articleRevision = final.revision;
  await check('联调保留原资料和联系设置，测试文章最终下架', async () => { assert.equal(digest(await api.profile()), profileBefore); assert.equal(digest(await api.contactSettings()), contactBefore); assert.equal(final.state, 'AUTHOR_OFFLINE'); });
  const oldToken = session.token(); await account.logout();
  await check('退出真实撤销令牌，端侧会话清空', async () => { assert.equal(session.token(), ''); const r = await fetch(base + '/api/v1/me', { headers: { Authorization: 'Bearer ' + oldToken } }); assert.equal(r.status, 401); });
  completed = true;
}
main().catch(() => { process.exitCode = 1; }).finally(async () => {
  if (!completed && session.token()) {
    try { if (createdComment) await api.deleteComment(createdComment); if (replyComment) await api.deleteComment(replyComment); if (articleId) { const draft = await api.draft(articleId); if (draft.state === 'PUBLISHED') await api.articleState(articleId, 'unpublish', draft.revision); await api.favorite(articleId, false); } } catch (_) { /* Report incomplete cleanup without private payloads. */ }
    try { await account.logout(); } catch (_) { session.clear('logout'); }
  }
  media.clear();
  const result = { checkedAt: new Date().toISOString(), status: completed ? 'PASS' : 'FAIL', base, fixtureArticleId: articleId, fixtureFinalState: completed ? 'AUTHOR_OFFLINE' : 'CHECK_REQUIRED', checks, evidence, notExecuted: ['真实验证码邮件与注册/重置', '第二账号成功投币', '微信原生上传与下载行为', '手机真机'], notes: ['使用既有本机账号与私密密码，不输出凭据。', '未修改个人资料、联系设置或原有文章。', '未领取奖励；阅读会话只建立服务端计时，不自动发币。', 'Node wx桥用于执行实际前端service适配器；不代表模拟器或真机验收。'] };
  fs.writeFileSync(path.resolve(__dirname, '../docs/REAL-MEMBER-API-20261106.json'), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ status: result.status, passed: checks.length, requests: evidence.length, fixtureArticleId: articleId, fixtureFinalState: result.fixtureFinalState, credentials: 'omitted' }));
});
