const api = require('../../services/member');
const media = require('../../services/media');
const access = require('../../utils/member-page');
const local = require('../../utils/operation');
const content = require('../../utils/editor-content');
const { bytes } = require('../../utils/validation');
const config = require('../../config/env');
const sourceTypes = [null, 'FIRST_HAND', 'CURATED', 'DEMO'];
const stateNames = { DRAFT: '草稿', PUBLISHED: '已发布', AUTHOR_OFFLINE: '作者已下架', ADMIN_HIDDEN: '管理员已隐藏', DELETED: '已删除' };
const blank = { form: null, revision: 0, articleState: '', stateText: '', readOnly: false, complexSource: false, uploads: [], recovery: null, conflict: null, pendingPublish: null, reconciled: null, preview: false, blocks: [], blockSupported: true, dirty: false, tagGroups: [], sourceIndex: 0, tocDrawerVisible: false, hasBackup: false, publishRestricted: false };
Page({
  data: Object.assign({ authStatus: 'checking', articleId: 0, busy: false, error: '', notice: '', catalog: null, tagGroups: [], assetBaseUrl: config.apiBaseUrl, webHandoff: !!config.webBaseUrl, tocDrawerVisible: false, sourceNames: ['请选择来源', '亲历分享', '资料整理', '演示内容'], sourceIndex: 0, hintsVisible: false, orderVisible: false }, blank),
  onLoad(options) {
    const articleId = Number(options.id); this.setData({ articleId });
    access.attach(this, blank, (reason) => { if (reason && reason !== 'logout') this.checkpoint(); this.saved = null; });
  },
  async onShow() {
    if (this._choosingMedia && this.data.authStatus === 'ready') return;
    if (!Number.isSafeInteger(this.data.articleId) || this.data.articleId <= 0) { this.setData({ authStatus: 'error', error: '草稿编号无效，请返回创作页' }); return; }
    if (!await access.enter(this)) return; if (!this.data.form) await this.load();
  },
  onHide() { this.checkpoint(); },
  onUnload() { this.checkpoint(); access.leave(this); }, login: access.login,
  checkpoint() {
    if (!this.data.form || !this._ownerId) return;
    try { local.write(`draft:${this.data.articleId}`, { form: this.data.form, baseRevision: this.data.revision, uploads: this.data.uploads, pendingPublish: this.data.pendingPublish, dirty: this.data.dirty, savedAt: new Date().toISOString() }, this._ownerId); }
    catch (_) { this.setData({ notice: '本机副本存储失败，请保持当前页面并先复制正文。云端保存尚未确认。' }); }
  },
  async load() {
    const epoch = this._epoch; this.setData({ busy: true, error: '' });
    try {
      const [draft, catalog, profile] = await Promise.all([api.draft(this.data.articleId), api.call('/catalog'), api.profile()]); if (epoch !== this._epoch) return;
      this.setData({ catalog }); this.applyDraft(draft);
      const recovery = local.read(`draft:${this.data.articleId}`); this.setData({ hasBackup: !!local.read(`backup:${this.data.articleId}`) }); if (recovery && (JSON.stringify(recovery.form) !== this.saved || recovery.pendingPublish || (recovery.uploads || []).some((f) => f.state !== 'done'))) this.setData({ recovery });
      else if (draft.revision === 0 && !this.data.form.majorIds.length && profile.majorId && catalog.majors.some((m) => m.id === profile.majorId)) this.updateForm(Object.assign({}, this.data.form, { majorIds: [profile.majorId] }));
    } catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); } finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  applyDraft(draft) { const form = content.formFromDraft(draft); const complexSource = content.blocks(form.body) === null; this.saved = JSON.stringify(form); this.setData({ revision: draft.revision, articleState: draft.state, stateText: stateNames[draft.state] || '状态待核对', complexSource, preview: complexSource || this.data.preview, readOnly: complexSource || draft.state === 'ADMIN_HIDDEN' || draft.state === 'DELETED', dirty: false }); this.updateForm(form, false); },
  updateForm(form, dirty = true) {
    const groups = this.data.catalog ? [['jobs', 'jobIds', '目标岗位'], ['majors', 'majorIds', '文章适用专业'], ['companies', 'companyIds', '公司（可选）'], ['topics', 'topicIds', '主题（可选）']].map(([name, field, label]) => ({ name, field, label, items: this.data.catalog[name].map((item) => Object.assign({}, item, { selected: form[field].includes(item.id) })) })) : [];
    const blocks = content.blocks(form.body);
    this.setData({ form, tagGroups: groups, dirty: dirty && JSON.stringify(form) !== this.saved, blocks: blocks || [], blockSupported: !!blocks, sourceIndex: Math.max(0, sourceTypes.indexOf(form.sourceType)) });
    if (dirty) this.checkpoint();
    if (this.data.dirty && wx.enableAlertBeforeUnload) wx.enableAlertBeforeUnload({ message: '修改尚未保存到云端，本机副本已尽力保留。' });
    else if (wx.disableAlertBeforeUnload) wx.disableAlertBeforeUnload();
  },
  input(e) { if (!this.editable()) return; const field = e.currentTarget.dataset.field; if (['title', 'summary', 'body', 'experienceStage', 'sourceNote'].includes(field)) this.updateForm(Object.assign({}, this.data.form, { [field]: e.detail.value })); },
  editable() { return this.data.authStatus === 'ready' && !!this.data.form && !this.data.readOnly && !this.data.busy && !this.data.conflict && !this.data.pendingPublish; },
  toggleTag(e) { if (!this.editable()) return; const field = e.currentTarget.dataset.field; if (!['jobIds', 'majorIds', 'companyIds', 'topicIds'].includes(field)) return; const id = Number(e.currentTarget.dataset.id); const ids = this.data.form[field]; this.updateForm(Object.assign({}, this.data.form, { [field]: ids.includes(id) ? ids.filter((n) => n !== id) : ids.concat(id) })); },
  source(e) { if (this.editable()) this.updateForm(Object.assign({}, this.data.form, { sourceType: sourceTypes[Number(e.detail.value)] })); },
  linkInput(e) { if (!this.editable()) return; const index = Number(e.currentTarget.dataset.index); const field = e.currentTarget.dataset.field; if (!['label', 'url'].includes(field)) return; const links = this.data.form.sourceLinks.map((row) => Object.assign({}, row)); links[index][field] = e.detail.value; this.updateForm(Object.assign({}, this.data.form, { sourceLinks: links })); },
  addLink() { if (this.editable()) this.updateForm(Object.assign({}, this.data.form, { sourceLinks: this.data.form.sourceLinks.concat({ label: '', url: '' }) })); },
  removeLink(e) { if (this.editable()) this.updateForm(Object.assign({}, this.data.form, { sourceLinks: this.data.form.sourceLinks.filter((_, i) => i !== Number(e.currentTarget.dataset.index)) })); },
  toggleHints() { this.setData({ hintsVisible: !this.data.hintsVisible }); },
  toggleOrder() { this.setData({ orderVisible: !this.data.orderVisible }); },
  move(e) { if (!this.editable()) return; this.updateForm(Object.assign({}, this.data.form, { body: content.moveBlock(this.data.form.body, Number(e.currentTarget.dataset.index), Number(e.currentTarget.dataset.delta)) })); },
  removeBlock(e) { if (!this.editable()) return; const index = Number(e.currentTarget.dataset.index); wx.showModal({ title: '移除这一段？', content: '只移除草稿中的文字或图片引用，保存前可通过本机副本核对。', success: (r) => { if (r.confirm && this.editable()) this.updateForm(Object.assign({}, this.data.form, { body: content.removeBlock(this.data.form.body, index) })); } }); },
  addText() { if (this.editable()) this.updateForm(Object.assign({}, this.data.form, { body: this.data.form.body + '\n\n新段落，请在正文中编辑。' })); },
  async chooseImages() {
    if (!this.editable()) return;
    const epoch = this._epoch;
    this._choosingMedia = true;
    try { const selected = await media.choose(); if (epoch !== this._epoch) return; for (const file of selected) { try { await media.validate(file); if (epoch !== this._epoch) return; const entry = { filePath: file.tempFilePath, requestId: local.uuid(), state: 'waiting', error: '', image: null }; this.setData({ uploads: this.data.uploads.concat(entry) }); this.checkpoint(); } catch (error) { this.setData({ error: error.message }); } } await this.uploadWaiting(); }
    catch (error) { if (epoch === this._epoch) this.setData({ error: access.errorText(error) }); }
    finally { this._choosingMedia = false; }
  },
  async uploadWaiting() {
    if (!this.editable()) return; this.setData({ busy: true }); const epoch = this._epoch;
    try { for (let i = 0; i < this.data.uploads.length; i += 1) { const entry = this.data.uploads[i]; if (entry.state !== 'waiting') continue; this.setData({ [`uploads[${i}].state`]: 'uploading' });
      try { const image = await api.uploadArticle(this.data.articleId, entry.filePath, entry.requestId); if (epoch !== this._epoch) return; this.setData({ [`uploads[${i}].state`]: 'done', [`uploads[${i}].image`]: image }); this.updateForm(Object.assign({}, this.data.form, { body: `${this.data.form.body}\n\n![图片](${image.url})` })); }
      catch (error) { if (epoch !== this._epoch) return; this.setData({ [`uploads[${i}].state`]: 'failed', [`uploads[${i}].error`]: access.errorText(error) }); }
      this.checkpoint();
    } } finally { if (epoch === this._epoch) { this.setData({ busy: false }); this.checkpoint(); } }
  },
  retryUpload(e) { if (!this.editable()) return; const i = Number(e.currentTarget.dataset.index); this.setData({ [`uploads[${i}].state`]: 'waiting' }); this.uploadWaiting(); },
  removeUpload(e) { if (!this.editable()) return; this.setData({ uploads: this.data.uploads.filter((_, i) => i !== Number(e.currentTarget.dataset.index)) }); this.checkpoint(); },
  async save() {
    if (!this.editable()) return false;
    const form = this.data.form; if (bytes(form.body) > 1048576) { this.setData({ error: '正文超过 1MiB，请保留副本后缩短内容' }); return false; }
    const epoch = this._epoch; this.checkpoint(); this.setData({ busy: true, error: '', notice: '正在保存到云端…' });
    try { const draft = await api.saveDraft(this.data.articleId, Object.assign({ expectedRevision: this.data.revision }, form)); if (epoch !== this._epoch) return false; this.applyDraft(draft); this.setData({ notice: `已保存到云端 · 修订 ${draft.revision}` }); local.remove(`draft:${this.data.articleId}`); this.checkpoint(); return true; }
    catch (error) { if (epoch !== this._epoch) return false; this.setData({ error: access.errorText(error), notice: '云端保存未确认，本机副本已保留。' }); if (error.code === 'REVISION_CONFLICT') await this.readConflict(); return false; }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  async readConflict() { const epoch = this._epoch; try { const latest = await api.draft(this.data.articleId); if (epoch === this._epoch) this.setData({ conflict: { latest, serverRows: content.describe(content.formFromDraft(latest), this.data.catalog), localRows: content.describe(this.data.form, this.data.catalog) } }); } catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); } },
  recover() { const r = this.data.recovery; if (!r || this.data.readOnly) return; this.setData({ uploads: (r.uploads || []).map((f) => Object.assign({}, f, { state: f.state === 'uploading' ? 'failed' : f.state })), pendingPublish: r.pendingPublish || null, recovery: null }); const revision = this.data.revision; this.setData({ revision: r.baseRevision }); this.updateForm(r.form); if (revision !== r.baseRevision && !r.pendingPublish) this.readConflict(); },
  backup() { const recovery = local.read(`backup:${this.data.articleId}`); if (recovery) this.setData({ recovery }); },
  keepServer() { this.setData({ recovery: null }); },
  resolve(e) {
    const conflict = this.data.conflict; if (!conflict) return; this.checkpoint(); local.write(`backup:${this.data.articleId}`, { form: this.data.form, baseRevision: this.data.revision, uploads: this.data.uploads, pendingPublish: this.data.pendingPublish, dirty: this.data.dirty, savedAt: new Date().toISOString() }, this._ownerId); this.setData({ hasBackup: true });
    if (e.currentTarget.dataset.keep === 'local' && content.blocks(conflict.latest.content.body) === null) { this.setData({ error: '云端新版本含复杂 Markdown。本机副本已保留，请复制后在完整编辑器中比较合并。' }); return; }
    if (e.currentTarget.dataset.keep === 'local') { this.setData({ revision: conflict.latest.revision, articleState: conflict.latest.state, stateText: stateNames[conflict.latest.state] || '状态待核对', readOnly: conflict.latest.state === 'ADMIN_HIDDEN' || conflict.latest.state === 'DELETED', conflict: null, notice: '已保留本机内容，请比较修改后再次保存。尚未写入云端。' }); this.checkpoint(); }
    else { this.applyDraft(conflict.latest); this.setData({ conflict: null, notice: '已载入云端版本，原本机副本仍保留。' }); }
  },
  copy() { if (this.data.form) wx.setClipboardData({ data: JSON.stringify(this.data.form, null, 2) }); },
  copyBody() { if (this.data.form) wx.setClipboardData({ data: this.data.form.body }); },
  copyRecovery() { if (this.data.recovery) wx.setClipboardData({ data: JSON.stringify(this.data.recovery.form, null, 2) }); },
  preview() { if (!this.data.form || this.data.busy) return; this.setData({ preview: !this.data.preview }); },
  drawer(e) { this.setData({ tocDrawerVisible: e.detail.visible }); },
  previewSources() { wx.createSelectorQuery().select('#preview-sources').boundingClientRect((rect) => { if (rect) wx.createSelectorQuery().selectViewport().scrollOffset((view) => { if (view) wx.pageScrollTo({ scrollTop: view.scrollTop + rect.top - 20, duration: 200 }); }).exec(); }).exec(); },
  requestPublish() {
    if (!this.editable()) return;
    const missing = content.publishErrors(this.data.form); if (missing.length) { this.setData({ error: `发布前请：${missing.join('、')}` }); return; }
    if (this.data.uploads.some((f) => f.state !== 'done')) { this.setData({ error: '仍有图片尚未上传成功，请重试或移除失败项' }); return; }
    wx.showModal({ title: '公开发布这篇经验？', content: '标题、正文、分类、来源及正文图片将可被游客免费阅读。请勿包含不愿公开的个人信息。', confirmText: '确认发布', success: async (r) => { if (!r.confirm || !this.editable()) return; if (this.data.dirty && !await this.save()) return; const pendingPublish = { expectedRevision: this.data.revision, requestId: local.uuid() }; this.setData({ pendingPublish }); this.checkpoint(); this.retryPublish(); } });
  },
  async retryPublish() {
    if (!this.data.pendingPublish || this.data.busy || this.data.authStatus !== 'ready') return; const epoch = this._epoch; this.setData({ busy: true, error: '', notice: '正在核对发布结果…' });
    try { const receipt = await api.publish(this.data.articleId, this.data.pendingPublish); if (epoch !== this._epoch) return; this.setData({ pendingPublish: null, reconciled: null, publishRestricted: false, revision: receipt.revision, articleState: 'PUBLISHED', stateText: '已发布', notice: `发布成功 · 公开版本 ${receipt.versionId}`, dirty: false }); this.saved = JSON.stringify(this.data.form); this.checkpoint(); }
    catch (error) {
      if (epoch !== this._epoch) return;
      if (error.code === 'BEHAVIOR_RESTRICTED') {
        this.setData({ error: access.errorText(error), publishRestricted: true, pendingPublish: null, notice: '发布未受理，草稿已保留。' });
        this.checkpoint();
      } else this.setData({ error: access.errorText(error), notice: '发布结果未确认。继续核对会沿用原请求，不会重新保存或新建公开版本。' });
    }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  async reconcile() { const epoch = this._epoch; try { const draft = await api.draft(this.data.articleId); if (epoch === this._epoch) this.setData({ reconciled: draft }); } catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); } },
  endPending() { if (!this.data.reconciled) return; wx.showModal({ title: '停止重试上次发布？', content: '上次请求可能已经成功。请先查看当前公开文章核对；停止后保留本机内容，再按云端修订处理差异。', success: (r) => { if (!r.confirm) return; this.setData({ pendingPublish: null }); this.readConflict(); this.checkpoint(); } }); },
  appeals() { wx.navigateTo({ url: '/pages/appeals/appeals?scope=PUBLISH' }); },
  copyWebEditor() {
    if (!config.webBaseUrl || this.data.authStatus !== 'ready' || !this.data.complexSource || this.data.articleState === 'ADMIN_HIDDEN') return;
    wx.setClipboardData({ data: `${config.webBaseUrl}/#/editor/${this.data.articleId}` });
  },
  viewPublished() { wx.navigateTo({ url: `/pages/article-detail/article-detail?id=${this.data.articleId}` }); }
});
