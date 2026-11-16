const { call } = require('../../services/member');
const account = require('../../services/account');
const session = require('../../services/session');
const access = require('../../utils/member-page');
const local = require('../../utils/operation');
const { validId } = require('../../utils/community-view');
const { formatDateTime } = require('../../utils/format');
const blank = { record: null, appeal: null, challenge: null, code: '', reason: '', pending: null, wait: 0, expired: false, error: '', notice: '' };
const states = { VALID: '校园邮箱已验证', REVOKED: '校园认证已撤销', OPEN: '等待复核', ACCEPTED: '申诉已接受', REJECTED: '申诉已驳回' };

Page({
  data: Object.assign({ authStatus: 'checking', busy: false, appealId: null }, blank),
  onLoad(options) {
    this.setData({ appealId: validId(options.appealId) });
    access.attach(this, blank);
  },
  onShow() {
    if (access.enterPersonal(this)) this.load();
    clearInterval(this.timer);
    this.timer = setInterval(() => this.tick(), 1000);
  },
  onHide() { clearInterval(this.timer); },
  onUnload() { clearInterval(this.timer); access.leave(this); },
  login: access.login,
  async run(action) {
    if (this.data.busy || this.data.authStatus !== 'ready') return;
    const epoch = this._epoch;
    this.setData({ busy: true, error: '' });
    try { await action(() => epoch === this._epoch); }
    catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  load() {
    return this.run(async (current) => {
      if (this.data.appealId) {
        const value = await call(`/me/appeals/${this.data.appealId}`);
        if (current()) this.setData({ appeal: { state: states[value.state] || value.state, reason: value.reason, reply: value.reply, time: formatDateTime(value.updatedAt) } });
      } else {
        const value = await call('/me/certification');
        if (current()) this.setData({ record: Object.assign({}, value, {
          stateText: states[value.state] || value.state,
          verifiedText: value.verifiedAt ? formatDateTime(value.verifiedAt) : '',
          revokedText: value.revokedAt ? formatDateTime(value.revokedAt) : ''
        }) });
      }
    });
  },
  tick() {
    const challenge = this.data.challenge;
    const now = Date.now();
    this.setData({
      wait: challenge ? Math.max(0, Math.ceil((Date.parse(challenge.resendAt) - now) / 1000)) : 0,
      expired: !!challenge && Date.parse(challenge.expiresAt) <= now
    });
  },
  sendCode() {
    if (!this.data.record || !this.data.record.canReverify || this.data.wait) return;
    return this.run(async (current) => {
      const challenge = await call('/me/certification/reverification-codes', 'POST');
      if (!current()) return;
      this.setData({ challenge, code: '', notice: challenge.notice });
      this.tick();
    });
  },
  inputCode(e) { this.setData({ code: e.detail.value }); },
  inputReason(e) { this.setData({ reason: e.detail.value }); },
  verify() {
    const { challenge, code, expired } = this.data;
    if (!challenge || expired || !/^\d{6}$/.test(code)) return;
    return this.run(async (current) => {
      await call('/me/certification/reverification', 'POST', { challengeId: challenge.challengeId, code });
      if (!current()) return;
      session.clear('reverified');
      wx.redirectTo({ url: '/pages/account/account' });
    });
  },
  async submitAppeal() {
    const { record, reason, pending } = this.data;
    if (!pending && (!record || !record.canAppeal || !reason.trim())) return;
    return this.run(async (current) => {
      const payload = pending || { certificationRevision: record.revision, requestId: local.uuid(), reason: reason.trim() };
      this.setData({ pending: payload });
      try {
        const receipt = await call('/me/appeals', 'POST', payload);
        if (!current()) return;
        this.setData({ pending: null, reason: '' });
        wx.navigateTo({ url: `/pages/certification/certification?appealId=${receipt.id}` });
      } catch (e) {
        if (current() && ['REVISION_CONFLICT', 'CERTIFICATION_APPEAL_EXISTS', 'CERTIFICATION_NOT_REVOKED'].includes(e.code))
          this.setData({ pending: null });
        throw e;
      }
    });
  },
  openAppeal() {
    const id = this.data.record && this.data.record.appealId;
    if (id) wx.navigateTo({ url: `/pages/certification/certification?appealId=${id}` });
  },
  notifications() { wx.navigateTo({ url: '/pages/notifications/notifications' }); },
  logout() { return this.run(async () => { await account.logout(); }); }
});
