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
    sourceNote: { type: String, value: '' },
    demoMode: { type: Boolean, value: false }
  },
  data: {
    nodes: [],
    headings: [],
    images: [],
    tocDrawerVisible: false,
    tocSearch: '',
    filteredHeadings: [],
    currentHeadingId: 0,
    firstSourceLink: null,
  },
  observers: {
    'body, assetBaseUrl, sourceLinks, sourceNote': function contentChanged(body, assetBaseUrl, sourceLinks, sourceNote) {
      const parsed = parseMarkdown(body, { assetBaseUrl });
      const safeLinks = Array.isArray(sourceLinks) ? sourceLinks : [];
      const firstSourceLink = safeLinks[0] || null;
      this.anchorTops = null;
      this.setData({
        nodes: insertResourceCard(parsed.nodes, safeLinks),
        headings: parsed.headings,
        filteredHeadings: parsed.headings,
        tocSearch: '',
        currentHeadingId: 0,
        images: parsed.images,
        tocDrawerVisible: false,
        firstSourceLink
      }, () => this.measureHeadings());
    }
  },
  methods: {
    openToc() {
      this.setData({ tocDrawerVisible: true, tocSearch: '', filteredHeadings: this.data.headings });
      this.triggerEvent('drawerchange', { visible: true });
    },
    closeToc() {
      this.setData({ tocDrawerVisible: false });
      this.triggerEvent('drawerchange', { visible: false });
    },
    stopTouchMove() {},
    searchToc(event) {
      const tocSearch = event.detail.value;
      const keyword = tocSearch.trim().toLowerCase();
      this.setData({ tocSearch, filteredHeadings: this.data.headings.filter((item) => item.text.toLowerCase().includes(keyword)) });
    },
    measureHeadings() {
      wx.nextTick(() => {
        wx.createSelectorQuery().in(this).selectAll('.md-heading').boundingClientRect((rects) => {
          wx.createSelectorQuery().selectViewport().scrollOffset((viewport) => {
            if (!viewport || !rects) return;
            this.anchorTops = rects.map((rect, index) => ({ id: this.data.headings[index] && this.data.headings[index].id, top: viewport.scrollTop + rect.top }));
            this.updateReadingPosition(viewport.scrollTop);
          }).exec();
        }).exec();
      });
    },
    updateReadingPosition(scrollTop) {
      if (!this.anchorTops) return;
      let current = 0;
      this.anchorTops.forEach((heading) => { if (heading.top <= scrollTop + 80) current = heading.id; });
      if (current !== this.data.currentHeadingId) this.setData({ currentHeadingId: current });
    },
    scrollToHeading(event) {
      const id = Number(event.currentTarget.dataset.id);
      if (!id) return;
      this.closeToc();
      wx.nextTick(() => {
        wx.createSelectorQuery().in(this).select(`#md-block-${id}`).boundingClientRect((rect) => {
          if (!rect) return;
          wx.createSelectorQuery().selectViewport().scrollOffset((viewport) => {
            if (!viewport) return;
            this.setData({ currentHeadingId: id });
            wx.pageScrollTo({ scrollTop: Math.max(0, viewport.scrollTop + rect.top - 20), duration: 250 });
          }).exec();
        }).exec();
      });
    },
    handleImageError(event) {
      const index = Number(event.currentTarget.dataset.index);
      this.setData({ [`nodes[${index}].failed`]: true });
      this.measureHeadings();
    },
    handleImageLoad() { this.measureHeadings(); },
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
      const urls = this.data.nodes.filter((item) => item.type === 'image' && item.src && !item.failed).map((item) => item.src);
      wx.previewImage({ current: node.src, urls });
    },
    goToSources() {
      this.triggerEvent('sources');
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
        }),
        fail: () => wx.showToast({ title: '复制失败，请重试', icon: 'none' })
      });
    }
  }
});
