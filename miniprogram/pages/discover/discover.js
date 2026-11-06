const config = require('../../config/env');
const articlesApi = require('../../services/articles');
const { createRequestGate } = require('../../utils/request-gate');
const { emptyFilters, activeFilterChips, filterSummary } = require('../../utils/filters');
const { cleanKeyword, validateKeyword, buildArticleQuery, mergeUniqueArticles } = require('../../utils/query');
const session = require('../../services/session');
const member = require('../../services/member');
const access = require('../../utils/member-page');

Page({
  data: {
    filters: emptyFilters(),
    keywordInput: '',
    sort: 'recognized',
    catalog: { jobs: [], majors: [], companies: [], topics: [] },
    catalogError: '',
    catalogLoading: true,
    chips: [],
    filterSummary: { majorText: '不限', companyText: '不限' },
    articles: [],
    listStatus: 'loading',
    errorMessage: '',
    errorTitle: '加载失败',
    traceId: '',
    page: 1,
    total: 0,
    hasMore: false,
    loadingMore: false,
    loadMoreError: '',
    filterVisible: false,
    demoMode: config.demoMode,
    demoScenario: 'normal',
    signedIn: false,
    ownMajorId: 0,
    profileError: ''
  },

  onLoad(options) {
    this.profileRound = 0;
    this._unsubscribe = session.subscribe(() => { this.profileRound += 1; this.setData({ signedIn: false, ownMajorId: 0, profileError: '' }); });
    this.gate = createRequestGate();
    this.activeRequest = null;
    this.catalogRequest = null;
    this.lastScrollTop = 0;
    if (config.demoMode && options && options.scenario) articlesApi.setDemoScenario(options.scenario);
    this.setData({ demoScenario: config.demoMode ? articlesApi.getDemoScenario() : 'normal' });
    this.refreshAll();
  },

  onShow() {
    if (this.getTabBar && this.getTabBar()) this.getTabBar().setData({ active: 'discover' });
    this.loadOwnMajor();
    // Returning from detail leaves this page instance and its query/list intact.
    if (this.hasShown && this.lastScrollTop > 0) {
      wx.nextTick(() => wx.pageScrollTo({ scrollTop: this.lastScrollTop, duration: 0 }));
    }
    this.hasShown = true;
  },

  onPageScroll(event) {
    this.lastScrollTop = event.scrollTop;
  },

  onUnload() {
    if (this._unsubscribe) this._unsubscribe();
    this.profileRound += 1;
    if (this.activeRequest) this.activeRequest.abort();
    if (this.catalogRequest) this.catalogRequest.abort();
    this.gate.next();
  },

  loadOwnMajor() {
    const round = ++this.profileRound;
    if (!session.token() || config.demoMode) { this.setData({ signedIn: false, ownMajorId: 0 }); return; }
    member.profile().then((profile) => { if (round === this.profileRound) this.setData({ signedIn: true, ownMajorId: profile.majorId || 0, profileError: '' }); }).catch((error) => { if (round === this.profileRound) this.setData({ ownMajorId: 0, profileError: error.message }); });
  },
  login: access.login,
  editProfile() { wx.navigateTo({ url: '/pages/profile/profile' }); },

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

  syncFilterPresentation(filters) {
    const current = filters || this.data.filters;
    const chips = activeFilterChips(current, this.data.catalog).filter((item) => item.key === 'keyword' || item.key === 'topicId');
    this.setData({ chips, filterSummary: filterSummary(current, this.data.catalog) });
  },

  setListTitle(status) {
    wx.setNavigationBarTitle({ title: status === 'empty' ? '没有匹配的经验' : '城院帮' });
  },

  loadCatalog() {
    if (this.catalogRequest) this.catalogRequest.abort();
    this.setData({ catalogLoading: true });
    const operation = articlesApi.getCatalog();
    this.catalogRequest = operation;
    return operation.promise.then((catalog) => {
      if (this.catalogRequest !== operation) return;
      this.setData({ catalog, catalogError: '', catalogLoading: false });
      this.syncFilterPresentation();
    }).catch((error) => {
      if (this.catalogRequest !== operation) return;
      this.setData({ catalogError: error.message || '分类加载失败，请重试', catalogLoading: false });
    });
  },

  fetchArticles(reset) {
    if (!reset) return this.loadMore();
    if (this.activeRequest) this.activeRequest.abort();
    const token = this.gate.next();
    const query = buildArticleQuery(this.data.filters, this.data.sort, 1, config.pageSize);
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
    this.setListTitle('loading');
    const operation = articlesApi.listArticles(query);
    this.activeRequest = operation;
    return operation.promise.then((page) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      const items = mergeUniqueArticles([], page.items);
      const status = items.length ? 'ready' : 'empty';
      this.activeRequest = null;
      this.setData({
        articles: items,
        listStatus: status,
        page: page.page,
        total: page.total,
        hasMore: items.length < page.total && page.items.length > 0
      });
      this.setListTitle(status);
    }).catch((error) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      this.activeRequest = null;
      this.setData({
        articles: [],
        listStatus: 'error',
        errorTitle: error.kind === 'SERVICE' ? '服务暂时不可用' : error.kind === 'PERMISSION' ? '暂时无法访问' : '加载失败',
        errorMessage: error.message || '加载失败，请重试',
        traceId: error.traceId || '',
        hasMore: false
      });
      this.setListTitle('error');
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
    const next = Object.assign(emptyFilters(), filters, { keyword: cleanKeyword(filters.keyword) });
    this.setData({ filters: next, keywordInput: next.keyword });
    this.syncFilterPresentation(next);
    wx.pageScrollTo({ scrollTop: 0, duration: 0 });
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
    wx.pageScrollTo({ scrollTop: 0, duration: 0 });
    this.fetchArticles(true);
  },

  openFilter() {
    wx.hideKeyboard();
    wx.setNavigationBarTitle({ title: '筛选经验' });
    this.setData({ filterVisible: true });
  },

  closeFilter() {
    this.setData({ filterVisible: false });
    this.setListTitle(this.data.listStatus);
  },

  applyFilter(event) {
    this.closeFilter();
    this.updateFilters(event.detail.filters);
  },

  clearMajorSummary() {
    this.updateFilters(Object.assign({}, this.data.filters, { majorId: 0 }));
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
  }
});
