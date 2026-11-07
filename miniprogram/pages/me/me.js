const api = require('../../services/member');
const community = require('../../services/community');
const articles = require('../../services/articles');
const access = require('../../utils/member-page');

const emptyCounts = { articles: null, drafts: null, favorites: null, feedback: null };
const blank = { profile: null, avatar: '', profileSummary: '', wallet: null, rewards: null, counts: emptyCounts };
Page({
  data: Object.assign({ authStatus: 'checking', error: '', loading: false }, blank),
  onLoad() { access.attach(this, Object.assign({ loading: false }, blank)); },
  async onShow() {
    if (!await access.enter(this, 'me')) return;
    const epoch = this._epoch;
    this.setData({ loading: true });
    const results = await Promise.allSettled([
      api.profile(), articles.getCatalog().promise, api.wallet(), api.rewards(),
      api.owned(1), api.favorites(1, 1), community.cases(1, 1)
    ]);
    if (epoch !== this._epoch) return;
    const value = (i) => results[i].status === 'fulfilled' ? results[i].value : null;
    const profile = value(0), catalog = value(1), owned = value(4), favorites = value(5), feedback = value(6);
    const major = profile && catalog && catalog.majors.find((row) => row.id === profile.majorId);
    const stage = profile && ({ ENROLLED: '在校', GRADUATED: '毕业' })[profile.currentStage];
    const counts = Object.assign({}, emptyCounts);
    if (owned && owned.total <= owned.items.length) {
      counts.articles = owned.items.filter((row) => ['PUBLISHED', 'AUTHOR_OFFLINE', 'ADMIN_HIDDEN'].includes(row.state)).length;
      counts.drafts = owned.items.filter((row) => row.state === 'DRAFT').length;
    }
    if (favorites) counts.favorites = favorites.total;
    if (feedback) counts.feedback = feedback.total;
    const problem = results.slice(0, 4).find((row) => row.status === 'rejected');
    this.setData({
      profile, avatar: Array.from(profile && profile.nickname || '城')[0],
      profileSummary: [major ? major.name : '未填写专业', stage || '阶段未设置'].join(' · '),
      wallet: value(2), rewards: value(3), counts, loading: false, error: problem ? access.errorText(problem.reason) : ''
    });
  },
  onUnload() { access.leave(this); },
  login: access.login, discover: access.discover,
  profile() { wx.navigateTo({ url: '/pages/profile/profile' }); },
  wallet() { wx.navigateTo({ url: '/pages/wallet/wallet' }); },
  workspace(e) {
    getApp().globalData.workspaceFilter = e.currentTarget.dataset.filter;
    wx.switchTab({ url: '/pages/workspace/workspace' });
  },
  favorites() { wx.switchTab({ url: '/pages/favorites/favorites' }); },
  feedback() { wx.navigateTo({ url: '/pages/feedback/feedback' }); },
  settings() { wx.navigateTo({ url: '/pages/settings/settings' }); }
});
