const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');
const root = path.resolve(__dirname, '..');
const { parseMarkdown } = require('../utils/markdown');
const { mapDetail } = require('../services/article-mapper');
const results = [];
function load(relative, mocks = {}, globals = {}) {
  const file = path.join(root, relative);
  const nativeRequire = createRequire(file);
  const context = { module: { exports: {} }, require: key => key in mocks ? mocks[key] : nativeRequire(key), console, setTimeout, clearTimeout, ...globals };
  vm.runInNewContext(fs.readFileSync(file, 'utf8'), context, { filename: file });
  return context.module.exports;
}
function harness(relative, api, component = false) {
  let definition;
  const scrolls = [];
  const wx = { nextTick: cb => cb(), pageScrollTo: (value) => scrolls.push(value), hideKeyboard: () => {}, setNavigationBarTitle: () => {}, stopPullDownRefresh: () => {}, showToast: () => {} };
  load(relative, { '../../services/articles': api, '../../config/env': { demoMode: false, pageSize: 2 } }, { wx, [component ? 'Component' : 'Page']: value => { definition = value; } });
  const instance = Object.assign({}, component ? definition.methods : definition);
  instance.data = JSON.parse(JSON.stringify(definition.data));
  instance.setData = (patch, callback) => { Object.assign(instance.data, patch); if (callback) callback(); };
  instance.definition = definition;
  instance.events = [];
  instance.scrolls = scrolls;
  instance.triggerEvent = (name, detail) => instance.events.push({ name, detail });
  return instance;
}
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject, abort() { this.aborted = true; } };
}
const tick = () => new Promise(resolve => setImmediate(resolve));
async function test(name, run) {
  try { await run(); results.push({ name, pass: true }); } catch (error) { results.push({ name, pass: false, error: error.stack }); }
}
(async () => {
  await test('真实模式服务发出正确 URL/参数，503 不回退演示数据', async () => {
    const sent = [];
    const config = { demoMode: false, apiBaseUrl: 'http://127.0.0.1:8080', requestTimeout: 1000 };
    let serviceError = false;
    const requestModule = load('services/request.js', { '../config/env': config }, { wx: { request(options) {
      sent.push(options);
      if (serviceError) options.success({ statusCode: 503, header: {}, data: { code: 'DOWN', traceId: 'REAL-503' } });
      else options.success({ statusCode: 200, header: {}, data: { code: 'OK', data: options.url.endsWith('catalog') ? { jobs: [], majors: [], companies: [], topics: [] } : options.url.endsWith('/1001') ? { id: 1001, body: 'full' } : { items: [{ id: 1001 }], total: 1, page: 1, size: 2 } } });
      return { abort() {} };
    } } });
    const api = load('services/articles.js', { '../config/env': config, './request': requestModule });
    await api.getCatalog().promise;
    await api.listArticles({ keyword: 'C++', companyId: 21, sort: 'latest', page: 1, size: 2 }).promise;
    const article = await api.getArticle(1001).promise;
    assert.equal(article.body, 'full');
    assert.deepEqual(sent.map(item => item.url), ['http://127.0.0.1:8080/api/v1/catalog', 'http://127.0.0.1:8080/api/v1/articles', 'http://127.0.0.1:8080/api/v1/articles/1001']);
    assert.equal(sent[1].data.keyword, 'C++'); assert.equal(sent[1].data.companyId, 21);
    serviceError = true;
    await assert.rejects(api.listArticles({ page: 1, size: 2 }).promise, error => error.status === 503 && error.traceId === 'REAL-503');
  });
  await test('快速切换条件及分页时，晚到响应不能混入当前列表', async () => {
    const calls = [];
    const api = { getCatalog: () => ({ promise: Promise.resolve({ jobs: [], majors: [], companies: [], topics: [] }), abort() {} }), listArticles(query) { const task = deferred(); calls.push({ query, task }); return task; } };
    const page = harness('pages/discover/discover.js', api);
    page.onLoad({}); page.updateFilters({ keyword: 'new', jobId: 2 });
    calls[1].task.resolve({ items: [{ id: 2 }], page: 1, total: 4 }); await tick();
    calls[0].task.resolve({ items: [{ id: 1 }], page: 1, total: 1 }); await tick();
    assert.equal(page.data.articles[0].id, 2); assert.equal(page.data.filters.keyword, 'new');
    page.loadMore(); assert.equal(calls[2].query.page, 2);
    page.updateFilters({ keyword: 'third' });
    calls[2].task.resolve({ items: [{ id: 9 }], page: 2, total: 4 });
    calls[3].task.resolve({ items: [{ id: 3 }], page: 1, total: 1 }); await tick();
    assert.equal(page.data.articles.length, 1); assert.equal(page.data.articles[0].id, 3); assert.equal(page.data.loadingMore, false);
  });
  await test('分页失败保留内容，重试页码不跳过；返回不重新请求', async () => {
    const calls = [];
    const api = { getCatalog: () => ({ promise: Promise.resolve({ jobs: [], majors: [], companies: [], topics: [] }), abort() {} }), listArticles(query) { const task = deferred(); calls.push({ query, task }); return task; } };
    const page = harness('pages/discover/discover.js', api); page.onLoad({});
    calls[0].task.resolve({ items: [{ id: 1 }, { id: 2 }], page: 1, total: 3 }); await tick();
    page.loadMore(); calls[1].task.reject(new Error('offline')); await tick();
    assert.equal(page.data.page, 1); assert.equal(page.data.articles.length, 2); assert.equal(page.data.loadMoreError, 'offline');
    page.loadMore(); assert.equal(calls[2].query.page, 2);
    calls[2].task.resolve({ items: [{ id: 2 }, { id: 3 }], page: 2, total: 3 }); await tick();
    assert.equal(page.data.articles.length, 3); assert.equal(page.data.hasMore, false);
    page.onShow(); page.onPageScroll({ scrollTop: 500 }); page.onShow(); assert.equal(calls.length, 3);
  });
  await test('503 与空结果区分，重试保留原条件', async () => {
    const calls = [];
    const api = { getCatalog: () => ({ promise: Promise.resolve({ jobs: [], majors: [], companies: [], topics: [] }), abort() {} }), listArticles(query) { const task = deferred(); calls.push({ query, task }); return task; } };
    const page = harness('pages/discover/discover.js', api); page.onLoad({});
    calls[0].task.reject({ kind: 'SERVICE', message: 'down', traceId: '503' }); await tick();
    assert.equal(page.data.listStatus, 'error'); assert.equal(page.data.errorTitle, '服务暂时不可用');
    page.handleStateAction(); assert.equal(calls[1].query.sort, 'recognized');
    calls[1].task.resolve({ items: [], total: 0, page: 1 }); await tick(); assert.equal(page.data.listStatus, 'empty');
  });
  await test('区分无公开经验与筛选无匹配，清除条件保留排序', async () => {
    const calls = [];
    const api = { getCatalog: () => ({ promise: Promise.resolve({ jobs: [], majors: [], companies: [], topics: [] }), abort() {} }), listArticles(query) { calls.push(query); return { promise: Promise.resolve({ items: [], page: 1, total: 0 }), abort() {} }; } };
    const p = harness('pages/discover/discover.js', api);
    p.onLoad({}); await tick(); assert.equal(p.data.listStatus, 'empty');
    p.selectSort({ currentTarget: { dataset: { sort: 'latest' } } });
    p.updateFilters({ keyword: '没有匹配', jobId: 11 }); await tick();
    assert.equal(p.data.listStatus, 'no-match');
    assert.equal(p.data.chips.length, 2);
    p.resetFilters(); await tick();
    assert.equal(p.data.listStatus, 'empty'); assert.equal(calls.at(-1).sort, 'latest');
  });
  await test('刷新失败保留已加载页与条件，返回以离开时的位置为准', async () => {
    const calls = [];
    const api = { getCatalog: () => ({ promise: Promise.resolve({ jobs: [], majors: [], companies: [], topics: [] }), abort() {} }), listArticles(query) { const task = deferred(); calls.push({ query, task }); return task; } };
    const p = harness('pages/discover/discover.js', api);
    p.onLoad({}); p.updateFilters({ keyword: '保留这个词' });
    calls[1].task.resolve({ items: [{ id: 1 }, { id: 2 }], page: 1, total: 4 }); await tick();
    p.loadMore(); calls[2].task.resolve({ items: [{ id: 3 }], page: 2, total: 4 }); await tick();
    const refresh = p.refreshResults(); calls[3].task.reject(new Error('offline')); await refresh;
    assert.equal(p.data.page, 2); assert.equal(p.data.articles.length, 3); assert.equal(p.data.filters.keyword, '保留这个词');
    p.onPageScroll({ scrollTop: 432 }); p.onHide(); p.onPageScroll({ scrollTop: 0 }); p.onShow();
    assert.equal(p.scrolls.at(-1).scrollTop, 432); assert.equal(calls.length, 4);
  });
  await test('筛选取消不应用，重置保留搜索词，公司必须选择有效标签', () => {
    const filter = harness('components/filter-panel/index.js', {}, true);
    filter.data.filters = { keyword: 'C++', jobId: 1, majorId: 11, companyId: 21 };
    filter.data.catalog = { jobs: [{ id: 1, name: '后端' }], majors: [{ id: 11, name: '软件工程' }], companies: [{ id: 21, name: '示例公司' }], topics: [] };
    filter.resetDraft(filter.data.filters);
    filter.selectMajor({ currentTarget: { dataset: { value: 12 } } }); filter.close();
    assert.equal(filter.data.filters.majorId, 11); assert.equal(filter.events.some(event => event.name === 'apply'), false);
    filter.resetDraft(filter.data.filters); assert.equal(filter.data.draft.majorId, 11);
    filter.reset(); assert.equal(filter.data.draft.keyword, 'C++'); assert.equal(filter.data.draft.jobId, 0);
    filter.syncCompany('不存在'); filter.apply(); assert.equal(filter.events.some(event => event.name === 'apply'), false);
    filter.syncCompany(' 示例公司 '); filter.apply();
    assert.equal(filter.events.at(-1).detail.filters.companyId, 21);
    filter.clearSelected({ currentTarget: { dataset: { key: 'companyId' } } }); assert.equal(filter.data.companyInput, '');
  });
  await test('同专业快捷筛选可撤销，移除其他条件保留输入中的搜索词', async () => {
    const calls = [];
    const catalog = { jobs: [], majors: [{ id: 11, name: '软件工程' }], companies: [{ id: 21, name: '示例公司' }], topics: [] };
    const api = {
      getCatalog: () => ({ promise: Promise.resolve(catalog), abort() {} }),
      listArticles(query) { calls.push(query); return { promise: Promise.resolve({ items: [], page: 1, total: 0 }), abort() {} }; }
    };
    const page = harness('pages/discover/discover.js', api);
    page.onLoad({}); await tick();
    page.updateFilters({ keyword: '实习', companyId: 21 });
    page.setData({ ownMajorId: 11, keywordInput: '待提交的新搜索' });
    page.toggleSameMajor();
    assert.equal(calls.at(-1).majorId, 11);
    assert.equal(page.data.keywordInput, '待提交的新搜索');
    assert.equal(calls.at(-1).keyword, '实习');
    assert.ok(page.data.chips.some((chip) => chip.key === 'companyId'));
    page.clearChip({ currentTarget: { dataset: { key: 'companyId' } } });
    assert.equal(calls.at(-1).companyId, undefined);
    assert.equal(calls.at(-1).majorId, 11);
    page.toggleSameMajor();
    assert.equal(calls.at(-1).majorId, undefined);
    assert.equal(page.data.keywordInput, '待提交的新搜索');
  });
  await test('岗位选择器以分类 ID 更新请求，保留其他条件并同步选中项', async () => {
    const calls = [];
    const catalog = { jobs: [{ id: 49, name: 'Java 后端' }, { id: 12, name: '前端' }], majors: [], companies: [], topics: [] };
    const api = {
      getCatalog: () => ({ promise: Promise.resolve(catalog), abort() {} }),
      listArticles(query) {
        calls.push(query);
        return { promise: Promise.resolve({ items: [], page: 1, total: 0 }), abort() {} };
      }
    };
    const page = harness('pages/discover/discover.js', api);
    page.onLoad({});
    await tick();
    page.updateFilters({ keyword: '实习', companyId: 21 });
    page.selectJob({ detail: { value: '1' } });
    assert.equal(page.data.jobChoiceIndex, 1);
    assert.equal(calls.at(-1).jobId, 49);
    assert.equal(calls.at(-1).keyword, '实习');
    assert.equal(calls.at(-1).companyId, 21);
    const count = calls.length;
    page.selectJob({ detail: { value: '1' } });
    assert.equal(calls.length, count);
    page.selectJob({ detail: { value: '0' } });
    assert.equal(page.data.jobChoiceIndex, 0);
    assert.equal(calls.at(-1).jobId, undefined);
    assert.equal(calls.at(-1).companyId, 21);
  });
  await test('非固定章数、重复标题、六级标题、混合嵌套列表与全文末尾保留', () => {
    const body = Array.from({ length: 13 }, (_, index) => '## 相同标题\n\n章节' + index).join('\n\n') + '\n\n###### 深层标题\n\n3. 父项\n  - 子项\n    7. 孙项\n  - 第二子项\n4. 尾项\n\n~~~cpp\n<int> value;\n~~~\n\n前文![图](/api/v1/media/demo)后文\n\n全文末尾';
    const parsed = parseMarkdown(body, { assetBaseUrl: 'http://127.0.0.1:8080' });
    assert.equal(parsed.headings.length, 14); assert.equal(new Set(parsed.headings.map(item => item.id)).size, 14);
    const list = parsed.nodes.find(item => item.type === 'list');
    assert.deepEqual(list.items.map(item => item.level), [0, 1, 2, 1, 0]);
    assert.deepEqual(list.items.map(item => item.marker), ['3.', '•', '7.', '•', '4.']);
    assert.equal(parsed.nodes.find(item => item.type === 'code').text, '<int> value;');
    assert.equal(parsed.images.length, 1); assert.match(JSON.stringify(parsed.nodes), /后文/); assert.match(JSON.stringify(parsed.nodes.at(-1)), /全文末尾/);
  });
  await test('正文图片失败状态可恢复，重试期间不重复加载或预览', () => {
    const c = harness('components/markdown-view/index.js', {}, true);
    c.data.nodes = [{ type: 'image', originalSrc: 'http://127.0.0.1:8080/image.png', src: 'http://127.0.0.1:8080/image.png' }];
    let updates = 0;
    c.measureHeadings = () => {};
    c.setData = (patch, done) => {
      updates += 1;
      for (const [key, value] of Object.entries(patch)) c.data.nodes[0][key.split('.').pop()] = value;
      if (done) done();
    };
    const event = { currentTarget: { dataset: { index: 0 } } };
    c.handleImageError(event); assert.equal(c.data.nodes[0].failed, true);
    c.retryImage(event); c.retryImage(event); c.previewImage(event);
    assert.equal(updates, 2); assert.equal(c.data.nodes[0].retrying, true);
    c.handleImageLoad(event); assert.equal(c.data.nodes[0].retrying, false);
  });
  await test('正文观察器不递归更新父页面，目录检索/关闭事件正常', () => {
    const reader = harness('components/markdown-view/index.js', {}, true);
    reader.measureHeadings = () => {};
    reader.definition.observers['body, assetBaseUrl, sourceLinks, sourceNote'].call(reader, '## 第一节\n\n正文\n\n## 08 最后\n\n末尾', '', [], '');
    assert.equal(reader.events.length, 0, 'property observer must not synchronously set parent page data');
    reader.openToc(); assert.equal(reader.events.at(-1).detail.visible, true);
    reader.searchToc({ detail: { value: '08' } });
    assert.equal(reader.data.filteredHeadings.length, 1); assert.match(reader.data.filteredHeadings[0].text, /08/);
    reader.closeToc(); assert.equal(reader.events.at(-1).detail.visible, false);
  });
  await test('目录跳转动画中开始登录，仍记住用户选中的章节', () => {
    const reader = harness('components/markdown-view/index.js', {}, true);
    reader.anchorTops = [{ id: 1, top: 120 }, { id: 2, top: 510 }];
    reader.data.currentHeadingId = 1;
    reader.jumpBookmark = { headingId: 2, offset: -20, until: Date.now() + 500 };
    const bookmark = reader.capturePosition(150);
    assert.equal(bookmark.headingId, 2);
    assert.equal(bookmark.offset, -20);
    reader.jumpBookmark.until = 0;
    assert.equal(reader.capturePosition(150).headingId, 1);
  });
  await test('文章页目录入口跟随正文滚动，无章节时隐藏', () => {
    const scrolled = [];
    let opened = 0;
    const reader = { updateReadingPosition(top) { scrolled.push(top); }, openToc() { opened += 1; } };
    const detail = harness('pages/article-detail/article-detail.js');
    detail.selectComponent = () => reader;
    detail._tocPinAt = 160;
    detail.openToc();
    assert.equal(opened, 0);
    detail.setData({ hasToc: true });
    detail.openToc();
    assert.equal(opened, 1);
    detail.onPageScroll({ scrollTop: 161 });
    assert.equal(detail.data.tocPinned, true);
    detail.onPageScroll({ scrollTop: 152 });
    assert.equal(detail.data.tocPinned, false);
    assert.deepEqual(scrolled, [161, 152]);
  });
  await test('取消演示请求后 Promise 结束，不让刷新永久等待', async () => {
    const demo = require('../services/demo-service');
    demo.setScenario('normal'); const operation = demo.listArticles({ page: 1, size: 2 });
    operation.abort(); await assert.rejects(operation.promise, error => error.kind === 'ABORTED');
  });
  await test('来源零/一/多链接均保留说明并拒绝危险链接，受限字段裁剪', () => {
    for (const sourceLinks of [[], [{ label: '一', url: 'https://example.com' }], [{ url: 'https://a.example' }, { url: 'https://b.example' }, { url: 'javascript:alert(1)' }]]) {
      const detail = mapDetail({ id: 1, body: 'full', sourceNote: '完整说明', sourceLinks, author: { id: 9 }, comments: ['hidden'] });
      assert.equal(detail.sourceNote, '完整说明'); assert.equal(detail.sourceLinks.some(item => item.url.startsWith('javascript:')), false);
      assert.equal('author' in detail, false); assert.equal('comments' in detail, false);
    }
  });
  await test('详情刷新失败/404 清空旧正文；无效 ID 不发请求', async () => {
    const calls = [];
    const api = { getArticle(id) { const task = deferred(); calls.push({ id, task }); return task; } };
    const page = harness('pages/article-detail/article-detail.js', api);
    page.onLoad({ id: 'bad' }); assert.equal(calls.length, 0); assert.equal(page.data.status, 'unavailable');
    page.onLoad({ id: '1001' }); calls[0].task.resolve({ id: 1001, body: 'old' }); await tick();
    page.loadArticle(); assert.equal(page.data.article, null);
    calls[1].task.reject({ kind: 'UNAVAILABLE', message: 'hidden' }); await tick();
    assert.equal(page.data.status, 'unavailable'); assert.equal(page.data.article, null);
  });
  results.forEach(item => console.log(`${item.pass ? 'PASS' : 'FAIL'} ${item.name}${item.error ? '\n' + item.error : ''}`));
  console.log(`\n${results.filter(item => item.pass).length}/${results.length} behavior checks passed`);
  if (results.some(item => !item.pass)) process.exitCode = 1;
})();
