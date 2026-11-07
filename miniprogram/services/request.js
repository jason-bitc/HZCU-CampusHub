const config = require('../config/env');
const { ApiError, fromHttp, fromNetwork } = require('./api-error');
const session = require('./session');

function log(event) {
  if (!config.debug) return;
  console.info('[CampusHub API]', event);
}

function getTraceId(headers) {
  if (!headers) return '';
  return headers['X-Trace-Id'] || headers['x-trace-id'] || '';
}

function request(options) {
  const startedAt = Date.now();
  const accessToken = options.auth ? session.token() : '';
  const captured = session.version();
  if (options.auth && !accessToken) return { promise: Promise.reject(new ApiError({ kind: 'AUTH', status: 401, message: '请登录校园账号后继续' })), abort() {} };
  let task;
  const promise = new Promise((resolve, reject) => {
    const input = {
      url: `${config.apiBaseUrl}${options.path}`,
      method: options.method || 'GET',
      timeout: config.requestTimeout,
      header: Object.assign({ 'content-type': 'application/json' }, accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      success(response) {
        if (options.auth && captured !== session.version()) { reject(new ApiError({ kind: 'ABORTED', message: '会话已变化，已丢弃旧响应' })); return; }
        const event = {
          path: options.path,
          status: response.statusCode,
          durationMs: Date.now() - startedAt,
          traceId: getTraceId(response.header) || (response.data && response.data.traceId) || ''
        };
        log(event);
        const payload = response.data;
        if (response.statusCode >= 200 && response.statusCode < 300 && payload && payload.code === 'OK') {
          resolve(payload.data);
          return;
        }
        if (response.statusCode >= 200 && response.statusCode < 300) {
          reject(new ApiError({ kind: 'CONTRACT', status: response.statusCode, traceId: event.traceId, message: '服务响应格式不符合接口合同' }));
          return;
        }
        if (options.auth && (response.statusCode === 401 || response.statusCode === 403 && payload && payload.code === 'ACCESS_RESTRICTED')) session.invalidate(captured, response.statusCode);
        reject(fromHttp(response.statusCode, payload, event.traceId));
      },
      fail(error) {
        log({ path: options.path, status: 0, durationMs: Date.now() - startedAt, traceId: '' });
        reject(fromNetwork(error));
      }
    };
    // Bodyless writes must omit data entirely: even {} is rejected by interactions.
    if (Object.prototype.hasOwnProperty.call(options, 'data')) input.data = options.data;
    task = wx.request(input);
  });
  return {
    promise,
    abort() {
      if (task && typeof task.abort === 'function') task.abort();
    }
  };
}

module.exports = { request };
