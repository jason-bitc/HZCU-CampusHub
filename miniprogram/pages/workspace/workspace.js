const api = require('../../services/member');
const access = require('../../utils/member-page');
const pending = require('../../utils/operation');
const { formatDateTime } = require('../../utils/format');

const states = { DRAFT: '草稿', PUBLISHED: '公开', AUTHOR_OFFLINE: '自主下架', ADMIN_HIDDEN: '管理员隐藏' };
const filters = [
  { id: 'ALL', label: '全部' }, { id: 'PUBLISHED', label: '公开' }, { id: 'AUTHOR_OFFLINE', label: '自主下架' },
  { id: 'ADMIN_HIDDEN', label: '已隐藏' }, { id: 'DRAFT', label: '草稿' }
];
const blank = { items: [], visibleItems: [], total: 0, page: 0, loaded: false, pendingCreate: false, notice: '', restricted: false };
function visible(items, filter) {
  return items.filter((row) => row.state !== 'DELETED' && (filter === 'ALL' || (filter === 'EXPERIENCES' ? row.state !== 'DRAFT' : row.state === filter)));
}
Page({
  data: Object.assign({ authStatus: 'checking', busy: false, error: '', filter: 'ALL', filters }, blank),
  onLoad() { access.attach(this, blank); },
  async onShow() {
    if (this.data.busy && this.data.authStatus === 'ready') return;
    if (!await access.enter(this, 'workspace')) return;
    const global = getApp().globalData;
    if (global.workspaceFilter) {
      const filter = global.workspaceFilter;
      delete global.workspaceFilter;
      this.setData({ filter: filter === 'EXPERIENCES' || filters.some((row) => row.id === filter) ? filter : 'ALL' });
    }
    this.setData({ pendingCreate: !!pending.read('create') });
    await this.load(true);
  },
  onUnload() { access.leave(this); }, login: access.login,
  onReachBottom() { this.more(); },
  onPullDownRefresh() { this.load(true).finally(() => wx.stopPullDownRefresh()); },
  filter(e) {
    const filter = e.currentTarget.dataset.filter;
    if (this.data.busy || !filters.some((row) => row.id === filter)) return;
    this.setData({ filter, visibleItems: visible(this.data.items, filter), notice: '' });
  },
  async load(reset, preserveError = false) {
    if (this.data.authStatus !== 'ready' || this.data.busy) return;
    const epoch = this._epoch;
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ busy: true, error: preserveError ? this.data.error : '' });
    try {
      const result = await api.owned(page);
      if (epoch !== this._epoch) return;
      const rows = new Map((reset ? [] : this.data.items).map((row) => [row.articleId, row]));
      for (const row of result.items) {
        const local = row.state === 'DELETED' ? null : pending.read(`draft:${row.articleId}`);
        rows.set(row.articleId, Object.assign({}, row, {
          stateText: states[row.state] || '状态待核对', updatedText: formatDateTime(row.updatedAt),
          localCopy: !!(local && (local.dirty || local.pendingPublish || local.baseRevision !== row.revision || (local.uploads || []).some((file) => file.state !== 'done')))
        }));
      }
      const items = [...rows.values()];
      this.setData({ items, visibleItems: visible(items, this.data.filter), total: result.total, page: result.page, loaded: true });
    } catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  refresh() { return this.load(true); },
  more() { if (this.data.items.length < this.data.total) return this.load(false); },
  async create() {
    if (this.data.busy || this.data.authStatus !== 'ready') return;
    this.setData({ busy: true, error: '' });
    const epoch = this._epoch;
    try {
      const requestId = pending.read('create') || pending.uuid();
      pending.write('create', requestId);
      this.setData({ pendingCreate: true });
      const created = await api.create(requestId);
      if (epoch !== this._epoch) return;
      pending.remove('create');
      this.setData({ pendingCreate: false });
      wx.navigateTo({ url: `/pages/editor/editor?id=${created.articleId}` });
    } catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  edit(e) { wx.navigateTo({ url: `/pages/editor/editor?id=${e.currentTarget.dataset.id}` }); },
  read(e) { wx.navigateTo({ url: `/pages/article-detail/article-detail?id=${e.currentTarget.dataset.id}` }); },
  appeals() { wx.navigateTo({ url: '/pages/appeals/appeals?scope=PUBLISH' }); },
  state(e) {
    const row = this.data.items.find((item) => item.articleId === Number(e.currentTarget.dataset.id));
    const action = e.currentTarget.dataset.action;
    const valid = row && (action === 'restore' && row.state === 'AUTHOR_OFFLINE' || action === 'unpublish' && row.state === 'PUBLISHED' || action === 'delete' && ['DRAFT', 'AUTHOR_OFFLINE', 'ADMIN_HIDDEN'].includes(row.state));
    if (!valid || this.data.busy || this.data.authStatus !== 'ready') return;
    const epoch = this._epoch;
    const messages = {
      restore: ['恢复原公开版本？', '恢复的是上次发布的内容。草稿中的新修改需再次发布才会公开。'],
      unpublish: ['下架这篇经验？', '下架后将无法通过公开入口阅读，草稿仍保留。'],
      delete: ['删除这条记录？', '删除后不能继续编辑或恢复。此文章的本机编辑副本也会清理。']
    };
    wx.showModal({ title: messages[action][0], content: messages[action][1], confirmText: action === 'delete' ? '确认删除' : '确认', success: async (result) => {
      if (!result.confirm || epoch !== this._epoch || this.data.busy || this.data.authStatus !== 'ready') return;
      this.setData({ busy: true, error: '', notice: '', restricted: false });
      try {
        const receipt = await (action === 'delete' ? api.deleteArticle(row.articleId, row.revision) : api.articleState(row.articleId, action, row.revision));
        if (epoch !== this._epoch) return;
        if (action === 'delete' && receipt.state === 'DELETED') {
          pending.remove(`draft:${row.articleId}`, this._ownerId);
          pending.remove(`backup:${row.articleId}`, this._ownerId);
        }
        const items = this.data.items.map((item) => item.articleId === row.articleId ? Object.assign({}, item, { state: receipt.state, revision: receipt.revision, stateText: states[receipt.state] || '已删除' }) : item);
        this.setData({ items, visibleItems: visible(items, this.data.filter), notice: action === 'delete' ? '已确认删除。' : action === 'restore' ? '上次公开版本已恢复。' : '已下架，草稿已保留。' });
      } catch (error) {
        if (epoch === this._epoch) this.setData({ error: access.errorText(error), restricted: error.code === 'BEHAVIOR_RESTRICTED' });
      } finally {
        if (epoch === this._epoch) { this.setData({ busy: false }); await this.load(true, true); }
      }
    } });
  }
});
