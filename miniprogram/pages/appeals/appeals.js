const api = require('../../services/community');
const access = require('../../utils/member-page');
const local = require('../../utils/operation');
const view = require('../../utils/community-view');

const scope = 'appeal:input';
const blank = { restrictions: [], records: [], total: 0, page: 0, loaded: false, activeCount: 0, selected: null, reason: '', pending: null, uncertain: false, notice: '' };
Page({
  data: Object.assign({ authStatus: 'checking', busy: false, error: '' }, blank),
  onLoad(options) {
    this._preferredScope = ['PUBLISH', 'COMMENT'].includes(options.scope) ? options.scope : '';
    access.attach(this, blank, (why) => {
      if (why && why !== 'logout') this.checkpoint();
      this._loadedOwner = null;
    });
  },
  async onShow() {
    if (this.data.busy && this.data.authStatus === 'ready') return;
    if (!await access.enter(this)) return;
    if (this._loadedOwner !== this._ownerId) {
      this._loadedOwner = this._ownerId;
      const saved = local.read(scope, this._ownerId);
      if (saved) this.setData({ selected: saved.selected || null, reason: saved.reason || '', pending: saved.pending || null, uncertain: saved.uncertain === true });
    }
    await this.load(true);
  },
  onHide() { this.checkpoint(); },
  onUnload() { this.checkpoint(); access.leave(this); },
  login: access.login,
  checkpoint() {
    if (!this._loadedOwner) return false;
    const { selected, reason, pending, uncertain } = this.data;
    if (!reason && !pending) { local.remove(scope, this._loadedOwner); return true; }
    try { local.write(scope, { selected, reason, pending, uncertain }, this._loadedOwner); return true; }
    catch (_) { this.setData({ error: '无法保存本机申诉，请保留当前输入。' }); return false; }
  },
  async load(reset, preserveError = false) {
    if (this.data.busy || this.data.authStatus !== 'ready') return;
    const epoch = this._epoch;
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ busy: true, error: preserveError ? this.data.error : '' });
    try {
      const [current, result] = await Promise.all([api.restrictions(), api.appeals(page)]);
      if (epoch !== this._epoch) return;
      const restrictions = current.items.map(view.restriction).filter((row) => row.id);
      const records = new Map((reset ? [] : this.data.records).map((row) => [row.id, row]));
      result.items.map(view.appeal).filter((row) => row.id).forEach((row) => records.set(row.id, row));
      const active = restrictions.filter((row) => row.enabled);
      const hasOpen = (row) => [...records.values()].some((item) => item.restrictionId === row.id && item.restrictionRevision === row.revision && item.state === 'OPEN');
      const currentSelection = this.data.selected;
      let selected = currentSelection;
      let error = this.data.error;
      if (!this.data.pending) {
        if (currentSelection && !active.some((row) => row.id === currentSelection.id && row.revision === currentSelection.revision)) {
          selected = null;
          error = '原限制已变化。申诉说明已保留，请重新选择并核对处理决定。';
        } else if (!currentSelection && !this.data.reason) {
          const eligible = active.filter((item) => !hasOpen(item));
          const row = eligible.find((item) => item.scope === this._preferredScope) || eligible[0];
          if (row) selected = { id: row.id, revision: row.revision };
        }
      }
      const display = restrictions.map((row) => Object.assign({}, row, {
        pendingReview: hasOpen(row)
      }));
      this.setData({ restrictions: display, activeCount: active.length, records: [...records.values()], total: result.total, page: result.page, selected, loaded: true, error });
    } catch (e) { if (epoch === this._epoch) this.setData({ loaded: false, error: e.status === 404 ? '暂时无法读取处理记录，请稍后重试。' : access.errorText(e) }); }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  refresh() { return this.load(true); },
  more() { if (this.data.records.length < this.data.total) return this.load(false); },
  select(e) {
    if (!this.data.loaded || this.data.busy || this.data.pending) return;
    const id = view.validId(e.currentTarget.dataset.id);
    const row = this.data.restrictions.find((item) => item.id === id && item.enabled && !item.pendingReview);
    if (row) { this.setData({ selected: { id, revision: row.revision }, error: '' }); this.checkpoint(); }
  },
  input(e) { if (!this.data.busy && !this.data.pending) this.setData({ reason: e.detail.value }); },
  async submit() {
    if (this.data.busy || this.data.authStatus !== 'ready') return;
    let body = this.data.pending;
    const selected = this.data.selected;
    const reason = this.data.reason;
    if (!body) {
      const row = selected && this.data.restrictions.find((item) => item.id === selected.id && item.revision === selected.revision && item.enabled && !item.pendingReview);
      if (!this.data.loaded || !row || !reason.trim() || [...reason].length > 2000) {
        this.setData({ error: '请选择当前可申诉的限制，填写不超过 2000 字的说明。' }); return;
      }
    }
    const epoch = this._epoch;
    this.setData({ busy: true, error: '', notice: '' });
    try {
      if (!body) {
        const latest = await api.restrictions();
        if (epoch !== this._epoch) return;
        if (!latest.items.some((row) => row.id === selected.id && row.revision === selected.revision && row.enabled === true)) {
          this.setData({ error: '这项限制已变化。请重新读取并确认要申诉的决定。' }); return;
        }
        body = { restrictionId: selected.id, requestId: local.uuid(), reason };
        this.setData({ pending: body, uncertain: false });
      }
      if (!this.checkpoint()) return;
      const receipt = await api.appeal(body);
      if (epoch !== this._epoch) return;
      if (!view.validId(receipt.id)) throw new Error('申诉回执无法识别，请按原请求核对。');
      this.setData({ pending: null, uncertain: false, reason: '', selected: null, notice: `申诉 #${receipt.id} 已提交。可在下方查看复核结果。` });
      local.remove(scope, this._ownerId);
    } catch (e) {
      if (epoch !== this._epoch) return;
      const uncertain = !!body && (this.data.uncertain || !e.status || e.status >= 500 || e.kind === 'CONTRACT');
      const canEdit = !uncertain && (e.status === 400 || e.status === 404 || ['APPEAL_ALREADY_OPEN', 'RESTRICTION_INACTIVE'].includes(e.code));
      this.setData({ uncertain, pending: canEdit ? null : body, error: access.errorText(e) });
      this.checkpoint();
    } finally {
      if (epoch === this._epoch) { this.setData({ busy: false }); if (!this.data.pending) await this.load(true, true); }
    }
  }
});
