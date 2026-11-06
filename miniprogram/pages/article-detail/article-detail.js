const config = require('../../config/env');
const articlesApi = require('../../services/articles');
const { normalizeExternalUrl } = require('../../utils/markdown');
const { createRequestGate } = require('../../utils/request-gate');
const member = require('../../services/member');
const account = require('../../services/account');
const session = require('../../services/session');
const access = require('../../utils/member-page');
const pending = require('../../utils/operation');
const privateBlank = { authenticated: false, interaction: null, author: null, coinState: null, wallet: null, memberError: '', pendingCoin: null, busyAction: '', privateLoading: false };

Page({
  data: {
    articleId: 0,
    article: null,
    status: 'loading',
    errorMessage: '',
    traceId: '',
    tocDrawerVisible: false,
    errorTitle: '文章加载失败',
    assetBaseUrl: config.apiBaseUrl,
    demoMode: config.demoMode,
    demoScenario: 'normal',
    ...privateBlank
  },

  onLoad(options) {
    if (this._unsubscribe) this._unsubscribe();
    this._unsubscribe = session.subscribe(() => { this.memberRound = (this.memberRound || 0) + 1; this.setData(privateBlank); });
    this.gate = createRequestGate();
    this.activeRequest = null;
    const id = Number(options && options.id);
    if (config.demoMode && options && options.scenario) articlesApi.setDemoScenario(options.scenario);
    this.setData({
      articleId: id,
      demoScenario: config.demoMode ? articlesApi.getDemoScenario() : 'normal'
    });
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.setData({ status: 'unavailable', errorMessage: '文章链接不完整或格式无效' });
      return;
    }
    this.loadArticle();
  },

  onUnload() {
    if (this._unsubscribe) this._unsubscribe();
    this.memberRound = (this.memberRound || 0) + 1;
    if (this.activeRequest) this.activeRequest.abort();
    this.gate.next();
  },

  onShow() { this.refreshMember(); },
  async refreshMember() {
    const round = this.memberRound = (this.memberRound || 0) + 1;
    if (!session.token() || config.demoMode) { this.setData(privateBlank); return; }
    this.setData({ privateLoading: true, memberError: '', author: null, interaction: null, coinState: null, wallet: null });
    try {
      await account.verify(); if (round !== this.memberRound) return;
      this.setData({ authenticated: true });
      if (this.data.status !== 'ready') return;
      const id = this.data.articleId;
      const [interaction, author, coinState, wallet] = await Promise.all([member.interaction(id), member.articleAuthor(id).catch((e) => { if (e.status === 404) return null; throw e; }), member.coinState(id), member.wallet()]);
      if (round !== this.memberRound) return;
      this.setData({ interaction, author, coinState, wallet, pendingCoin: pending.read(`coin:${id}`) });
      member.startReading(id).catch(() => {});
    } catch (error) { if (round === this.memberRound) this.setData({ memberError: access.errorText(error) }); }
    finally { if (round === this.memberRound) this.setData({ privateLoading: false }); }
  },
  login: access.login,
  async toggleInteraction(e) {
    if (!this.data.authenticated) { this.login(); return; }
    if (!this.data.interaction || this.data.busyAction) return;
    const kind = e.currentTarget.dataset.kind; if (!['like', 'favorite'].includes(kind)) return;
    const round = this.memberRound; const target = kind === 'like' ? !this.data.interaction.liked : !this.data.interaction.favorited;
    this.setData({ busyAction: kind, memberError: '' });
    try { const result = await member[kind](this.data.articleId, target); if (round !== this.memberRound) return; this.setData(kind === 'like' ? { 'interaction.liked': result.liked, 'article.effectiveLikes': result.effectiveLikes } : { 'interaction.favorited': result.favorited }); }
    catch (error) { if (round === this.memberRound) this.setData({ memberError: access.errorText(error) }); }
    finally { if (round === this.memberRound) this.setData({ busyAction: '' }); }
  },
  comments() { if (!this.data.authenticated) { this.login(); return; } wx.navigateTo({ url: `/pages/comments/comments?id=${this.data.articleId}` }); },
  author() { if (this.data.author) wx.navigateTo({ url: `/pages/author/author?id=${this.data.author.authorId}` }); },
  coins() {
    if (!this.data.authenticated) { this.login(); return; }
    if (this.data.pendingCoin) { this.submitCoins(); return; }
    const state = this.data.coinState; const wallet = this.data.wallet;
    if (!state || !wallet || this.data.busyAction) return;
    const reasons = { NO_REAL_AUTHOR: '该文章没有关联真实作者，不能投币。', SELF_ARTICLE: '不能给自己的文章投币。', ARTICLE_COIN_LIMIT: '已达到这篇文章的累计投币额度。' };
    if (!state.eligible) { this.setData({ memberError: reasons[state.reason] || '当前文章暂不可投币' }); return; }
    const max = Math.min(2, state.remaining, wallet.balance);
    if (max < 1) { this.setData({ memberError: '非现金币余额不足，可在“我的 → 我的非现金币”查看奖励。' }); return; }
    const amounts = max >= 2 ? [1, 2] : [1];
    wx.showActionSheet({ itemList: amounts.map((n) => `投 ${n} 币`), success: (choice) => {
      const amount = amounts[choice.tapIndex];
      wx.showModal({ title: `确认投 ${amount} 币？`, content: '将从你的非现金币余额中扣除，不能自行撤回。投币不会购买内容，全文始终免费。', success: (r) => { if (!r.confirm || !this.data.authenticated) return; const value = { amount, requestId: pending.uuid() }; try { pending.write(`coin:${this.data.articleId}`, value); this.setData({ pendingCoin: value }); this.submitCoins(); } catch (_) { this.setData({ memberError: '无法保留投币请求，请稍后重试' }); } } });
    } });
  },
  async submitCoins() {
    if (!this.data.pendingCoin || this.data.busyAction) return; const round = this.memberRound; this.setData({ busyAction: 'coin', memberError: '' });
    try { const value = this.data.pendingCoin; await member.coin(this.data.articleId, value.amount, value.requestId); if (round !== this.memberRound) return; pending.remove(`coin:${this.data.articleId}`); this.setData({ pendingCoin: null, busyAction: '' }); this.refreshMember(); const refreshedRound = this.memberRound; const article = await articlesApi.getArticle(this.data.articleId).promise; if (refreshedRound === this.memberRound && this.data.article) this.setData({ 'article.effectiveCoins': article.effectiveCoins }); }
    catch (error) { if (round === this.memberRound) { if (error.status === 400 || error.status === 409 && error.code !== 'REQUEST_KEY_REUSED') { pending.remove(`coin:${this.data.articleId}`); this.setData({ pendingCoin: null, memberError: access.errorText(error) }); } else this.setData({ memberError: `${access.errorText(error)}。未确认时重试会沿用原请求。` }); } }
    finally { if (round === this.memberRound) this.setData({ busyAction: '' }); }
  },

  onPullDownRefresh() {
    if (!Number.isSafeInteger(this.data.articleId) || this.data.articleId <= 0) {
      wx.stopPullDownRefresh();
      return;
    }
    this.loadArticle().catch(() => {}).then(() => wx.stopPullDownRefresh());
  },

  onPageScroll(event) {
    const reader = this.selectComponent('#article-reader');
    if (reader) reader.updateReadingPosition(event.scrollTop);
  },

  handleDrawerChange(event) {
    if (event.detail.visible !== this.data.tocDrawerVisible) this.setData({ tocDrawerVisible: event.detail.visible });
  },

  goToSources() {
    wx.createSelectorQuery().select('#article-sources').boundingClientRect((rect) => {
      if (!rect) return;
      wx.createSelectorQuery().selectViewport().scrollOffset((viewport) => {
        if (viewport) wx.pageScrollTo({ scrollTop: Math.max(0, viewport.scrollTop + rect.top - 20), duration: 250 });
      }).exec();
    }).exec();
  },

  loadArticle() {
    if (this.activeRequest) this.activeRequest.abort();
    this.memberRound = (this.memberRound || 0) + 1;
    this.setData(privateBlank);
    const token = this.gate.next();
    this.setData({ article: null, status: 'loading', errorMessage: '', traceId: '', tocDrawerVisible: false });
    const operation = articlesApi.getArticle(this.data.articleId);
    this.activeRequest = operation;
    return operation.promise.then((article) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      this.activeRequest = null;
      this.setData({ article, status: 'ready' });
      this.refreshMember();
    }).catch((error) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      this.activeRequest = null;
      this.setData({
        article: null,
        status: error.kind === 'UNAVAILABLE' ? 'unavailable' : error.kind === 'PERMISSION' ? 'permission' : 'error',
        errorTitle: error.kind === 'SERVICE' ? '服务暂时不可用' : '文章加载失败',
        errorMessage: error.message || '文章加载失败，请重试',
        traceId: error.traceId || ''
      });
    });
  },

  goBack() {
    const pages = getCurrentPages();
    if (pages.length > 1) wx.navigateBack();
    else wx.reLaunch({ url: '/pages/discover/discover' });
  },

  copySourceLink(event) {
    const url = normalizeExternalUrl(event.currentTarget.dataset.url);
    if (!url) {
      wx.showToast({ title: '该资料链接格式不支持', icon: 'none' });
      return;
    }
    wx.setClipboardData({
      data: url,
      success: () => wx.showModal({
        title: '资料链接已复制',
        content: '外部资料由作者提供，请自行核验内容与访问权限。小程序不直接跳转任意外链，可粘贴到浏览器打开。',
        showCancel: false,
        confirmText: '知道了'
      }),
      fail: () => wx.showToast({ title: '复制失败，请重试', icon: 'none' })
    });
  }
});
