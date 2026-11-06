const config = require('../config/env');
const KEY = `campushub.session.v1:${config.apiBaseUrl}`;
let current = null;
let generation = 0;
let reason = '';
let expiryTimer;
const listeners = new Set();

function notify() { listeners.forEach((fn) => fn({ reason, generation })); }
function clear(why = 'logout') {
  clearTimeout(expiryTimer);
  current = null;
  generation += 1;
  reason = why;
  try { wx.removeStorageSync(KEY); } catch (_) { /* Memory is cleared even when storage is unavailable. */ }
  notify();
}
function scheduleExpiry() {
  clearTimeout(expiryTimer);
  if (!current) return;
  expiryTimer = setTimeout(() => { if (current && !valid(current)) clear('expired'); else scheduleExpiry(); }, Math.max(1, Math.min(2147483647, Date.parse(current.expiresAt) - Date.now())));
  if (expiryTimer && typeof expiryTimer.unref === 'function') expiryTimer.unref();
}
function valid(value) {
  return value && typeof value.accessToken === 'string' && value.accessToken.length > 20 && Number.isFinite(Date.parse(value.expiresAt)) && Date.parse(value.expiresAt) > Date.now();
}
function restore() {
  try { const saved = wx.getStorageSync(KEY); if (valid(saved)) { current = saved; generation += 1; scheduleExpiry(); } else if (saved) clear('expired'); } catch (_) { current = null; }
}
function token() {
  if (current && !valid(current)) clear('expired');
  return current ? current.accessToken : '';
}
function accept(login) {
  if (!valid(login) || login.tokenType !== 'Bearer') throw new Error('登录响应不完整，请重新登录');
  generation += 1;
  reason = '';
  current = { accessToken: login.accessToken, expiresAt: login.expiresAt, userId: null };
  scheduleExpiry();
  try { wx.setStorageSync(KEY, current); } catch (_) { /* This session remains in memory only. */ }
  notify();
}
function identify(me) {
  if (!current) return;
  if (!me || me.role !== 'USER' || me.accountState !== 'ACTIVE' || me.campusCertification !== 'VALID') { clear('restricted'); throw new Error('当前账号或校园认证不可用'); }
  current.userId = me.userId;
  try { wx.setStorageSync(KEY, current); } catch (_) { /* Do not log session data. */ }
}
function invalidate(captured, status) { if (captured === generation) clear(status === 403 ? 'restricted' : 'expired'); }
function subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }
module.exports = { restore, accept, identify, clear, token, invalidate, subscribe, version: () => generation, userId: () => current && current.userId, reason: () => reason };
