const { formatDateTime } = require('./format');
function mapComment(row) {
  const base = { commentId: row.commentId, articleId: row.articleId, rootCommentId: row.rootCommentId, replyToCommentId: row.replyToCommentId, available: row.available === true, canDelete: row.canDelete === true, replyCount: row.replyCount || 0 };
  if (!base.available) return base;
  return Object.assign(base, { body: String(row.body || ''), images: Array.isArray(row.images) ? row.images : [], commenter: row.commenter ? { nickname: String(row.commenter.nickname || '城院同学'), authorId: row.commenter.authorId || null } : null, createdAt: row.createdAt, createdText: formatDateTime(row.createdAt), replyTo: row.replyTo ? { commentId: row.replyTo.commentId, available: row.replyTo.available === true, commenter: row.replyTo.available && row.replyTo.commenter ? { nickname: String(row.replyTo.commenter.nickname || '城院同学') } : null } : null });
}
module.exports = { mapComment };
