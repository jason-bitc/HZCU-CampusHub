const SOURCE_LABELS = {
  FIRST_HAND: '亲历分享',
  CURATED: '资料整理',
  DEMO: '演示内容'
};

function sourceLabel(type) {
  return SOURCE_LABELS[type] || '公开经验';
}

function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '发布时间未知';
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${Number(month)}月${Number(day)}日`;
}
function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '时间未知';
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function categoryNames(categories, key) {
  const list = categories && Array.isArray(categories[key]) ? categories[key] : [];
  return list.map((item) => item.name).filter(Boolean);
}

function joinCategoryNames(categories, key) {
  return categoryNames(categories, key).join('、');
}

module.exports = { sourceLabel, formatDate, formatDateTime, categoryNames, joinCategoryNames };
