const config = require('../config/env');
const session = require('./session');
const { ApiError, fromHttp, fromNetwork } = require('./api-error');
const files = new Set();
const tasks = new Set();
function removeFile(file) { if (!file) return; files.delete(file); try { wx.getFileSystemManager().unlink({ filePath: file, fail() {} }); } catch (_) { /* Temporary files are also owned by the platform. */ } }
function denied(version, status) { if (status === 401 || status === 403) session.invalidate(version, status); }
function download(url) {
  const prefix = `${config.apiBaseUrl}/api/v1/media/`;
  const absolute = url.startsWith('/api/v1/media/') ? config.apiBaseUrl + url : url;
  if (!absolute.startsWith(prefix) || !/^https?:\/\//.test(absolute) || /[?#]/.test(absolute)) return Promise.reject(new Error('图片地址无效'));
  const token = session.token(); const version = session.version();
  if (!token) return Promise.reject(new ApiError({ kind: 'AUTH', status: 401, message: '请重新登录后查看图片' }));
  return new Promise((resolve, reject) => {
    let task;
    task = wx.downloadFile({ url: absolute, timeout: config.requestTimeout, header: { Authorization: `Bearer ${token}` }, success(response) {
      if (version !== session.version()) { removeFile(response.tempFilePath); reject(new ApiError({ kind: 'ABORTED', message: '会话已变化' })); return; }
      if (response.statusCode !== 200) {
        denied(version, response.statusCode); removeFile(response.tempFilePath);
        reject(fromHttp(response.statusCode, {}, '')); return;
      }
      files.add(response.tempFilePath); resolve(response.tempFilePath);
    }, fail(error) { reject(fromNetwork(error)); }, complete() { tasks.delete(task); } });
    tasks.add(task);
  });
}
function upload(path, filePath, requestId) {
  const token = session.token(); const version = session.version();
  if (!token) return Promise.reject(new ApiError({ kind: 'AUTH', status: 401, message: '请登录后上传' }));
  return new Promise((resolve, reject) => {
    let task;
    task = wx.uploadFile({ url: `${config.apiBaseUrl}/api/v1${path}`, filePath, name: 'file', formData: { requestId }, header: { Authorization: `Bearer ${token}` }, timeout: config.requestTimeout,
      success(response) {
        if (version !== session.version()) { reject(new ApiError({ kind: 'ABORTED', message: '会话已变化' })); return; }
        let payload; try { payload = JSON.parse(response.data); } catch (_) { reject(new ApiError({ kind: 'CONTRACT', message: '上传响应无法识别，请保留原图片重试' })); return; }
        if (response.statusCode >= 200 && response.statusCode < 300 && payload.code === 'OK') resolve(payload.data);
        else { denied(version, response.statusCode); reject(fromHttp(response.statusCode, payload, payload.traceId)); }
      }, fail(error) { reject(fromNetwork(error)); }, complete() { tasks.delete(task); }
    });
    tasks.add(task);
  });
}
function choose() {
  return new Promise((resolve, reject) => wx.chooseMedia({ count: 9, mediaType: ['image'], sizeType: ['original'], sourceType: ['album', 'camera'], success: (r) => resolve(r.tempFiles), fail: (e) => /cancel/.test(e.errMsg || '') ? resolve([]) : reject(new Error('选择图片失败，请重试')) }));
}
function validate(file) {
  if (file.size > 10 * 1024 * 1024) return Promise.reject(new Error('单张图片不能超过 10MiB'));
  return new Promise((resolve, reject) => wx.getImageInfo({ src: file.tempFilePath, success(info) { if (!['jpeg', 'jpg', 'png'].includes(String(info.type).toLowerCase())) reject(new Error('请选择 JPEG 或 PNG 图片')); else if (info.width * info.height > 25000000) reject(new Error('图片不能超过 2500 万像素')); else resolve(file); }, fail: () => reject(new Error('图片无法读取，请重新选择')) }));
}
function clear() { tasks.forEach((task) => { if (task && task.abort) task.abort(); }); tasks.clear(); [...files].forEach(removeFile); }
session.subscribe(clear);
module.exports = { download, upload, choose, validate, release: removeFile, clear };
