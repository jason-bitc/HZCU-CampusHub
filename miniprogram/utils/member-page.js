const session = require('../services/session');
const account = require('../services/account');
function errorText(error) { return `${error.message || '请求未完成，请重试'}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`; }
function attach(page, blank, beforeClear) {
  page._privateBlank = blank;
  page._unsubscribe = session.subscribe(({ reason }) => {
    page._epoch = (page._epoch || 0) + 1;
    if (beforeClear) beforeClear(reason);
    page.setData(Object.assign({}, blank, { authStatus: 'guest', busy: false, error: reason === 'restricted' ? '当前账号或校园认证不可用。' : '请登录校园账号后继续。' }));
  });
}
async function enter(page, tab) {
  page._epoch = (page._epoch || 0) + 1;
  const epoch = page._epoch;
  if (tab && page.getTabBar && page.getTabBar()) page.getTabBar().setData({ active: tab });
  page.setData({ error: '', authStatus: 'checking' });
  try { const me = await account.verify(); if (epoch !== page._epoch) return false; page._ownerId = me.userId; page.setData({ authStatus: 'ready' }); return true; }
  catch (error) { if (epoch === page._epoch) page.setData({ authStatus: session.token() ? 'error' : 'guest', error: errorText(error) }); return false; }
}
function leave(page) { page._epoch = (page._epoch || 0) + 1; if (page._unsubscribe) page._unsubscribe(); }
function login() { const pages = getCurrentPages(); const page = pages[pages.length - 1]; const route = page && page.route; const query = page && page.options || {}; const url = route ? `/${route}${Object.keys(query).length ? '?' + Object.keys(query).map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(query[k])}`).join('&') : ''}` : '/pages/me/me'; wx.navigateTo({ url: `/pages/account/account?returnTo=${encodeURIComponent(url)}` }); }
function discover() { wx.switchTab({ url: '/pages/discover/discover' }); }
module.exports = { attach, enter, leave, login, discover, errorText };
