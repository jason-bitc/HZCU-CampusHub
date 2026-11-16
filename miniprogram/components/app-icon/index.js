Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    name: { type: String, value: 'document' },
    size: { type: Number, value: 32 }
  },
  data: { src: '' },
  observers: {
    name(name) {
      const allowed = ['search', 'filter', 'close', 'chevron-left', 'chevron-right', 'refresh', 'document', 'alert', 'lock', 'image', 'link', 'home', 'favorite', 'create', 'user', 'like', 'coin', 'star', 'comment', 'list', 'check'];
      const safeName = allowed.indexOf(name) >= 0 ? name : 'document';
      this.setData({ src: `/assets/icons/${safeName}.svg` });
    }
  }
});
