const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const backend = process.argv[2], credentialsPath = process.argv[3];
if (!backend || !credentialsPath) throw new Error('Provide backend directory and private second-account file');
const base = 'http://127.0.0.1:8080'; const evidence = []; const values = new Map();
global.wx = { getStorageSync: (k) => values.get(k), setStorageSync: (k, v) => values.set(k, v), removeStorageSync: (k) => values.delete(k), request(o) {
  const method = o.method || 'GET'; const url = new URL(o.url); if (method === 'GET' && o.data) Object.entries(o.data).forEach(([k, v]) => url.searchParams.set(k, String(v)));
  const input = { method, headers: o.header }; if (method !== 'GET' && 'data' in o) input.body = JSON.stringify(o.data);
  fetch(url, input).then(async (r) => { const payload = await r.json(); evidence.push({ method, path: url.pathname, status: r.status, code: payload.code, traceId: payload.traceId }); o.success({ statusCode: r.status, header: {}, data: payload }); }).catch(() => o.fail({ errMsg: 'request:fail' })); return { abort() {} };
} };
const config = require('../config/env'); config.apiBaseUrl = base; config.demoMode = false; config.debug = false;
const account = require('../services/account'), api = require('../services/member'), session = require('../services/session');
const localFile = path.resolve(__dirname, '../../tmp/coin-verification-state.json');
const firstEmail = /\$email\s*=\s*'([^']+)'/.exec(fs.readFileSync(path.join(backend, 'scripts/Verify-Authoring.ps1'), 'utf8'))[1];
const firstPassword = JSON.parse(fs.readFileSync(path.join(backend, '.env.account-test.json'), 'utf8')).password;
const second = JSON.parse(fs.readFileSync(credentialsPath, 'utf8'));
let state = fs.existsSync(localFile) ? JSON.parse(fs.readFileSync(localFile, 'utf8')) : { articleId: JSON.parse(fs.readFileSync(path.resolve(__dirname, '../docs/REAL-MEMBER-API-20261106.json'), 'utf8')).fixtureArticleId, coinKey: crypto.randomUUID(), loginKey: crypto.randomUUID(), completed: false };
fs.writeFileSync(localFile, JSON.stringify(state));
let result, firstId, receivedBefore, needsCleanup = false;
(async () => {
  const first = await account.login(firstEmail, firstPassword); firstId = first.userId; receivedBefore = (await api.wallet()).receivedContribution;
  const draft = await api.draft(state.articleId); assert.equal(draft.content.sourceType, 'DEMO');
  if (draft.state === 'AUTHOR_OFFLINE') await api.articleState(state.articleId, 'restore', draft.revision);
  else assert.equal(draft.state, 'PUBLISHED'); needsCleanup = true; await account.logout();
  const reader = await account.login(second.email, second.password); assert.notEqual(reader.userId, firstId);
  let wallet = await api.wallet(); const initialBalance = wallet.balance; let loginRewardClaimed = 0;
  if (wallet.balance < 1) { const receipt = await api.claimLogin(state.loginKey); loginRewardClaimed = receipt.delta; wallet = await api.wallet(); }
  const before = wallet.balance; const coinBefore = await api.coinState(state.articleId);
  const event = await api.coin(state.articleId, 1, state.coinKey); const repeat = await api.coin(state.articleId, 1, state.coinKey); assert.equal(event.eventId, repeat.eventId);
  const after = await api.wallet(); const coinAfter = await api.coinState(state.articleId);
  const delta = state.completed ? 0 : 1; assert.equal(after.balance, before - delta); assert.equal(coinAfter.spent, coinBefore.spent + delta);
  await account.logout(); await account.login(firstEmail, firstPassword);
  const receivedAfter = (await api.wallet()).receivedContribution; assert.equal(receivedAfter, receivedBefore + delta);
  const current = await api.draft(state.articleId); const offline = await api.articleState(state.articleId, 'unpublish', current.revision); assert.equal(offline.state, 'AUTHOR_OFFLINE'); needsCleanup = false;
  state.completed = true; fs.writeFileSync(localFile, JSON.stringify(state)); await account.logout();
  result = { status: 'PASS', checkedAt: new Date().toISOString(), base, fixtureArticleId: state.articleId, fixtureFinalState: 'AUTHOR_OFFLINE', distinctAccounts: true, loginRewardClaimed, readerInitialBalance: initialBalance, readerBalanceBeforeCoin: before, readerBalanceAfterCoin: after.balance, amount: 1, duplicateRequestSameEvent: true, authorContributionDelta: delta, evidence, notes: ['第二账号由用户本轮提供，仅从本机忽略文件读取。', '没有发送验证码、重置密码、修改任何人的资料或联系设置。', '如原余额不足，实际领取登录奖励；实际投出1个非现金币，相同请求重试未重复扣除。', '投币没有撤回接口；真实账目及贡献保留。测试文章最终下架。', '实际前端服务适配器通过Node wx请求桥联调，不代替原生模拟器/真机。'] };
})().catch(() => { process.exitCode = 1; result = { status: 'FAIL', checkedAt: new Date().toISOString(), evidence, credentials: 'omitted' }; }).finally(async () => {
  if (needsCleanup) { try { if (session.token()) await account.logout(); await account.login(firstEmail, firstPassword); const d = await api.draft(state.articleId); if (d.state === 'PUBLISHED') await api.articleState(state.articleId, 'unpublish', d.revision); } catch (_) {} }
  if (session.token()) { try { await account.logout(); } catch (_) { session.clear('logout'); } }
  fs.writeFileSync(path.resolve(__dirname, '../docs/REAL-COINS-API-20261106.json'), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ status: result.status, requests: evidence.length, fixtureFinalState: result.fixtureFinalState, loginRewardClaimed: result.loginRewardClaimed, amount: result.amount, duplicateRequestSameEvent: result.duplicateRequestSameEvent, credentials: 'omitted' }));
});
