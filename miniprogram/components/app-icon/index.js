Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    name: { type: String, value: 'document' },
    size: { type: Number, value: 32 }
  },
  data: { src: '' },
  observers: {
    name(name) {
      const allowed = ['search', 'filter', 'close', 'chevron-right', 'refresh', 'document', 'alert', 'lock', 'image', 'link'];
      const safeName = allowed.indexOf(name) >= 0 ? name : 'document';
      this.setData({ src: `/assets/icons/${safeName}.svg` });
    }
  }
});
