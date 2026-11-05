class ApiError extends Error {
  constructor(options) {
    super(options.message || '请求未完成');
    this.name = 'ApiError';
    this.kind = options.kind || 'UNKNOWN';
    this.status = options.status || 0;
    this.code = options.code || '';
    this.traceId = options.traceId || '';
  }
}

function fromHttp(status, payload, traceId) {
  const body = payload && typeof payload === 'object' ? payload : {};
  const tracking = traceId || body.traceId || '';
  if (status === 401 || status === 403) {
    return new ApiError({ kind: 'PERMISSION', status, code: body.code, traceId: tracking, message: '当前内容无法公开访问，请返回发现页查看其他经验' });
  }
  if (status === 404) {
    return new ApiError({ kind: 'UNAVAILABLE', status, code: body.code, traceId: tracking, message: '这篇经验不存在或当前不可用' });
  }
  if (status === 400) {
    return new ApiError({ kind: 'INVALID', status, code: body.code, traceId: tracking, message: body.message || '查询条件不符合接口要求，请调整后重试' });
  }
  if (status === 503) {
    return new ApiError({ kind: 'SERVICE', status, code: body.code, traceId: tracking, message: '服务暂时不可用，请稍后重试' });
  }
  if (status >= 500) {
    return new ApiError({ kind: 'SERVICE', status, code: body.code, traceId: tracking, message: '服务处理出现异常，请稍后重试' });
  }
  return new ApiError({ kind: 'HTTP', status, code: body.code, traceId: tracking, message: body.message || `请求未完成（HTTP ${status}）` });
}

function fromNetwork(error) {
  const message = error && error.errMsg ? String(error.errMsg) : '';
  const timeout = /timeout/i.test(message);
  return new ApiError({
    kind: timeout ? 'TIMEOUT' : 'NETWORK',
    message: timeout ? '连接超时，请检查网络后重试' : '无法连接服务，请检查网络和服务地址'
  });
}

module.exports = { ApiError, fromHttp, fromNetwork };
