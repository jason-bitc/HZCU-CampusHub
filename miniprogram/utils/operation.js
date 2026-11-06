const session = require('../services/session');
const config = require('../config/env');
function uuid() {
  // Request keys provide collision resistance, not authentication secrets.
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => { const r = Math.floor(Math.random() * 16); return (c === 'x' ? r : (r & 3) | 8).toString(16); });
}
function key(scope, owner) { return `campushub.private.v1:${config.apiBaseUrl}:${owner || session.userId()}:${scope}`; }
function read(scope, owner) { try { return wx.getStorageSync(key(scope, owner)) || null; } catch (_) { return null; } }
function write(scope, value, owner) { wx.setStorageSync(key(scope, owner), value); }
function remove(scope, owner) { try { wx.removeStorageSync(key(scope, owner)); } catch (_) { /* Best effort; never log private values. */ } }
function purge() { try { wx.getStorageInfoSync().keys.filter((k) => k.startsWith(`campushub.private.v1:${config.apiBaseUrl}:`)).forEach((k) => wx.removeStorageSync(k)); } catch (_) { /* Do not clear unrelated app storage. */ } }
session.subscribe(({ reason }) => { if (reason === 'logout') purge(); });
module.exports = { uuid, key, read, write, remove, purge };
