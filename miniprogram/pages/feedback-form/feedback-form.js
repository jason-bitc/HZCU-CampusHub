const api = require('../../services/community');
const media = require('../../services/media');
const access = require('../../utils/member-page');
const local = require('../../utils/operation');
const view = require('../../utils/community-view');

const blank = { description: '', category: '', categoryText: '', uploads: [], pending: null, uncertain: false, receipt: null, notice: '' };

Page({
  data: Object.assign({ authStatus: 'checking', busy: false, error: '', kind: 'FEEDBACK', choices: view.categories.FEEDBACK, targetType: null, targetId: null, targetLabel: '', invalidTarget: false }, blank),
  onLoad(options) {
    const type = ['ARTICLE', 'COMMENT'].includes(options.type) ? options.type : null;
    const id = view.validId(options.id);
    const invalidTarget = !!(options.type || options.id) && !(type && id);
    const kind = type && id && options.kind !== 'FEEDBACK' ? 'REPORT' : 'FEEDBACK';
    this._target = { type: invalidTarget ? null : type, id: invalidTarget ? null : id };
    this._scope = `feedback:${this._target.type || 'GENERAL'}:${this._target.id || 0}`;
    this.setData({ kind, choices: view.categories[kind], targetType: this._target.type, targetId: this._target.id, invalidTarget, targetLabel: view.targetText(type, id, id) });
    access.attach(this, blank, (reason) => {
      if (reason && reason !== 'logout') this.checkpoint();
      this._loadedOwner = null;
    });
  },
  async onShow() {
    if (this._choosing || this.data.busy && this.data.authStatus === 'ready') return;
    if (!await access.enter(this)) return;
    if (this._loadedOwner === this._ownerId) return;
    this._loadedOwner = this._ownerId;
    const saved = local.read(this._scope, this._ownerId);
    if (!saved) return;
    const kind = saved.kind === 'REPORT' && this._target.type ? 'REPORT' : 'FEEDBACK';
    const choices = view.categories[kind];
    const category = choices.find((row) => row.value === saved.category);
    this.setData({ kind, choices, category: category ? category.value : '', categoryText: category ? category.label : '', description: saved.description || '', uploads: saved.uploads || [], pending: saved.pending || null, uncertain: saved.uncertain === true, notice: '已恢复本机输入。' });
  },
  onHide() { this.checkpoint(); },
  onUnload() { this.checkpoint(); access.leave(this); },
  login: access.login,
  checkpoint() {
    if (!this._loadedOwner || this.data.receipt || this.data.invalidTarget) return false;
    const { kind, category, description, uploads, pending, uncertain } = this.data;
    try { local.write(this._scope, { kind, category, description, uploads, pending, uncertain }, this._loadedOwner); return true; }
    catch (_) { this.setData({ error: '无法保存本机输入，请保留当前页面。' }); return false; }
  },
  saveInput() { if (this.checkpoint()) this.setData({ notice: '已保存输入及附件状态。未上传的临时图片失效后需要重新选择。' }); },
  changeKind(e) {
    const kind = e.currentTarget.dataset.kind;
    if (this.data.busy || this.data.pending || !view.categories[kind] || kind === 'REPORT' && !this._target.type || kind === this.data.kind) return;
    this.setData({ kind, choices: view.categories[kind], category: '', categoryText: '', error: '' });
    this.checkpoint();
  },
  changeCategory(e) {
    if (this.data.busy || this.data.pending) return;
    const category = this.data.choices[Number(e.detail.value)];
    if (category) this.setData({ category: category.value, categoryText: category.label });
  },
  input(e) { if (!this.data.busy && !this.data.pending) this.setData({ description: e.detail.value }); },
  async chooseImages() {
    if (this.data.busy || this.data.pending || this._choosing) return;
    const epoch = this._epoch;
    this._choosing = true;
    try {
      const selected = await media.choose();
      for (const file of selected) {
        try { await media.validate(file); }
        catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); continue; }
        if (epoch !== this._epoch) return;
        this.setData({ uploads: this.data.uploads.concat({ requestId: local.uuid(), filePath: file.tempFilePath, image: null, state: 'waiting', error: '' }) });
      }
      this.checkpoint();
      await this.uploadWaiting();
    } catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); }
    finally { this._choosing = false; }
  },
  async uploadWaiting() {
    if (this.data.authStatus !== 'ready' || this.data.busy || this.data.pending) return;
    const epoch = this._epoch;
    this.setData({ busy: true });
    try {
      for (let index = 0; index < this.data.uploads.length; index += 1) {
        const row = this.data.uploads[index];
        if (row.state !== 'waiting') continue;
        try {
          const response = await api.uploadEvidence(row.filePath, row.requestId);
          if (epoch !== this._epoch) return;
          const image = view.evidenceImage(response);
          if (!image) throw new Error('附件回执无法识别，请用原图片重试。');
          this.setData({ [`uploads[${index}]`]: { requestId: row.requestId, image, state: 'done', error: '' } });
        } catch (e) {
          if (epoch !== this._epoch) return;
          this.setData({ [`uploads[${index}].state`]: 'failed', [`uploads[${index}].error`]: access.errorText(e) });
        }
        this.checkpoint();
      }
    } finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  retryImage(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (this.data.busy || this.data.pending || !this.data.uploads[index] || this.data.uploads[index].state === 'done') return;
    this.setData({ [`uploads[${index}].state`]: 'waiting' });
    return this.uploadWaiting();
  },
  removeImage(e) {
    if (this.data.busy || this.data.pending) return;
    this.setData({ uploads: this.data.uploads.filter((_, i) => i !== Number(e.currentTarget.dataset.index)) });
    this.checkpoint();
  },
  async submit() {
    if (this.data.authStatus !== 'ready' || this.data.busy || this.data.invalidTarget || this.data.receipt) return;
    let body = this.data.pending;
    if (!body) {
      const { kind, category, description, uploads } = this.data;
      if (!view.categories[kind].some((row) => row.value === category)) { this.setData({ error: '请选择反馈类别。' }); return; }
      if (!description.trim() || [...description].length > 10000) { this.setData({ error: '请填写具体问题，最多 10000 个字符。' }); return; }
      if (uploads.some((row) => row.state !== 'done')) { this.setData({ error: '请先重试或移除上传失败的图片。' }); return; }
      body = { requestId: local.uuid(), kind, category, targetType: this._target.type, targetId: this._target.id, description, imageIds: uploads.map((row) => row.image.id) };
      this.setData({ pending: body, uncertain: false });
    }
    if (!this.checkpoint()) return;
    const epoch = this._epoch;
    this.setData({ busy: true, error: '', notice: '' });
    try {
      const receipt = await api.createCase(body);
      if (epoch !== this._epoch) return;
      if (!view.validId(receipt.caseId)) throw new Error('回执缺少反馈编号，请按原请求核对。');
      local.remove(this._scope, this._ownerId);
      this.setData(Object.assign({}, blank, { receipt: { caseId: receipt.caseId } }));
    } catch (e) {
      if (epoch !== this._epoch) return;
      const uncertain = this.data.uncertain || !e.status || e.status >= 500 || e.kind === 'CONTRACT';
      const canEdit = !uncertain && [400, 404, 413, 415, 422].includes(e.status);
      this.setData({ uncertain, pending: canEdit ? null : body, error: e.status === 404 ? '关联内容当前不可用。输入仍保留，可返回从可用内容发起。' : access.errorText(e) });
      this.checkpoint();
    } finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  record() { if (this.data.receipt) wx.redirectTo({ url: `/pages/feedback/feedback?id=${this.data.receipt.caseId}` }); },
  records() { wx.navigateTo({ url: '/pages/feedback/feedback' }); }
});
