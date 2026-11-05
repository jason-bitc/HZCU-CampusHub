const config = require('./config/env');

App({
  globalData: {
    demoMode: config.demoMode
  },
  onLaunch() {
    if (config.debug) {
      console.info('[CampusHub] launched', {
        demoMode: config.demoMode,
        apiBaseUrl: config.apiBaseUrl
      });
    }
  }
});
