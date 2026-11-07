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
  if (status === 403 && body.code === 'BEHAVIOR_RESTRICTED') {
    return new ApiError({ kind: 'BEHAVIOR', status, code: body.code, traceId: tracking, message: '此行为当前受限，可在本人处理记录查看原因并申诉。' });
  }
  if (status === 401 || status === 403) {
    return new ApiError({ kind: status === 401 ? 'AUTH' : 'PERMISSION', status, code: body.code, traceId: tracking, message: body.code === 'LOGIN_FAILED' ? '邮箱或密码不正确，或当前账号暂不可用' : status === 401 ? '登录已失效，请重新登录' : '当前账号或校园认证不可用，请退出后重新核对' });
  }
  if (status === 404) {
    return new ApiError({ kind: 'UNAVAILABLE', status, code: body.code, traceId: tracking, message: '这篇经验不存在或当前不可用' });
  }
  if (status === 400) {
    return new ApiError({ kind: 'INVALID', status, code: body.code, traceId: tracking, message: body.message || '查询条件不符合接口要求，请调整后重试' });
  }
  if (status === 409 || status === 422 || status === 429 || status === 413 || status === 415) {
    return new ApiError({ kind: status === 409 ? 'CONFLICT' : status === 429 ? 'RATE_LIMIT' : 'INVALID', status, code: body.code, traceId: tracking, message: body.message || (status === 429 ? '请求过于频繁，请稍后重试' : '请检查输入后重试') });
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
  if (/file not found|no such file|ENOENT|fail file|invalid file/i.test(message)) return new ApiError({ kind: 'FILE', message: '本机临时图片已不可读，请移除失败项并重新选择' });
  const timeout = /timeout/i.test(message);
  return new ApiError({
    kind: timeout ? 'TIMEOUT' : 'NETWORK',
    message: timeout ? '连接超时，请检查网络后重试' : '无法连接服务，请检查网络和服务地址'
  });
}

module.exports = { ApiError, fromHttp, fromNetwork };
