const api = require('../../services/member');
const access = require('../../utils/member-page');
const stages = ['UNSET', 'ENROLLED', 'GRADUATED'];
const types = ['WECHAT', 'QQ', 'WEB'];
const blank = { profile: null, contact: null, conflict: null, nickname: '', bio: '', majorId: null, majorName: '暂不填写', stageIndex: 0, contactValue: '', enabled: false };
Page({
  data: Object.assign({ authStatus: 'checking', busy: false, error: '', notice: '', majors: [], stageNames: ['未设置', '在校', '毕业'], typeNames: ['微信', 'QQ', '网站链接'], typeIndex: 0 }, blank),
  onLoad() { access.attach(this, blank); },
  async onShow() { if (!await access.enter(this)) return; if (!this.data.profile) await this.load(); },
  onUnload() { access.leave(this); }, login: access.login,
  async load() {
    const epoch = this._epoch; this.setData({ busy: true, error: '' });
    try { const [profile, contact, catalog] = await Promise.all([api.profile(), api.contactSettings(), api.call('/catalog')]); if (epoch !== this._epoch) return; this.setData({ majors: [{ id: null, name: '暂不填写' }].concat(catalog.majors) }); this.useProfile(profile); this.useContact(contact); }
    catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  useProfile(profile) { const major = this.data.majors.find((m) => m.id === profile.majorId); this.setData({ profile, nickname: profile.nickname, bio: profile.bio, majorId: profile.majorId, majorName: major ? major.name : '原专业已停用，请重新选择', stageIndex: Math.max(0, stages.indexOf(profile.currentStage)) }); },
  useContact(contact) { this.setData({ contact, enabled: contact.enabled, typeIndex: Math.max(0, types.indexOf(contact.type)), contactValue: contact.value || '' }); },
  input(e) { const key = e.currentTarget.dataset.field; if (['nickname', 'bio', 'contactValue'].includes(key)) this.setData({ [key]: e.detail.value, notice: '' }); },
  major(e) { const item = this.data.majors[Number(e.detail.value)]; if (item) this.setData({ majorId: item.id, majorName: item.name }); },
  stage(e) { this.setData({ stageIndex: Number(e.detail.value) }); },
  type(e) { this.setData({ typeIndex: Number(e.detail.value) }); },
  enabled(e) { this.setData({ enabled: e.detail.value }); },
  async save(e) {
    if (this.data.busy || this.data.conflict) return;
    const kind = e.currentTarget.dataset.kind; const isProfile = kind === 'profile'; if (!this.data.profile || !this.data.contact) return;
    const body = isProfile ? { expectedRevision: this.data.profile.revision, nickname: this.data.nickname, majorId: this.data.majorId, currentStage: stages[this.data.stageIndex], bio: this.data.bio } : { expectedRevision: this.data.contact.revision, enabled: this.data.enabled, type: this.data.contactValue.trim() ? types[this.data.typeIndex] : null, value: this.data.contactValue.trim() ? this.data.contactValue : null };
    if (isProfile && (!body.nickname.trim() || Array.from(body.nickname.trim()).length > 40)) { this.setData({ error: '昵称须为 1–40 个字符' }); return; }
    if (!isProfile && body.enabled && !body.value) { this.setData({ error: '开启联系前请填写联系方式' }); return; }
    const epoch = this._epoch; this.setData({ busy: true, error: '', notice: '' });
    try { const result = await (isProfile ? api.saveProfile(body) : api.saveContact(body)); if (epoch === this._epoch) { if (isProfile) this.useProfile(result); else this.useContact(result); this.setData({ notice: isProfile ? '个人资料已保存' : '联系设置已保存' }); } }
    catch (error) { if (epoch !== this._epoch) return; this.setData({ error: access.errorText(error) }); if (error.code === 'REVISION_CONFLICT') { try { const latest = await (isProfile ? api.profile() : api.contactSettings()); if (epoch === this._epoch) this.setData({ conflict: { kind, latest, local: JSON.stringify(body), server: JSON.stringify(latest) } }); } catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); } } }
    finally { if (epoch === this._epoch) this.setData({ busy: false }); }
  },
  resolve(e) { const conflict = this.data.conflict; if (!conflict) return; const keep = e.currentTarget.dataset.keep === 'local'; if (keep) { this.setData(conflict.kind === 'profile' ? { profile: conflict.latest } : { contact: conflict.latest }); } else if (conflict.kind === 'profile') this.useProfile(conflict.latest); else this.useContact(conflict.latest); this.setData({ conflict: null, error: '', notice: keep ? '保留了本机输入。请核对后再次点击保存，提交为新的修订。' : '已采用云端资料' }); }
});
