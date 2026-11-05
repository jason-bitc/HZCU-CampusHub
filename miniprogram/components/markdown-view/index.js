const { parseMarkdown, normalizeExternalUrl } = require('../../utils/markdown');

Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    body: { type: String, value: '' },
    assetBaseUrl: { type: String, value: '' }
  },
  data: {
    nodes: [],
    headings: [],
    images: [],
    tocExpanded: false
  },
  observers: {
    'body, assetBaseUrl': function bodyChanged(body, assetBaseUrl) {
      const parsed = parseMarkdown(body, { assetBaseUrl });
      this.setData({ nodes: parsed.nodes, headings: parsed.headings, images: parsed.images, tocExpanded: false });
    }
  },
  methods: {
    toggleToc() {
      this.setData({ tocExpanded: !this.data.tocExpanded });
    },
    scrollToHeading(event) {
      const id = Number(event.currentTarget.dataset.id);
      if (!id) return;
      wx.createSelectorQuery().in(this).select(`#md-block-${id}`).boundingClientRect((rect) => {
        if (!rect) return;
        wx.createSelectorQuery().selectViewport().scrollOffset((viewport) => {
          wx.pageScrollTo({ scrollTop: Math.max(0, viewport.scrollTop + rect.top - 20), duration: 250 });
        }).exec();
      }).exec();
    },
    handleImageError(event) {
      const index = Number(event.currentTarget.dataset.index);
      this.setData({ [`nodes[${index}].failed`]: true });
    },
    retryImage(event) {
      const index = Number(event.currentTarget.dataset.index);
      const node = this.data.nodes[index];
      if (!node || !node.originalSrc) return;
      this.setData({ [`nodes[${index}].failed`]: false, [`nodes[${index}].src`]: '' });
      wx.nextTick(() => this.setData({ [`nodes[${index}].src`]: node.originalSrc }));
    },
    previewImage(event) {
      const index = Number(event.currentTarget.dataset.index);
      const node = this.data.nodes[index];
      if (!node || !node.src || node.failed) return;
      wx.previewImage({ current: node.src, urls: this.data.images });
    },
    copyLink(event) {
      const url = normalizeExternalUrl(event.currentTarget.dataset.url);
      if (!url) {
        wx.showToast({ title: '该链接格式不支持', icon: 'none' });
        return;
      }
      wx.setClipboardData({
        data: url,
        success: () => wx.showModal({
          title: '链接已复制',
          content: '小程序不直接跳转任意外部网页。你可以将链接粘贴到浏览器中打开。',
          showCancel: false,
          confirmText: '知道了'
        })
      });
    }
  }
});
