# WBS 5.4.2 读者发现、筛选与阅读验证

2026-11-08。分支 `feat/ljh-frontend-foundation`，接手基线 `675d4db08159f5e0f71ab0cb89569fc40c1b339b`。范围对应 M01–M06、W01–W03、S02，需求 FR-R01～FR-R06、FR-P03；未扩展到创作、社区管理或运维任务。

## 原型差距与处理

| 原型／需求 | 复现到的差距 | 本轮处理 | 结果 |
| --- | --- | --- | --- |
| W01、FR-R03 | 网页只有手动专业下拉框，没有认证读者“同专业”快捷条件 | 复用真实 `GET /api/v1/me/profile`；保留其他 URL 条件，未填专业时进入本人资料页，读取失败不影响公开列表 | 已修复 |
| W01、S02、FR-P03 | 后端未启动时代理返回空响应，页面直接显示 JSON 解析异常 | 空／非 JSON 的 HTTP 失败转为明确错误；空 401 仍清理私有会话 | 已修复并回归 |
| W01、S02 | 新查询失败后仍显示上一次“找到 N 篇经验” | 失败时改显“当前条件读取失败”，不再混用旧计数 | 已修复并复核截图 |
| M01–M06、S02 | 既有小程序流程已覆盖目标岗位、组合筛选、游客阅读、空态与重试 | 未改小程序业务代码；切到本机真实配置后做原生回归 | 通过本轮范围 |

网页保留整卡点击、悬停、独立收藏和圆形回顶实现；小程序保留阅读区右上角 44×44px 方框目录及“硬币”文案。本批没有改动这些既有交互。

## 读者候选人任务复现

1. 启动本机业务后端 `8080`，健康检查返回 `code=OK`、`data.status=UP`；网页使用既有 `5173`。
2. 游客进入发现页，选择“Java后端开发（演示）”。网页和小程序均返回 6 篇，URL／页面条件保留 `jobId=1`。
3. 打开《【演示】Java后端实习准备路线》（文章 1001）。游客可阅读完整 Markdown、目录和来源链接；网页不请求本人资料，小程序显示登录入口。
4. 再试同专业：既有真实测试账号的 `majorId=null`，两端均显示“完善本人专业”，未修改其资料。网页正向分支另将 `GET /me/profile` 模拟为 `majorId=11`，点击后保留 `jobId=1` 并加入 `majorId=11`；文章列表仍由真实接口返回 5 篇。该正向截图不是实际账号资料证明。
5. 输入不存在的关键词，网页与小程序均显示空结果而非错误，岗位和关键词仍可见；清除后恢复公开列表。
6. 网页注入一次空体 503，显示可执行错误，点击重试后沿用岗位、专业和排序恢复。小程序验收时实际停止本轮启动的后端，页面显示“加载失败”且保留岗位；重启后点击“重试当前请求”恢复 6 篇。

## 分层验证

| 层级 | 实际执行与结果 |
| --- | --- |
| 小程序自动化 | `tests/run.js` 10/10、`tests/behavior.js` 10/10、`tests/member-behavior.js` 25/25 |
| 网页自动化与构建 | `npm test` 22/22；`npm run build` 的类型检查和 user/admin/ops 三份 Vite 构建均通过 |
| 真实公开 API | `node miniprogram/tests/real-api.js http://127.0.0.1:8080` 通过；7 篇公开文章，认可／最新排序、稳定分页、四类组合筛选、空结果、全文及非公开 404 均符合合同。证据见 [REAL-PUBLIC-API-20261108.json](../miniprogram/docs/REAL-PUBLIC-API-20261108.json) |
| 浏览器 | Kimi Code 内置浏览器复现原始错误并完成真实岗位筛选和游客阅读；Playwright／本机 Chrome 完成可重复走查及截图，49 次 API 响应记录。仅登录、退出，不创建内容、不修改资料、不点赞、不投币。详见 [REAL-READER-20261108.json](../web/docs/REAL-READER-20261108.json) |
| 微信模拟器 | 微信开发者工具直接打开当前 `miniprogram/`，本机忽略配置为 `demoMode:false`、业务地址 `127.0.0.1:8080`。原生页面数据确认 `ready/7`、岗位 `6`、空态 `empty/0`、恢复 `ready/7`；真实断网及重试通过，控制台过滤 `error` 无结果。截图 289×625 |
| 真实用户与真机 | 未由刘景浩本人另行走查；未执行手机扫码、真机或小屏系统大字号验收。浏览器移动视口和微信模拟器不替代真机 |

读者验收使用主后端目录现有私密配置，源码提交 `399dcbb0009fa9ae4ac557fb7e9296775fcd7be9`。PR #8 工作树因没有本机数据库凭据未启动；本批不调用治理接口。密码、令牌、本机配置和 IDE 网络原始日志未写入本记录。

## 接口核对

| 页面 | 接口 | 实际响应／条件 | 缺口 |
| --- | --- | --- | --- |
| 发现 | `GET /api/v1/catalog`、`GET /api/v1/articles` | 真实 200；岗位、专业、公司、主题及排序参数符合合同 | 无 |
| 阅读 | `GET /api/v1/articles/1001` | 真实 200，返回公开全文和来源；游客响应无作者、评论或本人互动字段 | 无 |
| 同专业 | `GET /api/v1/me/profile` | 真实账号 200 且 `majorId=null`；正向 majorId=11 仅为明确标注的浏览器模拟 | 无 |
| 失败与重试 | 同上 | 网页空体 503 为故障注入；小程序为实际停止服务后的连接失败，均保留条件并恢复 | 无 |

## 实际页面截图

### 小程序原生模拟器

- [M01 真实发现](../miniprogram/docs/screenshots/20261108-reader/M01-discover.png) · [M01 目标岗位](../miniprogram/docs/screenshots/20261108-reader/M01-job-filter.png)
- [M03 游客筛选](../miniprogram/docs/screenshots/20261108-reader/M03-guest-filter.png) · [M03 真实账号未填专业](../miniprogram/docs/screenshots/20261108-reader/M03-no-major.png)
- [M05 游客阅读](../miniprogram/docs/screenshots/20261108-reader/M05-guest-reading.png) · [M04 空结果](../miniprogram/docs/screenshots/20261108-reader/M04-empty-result.png)
- [S02 真实连接失败](../miniprogram/docs/screenshots/20261108-reader/S02-native-connection-failure.png) · [重试恢复](../miniprogram/docs/screenshots/20261108-reader/M01-native-retry-recovered.png)

### 网页浏览器

- [W01 目标岗位](../web/docs/screenshots/20261108-reader/W01-job-filter.png) · [W02 游客全文](../web/docs/screenshots/20261108-reader/W02-guest-reading.png)
- [W01 空结果](../web/docs/screenshots/20261108-reader/W01-empty-result.png) · [真实账号未填专业](../web/docs/screenshots/20261108-reader/W01-no-major.png)
- [同专业正向分支（资料模拟）](../web/docs/screenshots/20261108-reader/W01-same-major.png) · [移动视口](../web/docs/screenshots/20261108-reader/W01-mobile.png)
- [S02 空体 503](../web/docs/screenshots/20261108-reader/S02-empty-gateway-failure.png) · [重试恢复](../web/docs/screenshots/20261108-reader/W01-retry-recovered.png)
