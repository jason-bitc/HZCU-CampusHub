const { sourceLabel, formatDate, joinCategoryNames } = require('../utils/format');

function categories(value) {
  const input = value && typeof value === 'object' ? value : {};
  return ['jobs', 'majors', 'companies', 'topics'].reduce((result, key) => {
    result[key] = Array.isArray(input[key]) ? input[key].map((item) => ({ id: item.id, name: String(item.name || '') })) : [];
    return result;
  }, {});
}

function mapSummary(raw) {
  const mappedCategories = categories(raw.categories);
  const jobs = joinCategoryNames(mappedCategories, 'jobs');
  const majors = joinCategoryNames(mappedCategories, 'majors');
  const companies = joinCategoryNames(mappedCategories, 'companies');
  const topics = joinCategoryNames(mappedCategories, 'topics');
  return {
    id: raw.id,
    title: String(raw.title || '未命名经验'),
    summary: String(raw.summary || ''),
    categories: mappedCategories,
    jobText: jobs || '岗位未分类',
    majorText: majors || '专业未标注',
    companyText: companies,
    topicText: topics,
    experienceStage: String(raw.experienceStage || '阶段未标注'),
    sourceType: raw.sourceType || '',
    sourceText: sourceLabel(raw.sourceType),
    publishedAt: raw.publishedAt || '',
    publishedText: formatDate(raw.publishedAt),
    effectiveCoins: raw.counts && Number(raw.counts.effectiveCoins) >= 0 ? Number(raw.counts.effectiveCoins) : 0,
    effectiveLikes: raw.counts && Number(raw.counts.effectiveLikes) >= 0 ? Number(raw.counts.effectiveLikes) : 0
  };
}

function mapDetail(raw) {
  return Object.assign(mapSummary(raw), {
    body: String(raw.body || ''),
    bodyFormat: raw.bodyFormat || 'MARKDOWN',
    sourceNote: String(raw.sourceNote || ''),
    sourceLinks: Array.isArray(raw.sourceLinks) ? raw.sourceLinks.map((item) => ({
      label: String(item.label || '外部资料'),
      url: String(item.url || '')
    })) : []
  });
}

function mapPage(raw) {
  const input = raw && typeof raw === 'object' ? raw : {};
  return {
    items: Array.isArray(input.items) ? input.items.map(mapSummary) : [],
    total: Math.max(0, Number(input.total) || 0),
    page: Math.max(1, Number(input.page) || 1),
    size: Math.max(1, Number(input.size) || 10)
  };
}

function mapCatalog(raw) {
  return categories(raw);
}

module.exports = { mapSummary, mapDetail, mapPage, mapCatalog };
