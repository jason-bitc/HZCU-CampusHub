const api = require('../../services/member');
const access = require('../../utils/member-page');
const pending = require('../../utils/operation');
const { formatDateTime } = require('../../utils/format');
const labels = { DRAFT: '草稿', PUBLISHED: '已发布', AUTHOR_OFFLINE: '作者已下架', ADMIN_HIDDEN: '管理员已隐藏', DELETED: '已删除' };
Page({
  data: { authStatus: 'checking', items: [], total: 0, page: 0, loaded: false, busy: false, error: '', pendingCreate: false },
  onLoad() { access.attach(this, { items: [], total: 0, page: 0, loaded: false, pendingCreate: false }); },
  async onShow() { if (!await access.enter(this, 'workspace')) return; this.setData({ pendingCreate: !!pending.read('create') }); this.load(true); },
  onUnload() { access.leave(this); }, login: access.login,
  onReachBottom() { if (this.data.items.length < this.data.total) this.load(false); },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()); },
  async load(reset, preserveError = false) {
    if (this.data.authStatus !== 'ready' || this.data.busy) return; const epoch = this._epoch; const page = reset === true ? 1 : this.data.page + 1; this.setData({ busy: true, error: preserveError ? this.data.error : '' });
    try { const result = await api.owned(page); if (epoch !== this._epoch) return; const seen = new Map((page === 1 ? [] : this.data.items).map((row) => [row.articleId, row])); result.items.forEach((row) => seen.set(row.articleId, Object.assign({}, row, { stateText: labels[row.state] || '状态待核对', updatedText: formatDateTime(row.updatedAt) }))); this.setData({ items: [...seen.values()], total: result.total, page, loaded: true }); }
    catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); } finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  retry() { this.load(!this.data.loaded); },
  async create() {
    if (this.data.busy || this.data.authStatus !== 'ready') return; this.setData({ busy: true, error: '' }); const epoch = this._epoch;
    try { const requestId = pending.read('create') || pending.uuid(); pending.write('create', requestId); this.setData({ pendingCreate: true }); const created = await api.create(requestId); if (epoch !== this._epoch) return; pending.remove('create'); this.setData({ pendingCreate: false }); wx.navigateTo({ url: `/pages/editor/editor?id=${created.articleId}` }); }
    catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); } finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  edit(e) { wx.navigateTo({ url: `/pages/editor/editor?id=${e.currentTarget.dataset.id}` }); },
  read(e) { wx.navigateTo({ url: `/pages/article-detail/article-detail?id=${e.currentTarget.dataset.id}` }); },
  state(e) {
    const row = this.data.items.find((item) => item.articleId === Number(e.currentTarget.dataset.id)); const action = e.currentTarget.dataset.action; if (!row || this.data.busy) return;
    wx.showModal({ title: action === 'restore' ? '恢复原公开版本？' : '下架这篇经验？', content: action === 'restore' ? '恢复的是上次发布的内容。草稿中的新修改需再次发布才会公开。' : '下架后将无法通过公开入口阅读，草稿仍保留。', success: async (result) => { if (!result.confirm) return; const epoch = this._epoch; this.setData({ busy: true, error: '' }); try { await api.articleState(row.articleId, action, row.revision); } catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); } finally { if (epoch === this._epoch) { this.setData({ busy: false }); this.load(true, true); } } } });
  }
});
