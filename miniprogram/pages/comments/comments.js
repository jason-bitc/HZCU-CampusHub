const api = require('../../services/member');
const media = require('../../services/media');
const access = require('../../utils/member-page');
const local = require('../../utils/operation');
const { mapComment } = require('../../utils/comment-view');
const blank = { items: [], root: null, total: 0, page: 0, body: '', uploads: [], replyTo: null, pendingComment: null, commentUncertain: false, loaded: false };
Page({
  data: Object.assign({ articleId: 0, rootId: 0, authStatus: 'checking', busy: false, error: '', notice: '' }, blank),
  onLoad(options) { this.setData({ articleId: Number(options.id) }); access.attach(this, blank, (reason) => { if (reason && reason !== 'logout') this.checkpoint(); }); },
  async onShow() { if (this._choosingMedia && this.data.authStatus === 'ready') return; if (!await access.enter(this)) return; if (!Number.isSafeInteger(this.data.articleId) || this.data.articleId <= 0) { this.setData({ error: '文章链接无效' }); return; } const saved = local.read(`comment:${this.data.articleId}`); if (saved && !this.data.body && !this.data.uploads.length) this.setData(saved); await this.load(true); },
  onHide() { this.checkpoint(); }, onUnload() { this.checkpoint(); access.leave(this); }, login: access.login,
  checkpoint() { if (!this._ownerId) return; try { local.write(`comment:${this.data.articleId}`, { body: this.data.body, uploads: this.data.uploads, replyTo: this.data.replyTo, pendingComment: this.data.pendingComment, commentUncertain: this.data.commentUncertain }, this._ownerId); } catch (_) { this.setData({ notice: '本机副本存储失败，请保留当前页面。' }); } },
  async load(reset, preserveError = false) {
    if (this.data.busy || this.data.authStatus !== 'ready') return; const epoch = this._epoch; const root = this.data.rootId; const page = reset === true ? 1 : this.data.page + 1; this.setData({ busy: true, error: preserveError ? this.data.error : '' });
    try { const result = await (root ? api.replies(root, page) : api.comments(this.data.articleId, page)); if (epoch !== this._epoch || root !== this.data.rootId) return; const seen = new Map((page === 1 ? [] : this.data.items).map((r) => [r.commentId, r])); result.items.map(mapComment).forEach((r) => seen.set(r.commentId, r)); this.setData({ items: [...seen.values()], root: result.root ? mapComment(result.root) : null, total: result.total, page, loaded: true }); }
    catch (error) { if (epoch === this._epoch) this.setData(Object.assign({ error: access.errorText(error) }, error.status === 404 ? { items: [], root: null, loaded: false } : {})); }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  more() { this.load(false); }, retry() { this.load(!this.data.loaded); },
  thread(e) { if (this.data.busy) return; this.setData({ rootId: Number(e.currentTarget.dataset.id), items: [], root: null, page: 0, loaded: false }); this.load(true); },
  roots() { if (this.data.busy) return; this.setData({ rootId: 0, items: [], root: null, page: 0, loaded: false }); this.load(true); },
  reply(e) { if (this.data.pendingComment) return; const commentId = Number(e.currentTarget.dataset.id); this.setData({ replyTo: { commentId, nickname: e.currentTarget.dataset.name || '这位同学' } }); this.checkpoint(); },
  cancelReply() { if (!this.data.pendingComment) { this.setData({ replyTo: null }); this.checkpoint(); } },
  input(e) { if (!this.data.pendingComment && !this.data.busy) { this.setData({ body: e.detail.value }); this.checkpoint(); } },
  async chooseImages() {
    if (this.data.busy || this.data.pendingComment) return; const epoch = this._epoch;
    this._choosingMedia = true;
    try { const selected = await media.choose(); for (const file of selected) { await media.validate(file); if (epoch !== this._epoch) return; this.setData({ uploads: this.data.uploads.concat({ requestId: local.uuid(), filePath: file.tempFilePath, state: 'waiting', image: null, error: '' }) }); this.checkpoint(); } await this.upload(); }
    catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); }
    finally { this._choosingMedia = false; }
  },
  async upload() {
    if (this.data.busy || this.data.pendingComment) return; const epoch = this._epoch; this.setData({ busy: true });
    try { for (let index = 0; index < this.data.uploads.length; index += 1) { const f = this.data.uploads[index]; if (f.state !== 'waiting') continue; try { const image = await api.uploadComment(this.data.articleId, f.filePath, f.requestId); if (epoch !== this._epoch) return; this.setData({ [`uploads[${index}].image`]: image, [`uploads[${index}].state`]: 'done' }); } catch (e) { if (epoch !== this._epoch) return; this.setData({ [`uploads[${index}].state`]: 'failed', [`uploads[${index}].error`]: access.errorText(e) }); } this.checkpoint(); } }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  retryImage(e) { if (this.data.busy || this.data.pendingComment) return; const index = Number(e.currentTarget.dataset.index); this.setData({ [`uploads[${index}].state`]: 'waiting' }); this.upload(); },
  removeImage(e) { if (!this.data.busy && !this.data.pendingComment) { this.setData({ uploads: this.data.uploads.filter((_, i) => i !== Number(e.currentTarget.dataset.index)) }); this.checkpoint(); } },
  async send() {
    if (this.data.busy || this.data.authStatus !== 'ready') return;
    if (!this.data.pendingComment) {
      if (this.data.uploads.some((f) => f.state !== 'done')) { this.setData({ error: '请先重试或移除未上传成功的图片' }); return; }
      if (!this.data.body.trim() && !this.data.uploads.length) { this.setData({ error: '请填写文字或添加图片' }); return; }
      this.setData({ commentUncertain: false, pendingComment: { requestId: local.uuid(), body: this.data.body, imageIds: this.data.uploads.map((f) => f.image.imageId), replyToCommentId: this.data.replyTo ? this.data.replyTo.commentId : null } }); this.checkpoint();
    }
    const epoch = this._epoch; this.setData({ busy: true, error: '' });
    try { const receipt = await api.comment(this.data.articleId, this.data.pendingComment); if (epoch !== this._epoch) return; this.setData({ body: '', replyTo: null, pendingComment: null, commentUncertain: false, uploads: [], notice: `评论已提交 · 编号 ${receipt.commentId}` }); local.remove(`comment:${this.data.articleId}`); }
    catch (error) { if (epoch === this._epoch) { this.setData({ error: access.errorText(error) }); if (!error.status || error.status >= 500) this.setData({ commentUncertain: true }); if (!this.data.commentUncertain && error.status >= 400 && error.status < 500 && ![401, 403, 409].includes(error.status)) this.setData({ pendingComment: null }); this.checkpoint(); } }
    finally { if (epoch === this._epoch) { this.setData({ busy: false }); if (!this.data.pendingComment) this.load(true, true); } }
  },
  copy() { wx.setClipboardData({ data: this.data.body }); },
  stopRetry() { if (!this.data.pendingComment || this.data.busy) return; wx.showModal({ title: '停止重试上次评论？', content: '上次评论可能已提交。请先刷新并核对列表；停止后保留文字副本，继续发送将作为新评论，可能重复。', confirmText: '保留副本', success: (r) => { if (!r.confirm) return; try { local.write(`comment-backup:${this.data.articleId}`, this.data.pendingComment, this._ownerId); this.setData({ pendingComment: null, commentUncertain: false, notice: '已保留原请求副本，请确认没有重复内容后再决定是否提交。' }); this.checkpoint(); } catch (_) { this.setData({ error: '无法保留副本，原请求仍保留。' }); } } }); },
  remove(e) {
    if (this.data.busy) return; const commentId = Number(e.currentTarget.dataset.id);
    wx.showModal({ title: '删除这条本人评论？', content: '删除后正文和图片不再展示，他人的回复会保留。', success: async (r) => { if (!r.confirm) return; const epoch = this._epoch; this.setData({ busy: true }); try { await api.deleteComment(commentId); } catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); } finally { if (epoch === this._epoch) { this.setData({ busy: false }); this.load(true); } } } });
  },
  author(e) { wx.navigateTo({ url: `/pages/author/author?id=${e.currentTarget.dataset.id}` }); }
});
