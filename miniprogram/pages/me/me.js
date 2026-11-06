const api = require('../../services/member');
const account = require('../../services/account');
const session = require('../../services/session');
const access = require('../../utils/member-page');
Page({
  data: { authStatus: 'checking', profile: null, wallet: null, error: '', busy: false, logoutUncertain: false },
  onLoad() { access.attach(this, { profile: null, wallet: null, logoutUncertain: false }); },
  async onShow() { if (!await access.enter(this, 'me')) return; const epoch = this._epoch; try { const [profile, wallet] = await Promise.all([api.profile(), api.wallet()]); if (epoch === this._epoch) this.setData({ profile, wallet }); } catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); } },
  onUnload() { access.leave(this); }, login: access.login, discover: access.discover,
  profile() { wx.navigateTo({ url: '/pages/profile/profile' }); }, wallet() { wx.navigateTo({ url: '/pages/wallet/wallet' }); }, articles() { wx.switchTab({ url: '/pages/workspace/workspace' }); }, favorites() { wx.switchTab({ url: '/pages/favorites/favorites' }); },
  async logout() { if (this.data.busy) return; this.setData({ busy: true, error: '' }); try { await account.logout(); this.setData({ logoutUncertain: false }); } catch (error) { this.setData({ error: access.errorText(error), logoutUncertain: error.status !== 401 && error.status !== 403 }); } finally { this.setData({ busy: false }); } },
  forget() { session.clear('logout'); this.setData({ logoutUncertain: false, error: '已清理本机登录与私有数据。服务端退出未确认，原会话将在到期后失效。' }); }
});
