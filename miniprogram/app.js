const config = require('./config/env');
const session = require('./services/session');
const account = require('./services/account');
require('./utils/operation');

App({
  globalData: {
    demoMode: config.demoMode
  },
  onLaunch() {
    session.restore();
    if (config.debug) {
      console.info('[CampusHub] launched', {
        demoMode: config.demoMode,
        apiBaseUrl: config.apiBaseUrl
      });
    }
  },
  onShow() { if (session.token()) account.verify().catch(() => {}); }
});
