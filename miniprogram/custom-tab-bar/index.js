Component({
  options: { styleIsolation: 'apply-shared' },
  data: { active: 'discover' },
  lifetimes: { attached() { const pages = getCurrentPages(); const current = pages[pages.length - 1]; if (current) { const name = current.route.split('/')[1]; if (['discover', 'favorites', 'workspace', 'me'].includes(name)) this.setData({ active: name }); } } }
});
