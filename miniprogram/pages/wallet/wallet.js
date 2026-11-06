const api = require('../../services/member');
const access = require('../../utils/member-page');
const local = require('../../utils/operation');
const blank = { wallet: null, rewards: null, ledger: [], page: 0, total: 0, snapshot: null, readingSeconds: null, readySession: null, pendingLogin: null, pendingReading: null };
Page({
  data: Object.assign({ authStatus: 'checking', busy: false, error: '', notice: '' }, blank),
  onLoad() { access.attach(this, blank); },
  async onShow() { if (!await access.enter(this)) return; this.setData({ pendingLogin: local.read('reward:login'), pendingReading: local.read('reward:reading') }); await this.load(); clearInterval(this.timer); this.timer = setInterval(() => this.tick(), 1000); },
  onHide() { clearInterval(this.timer); }, onUnload() { clearInterval(this.timer); access.leave(this); }, login: access.login,
  tick() { if (!this.data.rewards) return; const now = Date.now() + (this.offset || 0); const sessions = this.data.rewards.readingSessions; const ready = sessions.find((r) => Date.parse(r.claimableAt) <= now); const soonest = sessions.slice().sort((a, b) => Date.parse(a.claimableAt) - Date.parse(b.claimableAt))[0]; this.setData({ readySession: ready || null, readingSeconds: soonest ? Math.max(0, Math.ceil((Date.parse(soonest.claimableAt) - now) / 1000)) : null }); },
  async load(preserveError) { if (this.data.busy || this.data.authStatus !== 'ready') return; const epoch = this._epoch; this.setData({ busy: true, error: preserveError === true ? this.data.error : '' }); try { const [wallet, rewards, ledger] = await Promise.all([api.wallet(), api.rewards(), api.ledger(1)]); if (epoch !== this._epoch) return; this.offset = Date.parse(rewards.serverTime) - Date.now(); this.setData({ wallet, rewards, ledger: ledger.items, snapshot: ledger.snapshot, total: ledger.total, page: 1 }); this.tick(); } catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); } finally { if (epoch === this._epoch) this.setData({ busy: false }); } },
  async more() { if (this.data.busy) return; const epoch = this._epoch; this.setData({ busy: true }); try { const result = await api.ledger(this.data.page + 1, this.data.snapshot); if (epoch === this._epoch) this.setData({ ledger: this.data.ledger.concat(result.items), total: result.total, page: result.page }); } catch (e) { if (epoch === this._epoch) this.setData({ error: access.errorText(e) }); } finally { if (epoch === this._epoch) this.setData({ busy: false }); } },
  async claim(e) {
    const kind = e.currentTarget.dataset.kind; if (!['login', 'reading'].includes(kind) || this.data.busy) return; const epoch = this._epoch; const field = kind === 'login' ? 'pendingLogin' : 'pendingReading';
    let op = this.data[field]; if (!op) { if (kind === 'reading' && !this.data.readySession) return; op = { requestId: local.uuid(), sessionId: kind === 'reading' ? this.data.readySession.sessionId : null }; try { local.write(`reward:${kind}`, op); } catch (_) { this.setData({ error: '无法保留领取请求，请稍后重试' }); return; } this.setData({ [field]: op }); }
    this.setData({ busy: true, error: '', notice: '' });
    try { const receipt = await (kind === 'login' ? api.claimLogin(op.requestId) : api.claimReading(op.sessionId, op.requestId)); if (epoch !== this._epoch) return; local.remove(`reward:${kind}`); this.setData({ [field]: null, notice: `领取结果已确认 · +${receipt.delta} 币` }); }
    catch (error) { if (epoch === this._epoch) { this.setData({ error: access.errorText(error) }); if (['REWARD_ALREADY_CLAIMED', 'READING_DAY_EXPIRED', 'LOGIN_REQUIRED_TODAY'].includes(error.code)) { local.remove(`reward:${kind}`); this.setData({ [field]: null }); } } }
    finally { if (epoch === this._epoch) { this.setData({ busy: false }); this.load(true); } }
  }
});
