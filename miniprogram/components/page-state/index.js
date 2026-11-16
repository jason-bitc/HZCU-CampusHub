Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    status: { type: String, value: 'loading' },
    title: { type: String, value: '' },
    description: { type: String, value: '' },
    actionText: { type: String, value: '' },
    secondaryText: { type: String, value: '' },
    traceId: { type: String, value: '' },
    compact: { type: Boolean, value: false }
  },
  data: {
    iconName: 'document'
  },
  observers: {
    status(status) {
      const icons = { loading: 'document', empty: 'search', success: 'check', error: 'alert', unavailable: 'lock', permission: 'lock' };
      this.setData({ iconName: icons[status] || 'document' });
    }
  },
  methods: {
    handleAction() { this.triggerEvent('action'); },
    handleSecondary() { this.triggerEvent('secondary'); }
  }
});
