function email(value) { const result = String(value || '').trim().toLowerCase(); const local = result.split('@')[0]; return /^[a-z0-9_+%.-]+@stu\.hzcu\.edu\.cn$/.test(result) && local.length <= 64 && !local.startsWith('.') && !local.endsWith('.') && !local.includes('..') ? result : ''; }
function bytes(value) { return unescape(encodeURIComponent(value)).length; }
function password(value) { try { const n = Array.from(value).length; return n >= 8 && n <= 64 && bytes(value) <= 72; } catch (_) { return false; } }
module.exports = { email, password, bytes };
