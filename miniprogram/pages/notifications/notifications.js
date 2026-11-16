const { call } = require('../../services/member');
const access = require('../../utils/member-page');
const { formatDateTime } = require('../../utils/format');
const { validId } = require('../../utils/community-view');
const blank = { items: [], page: 1, total: 0, unreadCount: 0, loaded: false, error: '' };
const outcomes = {
  HIDDEN: '已隐藏', RESTORED: '已恢复', ANSWERED: '已答复', RECORDED: '已记录',
  NO_VIOLATION: '未发现违规', DUPLICATE: '重复案件', REQUIRES_GOVERNANCE: '待治理',
  GOVERNANCE_COMPLETED: '已完成治理', ENABLED: '限制已启用', DISABLED: '限制已解除',
  ACCEPTED: '申诉已接受', REJECTED: '申诉已驳回', INVALIDATED: '已标记失效'
};

function destination(target) {
  if (!target) return '';
  const id = validId(target.id);
  const article = validId(target.articleId);
  const root = validId(target.rootCommentId);
  switch (target.type) {
    case 'CERTIFICATION': return '/pages/certification/certification';
    case 'CASE': return id ? `/pages/feedback/feedback?id=${id}` : '';
    case 'ARTICLE': return id ? `/pages/article-detail/article-detail?id=${id}` : '';
    case 'RESTRICTION': return '/pages/appeals/appeals';
    case 'APPEAL': return id ? `/pages/certification/certification?appealId=${id}` : '';
    case 'COMMENT': return article && root ? `/pages/comments/comments?id=${article}&root=${root}&comment=${id || root}` : '';
    default: return '';
  }
}

Page({
  data: Object.assign({ busy: false, authStatus: 'checking' }, blank),
  onLoad() { access.attach(this, blank); },
  onShow() { if (access.enterPersonal(this)) this.load(this.data.page); },
  onUnload() { access.leave(this); },
  login: access.login,
  async load(page) {
    if (this.data.busy || this.data.authStatus !== 'ready') return;
    const epoch = this._epoch;
    this.setData({ busy: true, error: '' });
    try {
      const result = await call('/me/notifications', 'GET', { page, size: 20 });
      if (epoch !== this._epoch) return;
      const items = result.items.map((row) => ({
        id: row.id, message: row.message, readAt: row.readAt, time: formatDateTime(row.createdAt),
        outcome: outcomes[row.outcome] || '', url: destination(row.target)
      }));
      this.setData({ items, page: result.page, total: result.total, unreadCount: result.unreadCount, loaded: true });
    } catch (e) {
      if (epoch === this._epoch) this.setData({ items: [], loaded: false, error: access.errorText(e) });
    } finally {
      if (epoch === this._epoch) this.setData({ busy: false });
    }
  },
  refresh() { this.load(1); },
  previous() { if (this.data.page > 1) this.load(this.data.page - 1); },
  next() { if (this.data.page * 20 < this.data.total) this.load(this.data.page + 1); },
  async read(e) {
    const id = validId(e.currentTarget.dataset.id);
    if (!this.data.items.some((row) => row.id === id && !row.readAt) || this.data.busy) return;
    const epoch = this._epoch;
    this.setData({ busy: true, error: '' });
    try {
      const receipt = await call(`/me/notifications/${id}/read`, 'PUT');
      if (epoch !== this._epoch) return;
      const items = this.data.items.map((row) => row.id === id ? Object.assign({}, row, { readAt: receipt.readAt }) : row);
      this.setData({ items, unreadCount: Math.max(0, this.data.unreadCount - 1) });
    } catch (error) {
      if (epoch === this._epoch) this.setData({ error: access.errorText(error) });
    } finally {
      if (epoch === this._epoch) this.setData({ busy: false });
    }
  },
  open(e) {
    const row = this.data.items.find((item) => item.id === validId(e.currentTarget.dataset.id));
    if (row && row.url) wx.navigateTo({ url: row.url });
  }
});
