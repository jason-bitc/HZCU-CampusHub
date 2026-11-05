Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    article: { type: Object, value: {} }
  },
  methods: {
    handleTap() {
      const article = this.data.article;
      if (article && article.id) this.triggerEvent('open', { id: article.id });
    }
  }
});
