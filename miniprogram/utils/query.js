const FILTER_KEYS = ['jobId', 'majorId', 'companyId', 'topicId'];

function cleanKeyword(value) {
  return String(value || '').trim();
}

function validateKeyword(value) {
  const keyword = cleanKeyword(value);
  if (keyword.length > 100) return '关键词最多100个字符';
  if (/[\u0000-\u001f\u007f]/.test(keyword)) return '关键词包含不支持的控制字符';
  return '';
}

function buildArticleQuery(filters, sort, page, size) {
  const query = {
    sort: sort === 'latest' ? 'latest' : 'recognized',
    page: Math.max(1, Number(page) || 1),
    size: Math.min(100, Math.max(1, Number(size) || 10))
  };
  const keyword = cleanKeyword(filters.keyword);
  if (keyword) query.keyword = keyword;
  FILTER_KEYS.forEach((key) => {
    const value = Number(filters[key]);
    if (Number.isSafeInteger(value) && value > 0) query[key] = value;
  });
  return query;
}

function mergeUniqueArticles(current, incoming) {
  const result = Array.isArray(current) ? current.slice() : [];
  const positions = new Map(result.map((item, index) => [String(item.id), index]));
  (Array.isArray(incoming) ? incoming : []).forEach((item) => {
    const key = String(item.id);
    if (positions.has(key)) {
      result[positions.get(key)] = item;
    } else {
      positions.set(key, result.length);
      result.push(item);
    }
  });
  return result;
}

module.exports = { FILTER_KEYS, cleanKeyword, validateKeyword, buildArticleQuery, mergeUniqueArticles };
