const media = require('../../services/media');
const session = require('../../services/session');
Component({
  options: { styleIsolation: 'apply-shared' },
  properties: { url: { type: String, value: '' }, alt: { type: String, value: '图片' } },
  data: { src: '', error: '', loading: false },
  observers: { url() { if (this._attached) this.load(); } },
  lifetimes: {
    attached() { this._attached = true; this._off = session.subscribe(() => this.clear()); this.load(); },
    detached() { this._attached = false; if (this._off) this._off(); this.clear(); }
  },
  pageLifetimes: { hide() { this.clear(); }, show() { if (this._attached) this.load(); } },
  methods: {
    clear() { this._round = (this._round || 0) + 1; if (this.data.src) media.release(this.data.src); this.setData({ src: '', loading: false }); },
    async load() {
      this.clear(); const round = this._round;
      if (!this.data.url) return;
      this.setData({ loading: true, error: '' });
      try { const src = await media.download(this.data.url); if (round !== this._round) { media.release(src); return; } this.setData({ src, loading: false }); }
      catch (e) { if (round === this._round) this.setData({ error: e.message || '图片读取失败', loading: false }); }
    },
    preview() { if (this.data.src) wx.previewImage({ current: this.data.src, urls: [this.data.src] }); }
  }
});
