const { parseMarkdown, normalizeExternalUrl } = require('../../utils/markdown');

function insertResourceCard(nodes, sourceLinks) {
  if (!Array.isArray(sourceLinks) || !sourceLinks.length) return nodes;
  const result = [];
  let paragraphCount = 0;
  let inserted = false;
  nodes.forEach((node) => {
    result.push(node);
    if (node.type === 'paragraph') paragraphCount += 1;
    if (!inserted && node.type === 'paragraph' && paragraphCount >= 2) {
      result.push({ id: 'resource-card', type: 'resource' });
      inserted = true;
    }
  });
  if (!inserted) result.push({ id: 'resource-card', type: 'resource' });
  return result;
}

Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    body: { type: String, value: '' },
    assetBaseUrl: { type: String, value: '' },
    sourceLinks: { type: Array, value: [] },
    sourceNote: { type: String, value: '' }
  },
  data: {
    nodes: [],
    headings: [],
    images: [],
    tocExpanded: false,
    tocDrawerVisible: false,
    firstSourceLink: null,
    resourceTitle: ''
  },
  observers: {
    'body, assetBaseUrl, sourceLinks, sourceNote': function contentChanged(body, assetBaseUrl, sourceLinks, sourceNote) {
      const parsed = parseMarkdown(body, { assetBaseUrl });
      const safeLinks = Array.isArray(sourceLinks) ? sourceLinks : [];
      const firstSourceLink = safeLinks[0] || null;
      this.setData({
        nodes: insertResourceCard(parsed.nodes, safeLinks),
        headings: parsed.headings,
        images: parsed.images,
        tocExpanded: parsed.headings.length > 1,
        tocDrawerVisible: false,
        firstSourceLink,
        resourceTitle: sourceNote || (firstSourceLink ? firstSourceLink.label : '外部资料')
      });
    }
  },
  methods: {
    toggleToc() {
      this.setData({ tocExpanded: !this.data.tocExpanded });
    },
    openToc() {
      this.setData({ tocDrawerVisible: true });
    },
    closeToc() {
      this.setData({ tocDrawerVisible: false });
    },
    scrollToHeading(event) {
      const id = Number(event.currentTarget.dataset.id);
      if (!id) return;
      this.setData({ tocDrawerVisible: false });
      setTimeout(() => {
        wx.createSelectorQuery().in(this).select(`#md-block-${id}`).boundingClientRect((rect) => {
          if (!rect) return;
          wx.createSelectorQuery().selectViewport().scrollOffset((viewport) => {
            wx.pageScrollTo({ scrollTop: Math.max(0, viewport.scrollTop + rect.top - 20), duration: 250 });
          }).exec();
        }).exec();
      }, 80);
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
