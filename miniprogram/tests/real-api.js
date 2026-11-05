// Read-only public API checks. Requires Node 18+ and the existing Java/MySQL backend.
const assert = require('node:assert/strict');
const base = (process.argv[2] || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const evidence = [];
async function get(endpoint, expected = 200) {
  const response = await fetch(base + endpoint, { signal: AbortSignal.timeout(10000) });
  const payload = await response.json();
  assert.equal(response.status, expected, endpoint);
  assert.equal(payload.traceId, response.headers.get('x-trace-id'), 'trace header/body must agree');
  assert.ok(payload.traceId);
  assert.equal(payload.code === 'OK', expected === 200);
  evidence.push({ endpoint, status: response.status, code: payload.code, traceId: payload.traceId });
  return payload;
}
function publicOnly(article) {
  for (const key of Object.keys(article)) assert.ok(!/author|avatar|nickname|contact|comment|email|token/i.test(key), 'restricted key: ' + key);
}
(async () => {
  const health = await get('/actuator/health'); assert.equal(health.data.status, 'UP');
  const catalog = (await get('/api/v1/catalog')).data;
  const recognized = (await get('/api/v1/articles?sort=recognized&page=1&size=100')).data;
  const latest = (await get('/api/v1/articles?sort=latest&page=1&size=100')).data;
  assert.ok(recognized.items.length, 'use an existing development database containing public articles');
  recognized.items.forEach(publicOnly);
  function compare(a, b, sort) {
    return (sort === 'recognized' ? b.counts.effectiveCoins - a.counts.effectiveCoins || b.counts.effectiveLikes - a.counts.effectiveLikes : 0) || Date.parse(b.publishedAt) - Date.parse(a.publishedAt) || b.id - a.id;
  }
  for (const [sort, page] of [['recognized', recognized], ['latest', latest]]) for (let i = 1; i < page.items.length; i++) assert.ok(compare(page.items[i - 1], page.items[i], sort) <= 0, sort + ' order');
  const paged = [];
  for (let page = 1; paged.length < latest.total; page++) {
    assert.ok(page <= Math.ceil(latest.total / 2) + 1, 'pagination must finish');
    const data = (await get('/api/v1/articles?sort=latest&page=' + page + '&size=2')).data;
    paged.push(...data.items.map(a => a.id));
    assert.ok(data.items.length, 'stable dataset must not lose rows');
  }
  assert.equal(new Set(paged).size, paged.length, 'no duplicates on the stable dataset');
  assert.deepEqual(paged, latest.items.map(a => a.id));
  const combination = new URLSearchParams({ sort: 'latest', page: '1', size: '100' });
  for (const [parameter, category] of [['jobId', 'jobs'], ['majorId', 'majors'], ['companyId', 'companies'], ['topicId', 'topics']]) {
    if (!catalog[category].length) continue;
    const id = catalog[category][0].id;
    const data = (await get('/api/v1/articles?' + parameter + '=' + id + '&sort=latest&page=1&size=100')).data;
    assert.ok(data.items.every(a => a.categories[category].some(c => c.id === id)), parameter);
    combination.set(parameter, String(id));
  }
  const combined = (await get('/api/v1/articles?' + combination)).data;
  assert.ok(combined.items.every(a => [['jobId','jobs'],['majorId','majors'],['companyId','companies'],['topicId','topics']].every(([parameter,category]) => !combination.has(parameter) || a.categories[category].some(c => String(c.id) === combination.get(parameter)))));
  const empty = (await get('/api/v1/articles?keyword=' + encodeURIComponent('不存在的关键词-20261105') + '&sort=latest&page=1&size=2')).data;
  assert.equal(empty.total, 0); assert.equal(empty.items.length, 0);
  const detail = (await get('/api/v1/articles/' + latest.items[0].id)).data;
  publicOnly(detail); assert.equal(typeof detail.body, 'string'); assert.equal(detail.bodyFormat, 'MARKDOWN');
  for (const id of [2001,2002,2003,2004]) { const unavailable = await get('/api/v1/articles/' + id, 404); assert.ok(!('data' in unavailable)); }
  await get('/api/v1/articles?jobId=&page=1&size=2', 400);
  console.log(JSON.stringify({ checkedAt: new Date().toISOString(), base, publicTotal: latest.total, pagedIds: paged, allCurrentCountsZero: recognized.items.every(a => !a.counts.effectiveCoins && !a.counts.effectiveLikes), detailId: detail.id, bodyLength: detail.body.length, status: 'PASS', evidence }, null, 2));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
