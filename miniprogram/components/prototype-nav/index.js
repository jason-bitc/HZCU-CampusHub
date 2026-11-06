Component({
  options: { styleIsolation: 'apply-shared' },
  properties: { active: { type: String, value: 'discover' } },
  methods: { navigate(e) { const name = e.currentTarget.dataset.name; if (!['discover', 'favorites', 'workspace', 'me'].includes(name) || name === this.data.active) return; wx.switchTab({ url: `/pages/${name}/${name}` }); } }
});
