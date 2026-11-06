const { request } = require('./request');
const session = require('./session');
let checking;
function login(email, password) { return request({ path: '/api/v1/auth/login', method: 'POST', data: { email, password } }).promise.then((result) => { session.accept(result); return verify(); }); }
function verify() {
  const version = session.version();
  if (checking && checking.version === version) return checking.promise;
  const promise = request({ path: '/api/v1/me', auth: true }).promise.then((me) => { if (version !== session.version()) throw new Error('会话已变化'); session.identify(me); return me; });
  checking = { version, promise };
  promise.then(() => { if (checking && checking.promise === promise) checking = null; }, () => { if (checking && checking.promise === promise) checking = null; });
  return promise;
}
function logout() {
  return request({ path: '/api/v1/auth/logout', method: 'POST', auth: true }).promise.then(() => { session.clear('logout'); }, (error) => { if (error.status === 401 || error.status === 403) session.clear('logout'); throw error; });
}
function sendCode(mode, email) { return request({ path: `/api/v1/auth/${mode === 'register' ? 'registration-codes' : 'password-reset-codes'}`, method: 'POST', data: { email } }).promise; }
function complete(mode, data) { return request({ path: `/api/v1/auth/${mode === 'register' ? 'register' : 'password-resets'}`, method: 'POST', data }).promise; }
module.exports = { login, verify, logout, sendCode, complete };
