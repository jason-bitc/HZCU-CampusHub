const config = require('../config/env');
const { request } = require('./request');
const demo = require('./demo-service');
const { mapCatalog, mapPage, mapDetail } = require('./article-mapper');

function adapt(call, mapper) {
  if (config.demoMode) {
    const operation = call();
    return { promise: operation.promise.then(mapper), abort: operation.abort };
  }
  const operation = request(call);
  return { promise: operation.promise.then(mapper), abort: operation.abort };
}

function getCatalog() {
  return adapt(() => config.demoMode ? demo.getCatalog() : { path: '/api/v1/catalog' }, mapCatalog);
}

function listArticles(query) {
  return adapt(() => config.demoMode ? demo.listArticles(query) : { path: '/api/v1/articles', data: query }, mapPage);
}

function getArticle(id) {
  return adapt(() => config.demoMode ? demo.getArticle(id) : { path: `/api/v1/articles/${encodeURIComponent(id)}` }, mapDetail);
}

module.exports = { getCatalog, listArticles, getArticle, setDemoScenario: demo.setScenario, getDemoScenario: demo.getScenario };
