const { request } = require('./request');
const { mapSummary } = require('./article-mapper');
const { upload } = require('./media');
function call(path, method = 'GET', data) { const options = { path: `/api/v1${path}`, method, auth: true }; if (data !== undefined) options.data = data; return request(options).promise; }
function id(value) { if (!Number.isSafeInteger(Number(value)) || Number(value) <= 0) throw new Error('链接编号无效'); return String(Number(value)); }
function favorites(page, size = 20) { return call('/me/favorites', 'GET', { page, size }).then((r) => Object.assign({}, r, { items: r.items.map((row) => row.available ? { articleId: row.articleId, available: true, article: mapSummary(row.article) } : { articleId: row.articleId, available: false }) })); }
module.exports = {
  call, id, favorites,
  profile: () => call('/me/profile'), saveProfile: (data) => call('/me/profile', 'PUT', data),
  contactSettings: () => call('/me/contact-settings'), saveContact: (data) => call('/me/contact-settings', 'PUT', data),
  interaction: (article) => call(`/articles/${id(article)}/interaction-state`),
  like: (article, active) => call(`/articles/${id(article)}/like`, active ? 'PUT' : 'DELETE'),
  favorite: (article, active) => call(`/articles/${id(article)}/favorite`, active ? 'PUT' : 'DELETE'),
  owned: (page, size = 20) => call('/me/articles', 'GET', { page, size }),
  deleteArticle: (article, revision) => call(`/me/articles/${id(article)}?expectedRevision=${encodeURIComponent(revision)}`, 'DELETE'),
  create: (requestId) => call('/me/articles', 'POST', { requestId }),
  draft: (article) => call(`/me/articles/${id(article)}/draft`),
  saveDraft: (article, data) => call(`/me/articles/${id(article)}/draft`, 'PUT', data),
  publish: (article, data) => call(`/me/articles/${id(article)}/publish`, 'POST', data),
  articleState: (article, action, revision) => { if (!['unpublish', 'restore'].includes(action)) throw new Error('操作无效'); return call(`/me/articles/${id(article)}/${action}`, 'POST', { expectedRevision: revision }); },
  uploadArticle: (article, file, key) => upload(`/me/articles/${id(article)}/images`, file, key),
  comments: (article, page) => call(`/articles/${id(article)}/comments`, 'GET', { page, size: 20 }),
  replies: (root, page) => call(`/comments/${id(root)}/replies`, 'GET', { page, size: 20 }),
  comment: (article, data) => call(`/articles/${id(article)}/comments`, 'POST', data),
  deleteComment: (comment) => call(`/comments/${id(comment)}`, 'DELETE'),
  uploadComment: (article, file, key) => upload(`/articles/${id(article)}/comment-images`, file, key),
  articleAuthor: (article) => call(`/articles/${id(article)}/author`),
  author: (author) => call(`/authors/${id(author)}`),
  authorArticles: (author, page) => call(`/authors/${id(author)}/articles`, 'GET', { page, size: 20 }).then((r) => Object.assign({}, r, { items: r.items.map(mapSummary) })),
  authorContact: (author) => call(`/authors/${id(author)}/contact`),
  wallet: () => call('/me/coins'), rewards: () => call('/me/rewards'),
  ledger: (page, snapshot) => { const query = { page, size: 20 }; if (snapshot !== undefined && snapshot !== null) query.snapshot = snapshot; return call('/me/coin-ledger', 'GET', query); },
  claimLogin: (key) => call('/me/rewards/login', 'POST', { requestId: key }),
  startReading: (article) => call(`/articles/${id(article)}/reading-session`, 'POST'),
  claimReading: (reading, key) => call('/me/rewards/reading', 'POST', { sessionId: reading, requestId: key }),
  coinState: (article) => call(`/articles/${id(article)}/coin-state`),
  coin: (article, amount, key) => call(`/articles/${id(article)}/coins`, 'POST', { amount, requestId: key })
};
