const { call, id } = require('./member');
const { upload } = require('./media');

function cases(page, size = 20, snapshot) {
  const query = { page, size };
  if (snapshot !== null && snapshot !== undefined) query.snapshot = snapshot;
  return call('/me/cases', 'GET', query);
}

module.exports = {
  cases,
  caseDetail: (value) => call(`/me/cases/${id(value)}`),
  createCase: (body) => call('/me/cases', 'POST', body),
  uploadEvidence: (file, requestId) => upload('/me/case-images', file, requestId),
  restrictions: () => call('/me/restrictions'),
  appeals: (page) => call('/me/appeals', 'GET', { page, size: 20 }),
  appeal: (body) => call('/me/appeals', 'POST', body)
};
