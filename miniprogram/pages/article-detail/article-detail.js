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
    this.loadArticle().catch(() => {}).then(() => wx.stopPullDownRefresh());
  },

  loadArticle() {
    if (this.activeRequest) this.activeRequest.abort();
    const token = this.gate.next();
    this.setData({ article: null, status: 'loading', errorMessage: '', traceId: '' });
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
        status: error.kind === 'UNAVAILABLE' ? 'unavailable' : 'error',
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
      })
    });
  },

  chooseDemoScenario() {
    if (!config.demoMode) return;
    const scenarios = ['normal', 'network', 'service', 'unavailable'];
    wx.showActionSheet({
      itemList: ['正常文章', '网络失败状态', '服务异常状态', '文章不可用状态'],
      success: (result) => {
        const scenario = scenarios[result.tapIndex];
        articlesApi.setDemoScenario(scenario);
        this.setData({ demoScenario: scenario });
        this.loadArticle();
      }
    });
  }
});
