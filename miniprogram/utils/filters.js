const { cleanKeyword } = require('./query');

const DEFINITIONS = [
  { key: 'jobId', catalogKey: 'jobs', label: '岗位' },
  { key: 'majorId', catalogKey: 'majors', label: '专业' },
  { key: 'companyId', catalogKey: 'companies', label: '公司' },
  { key: 'topicId', catalogKey: 'topics', label: '主题' }
];

function emptyFilters() {
  return { keyword: '', jobId: 0, majorId: 0, companyId: 0, topicId: 0 };
}

function categoryName(catalog, catalogKey, id) {
  const list = catalog && Array.isArray(catalog[catalogKey]) ? catalog[catalogKey] : [];
  const found = list.find((item) => String(item.id) === String(id));
  return found ? found.name : `分类 ${id}`;
}

function activeFilterChips(filters, catalog) {
  const chips = [];
  const keyword = cleanKeyword(filters.keyword);
  if (keyword) chips.push({ key: 'keyword', value: '', label: `关键词：${keyword}` });
  DEFINITIONS.forEach((definition) => {
    const id = Number(filters[definition.key]);
    if (id > 0) {
      chips.push({
        key: definition.key,
        value: id,
        label: `${definition.label}：${categoryName(catalog, definition.catalogKey, id)}`
      });
    }
  });
  return chips;
}

function selectedFilterNames(filters, catalog) {
  return DEFINITIONS.filter((definition) => Number(filters[definition.key]) > 0).map((definition) => ({
    key: definition.key,
    value: Number(filters[definition.key]),
    label: categoryName(catalog, definition.catalogKey, filters[definition.key])
  }));
}

function filterSummary(filters, catalog) {
  const majorId = Number(filters.majorId) || 0;
  const companyId = Number(filters.companyId) || 0;
  return {
    majorText: majorId ? categoryName(catalog, 'majors', majorId) : '不限',
    companyText: companyId ? categoryName(catalog, 'companies', companyId) : '不限'
  };
}

function catalogReady(catalog) {
  return catalog && ['jobs', 'majors', 'companies', 'topics'].every((key) => Array.isArray(catalog[key]));
}

module.exports = { DEFINITIONS, emptyFilters, activeFilterChips, selectedFilterNames, filterSummary, catalogReady, categoryName };
