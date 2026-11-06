const { emptyFilters, selectedFilterNames, categoryName } = require('../../utils/filters');
const { validateKeyword } = require('../../utils/query');

function findCompany(catalog, value) {
  const companies = catalog && Array.isArray(catalog.companies) ? catalog.companies : [];
  const keyword = String(value || '').trim();
  if (!keyword) return { exact: null, matches: [] };
  return {
    exact: companies.find((item) => item.name === keyword) || null,
    matches: companies.filter((item) => item.name.indexOf(keyword) >= 0)
  };
}

Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    visible: { type: Boolean, value: false },
    filters: { type: Object, value: {} },
    catalog: { type: Object, value: {} },
    catalogLoading: { type: Boolean, value: false },
    catalogError: { type: String, value: '' },
    signedIn: { type: Boolean, value: false }, ownMajorId: { type: Number, value: 0 }, profileError: { type: String, value: '' }
  },
  data: {
    draft: emptyFilters(),
    selectedChips: [],
    companyInput: '',
    companySuggestions: [],
    companyError: ''
  },
  observers: {
    visible(visible) {
      if (visible) this.resetDraft(this.data.filters);
    },
    'visible, catalog': function catalogChanged(visible, catalog) {
      if (visible) this.syncCompany(this.data.companyInput, catalog);
    }
  },
  methods: {
    resetDraft(filters) {
      const draft = Object.assign(emptyFilters(), filters || {});
      const currentCompany = draft.companyId ? categoryName(this.data.catalog, 'companies', draft.companyId) : '';
      this.setData({
        draft,
        selectedChips: selectedFilterNames(draft, this.data.catalog),
        companyInput: currentCompany,
        companySuggestions: [],
        companyError: ''
      });
    },
    close() { this.triggerEvent('close'); },
    sameMajor() { if (this.data.ownMajorId && this.data.catalog.majors.some((m) => m.id === this.data.ownMajorId)) this.updateDraft(Object.assign({}, this.data.draft, { majorId: this.data.ownMajorId })); else this.triggerEvent(this.data.signedIn ? 'profile' : 'login'); },
    retry() { this.triggerEvent('retry'); },
    updateDraft(draft) {
      this.setData({ draft, selectedChips: selectedFilterNames(draft, this.data.catalog) });
    },
    clearSelected(event) {
      const key = event.currentTarget.dataset.key;
      const patch = {};
      if (key === 'companyId') {
        patch.companyInput = '';
        patch.companySuggestions = [];
        patch.companyError = '';
      }
      this.setData(patch);
      this.updateDraft(Object.assign({}, this.data.draft, { [key]: 0 }));
    },
    selectMajor(event) {
      const id = Number(event.currentTarget.dataset.value) || 0;
      const majorId = this.data.draft.majorId === id ? 0 : id;
      this.updateDraft(Object.assign({}, this.data.draft, { majorId }));
    },
    selectJob(event) {
      const id = Number(event.currentTarget.dataset.value) || 0;
      this.updateDraft(Object.assign({}, this.data.draft, { jobId: id }));
    },
    selectTopic(event) {
      const id = Number(event.currentTarget.dataset.value) || 0;
      const topicId = this.data.draft.topicId === id ? 0 : id;
      this.updateDraft(Object.assign({}, this.data.draft, { topicId }));
    },
    syncCompany(value, catalog) {
      const keyword = String(value || '').trim();
      const { exact, matches } = findCompany(catalog || this.data.catalog, keyword);
      const companyId = exact ? exact.id : 0;
      let companyError = '';
      if (keyword && !companyId) {
        companyError = matches.length ? '请从下方选择公司。' : '没有匹配的公司，请修改输入或清空公司条件。';
      }
      const draft = Object.assign({}, this.data.draft, { companyId });
      this.setData({
        companyInput: value,
        companySuggestions: keyword && !exact ? matches : [],
        companyError,
        draft,
        selectedChips: selectedFilterNames(draft, this.data.catalog)
      });
    },
    handleCompanyInput(event) {
      this.syncCompany(event.detail.value, this.data.catalog);
    },
    selectCompanySuggestion(event) {
      const id = Number(event.currentTarget.dataset.id) || 0;
      const name = categoryName(this.data.catalog, 'companies', id);
      const draft = Object.assign({}, this.data.draft, { companyId: id });
      this.setData({
        companyInput: name,
        companySuggestions: [],
        companyError: '',
        draft,
        selectedChips: selectedFilterNames(draft, this.data.catalog)
      });
    },
    clearCompany() {
      this.setData({ companyInput: '', companySuggestions: [], companyError: '' });
      this.updateDraft(Object.assign({}, this.data.draft, { companyId: 0 }));
    },
    reset() {
      this.setData({ companyInput: '', companySuggestions: [], companyError: '' });
      this.updateDraft(Object.assign(emptyFilters(), { keyword: this.data.filters.keyword || '' }));
    },
    apply() {
      if (this.data.catalogLoading || this.data.catalogError) return;
      const keywordError = validateKeyword(this.data.draft.keyword);
      if (keywordError) {
        wx.showToast({ title: keywordError, icon: 'none' });
        return;
      }
      if (this.data.companyError) {
        wx.showToast({ title: '请先处理公司筛选', icon: 'none' });
        return;
      }
      this.triggerEvent('apply', { filters: Object.assign({}, this.data.draft) });
    }
  }
});
