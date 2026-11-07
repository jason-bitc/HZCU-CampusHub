const api = require('../../services/community');
const access = require('../../utils/member-page');
const view = require('../../utils/community-view');

const blank = { items: [], detail: null, page: 0, total: 0, snapshot: null, loaded: false, unavailable: false };
Page({
  data: Object.assign({ id: null, invalid: false, authStatus: 'checking', busy: false, error: '' }, blank),
  onLoad(options) {
    const id = view.validId(options.id);
    this.setData({ id, invalid: !!options.id && !id });
    if (id) wx.setNavigationBarTitle({ title: '我的反馈进度' });
    access.attach(this, blank);
  },
  async onShow() {
    if (this.data.busy && this.data.authStatus === 'ready') return;
    if (await access.enter(this)) await this.load(true);
  },
  onUnload() { access.leave(this); },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()); },
  onReachBottom() { this.more(); },
  login: access.login,
  async load(reset) {
    if (this.data.authStatus !== 'ready' || this.data.busy || this.data.invalid) return;
    const epoch = this._epoch;
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ busy: true, error: '', unavailable: false });
    try {
      if (this.data.id) {
        const detail = view.caseDetail(await api.caseDetail(this.data.id));
        if (epoch === this._epoch) this.setData({ detail, loaded: true });
      } else {
        const result = await api.cases(page, 20, reset ? null : this.data.snapshot);
        if (epoch !== this._epoch) return;
        const rows = new Map((reset ? [] : this.data.items).map((row) => [row.caseId, row]));
        result.items.map(view.caseRow).filter((row) => row.caseId).forEach((row) => rows.set(row.caseId, row));
        this.setData({ items: [...rows.values()], total: result.total, page: result.page, snapshot: result.snapshot, loaded: true });
      }
    } catch (e) {
      if (epoch === this._epoch) {
        const unavailable = !!this.data.id && e.status === 404;
        this.setData({ detail: null, unavailable, error: unavailable ? '这条反馈不存在，或当前账号无法查看。' : e.status === 404 ? '暂时无法读取反馈记录，请重试。' : access.errorText(e) });
      }
    } finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  refresh() { return this.load(true); },
  more() { if (!this.data.id && this.data.items.length < this.data.total) return this.load(false); },
  detail(e) {
    const id = view.validId(e.currentTarget.dataset.id);
    if (id) wx.navigateTo({ url: `/pages/feedback/feedback?id=${id}` });
  },
  list() {
    const pages = getCurrentPages();
    const previous = pages[pages.length - 2];
    if (previous && previous.route === 'pages/feedback/feedback' && !(previous.options && previous.options.id)) wx.navigateBack();
    else wx.redirectTo({ url: '/pages/feedback/feedback' });
  },
  create() { wx.navigateTo({ url: '/pages/feedback-form/feedback-form' }); }
});
