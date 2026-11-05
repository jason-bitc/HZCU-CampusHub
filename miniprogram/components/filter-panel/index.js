const { emptyFilters, activeFilterChips } = require('../../utils/filters');
const { validateKeyword } = require('../../utils/query');

Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    visible: { type: Boolean, value: false },
    filters: { type: Object, value: {} },
    catalog: { type: Object, value: {} },
    catalogError: { type: String, value: '' }
  },
  data: {
    draft: emptyFilters(),
    chips: [],
    keywordError: '',
    sections: [
      { key: 'jobId', catalogKey: 'jobs', label: '岗位' },
      { key: 'majorId', catalogKey: 'majors', label: '文章适用专业' },
      { key: 'companyId', catalogKey: 'companies', label: '公司（可选）' },
      { key: 'topicId', catalogKey: 'topics', label: '主题' }
    ]
  },
  observers: {
    visible(visible) {
      if (visible) this.resetDraft(this.data.filters);
    }
  },
  methods: {
    resetDraft(filters) {
      const draft = Object.assign(emptyFilters(), filters || {});
      this.setData({ draft, chips: activeFilterChips(draft, this.data.catalog), keywordError: '' });
    },
    close() { this.triggerEvent('close'); },
    noop() {},
    updateDraft(draft) {
      this.setData({ draft, chips: activeFilterChips(draft, this.data.catalog) });
    },
    handleKeyword(event) {
      const draft = Object.assign({}, this.data.draft, { keyword: event.detail.value });
      this.updateDraft(draft);
    },
    clearChip(event) {
      const key = event.currentTarget.dataset.key;
      const patch = key === 'keyword' ? { keyword: '' } : { [key]: 0 };
      this.updateDraft(Object.assign({}, this.data.draft, patch));
    },
    selectOption(event) {
      const { key, value } = event.currentTarget.dataset;
      const id = Number(value) || 0;
      const nextValue = this.data.draft[key] === id ? 0 : id;
      this.updateDraft(Object.assign({}, this.data.draft, { [key]: nextValue }));
    },
    showSameMajorExplanation() {
      wx.showModal({
        title: '关于“只看同专业”',
        content: '该快捷条件必须读取认证账号已确认的本人专业。本批尚未接入认证，不会假设游客身份或伪造专业。你可以先手动选择文章专业标签。',
        showCancel: false,
        confirmText: '知道了'
      });
    },
    retryCatalog() { this.triggerEvent('retrycatalog'); },
    reset() {
      this.updateDraft(emptyFilters());
    },
    apply() {
      const keywordError = validateKeyword(this.data.draft.keyword);
      if (keywordError) {
        this.setData({ keywordError });
        return;
      }
      this.triggerEvent('apply', { filters: Object.assign({}, this.data.draft) });
    }
  }
});
