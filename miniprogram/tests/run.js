const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const { buildArticleQuery, mergeUniqueArticles, validateKeyword } = require('../utils/query');
const { createRequestGate } = require('../utils/request-gate');
const { parseMarkdown, normalizeExternalUrl, resolveAssetUrl } = require('../utils/markdown');
const { mapSummary, mapDetail } = require('../services/article-mapper');
const demo = require('../services/demo-service');
const { request } = require('../services/request');

const results = [];
function test(name, callback) {
  try {
    callback();
    results.push({ name, status: 'PASS' });
  } catch (error) {
    results.push({ name, status: 'FAIL', message: error.message });
  }
}

function walk(directory, extension, output) {
  fs.readdirSync(directory, { withFileTypes: true }).forEach((entry) => {
    if (['node_modules', 'docs', 'tests'].includes(entry.name)) return;
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(target, extension, output);
    else if (entry.name.endsWith(extension)) output.push(target);
  });
  return output;
}

test('工程 JSON 严格可解析，页面与组件文件完整', () => {
  const jsonFiles = walk(root, '.json', []);
  jsonFiles.forEach((file) => JSON.parse(fs.readFileSync(file, 'utf8')));
  const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
  assert.strictEqual(app.lazyCodeLoading, 'requiredComponents');
  assert.ok(app.pages.length > 0);
  app.pages.forEach((page) => ['.js', '.wxml', '.wxss', '.json'].forEach((extension) => {
    assert.ok(fs.existsSync(path.join(root, `${page}${extension}`)), `${page}${extension} missing`);
  }));
  const project = JSON.parse(fs.readFileSync(path.join(root, 'project.config.json'), 'utf8'));
  assert.strictEqual(project.miniprogramRoot, './');
});

test('WXML 不使用网页标签或复杂内联对象', () => {
  const files = walk(root, '.wxml', []);
  files.forEach((file) => {
    const source = fs.readFileSync(file, 'utf8');
    assert.ok(!/<\/?(article|section|main|nav|header|footer|div|span|p|ul|ol|li|img|script|iframe)(\s|>)/i.test(source), `${file}: HTML tag`);
    assert.ok(!/wx:for="\{\{\s*\[/.test(source), `${file}: inline object array`);
    assert.ok(!/[?.]\?|\?\?/.test(source), `${file}: unsupported optional chaining`);
    assert.ok(!/\{\{[^{}]*\(/.test(source), `${file}: unsupported WXML parentheses expression`);
  });
});

test('查询合同只发送允许的筛选参数并重置分页', () => {
  const query = buildArticleQuery({ keyword: '  Java  ', jobId: 1, majorId: 0, companyId: 21, topicId: 31 }, 'latest', 1, 10);
  assert.deepStrictEqual(query, { sort: 'latest', page: 1, size: 10, keyword: 'Java', jobId: 1, companyId: 21, topicId: 31 });
  assert.strictEqual(validateKeyword('abc'), '');
  assert.ok(validateKeyword('a'.repeat(101)));
});

test('分页按文章 ID 去重且保留新公开版本', () => {
  const merged = mergeUniqueArticles([{ id: 1, title: 'old' }, { id: 2 }], [{ id: 1, title: 'new' }, { id: 3 }]);
  assert.deepStrictEqual(merged.map((item) => item.id), [1, 2, 3]);
  assert.strictEqual(merged[0].title, 'new');
});

test('过期请求令牌不能覆盖新查询', () => {
  const gate = createRequestGate();
  const oldToken = gate.next();
  const currentToken = gate.next();
  assert.strictEqual(gate.isCurrent(oldToken), false);
  assert.strictEqual(gate.isCurrent(currentToken), true);
});

test('Markdown 常用结构、图片与危险链接安全处理', () => {
  const parsed = parseMarkdown('# 标题\n\n段落 **加粗** [资料](https://example.com)\n\n- 项目\n\n> 引用\n\n```html\n<script>alert(1)</script>\n```\n\n![图](/api/v1/media/1)', { assetBaseUrl: 'http://127.0.0.1:8080' });
  assert.deepStrictEqual(parsed.nodes.map((node) => node.type), ['heading', 'paragraph', 'list', 'quote', 'code', 'image']);
  assert.strictEqual(parsed.nodes[2].items[0].marker, '•');
  assert.strictEqual(parsed.headings.length, 1);
  assert.strictEqual(parsed.images[0], 'http://127.0.0.1:8080/api/v1/media/1');
  assert.strictEqual(normalizeExternalUrl('javascript:alert(1)'), '');
  assert.strictEqual(resolveAssetUrl('data:text/html,abc', ''), '');
});

test('公开数据映射不携带作者、联系方式或评论字段', () => {
  const raw = {
    id: 1, title: '公开', summary: '摘要', categories: {}, sourceType: 'DEMO', publishedAt: '2026-01-01T00:00:00Z',
    counts: {}, body: '正文', author: { nickname: '受限' }, avatar: '受限', contact: '受限', comments: ['受限']
  };
  const summary = mapSummary(raw);
  const detail = mapDetail(raw);
  ['author', 'avatar', 'nickname', 'contact', 'comments'].forEach((key) => {
    assert.ok(!(key in summary));
    assert.ok(!(key in detail));
  });
});

async function asyncTests() {
  try {
    demo.setScenario('normal');
    const recognized = await demo.listArticles({ page: 1, size: 2, sort: 'recognized' }).promise;
    assert.strictEqual(recognized.items[0].id, 1003);
    const latest = await demo.listArticles({ page: 1, size: 2, sort: 'latest' }).promise;
    assert.strictEqual(latest.items[0].id, 1006);
    const second = await demo.listArticles({ page: 2, size: 2, sort: 'latest' }).promise;
    assert.ok(!second.items.some((item) => latest.items.some((first) => first.id === item.id)));
    const filtered = await demo.listArticles({ page: 1, size: 10, sort: 'latest', jobId: 1, majorId: 12 }).promise;
    assert.ok(filtered.items.every((item) => item.categories.jobs.some((job) => job.id === 1) && item.categories.majors.some((major) => major.id === 12)));
    results.push({ name: '演示服务认可/最新排序、分页与组合筛选', status: 'PASS' });
  } catch (error) {
    results.push({ name: '演示服务认可/最新排序、分页与组合筛选', status: 'FAIL', message: error.message });
  }

  try {
    demo.setScenario('empty');
    const empty = await demo.listArticles({ page: 1, size: 10 }).promise;
    assert.strictEqual(empty.total, 0);
    demo.setScenario('network');
    await assert.rejects(demo.listArticles({ page: 1, size: 10 }).promise, (error) => error.kind === 'NETWORK');
    demo.setScenario('service');
    await assert.rejects(demo.listArticles({ page: 1, size: 10 }).promise, (error) => error.kind === 'SERVICE' && error.status === 503);
    demo.setScenario('unavailable');
    await assert.rejects(demo.getArticle(1001).promise, (error) => error.kind === 'UNAVAILABLE' && error.status === 404);
    demo.setScenario('normal');
    results.push({ name: '空结果、网络失败、503 与不可用状态分别可复现', status: 'PASS' });
  } catch (error) {
    demo.setScenario('normal');
    results.push({ name: '空结果、网络失败、503 与不可用状态分别可复现', status: 'FAIL', message: error.message });
  }

  try {
    global.wx = {
      request(options) {
        options.success({ statusCode: 503, header: { 'X-Trace-Id': 'TRACE-TEST' }, data: { code: 'SERVICE_UNAVAILABLE', message: 'down', traceId: 'TRACE-TEST' } });
        return { abort() {} };
      }
    };
    await assert.rejects(request({ path: '/api/v1/articles' }).promise, (error) => error.kind === 'SERVICE' && error.traceId === 'TRACE-TEST');
    results.push({ name: 'HTTP 503、业务错误与 traceId 统一转换', status: 'PASS' });
  } catch (error) {
    results.push({ name: 'HTTP 503、业务错误与 traceId 统一转换', status: 'FAIL', message: error.message });
  }

  results.forEach((result) => console.log(`${result.status} ${result.name}${result.message ? ` - ${result.message}` : ''}`));
  const failed = results.filter((result) => result.status === 'FAIL');
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  if (failed.length) process.exitCode = 1;
}

asyncTests().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
