const config = require('../../config/env');
const articlesApi = require('../../services/articles');
const { createRequestGate } = require('../../utils/request-gate');
const { emptyFilters, activeFilterChips } = require('../../utils/filters');
const { validateKeyword, buildArticleQuery, mergeUniqueArticles } = require('../../utils/query');

Page({
  data: {
    filters: emptyFilters(),
    keywordInput: '',
    sort: 'recognized',
    catalog: { jobs: [], majors: [], companies: [], topics: [] },
    catalogError: '',
    chips: [],
    articles: [],
    listStatus: 'loading',
    errorMessage: '',
    traceId: '',
    page: 1,
    total: 0,
    hasMore: false,
    loadingMore: false,
    loadMoreError: '',
    filterVisible: false,
    demoMode: config.demoMode,
    demoScenario: 'normal'
  },

  onLoad(options) {
    this.gate = createRequestGate();
    this.activeRequest = null;
    this.catalogRequest = null;
    this.lastScrollTop = 0;
    if (config.demoMode && options && options.scenario) {
      articlesApi.setDemoScenario(options.scenario);
    }
    this.setData({ demoScenario: config.demoMode ? articlesApi.getDemoScenario() : 'normal' });
    this.refreshAll();
  },

  onShow() {
    if (this.hasShown && this.lastScrollTop > 0) {
      wx.nextTick(() => wx.pageScrollTo({ scrollTop: this.lastScrollTop, duration: 0 }));
    }
    this.hasShown = true;
  },

  onPageScroll(event) {
    this.lastScrollTop = event.scrollTop;
  },

  onUnload() {
    if (this.activeRequest) this.activeRequest.abort();
    if (this.catalogRequest) this.catalogRequest.abort();
    this.gate.next();
  },

  onPullDownRefresh() {
    const tasks = [this.loadCatalog(), this.fetchArticles(true)];
    Promise.all(tasks.map((task) => task.catch(() => {}))).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.loadMore();
  },

  refreshAll() {
    this.loadCatalog();
    return this.fetchArticles(true);
  },

  loadCatalog() {
    if (this.catalogRequest) this.catalogRequest.abort();
    const operation = articlesApi.getCatalog();
    this.catalogRequest = operation;
    return operation.promise.then((catalog) => {
      if (this.catalogRequest !== operation) return;
      this.setData({ catalog, catalogError: '' });
    }).catch((error) => {
      if (this.catalogRequest !== operation) return;
      this.setData({ catalogError: error.message || '分类加载失败，请重试' });
    });
  },

  fetchArticles(reset) {
    if (!reset) return this.loadMore();
    if (this.activeRequest) this.activeRequest.abort();
    const token = this.gate.next();
    const filters = Object.assign({}, this.data.filters);
    const query = buildArticleQuery(filters, this.data.sort, 1, config.pageSize);
    this.setData({
      articles: [],
      listStatus: 'loading',
      errorMessage: '',
      traceId: '',
      page: 1,
      total: 0,
      hasMore: false,
      loadingMore: false,
      loadMoreError: ''
    });
    const operation = articlesApi.listArticles(query);
    this.activeRequest = operation;
    return operation.promise.then((page) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      const items = mergeUniqueArticles([], page.items);
      this.activeRequest = null;
      this.setData({
        articles: items,
        listStatus: items.length ? 'ready' : 'empty',
        page: page.page,
        total: page.total,
        hasMore: items.length < page.total && page.items.length > 0
      });
    }).catch((error) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      this.activeRequest = null;
      this.setData({
        articles: [],
        listStatus: 'error',
        errorMessage: error.message || '加载失败，请重试',
        traceId: error.traceId || '',
        hasMore: false
      });
    });
  },

  loadMore() {
    if (this.data.listStatus !== 'ready' || !this.data.hasMore || this.data.loadingMore) return Promise.resolve();
    const token = this.gate.current();
    const nextPage = this.data.page + 1;
    const query = buildArticleQuery(this.data.filters, this.data.sort, nextPage, config.pageSize);
    this.setData({ loadingMore: true, loadMoreError: '' });
    const operation = articlesApi.listArticles(query);
    this.activeRequest = operation;
    return operation.promise.then((page) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      const items = mergeUniqueArticles(this.data.articles, page.items);
      this.activeRequest = null;
      this.setData({
        articles: items,
        page: nextPage,
        total: page.total,
        loadingMore: false,
        hasMore: items.length < page.total && page.items.length > 0
      });
    }).catch((error) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      this.activeRequest = null;
      this.setData({ loadingMore: false, loadMoreError: error.message || '加载更多失败，请重试' });
    });
  },

  updateFilters(filters) {
    const keywordError = validateKeyword(filters.keyword);
    if (keywordError) {
      wx.showToast({ title: keywordError, icon: 'none' });
      return;
    }
    const next = Object.assign(emptyFilters(), filters);
    this.setData({
      filters: next,
      keywordInput: next.keyword,
      chips: activeFilterChips(next, this.data.catalog)
    });
    this.fetchArticles(true);
  },

  handleKeywordInput(event) {
    this.setData({ keywordInput: event.detail.value });
  },

  submitSearch() {
    this.updateFilters(Object.assign({}, this.data.filters, { keyword: this.data.keywordInput }));
  },

  clearSearch() {
    this.updateFilters(Object.assign({}, this.data.filters, { keyword: '' }));
  },

  selectJob(event) {
    const id = Number(event.currentTarget.dataset.id) || 0;
    const jobId = this.data.filters.jobId === id ? 0 : id;
    this.updateFilters(Object.assign({}, this.data.filters, { jobId }));
  },

  selectSort(event) {
    const sort = event.currentTarget.dataset.sort === 'latest' ? 'latest' : 'recognized';
    if (sort === this.data.sort) return;
    this.setData({ sort });
    this.fetchArticles(true);
  },

  openFilter() {
    this.setData({ filterVisible: true });
  },

  closeFilter() {
    this.setData({ filterVisible: false });
  },

  applyFilter(event) {
    this.setData({ filterVisible: false });
    this.updateFilters(event.detail.filters);
  },

  clearChip(event) {
    const key = event.currentTarget.dataset.key;
    const patch = key === 'keyword' ? { keyword: '' } : { [key]: 0 };
    this.updateFilters(Object.assign({}, this.data.filters, patch));
  },

  resetFilters() {
    this.updateFilters(emptyFilters());
  },

  handleStateAction() {
    if (this.data.listStatus === 'error') {
      this.fetchArticles(true);
      return;
    }
    if (this.data.filters.companyId) {
      this.updateFilters(Object.assign({}, this.data.filters, { companyId: 0 }));
    } else {
      this.resetFilters();
    }
  },

  handleStateSecondary() {
    this.openFilter();
  },

  openArticle(event) {
    const id = Number(event.detail.id);
    if (!id) return;
    wx.navigateTo({ url: `/pages/article-detail/article-detail?id=${id}` });
  },

  chooseDemoScenario() {
    if (!config.demoMode) return;
    const scenarios = ['normal', 'empty', 'network', 'service'];
    wx.showActionSheet({
      itemList: ['正常演示数据', '空结果状态', '网络失败状态', '服务异常状态'],
      success: (result) => {
        const scenario = scenarios[result.tapIndex];
        articlesApi.setDemoScenario(scenario);
        this.setData({ demoScenario: scenario });
        this.refreshAll();
      }
    });
  }
});
