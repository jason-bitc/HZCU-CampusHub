Component({
  options: { styleIsolation: 'apply-shared' },
  methods: {
    showFuture(event) {
      const name = event.currentTarget.dataset.name || '该功能';
      wx.showToast({ title: `${name}后续批次开放`, icon: 'none' });
    }
  }
});
