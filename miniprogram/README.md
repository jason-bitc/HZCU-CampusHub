# 城院帮微信小程序 · 第一批前端

本目录是微信原生小程序工程源码根。已接入公开只读接口合同：

- `GET /api/v1/catalog`
- `GET /api/v1/articles`
- `GET /api/v1/articles/{id}`

本批实现游客的发现、组合筛选、排序、刷新分页、文章详情、完整长文阅读、固定目录跳转、Markdown 安全展示及加载/空/失败/不可用状态。未实现认证、评论、作者联系、创作和个人工作区；对应底部入口仅以禁用状态提示“后续批次开放”，不伪装成可用功能。

## 目录

```text
miniprogram/
  app.json / app.js / app.wxss
  project.config.json
  pages/
    discover/          发现与组合筛选（M01、M03、M04）
    article-detail/    游客经验详情（M05）
  components/
    article-card/      公开经验列表项
    filter-panel/      组合筛选与同专业说明
    markdown-view/     安全 Markdown 阅读
    page-state/        加载、空、失败、权限/不可用状态
    prototype-nav/     原型底部导航框架（未开放入口禁用提示）
    app-icon/          统一本地 SVG 图标
  services/
    request.js         wx.request 封装、超时、HTTP/业务错误、traceId
    articles.js        catalog/articles 公开接口入口
    article-mapper.js  只保留公开字段，不映射作者私有资料
    demo-service.js    显式演示模式（默认关闭）
  config/
    defaults.js        默认服务地址和请求参数
    env.js             合并本地配置
    local.example.js   本机配置示例
    local.js           本机演示配置；已忽略，不入库
  utils/               查询、筛选、Markdown、格式化、请求失效保护
  assets/icons/        统一图标
  styles/              颜色、字号、间距、圆角等变量
  tests/run.js         源码结构、合同与安全行为检查
```

## 导入微信开发者工具

1. 打开微信开发者工具，选择“导入项目”。
2. 项目目录选择本文件所在目录：
   `C:\Users\wangc\Desktop\HZCU-CampusHub-Frontend\miniprogram`
   不要选择仓库根目录。
3. `project.private.config.json` 已在忽略规则中。首次导入时把其中的 `appid` 从 `touristappid` 改成本人真实小程序 AppID；不要提交该文件或任何账号凭据。
4. 开发阶段保留私密配置中的 `urlCheck: false`，用于访问本地开发后端。

## 运行真实后端

1. 在 `config/local.js` 中设置：

```js
module.exports = {
  demoMode: false,
  apiBaseUrl: 'http://127.0.0.1:8080',
  requestTimeout: 10000,
  debug: false
};
```

2. 启动既有 Java 后端，并确认以下接口返回统一 JSON：

- `GET /api/v1/catalog`
- `GET /api/v1/articles?keyword=&jobId=&majorId=&companyId=&topicId=&sort=recognized|latest&page=1&size=10`
- `GET /api/v1/articles/{id}`

3. 切换筛选或排序时，前端会把 `page` 重置为 1；认可排序由服务端完成，前端不对单页重新排序。

## 开发者工具与手机真机地址

- 微信开发者工具模拟器访问本机后端：`http://127.0.0.1:8080`。
- 手机真机不能访问电脑上的 `localhost`。把 `config/local.js` 的 `apiBaseUrl` 改为电脑在局域网中的 IP，例如 `http://192.168.1.20:8080`，同时确认后端监听 `0.0.0.0`、防火墙允许该端口。
- 真机联调后应把真实结果写入验证记录；开发工具截图不等于真机验证通过。

## 演示模式

默认仓库配置 `demoMode: false`，只连接真实接口。当前工作区存在被忽略的 `config/local.js`，其中显式设置了 `demoMode: true`，用于本机 WorkBuddy 预览；页面顶部会显示醒目的“演示模式”条，不会被静默当成真实联调。

演示状态只用于走查：

- 发现页演示条可切换：正常数据、空结果、网络失败、服务异常。
- 详情页演示条可切换：正常文章、网络失败、服务异常、文章不可用。
- 演示数据位于 `services/demo-data.js`，只包含公开字段，不包含作者昵称、头像、联系方式或评论。

要恢复真实接口，将 `demoMode` 改为 `false` 或删除本机 `config/local.js`。

## 阅读体验与完整正文

- 正文不做前端截断：Markdown 解析器把标题、段落、列表、引用、代码和图片全部转成节点渲染。
- 演示长文 `1001` 包含 8 个完整章节，测试会校验目录数量、最后一章和正文末尾内容，防止只渲染开头。
- 文章开头保留“文章目录”；阅读过程中右侧固定显示“目录”悬浮按钮，点击打开章节抽屉并定位到对应章节。
- 外部资料在正文内以资料卡呈现，点击复制链接；小程序不直接跳转任意外链。
- 真实接口如果暂未返回 `jobNature` 或 `baseLevel`，详情页显示“岗位性质未标注/未填写”，不伪造经历信息。

## 调试日志

在 `config/local.js` 设置 `debug: true` 后，控制台记录：

- 请求路径
- HTTP 状态
- 请求耗时
- traceId

日志不记录密码、令牌或完整敏感响应。界面错误使用用户可理解的文案；列表服务失败显示“加载失败/重试”，不会显示成空列表；文章 404 清除正文缓存并显示不可用状态。

## 本批检查

在仓库任意位置执行：

```bash
node miniprogram/tests/run.js
```

当前结果：`10/10 checks passed`。该检查覆盖严格 JSON、页面文件、WXML 约束、查询合同、分页去重、请求失效保护、Markdown 安全处理、公开数据字段裁剪、演示排序/筛选、完整长文章节保留及 503/traceId 错误分类。

更完整的原型、需求与验证映射见：

- `docs/TRACEABILITY.md`
- `docs/VERIFICATION.md`
