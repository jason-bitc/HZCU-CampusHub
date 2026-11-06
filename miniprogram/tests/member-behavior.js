const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const root = path.resolve(__dirname, '..');
function load(relative, mocks = {}, globals = {}) { const file = path.join(root, relative); const native = createRequire(file); const context = { module: { exports: {} }, require: (name) => name in mocks ? mocks[name] : native(name), console, setTimeout, clearTimeout, setInterval, clearInterval, ...globals }; vm.runInNewContext(fs.readFileSync(file, 'utf8'), context, { filename: file }); return context.module.exports; }
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }
const tick = () => new Promise((resolve) => setImmediate(resolve));
const login = () => ({ tokenType: 'Bearer', accessToken: 'test-only-opaque-token-not-a-real-session', expiresAt: new Date(Date.now() + 3600000).toISOString() });
function environment() { const values = new Map(); const wx = { getStorageSync: (k) => values.get(k), setStorageSync: (k, v) => values.set(k, v), removeStorageSync: (k) => values.delete(k), getStorageInfoSync: () => ({ keys: [...values.keys()] }) }; const config = { apiBaseUrl: 'http://127.0.0.1:8080', requestTimeout: 1000 }; const session = load('services/session.js', { '../config/env': config }, { wx }); return { values, wx, config, session }; }
function page(relative, mocks, wx = {}) { let definition; load(relative, mocks, { wx: { setNavigationBarTitle() {}, ...wx }, Page: (v) => { definition = v; } }); const p = { ...definition, data: structuredClone(definition.data) }; p.setData = (patch, cb) => { for (const [key, value] of Object.entries(patch)) { const parts = key.replace(/\[(\d+)\]/g, '.$1').split('.'); let obj = p.data; parts.slice(0, -1).forEach((part) => { obj = obj[part]; }); obj[parts.at(-1)] = value; } if (cb) cb(); }; return p; }
const results = [];
async function test(name, run) { try { await run(); results.push({ name, pass: true }); } catch (e) { results.push({ name, pass: false, error: e.stack }); } }
(async () => {
  await test('Bearer只发给明确的私有请求，无正文写入不产生data字段', async () => {
    const e = environment(); e.session.accept(login()); const sent = [];
    e.wx.request = (o) => { sent.push(o); o.success({ statusCode: 200, header: {}, data: { code: 'OK', data: {} } }); return { abort() {} }; };
    const transport = load('services/request.js', { '../config/env': e.config, './session': e.session }, { wx: e.wx });
    await transport.request({ path: '/api/v1/articles' }).promise;
    for (const method of ['PUT', 'DELETE', 'POST']) await transport.request({ path: '/api/v1/private', method, auth: true }).promise;
    assert.equal('Authorization' in sent[0].header, false);
    for (const o of sent.slice(1)) { assert.match(o.header.Authorization, /^Bearer /); assert.equal('data' in o, false); assert.equal(o.url.includes('token'), false); }
  });
  await test('退出使在途私有响应失效，旧401不能注销新登录', async () => {
    const e = environment(); e.session.accept(login()); const sent = [];
    e.wx.request = (o) => { sent.push(o); return { abort() {} }; };
    const transport = load('services/request.js', { '../config/env': e.config, './session': e.session }, { wx: e.wx });
    const first = transport.request({ path: '/api/v1/me', auth: true }).promise;
    e.session.clear('logout'); e.session.accept(login());
    sent[0].success({ statusCode: 401, header: {}, data: { code: 'UNAUTHORIZED' } });
    await assert.rejects(first, (error) => error.kind === 'ABORTED'); assert.ok(e.session.token());
    const second = transport.request({ path: '/api/v1/me', auth: true }).promise;
    sent[1].success({ statusCode: 403, header: {}, data: { code: 'ACCESS_RESTRICTED' } });
    await assert.rejects(second, (error) => error.status === 403); assert.equal(e.session.token(), '');
  });
  await test('会话恢复核验有效期，显式退出清除私有副本但保留无关缓存', () => {
    const e = environment(); const operations = load('utils/operation.js', { '../services/session': e.session, '../config/env': e.config }, { wx: e.wx }); e.session.accept(login()); e.session.identify({ userId: 10, role: 'USER', accountState: 'ACTIVE', campusCertification: 'VALID' });
    operations.write('draft:1', { body: 'private' }); e.values.set('unrelated', 1); e.session.clear('logout'); assert.equal(e.values.size, 1); assert.equal(e.values.get('unrelated'), 1);
    e.values.set('campushub.session.v1:' + e.config.apiBaseUrl, { accessToken: 'x'.repeat(30), expiresAt: '2000-01-01T00:00:00Z' }); e.session.restore(); assert.equal(e.session.token(), '');
  });
  await test('私有图片仅同源media附带鉴权，退出删除临时资源且丢弃晚到下载', async () => {
    const e = environment(); e.session.accept(login()); const calls = [], removed = [];
    e.wx.getFileSystemManager = () => ({ unlink: (o) => removed.push(o.filePath) }); e.wx.downloadFile = (o) => { calls.push(o); return { abort() {} }; };
    const media = load('services/media.js', { '../config/env': e.config, './session': e.session }, { wx: e.wx });
    await assert.rejects(media.download('https://other.test/api/v1/media/images/a'));
    await assert.rejects(media.download('/api/v1/media/images/a?token=x'));
    const first = media.download('/api/v1/media/images/a'); assert.equal(calls[0].url, e.config.apiBaseUrl + '/api/v1/media/images/a'); assert.match(calls[0].header.Authorization, /^Bearer /);
    calls[0].success({ statusCode: 200, tempFilePath: '/tmp/a' }); assert.equal(await first, '/tmp/a');
    const second = media.download('/api/v1/media/images/b'); e.session.clear('logout'); calls[1].success({ statusCode: 200, tempFilePath: '/tmp/b' }); await assert.rejects(second, (error) => error.kind === 'ABORTED'); assert.ok(removed.includes('/tmp/a')); assert.ok(removed.includes('/tmp/b'));
  });
  await test('无正文互动服务方法、不可用收藏和评论占位严格裁剪', async () => {
    const calls = [];
    const api = load('services/member.js', { './request': { request: (o) => { calls.push(o); return { promise: Promise.resolve({ items: [{ articleId: 9, available: false, article: { title: 'must not leak' } }] }) }; } } });
    await api.like(1, true); await api.favorite(1, false); await api.startReading(1); assert.equal(calls[0].method, 'PUT'); assert.equal(calls[1].method, 'DELETE'); assert.ok(calls.every((o) => !('data' in o)));
    assert.deepEqual(JSON.parse(JSON.stringify((await api.favorites(1)).items[0])), { articleId: 9, available: false });
    const { mapComment } = require('../utils/comment-view'); const row = mapComment({ commentId: 1, available: false, body: 'secret', commenter: { nickname: 'secret' }, images: ['secret'] }); assert.equal('body' in row, false); assert.equal('commenter' in row, false); assert.equal('images' in row, false);
  });
  await test('注册成功不会调用登录或创建会话，密码重置只回登录表单', async () => {
    let loginCalls = 0, clears = 0; const account = { complete: async () => ({}), login: async () => { loginCalls += 1; } };
    const p = page('pages/account/account.js', { '../../services/account': account, '../../services/session': { clear: () => { clears += 1; }, subscribe: () => () => {} } }); p.onLoad({ mode: 'register' }); p.setData({ email: 'test@stu.hzcu.edu.cn', password: 'abcdefgh', confirmation: 'abcdefgh', code: '123456', challenge: { challengeId: 'id' } }); await p.submit(); assert.equal(loginCalls, 0); assert.equal(p.data.mode, 'login'); assert.match(p.data.notice, /注册成功/);
    p.changeMode('reset'); p.setData({ password: 'abcdefgh', confirmation: 'abcdefgh', code: '123456', challenge: { challengeId: 'id' } }); await p.submit(); assert.equal(loginCalls, 0); assert.equal(clears, 1);
  });
  await test('验证码发送失败保留旧挑战，未发送成功不显示成功状态', async () => {
    const p = page('pages/account/account.js', { '../../services/account': { sendCode: async () => { throw new Error('MAIL_UNAVAILABLE'); } } }); p.onLoad({ mode: 'register' }); const old = { challengeId: 'old', resendAt: '2000-01-01', expiresAt: '2099-01-01' }; p.setData({ email: 'test@stu.hzcu.edu.cn', challenge: old }); await p.sendCode(); assert.equal(p.data.challenge.challengeId, 'old'); assert.equal(p.data.notice, ''); assert.match(p.data.error, /MAIL/);
  });
  await test('草稿409只读取最新版本并保留本机正文，不自动再次写入', async () => {
    let writes = 0; const localForm = { title: 'local', summary: '', body: '# local\n\n保留原文', jobIds: [1], majorIds: [11], companyIds: [], topicIds: [], experienceStage: '过去', sourceType: 'DEMO', sourceNote: '', sourceLinks: [] };
    const draft = { articleId: 1, state: 'DRAFT', revision: 9, content: { ...localForm, title: 'server', body: '# server', tags: { jobIds: [1], majorIds: [11], companyIds: [], topicIds: [] } } };
    const p = page('pages/editor/editor.js', { '../../services/member': { saveDraft: async () => { writes += 1; throw { code: 'REVISION_CONFLICT', message: 'conflict' }; }, draft: async () => draft }, '../../utils/operation': { write() {}, read() {}, remove() {} } }); p._epoch = 1; p._ownerId = 1; p.setData({ articleId: 1, authStatus: 'ready', form: localForm, revision: 3, dirty: true }); await p.save(); assert.equal(writes, 1); assert.equal(p.data.form.body, localForm.body); assert.equal(p.data.revision, 3); assert.equal(p.data.conflict.latest.revision, 9);
  });
  await test('发布网络不确定时重试保留同一键和修订，不再次保存草稿', async () => {
    const calls = []; const op = { expectedRevision: 2, requestId: 'preserved-key' }; const p = page('pages/editor/editor.js', { '../../services/member': { publish: async (id, body) => { calls.push({ id, body: structuredClone(body) }); throw new Error('network'); } }, '../../utils/operation': { write() {}, remove() {} } }); p._epoch = 1; p.setData({ articleId: 1, authStatus: 'ready', pendingPublish: op }); await p.retryPublish(); await p.retryPublish(); assert.equal(calls.length, 2); assert.deepEqual(calls[0], calls[1]); assert.equal(p.data.pendingPublish.requestId, 'preserved-key');
  });
  await test('创建网络不确定后继续沿用创建键，不产生第二个草稿键', async () => {
    let saved = null, ids = []; const p = page('pages/workspace/workspace.js', { '../../services/member': { create: async (id) => { ids.push(id); throw new Error('network'); } }, '../../utils/operation': { read: () => saved, write: (_, v) => { saved = v; }, uuid: () => 'one-key' } }); p._epoch = 1; p.setData({ authStatus: 'ready' }); await p.create(); await p.create(); assert.deepEqual(ids, ['one-key', 'one-key']);
  });
  await test('评论不确定时冻结原正文、回复和图片列表，重试内容完全一致', async () => {
    const calls = []; const p = page('pages/comments/comments.js', { '../../services/member': { comment: async (id, body) => { calls.push(structuredClone(body)); throw new Error('network'); } }, '../../utils/operation': { uuid: () => 'one-key', write() {} } }); p._epoch = 1; p.setData({ articleId: 1, authStatus: 'ready', body: '<script>literal</script>', replyTo: { commentId: 5 }, uploads: [{ state: 'done', image: { imageId: 'image' } }] }); await p.send(); p.input({ detail: { value: 'changed' } }); await p.send(); assert.deepEqual(calls[0], calls[1]); assert.equal(calls[1].body, '<script>literal</script>');
  });
  await test('编辑块重排仅处理简单内容，复杂Markdown和原始正文不丢失', () => {
    const editor = require('../utils/editor-content'); const complex = '# title\n\n```js\nconst a = 1;\n\nrun();\n```\n\n- nested\n  - list'; assert.equal(editor.blocks(complex), null); assert.equal(editor.moveBlock(complex, 0, 1), complex);
    const simple = 'first\n\n![图片](/api/v1/media/images/id)\n\nlast'; assert.equal(editor.blocks(simple).length, 3); assert.equal(editor.moveBlock(simple, 0, 1), '![图片](/api/v1/media/images/id)\n\nfirst\n\nlast');
  });
  await test('邮箱精确域名与Unicode密码字节校验，不截断或trim密码', () => {
    const v = require('../utils/validation'); assert.equal(v.email(' Test+1@stu.hzcu.edu.cn '), 'test+1@stu.hzcu.edu.cn'); assert.equal(v.email('a@stu.hzcu.edu.cn.evil'), ''); assert.equal(v.email('a..b@stu.hzcu.edu.cn'), ''); assert.equal(v.password('中'.repeat(25)), false); assert.equal(v.password(' 123456 '), true); assert.equal(v.password('😀'.repeat(19)), false);
  });
  await test('会话到期会主动通知页面清空，而不是等待下一次请求', async () => {
    const e = environment(); let expired = false; e.session.subscribe((event) => { if (event.reason === 'expired') expired = true; }); e.session.accept({ ...login(), expiresAt: new Date(Date.now() + 30).toISOString() }); await new Promise((resolve) => setTimeout(resolve, 65)); assert.equal(expired, true); assert.equal(e.session.token(), '');
  });
  await test('评论不确定后目标404仍保留原键，不静默转为一次新评论', async () => {
    let calls = 0; const p = page('pages/comments/comments.js', { '../../services/member': { comment: async () => { if (++calls === 1) throw new Error('network'); throw { status: 404, message: 'unavailable' }; } }, '../../utils/operation': { uuid: () => 'one-key', write() {} } }); p._epoch = 1; p.setData({ articleId: 1, authStatus: 'ready', body: 'retain' }); await p.send(); await p.send(); assert.equal(p.data.pendingComment.requestId, 'one-key'); assert.equal(p.data.commentUncertain, true);
  });
  await test('原生选图器返回触发onShow不会丢弃文章/评论已选图片', async () => {
    for (const relative of ['pages/editor/editor.js', 'pages/comments/comments.js']) {
      const choosing = deferred(); let entered = 0;
      const p = page(relative, { '../../services/media': { choose: () => choosing.promise, validate: async (f) => f }, '../../utils/member-page': { enter: async () => { entered += 1; p._epoch += 1; return true; }, errorText: (e) => e.message }, '../../utils/operation': { uuid: () => 'image-key', write() {} } });
      p._epoch = 1; p.setData({ articleId: 1, authStatus: 'ready', form: { body: 'keep' } }); p.uploadWaiting = async () => {}; p.upload = async () => {};
      const selecting = p.chooseImages(); await p.onShow(); choosing.resolve([{ tempFilePath: 'wxfile://test.png', size: 12 }]); await selecting;
      assert.equal(entered, 0); assert.equal(p.data.uploads.length, 1); assert.equal(p._choosingMedia, false);
    }
  });
  await test('登录返回路径只解码一次，保留原阅读/编辑目标', () => { const p = page('pages/account/account.js', {}); p.onLoad({ returnTo: encodeURIComponent('/pages/article-detail/article-detail?id=123') }); assert.equal(p.returnTo, '/pages/article-detail/article-detail?id=123'); p.onUnload(); });
  await test('资料修订冲突保留本机输入，用户选择之前不改变修订或重写', async () => {
    let writes = 0; const latest = { revision: 2, nickname: 'server', majorId: null, currentStage: 'UNSET', bio: '' };
    const p = page('pages/profile/profile.js', { '../../services/member': { saveProfile: async () => { writes += 1; throw { code: 'REVISION_CONFLICT', message: 'conflict' }; }, profile: async () => latest } }); p._epoch = 1; p.setData({ authStatus: 'ready', profile: { ...latest, revision: 0 }, contact: { revision: 0 }, nickname: 'local', bio: 'local bio' }); await p.save({ currentTarget: { dataset: { kind: 'profile' } } }); assert.equal(writes, 1); assert.equal(p.data.nickname, 'local'); assert.equal(p.data.profile.revision, 0); assert.equal(p.data.conflict.latest.revision, 2);
  });
  await test('投币成功刷新状态时释放按钮锁，重试只发送原金额与键', async () => {
    const sent = []; const p = page('pages/article-detail/article-detail.js', { '../../services/member': { coin: async (...args) => { sent.push(args); } }, '../../services/articles': { getArticle: () => ({ promise: Promise.resolve({ effectiveCoins: 1 }) }) }, '../../utils/operation': { remove() {} } }); p.memberRound = 1; p.refreshMember = () => { p.memberRound += 1; }; p.setData({ articleId: 1, article: {}, pendingCoin: { amount: 1, requestId: 'original-key' } }); await p.submitCoins(); assert.equal(p.data.busyAction, ''); assert.equal(p.data.pendingCoin, null); assert.deepEqual(sent[0], [1, 1, 'original-key']); assert.equal(p.data.article.effectiveCoins, 1);
  });
  await test('既有复杂Markdown进入只读预览，完整原文保留且不能被轻量编辑覆盖', () => {
    const p = page('pages/editor/editor.js', {}); const body = '# original\n\n```cpp\n<int> keep;\n```\n\n- list'; const draft = { articleId: 1, revision: 3, state: 'DRAFT', content: { title: 'complex', summary: '', body, tags: { jobIds: [1], majorIds: [11], companyIds: [], topicIds: [] }, experienceStage: '', sourceType: null, sourceNote: '', sourceLinks: [] } }; p.setData({ authStatus: 'ready' }); p.applyDraft(draft); assert.equal(p.data.complexSource, true); assert.equal(p.data.preview, true); assert.equal(p.data.readOnly, true); p.input({ currentTarget: { dataset: { field: 'body' } }, detail: { value: 'overwrite' } }); assert.equal(p.data.form.body, body);
  });
  results.forEach((r) => console.log(`${r.pass ? 'PASS' : 'FAIL'} ${r.name}${r.error ? '\n' + r.error : ''}`)); console.log(`\n${results.filter((r) => r.pass).length}/${results.length} member behavior checks passed`); if (results.some((r) => !r.pass)) process.exitCode = 1;
})();
