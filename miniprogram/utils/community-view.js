const { formatDateTime } = require('./format');

const categories = {
  REPORT: [{ value: 'ADVERTISEMENT', label: '广告' }, { value: 'MISLEADING_CONTENT', label: '误导性内容' }, { value: 'OTHER', label: '其他' }],
  FEEDBACK: [{ value: 'BUG', label: '使用问题' }, { value: 'SUGGESTION', label: '建议' }, { value: 'OTHER', label: '其他' }]
};
const states = { OPEN: '已提交', PROCESSING: '处理中', NEEDS_ACTION: '待后续处理', CLOSED: '已完成' };
const decisions = {
  NO_VIOLATION: '未发现违规', DUPLICATE: '重复举报', REQUIRES_GOVERNANCE: '进入后续治理',
  GOVERNANCE_COMPLETED: '已完成治理', ANSWERED: '已答复', RECORDED: '已记录'
};
const appealStates = { OPEN: '等待复核', ACCEPTED: '申诉已接受', REJECTED: '申诉已驳回' };
const uuidPattern = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const sqlUtc = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/;

function governanceTime(value) {
  // The governance SQL projections use UTC DATETIME without an offset.
  return formatDateTime(typeof value === 'string' && sqlUtc.test(value) ? value + 'Z' : value);
}

function validId(value) {
  const n = Number(value);
  return Number.isSafeInteger(n) && n > 0 ? n : null;
}

function targetText(type, article, comment) {
  if (type === 'ARTICLE' && article) return `文章 #${article}`;
  if (type === 'COMMENT' && comment) return `评论 #${comment}`;
  return '无关联内容';
}

function evidenceImage(raw) {
  if (!raw || typeof raw.id !== 'string' || !uuidPattern.test(raw.id)) return null;
  return { id: raw.id, url: `/api/v1/media/case-images/${raw.id}` };
}

function caseRow(raw) {
  const category = (categories[raw.kind] || []).find((row) => row.value === raw.category);
  return {
    caseId: validId(raw.caseId),
    kindText: raw.kind === 'REPORT' ? '内容举报' : '使用反馈',
    categoryText: category ? category.label : '其他',
    stateText: states[raw.state] || '状态待核对',
    targetText: targetText(raw.targetType, validId(raw.articleId), validId(raw.commentId)),
    createdText: formatDateTime(raw.createdAt), updatedText: formatDateTime(raw.updatedAt)
  };
}

function caseDetail(raw) {
  const history = (raw.history || []).map((row) => ({
    revision: row.revision,
    title: decisions[row.outcome] || (row.action === 'CLAIM' ? '已受理' : row.action === 'RELEASE' ? '返回待受理' : states[row.state] || '处理进度更新'),
    reply: typeof row.reply === 'string' ? row.reply : '',
    occurredText: formatDateTime(row.occurredAt)
  }));
  return Object.assign(caseRow(raw), {
    description: typeof raw.description === 'string' ? raw.description : '',
    images: (raw.images || []).map(evidenceImage).filter(Boolean), history
  });
}

function restriction(raw) {
  return {
    id: validId(raw.id), scope: raw.scope, enabled: raw.enabled === true, revision: raw.revision,
    scopeText: raw.scope === 'PUBLISH' ? '发布及作者恢复上架' : '新评论与回复',
    reason: typeof raw.reason === 'string' ? raw.reason : '', updatedText: governanceTime(raw.updatedAt)
  };
}

function appeal(raw) {
  return {
    id: validId(raw.id), restrictionId: validId(raw.restrictionId), restrictionRevision: raw.restrictionRevision,
    state: raw.state, stateText: appealStates[raw.state] || '状态待核对',
    reason: typeof raw.reason === 'string' ? raw.reason : '', reply: typeof raw.reply === 'string' ? raw.reply : '',
    updatedText: governanceTime(raw.updatedAt)
  };
}

module.exports = { categories, validId, targetText, evidenceImage, caseRow, caseDetail, restriction, appeal };
