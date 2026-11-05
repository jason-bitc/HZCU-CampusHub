# 城院帮原生小程序：公开发现与阅读

范围：M01 游客发现、M03 组合筛选、M04 空结果恢复、M05 游客详情、S02 本批适用状态。沿用既有 WXML / WXSS / JavaScript 工程及 Java / Spring Boot / MySQL 后端，不接入 CloudBase。

账号、认证、互动、创作、个人工作区和管理端属于后续批次。对应入口为真正禁用态，标明尚未开放。

## 在微信开发者工具运行

1. 导入本目录 `miniprogram`，不是仓库根目录；`project.config.json` 中 `miniprogramRoot` 为 `./`。
2. 在项目详情核对实际使用的 AppID 和本人权限。共享配置保留 `touristappid` 占位；本机已有 Git 忽略的 `project.private.config.json`，其中包含本机 AppID 及设置。不要只依据文件存在就认为手机预览权限已验证，也不要将本人配置提交。
3. 保留已有本机设置，不修改系统安全策略。本轮在既有 Stable 2.02.2608060、基础库 3.8.12 中原生编译并运行了两个页面。GUI验证与手机真机验证分别记录。
4. 工具菜单“工具 → 编译”（Ctrl+R）执行原生编译。改配置后应完整编译；热更新后的旧模块状态不能作为模式切换成功的依据。
5. 当前工作区原有 `config/local.js` 明确启用演示模式，本轮临时切换真实接口验证后已恢复原内容。该文件不上库。

## 连接现有真实后端

在 `config/local.js` 设置以下前端配置，不放数据库账号、密码或令牌：

```js
module.exports = {
  demoMode: false,
  apiBaseUrl: 'http://127.0.0.1:8080',
  requestTimeout: 10000,
  pageSize: 10,
  debug: false
};
```

按既有后端 `server/README.md` 和本机配置启动 Java 服务及 MySQL，不重建库、不改迁移、不重置密码。健康接口须返回 `HTTP 200 / code=OK / data.status=UP`。

公开合同只使用：

```text
GET /api/v1/catalog
GET /api/v1/articles?sort=recognized&page=1&size=10
GET /api/v1/articles?keyword=Java&jobId=1&majorId=11&companyId=21&topicId=31&sort=latest&page=1&size=10
GET /api/v1/articles/1001
```

数字筛选参数是可选正整数，不使用 `jobId=` / `companyId=` 等空数字。公司必须选择 catalog 中的有效标签，不发送任意公司文本。关键词只检索标题和摘要；岗位、专业、公司、主题分别使用分类 ID。排序、筛选在服务端执行，不在前端对单页重排。

真实模式失败不会回退演示数据。空结果、连接失败、服务异常、内容不可用分别展示；重试保留条件，分页失败保留已加载列表。快速切换时旧响应失效。返回详情前的页面实例保留查询、分页和滚动位置。

## 阅读、目录与游客边界

- 正文完整渲染，不按章节数截断。支持1–6级标题、重复标题的独立锚点、混合嵌套列表、原始列表序号、引用、代码空白及图片。
- 按本轮用户明确要求，删除正文开头目录。右上角为44×44px小方框；点击后打开可检索的目录抽屉。章节定位使用实际节点位置，图片加载后更新锚点。
- 图片可查看大图；单张失败可重试，不阻断其余正文。外链提供真实的复制操作，不伪装任意网页直接跳转。
- 文末始终提供来源说明，有0/1/多个资料链接时均不丢失；正文资料卡定位到完整来源区。
- 游客响应再次裁剪作者、头像、昵称、联系方式和评论；不可用/失败时清空旧正文。后端没有返回的“当时基础/岗位性质”显示未填写/未标注，不补造经历。

## 演示状态与开发辅助

演示模式必须显式开启，页面有非交互标识，数据统一标记为演示。正式阅读区没有故障切换按钮。

可在微信工具的自定义编译模式中使用：

| 页面 | 参数 | 场景 |
|---|---|---|
| `pages/discover/discover` | `scenario=normal` | 普通列表 |
| `pages/discover/discover` | `scenario=empty` | 空结果 |
| `pages/discover/discover` | `scenario=network` | 连接失败 |
| `pages/discover/discover` | `scenario=service` | 503服务异常 |
| `pages/article-detail/article-detail` | `id=1001&scenario=normal` | 八节完整长文 |
| `pages/article-detail/article-detail` | `id=2001&scenario=unavailable` | 内容不可用 |

这些参数只在 `demoMode:true` 生效。状态在演示服务内共享，切回普通场景时显式使用 `scenario=normal`。非固定章数、重复标题和嵌套列表另由行为检查覆盖。

## 本轮检查及证据

```powershell
node miniprogram/tests/run.js
node miniprogram/tests/behavior.js
# 需要真实 Java / MySQL 后端；只发 GET，不创建测试数据：
node miniprogram/tests/real-api.js http://127.0.0.1:8080
```

本轮：原有10项源码检查通过，新增10项行为检查通过；真实接口检查20次请求通过。原生编译器检查8份WXML、11份WXSS通过；GUI实际运行了发现、筛选、空结果、详情及真实连接失败恢复。两种模式、正文图片、目录及禁用边界有原生截图。

这不等于手机真机通过。目录末节可视定位、遮罩背景滚动、全部公司输入边界及小屏/横屏尚有待复测项，具体已执行范围见验证记录。

- [本轮差距及处理表](docs/TAKEOVER-20261105.md)
- [需求对应和接口缺口](docs/TRACEABILITY.md)
- [分层验证及历史记录](docs/VERIFICATION.md)
- [真实接口验证摘要](docs/REAL-API-20261105.json)
- [原生截图一览](docs/screenshots/20261105/index.html)

`debug:true` 仅记录路径、状态、耗时、traceId，不记录凭据或完整响应。控制台的本地HTTP图片/域名提示属于开发地址限制；正式发布须使用合规HTTPS资源，不能凭本机模拟器成功声称真机已通过。

## 手机真机下一步

手机不能通过电脑的 `127.0.0.1` 访问后端。真实联调需使用可达的HTTPS服务，或经本人确认的局域网开发地址和微信允许的调试方式；本轮没有修改防火墙、系统策略或后端监听范围。

在有权限的 AppID 下由本人扫码预览，实际检查小方框目录、目录搜索/遮罩、末节定位、图片重试、返回滚动恢复、网络失败恢复及安全区后，再补记真机结果。本轮未把扫码、手机运行或真机接口连通记为已完成。
