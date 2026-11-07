const config = require('../../config/env');
const account = require('../../services/account');
const session = require('../../services/session');
const check = require('../../utils/validation');
const { errorText } = require('../../utils/member-page');
const tabs = ['/pages/discover/discover', '/pages/favorites/favorites', '/pages/workspace/workspace', '/pages/me/me'];
const returns = tabs.concat(['/pages/editor/editor', '/pages/profile/profile', '/pages/wallet/wallet', '/pages/article-detail/article-detail', '/pages/comments/comments', '/pages/author/author', '/pages/feedback-form/feedback-form', '/pages/feedback/feedback', '/pages/appeals/appeals', '/pages/settings/settings']);
Page({
  data: { mode: 'login', title: '登录城院帮', email: '', password: '', confirmation: '', code: '', challenge: null, wait: 0, expired: false, busy: false, sending: false, error: '', errors: {}, notice: '', demoMode: config.demoMode },
  onLoad(options) { this.challenges = {}; try { this.returnTo = options.returnTo ? decodeURIComponent(options.returnTo) : '/pages/me/me'; } catch (_) { this.returnTo = '/pages/me/me'; } this.changeMode(options.mode || 'login'); this.offSession = session.subscribe(({ reason }) => { if (reason === 'logout') { this.challenges = {}; this.setData({ email: '', password: '', confirmation: '', code: '', challenge: null }); } }); },
  onShow() { this.tick(); clearInterval(this.timer); this.timer = setInterval(() => this.tick(), 1000); },
  onHide() { clearInterval(this.timer); this.setData({ password: '', confirmation: '', code: '' }); },
  onUnload() { this.destroyed = true; clearInterval(this.timer); if (this.offSession) this.offSession(); this.challenges = {}; },
  tick() { const challenge = this.data.challenge; this.setData({ wait: challenge ? Math.max(0, Math.ceil((Date.parse(challenge.resendAt) - Date.now()) / 1000)) : 0, expired: !!challenge && Date.parse(challenge.expiresAt) <= Date.now() }); },
  changeMode(mode) { const chosen = ['login', 'register', 'reset'].includes(mode) ? mode : 'login'; this.setData({ mode: chosen, title: chosen === 'login' ? '登录城院帮' : chosen === 'register' ? '校园邮箱注册' : '找回密码', password: '', confirmation: '', code: '', error: '', errors: {}, notice: '', challenge: null }); wx.setNavigationBarTitle({ title: this.data.title }); this.syncChallenge(); },
  switchMode(e) { if (!this.data.busy && !this.data.sending) this.changeMode(e.currentTarget.dataset.mode); },
  input(e) { const field = e.currentTarget.dataset.field; if (!['email', 'password', 'confirmation', 'code'].includes(field)) return; this.setData({ [field]: e.detail.value, errors: {}, error: '' }); if (field === 'email') this.syncChallenge(); },
  syncChallenge() { const key = `${this.data.mode}:${check.email(this.data.email)}`; this.setData({ challenge: this.challenges && this.challenges[key] || null }); this.tick(); },
  async sendCode() {
    if (this.data.sending || this.data.busy || this.data.wait) return;
    const email = check.email(this.data.email); if (!email) { this.setData({ errors: { email: '请输入有效的 stu.hzcu.edu.cn 校园邮箱' } }); return; }
    const mode = this.data.mode; this.setData({ sending: true, error: '', notice: '' });
    try { const result = await account.sendCode(mode, email); if (this.destroyed) return; this.challenges[`${mode}:${email}`] = result;
      if (mode === this.data.mode && email === check.email(this.data.email)) { this.setData({ challenge: result, notice: `${result.notice || '发送请求已受理，请查看邮箱。'}\n有效期至 ${new Date(result.expiresAt).toLocaleTimeString()}。受理不代表邮件已经送达。` }); this.tick(); }
    } catch (error) { if (!this.destroyed) this.setData({ error: errorText(error) }); } finally { if (!this.destroyed) this.setData({ sending: false }); }
  },
  async submit() {
    if (this.data.busy || this.data.sending) return;
    const { mode, password, confirmation, code, challenge } = this.data;
    const email = check.email(this.data.email); const errors = {};
    if (!email) errors.email = '请输入有效的 stu.hzcu.edu.cn 校园邮箱';
    if (!check.password(password)) errors.password = '密码须为 8–64 个字符，UTF-8 不超过 72 字节';
    if (mode !== 'login') { if (password !== confirmation) errors.confirmation = '两次密码输入不一致'; if (!challenge || this.data.expired) errors.code = '请先获取当前有效的邮箱验证码'; else if (!/^\d{6}$/.test(code)) errors.code = '请输入 6 位数字验证码'; }
    this.setData({ errors, error: '', notice: '' }); if (Object.keys(errors).length) return;
    this.setData({ busy: true });
    try {
      if (mode === 'login') { await account.login(email, password); if (!this.destroyed) this.finish(); }
      else { await account.complete(mode, { email, password, challengeId: challenge.challengeId, code }); if (this.destroyed) return; delete this.challenges[`${mode}:${email}`]; if (mode === 'reset') session.clear('password-reset'); this.changeMode('login'); this.setData({ email, notice: mode === 'register' ? '注册成功，校园邮箱已验证。请使用刚设置的密码登录。' : '密码已更新，所有旧会话已失效。请重新登录。' }); }
    } catch (error) { if (!this.destroyed) this.setData({ error: errorText(error), password: '', confirmation: '', code: '' }); } finally { if (!this.destroyed) this.setData({ busy: false }); }
  },
  finish() {
    this.setData({ email: '', password: '', confirmation: '', code: '' });
    let destination = this.returnTo; const route = destination.split('?')[0]; if (!returns.includes(route)) destination = '/pages/me/me';
    if (tabs.includes(destination)) { wx.switchTab({ url: destination }); return; }
    const pages = getCurrentPages(); if (pages.length > 1 && `/${pages[pages.length - 2].route}` === route) wx.navigateBack(); else wx.redirectTo({ url: destination });
  },
  browse() { wx.switchTab({ url: '/pages/discover/discover' }); }
});
