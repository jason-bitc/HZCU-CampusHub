const defaults = require('./defaults');

let local = {};
try {
  local = require('./local');
} catch (error) {
  local = {};
}

const config = Object.assign({}, defaults, local);
config.apiBaseUrl = String(config.apiBaseUrl || '').replace(/\/+$/, '');
const webOrigin = String(config.webBaseUrl || '').trim().replace(/\/+$/, '');
config.webBaseUrl = /^https?:\/\/[a-z0-9][a-z0-9.-]*(?::[0-9]{1,5})?$/i.test(webOrigin) ? webOrigin : '';
config.demoMode = config.demoMode === true;
config.debug = config.debug === true;

module.exports = config;
