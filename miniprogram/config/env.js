const defaults = require('./defaults');

let local = {};
try {
  local = require('./local');
} catch (error) {
  local = {};
}

const config = Object.assign({}, defaults, local);
config.apiBaseUrl = String(config.apiBaseUrl || '').replace(/\/+$/, '');
config.demoMode = config.demoMode === true;
config.debug = config.debug === true;

module.exports = config;
