const config = require('../../config/env');
const articlesApi = require('../../services/articles');
const { createRequestGate } = require('../../utils/request-gate');
const { emptyFilters, activeFilterChips } = require('../../utils/filters');
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
    jobChoices: [{ id: 0, name: '全部岗位' }],
    jobChoiceIndex: 0,
    chips: [],
    sameMajorAvailable: false,
    searchError: '',
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
    refreshError: '',
    filterVisible: false,
    demoMode: config.demoMode,
    demoScenario: 'normal',
    signedIn: false,
    ownMajorId: 0,
    profileError: ''
  },

  onLoad(options) {
    this.profileRound = 0;
    this._unsubscribe = session.subscribe(() => { this.profileRound += 1; this.setData({ signedIn: false, ownMajorId: 0, sameMajorAvailable: false, profileError: '' }); });
    this.gate = createRequestGate();
    this.activeRequest = null;
    this.catalogRequest = null;
    this.lastScrollTop = 0;
    if (config.demoMode && options && options.scenario) articlesApi.setDemoScenario(options.scenario);
    this.setData({ demoScenario: config.demoMode ? articlesApi.getDemoScenario() : 'normal' });
    this.refreshAll();
  },

  onShow() {
    const tabBar = this.getTabBar && this.getTabBar();
    if (tabBar) tabBar.setData({ active: 'discover', hidden: this.data.filterVisible });
    this.loadOwnMajor();
    // Returning from detail leaves this page instance and its query/list intact.
    if (this.restoreScrollTop > 0) {
      const scrollTop = this.restoreScrollTop;
      this.restoreScrollTop = 0;
      wx.nextTick(() => wx.pageScrollTo({ scrollTop, duration: 0 }));
    }
  },

  onHide() { this.restoreScrollTop = this.lastScrollTop; },

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
    if (!session.token() || config.demoMode) { this.setData({ signedIn: false, ownMajorId: 0, sameMajorAvailable: false }); return; }
    member.profile().then((profile) => {
      if (round !== this.profileRound) return;
      this.setData({ signedIn: true, ownMajorId: profile.majorId || 0, profileError: '' });
      this.syncFilterPresentation();
    }).catch((error) => {
      if (round === this.profileRound) this.setData({ ownMajorId: 0, sameMajorAvailable: false, profileError: error.message });
    });
  },
  login: access.login,
  editProfile() { wx.navigateTo({ url: '/pages/profile/profile' }); },

  onPullDownRefresh() {
    const tasks = [this.loadCatalog(), this.refreshResults()];
    Promise.all(tasks.map((task) => task.catch(() => {}))).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (!this.data.loadMoreError) this.loadMore();
  },

  refreshAll() {
    this.loadCatalog();
    return this.fetchArticles(true);
  },

  syncFilterPresentation(filters) {
    const current = filters || this.data.filters;
    const chips = activeFilterChips(current, this.data.catalog);
    const jobChoices = [{ id: 0, name: '全部岗位' }, ...this.data.catalog.jobs];
    const jobChoiceIndex = jobChoices.findIndex((job) => job.id === current.jobId);
    this.setData({
      chips, jobChoices, jobChoiceIndex: Math.max(0, jobChoiceIndex),
      sameMajorAvailable: this.data.catalog.majors.some((major) => major.id === this.data.ownMajorId)
    });
  },

  setListTitle(status) {
    wx.setNavigationBarTitle({ title: status === 'no-match' ? '没有匹配的经验' : '城院帮' });
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

  refreshResults() { return this.fetchArticles(true, this.data.listStatus === 'ready'); },

  fetchArticles(reset, retainList = false) {
    if (!reset) return this.loadMore();
    if (this.activeRequest) this.activeRequest.abort();
    const token = this.gate.next();
    const query = buildArticleQuery(this.data.filters, this.data.sort, 1, config.pageSize);
    this.setData({ refreshError: '', loadingMore: false });
    if (!retainList) this.setData({
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
    if (!retainList) this.setListTitle('loading');
    const operation = articlesApi.listArticles(query);
    this.activeRequest = operation;
    return operation.promise.then((page) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      const items = mergeUniqueArticles([], page.items);
      const status = items.length ? 'ready' : this.data.chips.length ? 'no-match' : 'empty';
      this.activeRequest = null;
      this.setData({
        articles: items,
        listStatus: status,
        loadMoreError: '',
        page: page.page,
        total: page.total,
        hasMore: items.length < page.total && page.items.length > 0
      });
      this.setListTitle(status);
    }).catch((error) => {
      if (!this.gate.isCurrent(token) || this.activeRequest !== operation) return;
      this.activeRequest = null;
      if (retainList) { this.setData({ refreshError: access.errorText(error) }); return; }
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
    if (this.data.listStatus !== 'ready' || !this.data.hasMore || this.activeRequest || this.data.loadingMore) return Promise.resolve();
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
      this.setData({ searchError: keywordError });
      return;
    }
    const next = Object.assign(emptyFilters(), filters, { keyword: cleanKeyword(filters.keyword) });
    const keywordInput = next.keyword === this.data.filters.keyword ? this.data.keywordInput : next.keyword;
    this.setData({ filters: next, keywordInput, searchError: '' });
    this.syncFilterPresentation(next);
    wx.pageScrollTo({ scrollTop: 0, duration: 0 });
    this.fetchArticles(true);
  },

  handleKeywordInput(event) {
    this.setData({ keywordInput: event.detail.value, searchError: '' });
  },

  submitSearch() {
    const keyword = cleanKeyword(this.data.keywordInput);
    this.setData({ keywordInput: keyword });
    this.updateFilters(Object.assign({}, this.data.filters, { keyword }));
  },

  clearSearch() {
    this.setData({ keywordInput: '' });
    this.updateFilters(Object.assign({}, this.data.filters, { keyword: '' }));
  },

  selectJob(event) {
    const job = this.data.jobChoices[Number(event.detail.value)];
    if (!job || job.id === this.data.filters.jobId) return;
    this.updateFilters(Object.assign({}, this.data.filters, { jobId: job.id }));
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
    this.setFilterVisible(true);
  },

  closeFilter() {
    this.setFilterVisible(false);
    this.setListTitle(this.data.listStatus);
  },

  setFilterVisible(visible) {
    this.setData({ filterVisible: visible });
    const tabBar = this.getTabBar && this.getTabBar();
    if (tabBar) tabBar.setData({ hidden: visible });
  },

  applyFilter(event) {
    this.closeFilter();
    this.updateFilters(event.detail.filters);
  },

  toggleSameMajor() {
    const own = this.data.ownMajorId;
    if (!own || !this.data.catalog.majors.some((item) => item.id === own)) return;
    this.updateFilters(Object.assign({}, this.data.filters, { majorId: this.data.filters.majorId === own ? 0 : own }));
  },

  clearChip(event) {
    const key = event.currentTarget.dataset.key;
    if (key === 'keyword') { this.clearSearch(); return; }
    const patch = { [key]: 0 };
    this.updateFilters(Object.assign({}, this.data.filters, patch));
  },

  resetFilters() {
    this.setData({ keywordInput: '' });
    this.updateFilters(emptyFilters());
  },

  handleStateAction() {
    if (['error', 'empty'].includes(this.data.listStatus)) {
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
