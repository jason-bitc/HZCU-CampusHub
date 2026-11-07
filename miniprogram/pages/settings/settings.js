const account = require('../../services/account');
const session = require('../../services/session');
const access = require('../../utils/member-page');

const rules = [
  { id: 'read', title: '免费阅读', summary: '公开经验全文无需购买或积分解锁。', detail: '游客可以搜索、筛选和阅读全文。点赞、收藏、评论及作者联系需要有效校园账号。' },
  { id: 'campus', title: '校园认证', summary: '验证校园关联，个人经历由作者自述。', detail: '使用 stu.hzcu.edu.cn 校园邮箱完成验证。认证只说明校园关联，经历与外部资料请自行核对。' },
  { id: 'coins', title: '硬币', summary: '用于社区认可，无充值或提现。', detail: '钱包余额与作者收到的有效贡献分别计算。领取条件以服务端显示为准，成功投币后不能自行撤回。' },
  { id: 'contact', title: '自愿站外联系', summary: '由作者决定是否公开，双方自行交流。', detail: '关闭联系后，新查询不会取得联系内容。已经被复制或保存的信息无法收回。' },
  { id: 'privacy', title: '数据与隐私', summary: '真实邮箱、私人账本与草稿不公开。', detail: '反馈及证据只供本人和有权限的处理人员查看。退出将清理本机登录和本应用的私有副本。' }
];
Page({
  data: { rules, expanded: '', signedIn: false, busy: false, error: '', logoutUncertain: false },
  onLoad() {
    this._off = session.subscribe(() => this.setData({ signedIn: !!session.token(), logoutUncertain: false }));
  },
  onShow() { this.setData({ signedIn: !!session.token() }); },
  onUnload() { if (this._off) this._off(); },
  toggle(e) {
    const id = e.currentTarget.dataset.id;
    this.setData({ expanded: this.data.expanded === id ? '' : id });
  },
  login: access.login,
  profile() { wx.navigateTo({ url: '/pages/profile/profile' }); },
  reset() { wx.navigateTo({ url: '/pages/account/account?mode=reset&returnTo=%2Fpages%2Fsettings%2Fsettings' }); },
  feedback() { wx.navigateTo({ url: '/pages/feedback-form/feedback-form' }); },
  records() { wx.navigateTo({ url: '/pages/feedback/feedback' }); },
  appeals() { wx.navigateTo({ url: '/pages/appeals/appeals' }); },
  async logout() {
    if (this.data.busy || !this.data.signedIn) return;
    this.setData({ busy: true, error: '' });
    try { await account.logout(); access.discover(); }
    catch (e) { this.setData({ error: access.errorText(e), logoutUncertain: e.status !== 401 && e.status !== 403 }); }
    finally { this.setData({ busy: false }); }
  },
  forget() { session.clear('logout'); this.setData({ logoutUncertain: false, error: '已清理本机登录与私有数据。服务端退出尚未确认。' }); }
});
