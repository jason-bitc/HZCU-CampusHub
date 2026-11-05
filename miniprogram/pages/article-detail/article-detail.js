const config = require('../../config/env');
const articlesApi = require('../../services/articles');
const { normalizeExternalUrl } = require('../../utils/markdown');
const { createRequestGate } = require('../../utils/request-gate');

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
    demoScenario: 'normal'
  },

  onLoad(options) {
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
    if (this.activeRequest) this.activeRequest.abort();
    this.gate.next();
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
    const token = this.gate.next();
    this.setData({ article: null, status: 'loading', errorMessage: '', traceId: '', tocDrawerVisible: false });
    const operation = articlesApi.getArticle(this.data.articleId);
    this.activeRequest = operation;
    return operation.promise.then((article) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      this.activeRequest = null;
      this.setData({ article, status: 'ready' });
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
