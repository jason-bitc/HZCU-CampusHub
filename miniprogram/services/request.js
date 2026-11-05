const config = require('../config/env');
const { ApiError, fromHttp, fromNetwork } = require('./api-error');

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
  let task;
  const promise = new Promise((resolve, reject) => {
    task = wx.request({
      url: `${config.apiBaseUrl}${options.path}`,
      method: options.method || 'GET',
      data: options.data || {},
      timeout: config.requestTimeout,
      header: { 'content-type': 'application/json' },
      success(response) {
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
          reject(new ApiError({ kind: 'CONTRACT', status: response.statusCode, traceId: event.traceId, message: '服务响应格式不符合公开接口合同' }));
          return;
        }
        reject(fromHttp(response.statusCode, payload, event.traceId));
      },
      fail(error) {
        log({ path: options.path, status: 0, durationMs: Date.now() - startedAt, traceId: '' });
        reject(fromNetwork(error));
      }
    });
  });
  return {
    promise,
    abort() {
      if (task && typeof task.abort === 'function') task.abort();
    }
  };
}

module.exports = { request };
