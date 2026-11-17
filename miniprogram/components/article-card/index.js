Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    article: { type: Object, value: {} },
    feed: { type: Boolean, value: false }
  },
  methods: {
    handleTap() {
      const article = this.data.article;
      if (article && article.id) this.triggerEvent('open', { id: article.id });
    }
  }
});
