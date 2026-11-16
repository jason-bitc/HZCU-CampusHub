const api = require('../../services/member');
const access = require('../../utils/member-page');
const { normalizeExternalUrl } = require('../../utils/markdown');
const blank = { author: null, contact: null, articles: [], total: 0, page: 0 };
Page({
  data: Object.assign({ authorId: 0, authStatus: 'checking', error: '', busy: false, contactLoading: false }, blank),
  onLoad(options) { this.setData({ authorId: Number(options.id) }); access.attach(this, blank); },
  async onShow() { if (await access.enter(this)) await this.load(true); },
  onHide() { this.contactRound = (this.contactRound || 0) + 1; this.setData({ contact: null, contactLoading: false }); }, onUnload() { access.leave(this); }, login: access.login,
  async load(reset) {
    if (this.data.busy) return;
    const epoch = this._epoch;
    const page = reset === true ? 1 : this.data.page + 1;
    this.setData({ busy: true, error: '', contact: null });
    try {
      const result = await Promise.all([api.author(this.data.authorId), api.authorArticles(this.data.authorId, page)]);
      if (epoch !== this._epoch) return;
      const articles = result[1];
      this.setData({
        author: result[0], articles: page === 1 ? articles.items : this.data.articles.concat(articles.items),
        total: articles.total, page
      });
    } catch (e) {
      if (epoch === this._epoch) this.setData(Object.assign({ error: access.errorText(e) }, e.status === 404 ? blank : {}));
    } finally {
      if (epoch === this._epoch) this.setData({ busy: false });
    }
  },
  more() { this.load(false); },
  async contact() {
    if (this.data.contactLoading) return null;
    const epoch = this._epoch;
    const round = this.contactRound = (this.contactRound || 0) + 1;
    const current = () => epoch === this._epoch && round === this.contactRound;
    this.setData({ contactLoading: true, contact: null, error: '' });
    try {
      const result = await api.authorContact(this.data.authorId);
      if (!current()) return null;
      const contact = result.enabled ? { enabled: true, type: result.type, value: result.value } : { enabled: false };
      this.setData({ contact });
      return contact;
    } catch (e) {
      if (current()) this.setData({ error: access.errorText(e) });
      return null;
    } finally {
      if (current()) this.setData({ contactLoading: false });
    }
  },
  async copy() {
    const read = this.contact();
    const round = this.contactRound;
    const epoch = this._epoch;
    const c = await read;
    if (!c || !c.enabled || round !== this.contactRound || epoch !== this._epoch) return;
    if (c.type === 'WEB' && !normalizeExternalUrl(c.value)) { this.setData({ error: '链接格式不支持' }); return; }
    wx.setClipboardData({ data: c.value });
  },
  open(e) { wx.navigateTo({ url: `/pages/article-detail/article-detail?id=${e.detail.id}` }); }
});
