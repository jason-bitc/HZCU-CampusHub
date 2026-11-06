const api = require('../../services/member');
const access = require('../../utils/member-page');
Page({
  data: { authStatus: 'checking', items: [], page: 0, total: 0, busy: false, loaded: false, error: '' },
  onLoad() { access.attach(this, { items: [], page: 0, total: 0, loaded: false }); },
  async onShow() { if (await access.enter(this, 'favorites')) this.load(true); },
  onUnload() { access.leave(this); }, login: access.login, discover: access.discover,
  onReachBottom() { if (this.data.items.length < this.data.total) this.load(false); },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()); },
  async load(reset, preserveError = false) {
    if (this.data.authStatus !== 'ready' || this.data.busy) return;
    const epoch = this._epoch; const page = reset === true ? 1 : this.data.page + 1; this.setData({ busy: true, error: preserveError ? this.data.error : '' });
    try { const result = await api.favorites(page); if (epoch !== this._epoch) return; const seen = new Map((page === 1 ? [] : this.data.items).map((row) => [row.articleId, row])); result.items.forEach((row) => seen.set(row.articleId, row)); this.setData({ items: [...seen.values()], total: result.total, page, loaded: true }); }
    catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); } finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  retry() { this.load(!this.data.loaded); },
  open(e) { wx.navigateTo({ url: `/pages/article-detail/article-detail?id=${e.detail.id}` }); },
  async remove(e) {
    if (this.data.busy) return; const articleId = Number(e.currentTarget.dataset.id); const epoch = this._epoch; this.setData({ busy: true, error: '' });
    try { await api.favorite(articleId, false); if (epoch === this._epoch) { this.setData({ items: this.data.items.filter((row) => row.articleId !== articleId), total: Math.max(0, this.data.total - 1) }); } }
    catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); } finally { if (epoch === this._epoch) { this.setData({ busy: false }); this.load(true, true); } }
  }
});
