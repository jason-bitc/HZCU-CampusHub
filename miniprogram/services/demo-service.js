const config = require('../config/env');
const { catalog, articles } = require('./demo-data');
const { ApiError } = require('./api-error');

let scenario = config.demoScenario || 'normal';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function setScenario(value) {
  scenario = ['normal', 'empty', 'network', 'service', 'unavailable'].includes(value) ? value : 'normal';
}

function getScenario() {
  return scenario;
}

function delayed(callback) {
  let timer;
  const promise = new Promise((resolve, reject) => {
    timer = setTimeout(() => {
      try {
        if (scenario === 'network') {
          reject(new ApiError({ kind: 'NETWORK', message: '演示：无法连接服务，请检查网络后重试' }));
          return;
        }
        if (scenario === 'service') {
          reject(new ApiError({ kind: 'SERVICE', status: 503, traceId: 'DEMO-503', message: '演示：服务暂时不可用，请稍后重试' }));
          return;
        }
        resolve(callback());
      } catch (error) {
        reject(error);
      }
    }, 180);
  });
  return { promise, abort() { if (timer) clearTimeout(timer); } };
}

function getCatalog() {
  return delayed(() => clone(catalog));
}

function listArticles(query) {
  return delayed(() => {
    let items = articles.slice();
    const keyword = String(query.keyword || '').toLowerCase();
    if (scenario === 'empty' || keyword === '无结果演示') items = [];
    ['jobId', 'majorId', 'companyId', 'topicId'].forEach((idKey) => {
      if (!query[idKey]) return;
      const categoryKey = { jobId: 'jobs', majorId: 'majors', companyId: 'companies', topicId: 'topics' }[idKey];
      items = items.filter((article) => article.categories[categoryKey].some((item) => item.id === query[idKey]));
    });
    if (keyword && keyword !== '无结果演示') {
      items = items.filter((article) => `${article.title} ${article.summary}`.toLowerCase().includes(keyword));
    }
    items.sort((left, right) => {
      if (query.sort === 'latest') return new Date(right.publishedAt) - new Date(left.publishedAt) || right.id - left.id;
      return right.counts.effectiveCoins - left.counts.effectiveCoins || right.counts.effectiveLikes - left.counts.effectiveLikes || new Date(right.publishedAt) - new Date(left.publishedAt) || right.id - left.id;
    });
    const total = items.length;
    const start = (query.page - 1) * query.size;
    return { items: clone(items.slice(start, start + query.size)), total, page: query.page, size: query.size };
  });
}

function getArticle(id) {
  return delayed(() => {
    if (scenario === 'unavailable' || [2001, 2002, 2003, 2004].includes(Number(id))) {
      throw new ApiError({ kind: 'UNAVAILABLE', status: 404, traceId: 'DEMO-404', message: '演示：这篇经验不存在或当前不可用' });
    }
    const article = articles.find((item) => item.id === Number(id));
    if (!article) throw new ApiError({ kind: 'UNAVAILABLE', status: 404, message: '这篇经验不存在或当前不可用' });
    return clone(article);
  });
}

module.exports = { setScenario, getScenario, getCatalog, listArticles, getArticle };
