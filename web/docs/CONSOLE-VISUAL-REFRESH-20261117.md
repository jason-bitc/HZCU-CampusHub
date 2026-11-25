# G04 社区管理与独立运维视觉验收

分支从控制台交互 PR #45 的 6572f3bf30e602a64ba7a5ed2317ffa6cbafc2c3 建立。创建评审时以 feat/ljh-console-ux 为基础分支，待 #45 合并后可将本 PR 的基础分支改为 main。两个界面沿用独立入口、登录和 API 代理。

## 社区管理改前与改后

| 页面、WBS 与需求 | 改前 | 改后与复现 |
| --- | --- | --- |
| A01–A02 登录、待办案件，WBS 5.9.2，FR-M01–M02 | 深绿导航较重，列表状态与负责人混在一起 | 白底导航保留 G04 标识，浅色表头、状态标签和次要重置。选择举报、待受理，打开案件并经过内容巡查返回。检查 URL 条件、行位置与焦点。拦截一次列表请求再重试。 |
| A03–A04 案件、文章和评论，WBS 5.9.2，FR-M02–M03、FR-M07 | 案件处置区使用旧色，正文长文本与操作权重接近 | 表单、操作反馈、危险动作和焦点沿用统一组件。认领隔离案件，推进服务端修订，核对 409 后当前理由并再次确认。 |
| A05–A08 校园认证、申诉、分类、投币与审计，WBS 5.9.2，FR-M04、FR-M06–M07、FR-G05–G06 | 表格、治理操作与回执没有统一浅色层级 | 沿用各自的权限、风险信息、审计和修订回读。实测投币失效、闭案、同义分类归并，以及隔离账号认证撤销、通知、申诉和复核回读。 |

## 独立运维改前与改后

| 页面、WBS 与需求 | 改前 | 改后与复现 |
| --- | --- | --- |
| O01–O03 登录、总览、日志，WBS 5.11.1，FR-A06、FR-O01–O02、NFR08 | 查询表和近期任务堆叠，列表较长时日志在任务后 | 总览区分业务健康与运维可达。日志优先显示查询栏，任务事件和业务请求分区，可用键盘聚焦滚动区，复制真实任务 ID 和追踪号。保留上次日志和当前查询条件。 |
| O04–O07 部署、重启、备份与恢复，WBS 5.11.1–5.11.2，FR-O03–O06 | 摘要、操作、确认和风险使用旧主题 | 复用主操作、危险状态和键盘对话框，保留预检文件、覆盖范围、恢复风险和服务端回执。备份丢失回执按原请求再次核对同一任务。 |
| O08–O10 任务、异常及手册，WBS 5.11.1–5.11.2，FR-O07–O08、NFR09 | 任务行状态使用较难区分的标签 | 受理、执行、成功、失败及中断按语义区分，保留最近阶段与查询追踪号。日志页可打开原任务。手册区分本机备份与离机交接。 |

## 分层验证

- 自动化 npm test 25 项通过。npm run build 完成类型检查及 user、admin、ops 三入口打包。
- Chrome 实际运行页 21 个页面与视口组合，检查 1440px、768px、390px，无文档级横向溢出。另运行 management-browser.mjs，24 张原型截图、6 个实际只读截图、3 项模拟契约检查通过。
- 真实社区管理使用 5174 → 8082。原套件通过重置焦点、路径和行位置、刷新失败留存、认领与真实 409、真实投币失效及审计、闭案和分类归并 7 项。后段的既有测试账号已撤销，从另一隔离账号补验完成。/me/certification、/me/notifications、POST /me/appeals 及对该申诉的复核和本人回读均成功。撤销后 /me 返回预期 403 ACCESS_RESTRICTED。
- 真实健康读取。8080 与 8082 的 /actuator/health 返回 200 OK，data.status=UP。隔离 8092 的 /health 返回 200 OK，data.status=UP、actorDomain=OPS_ADMIN。本机默认 8091 的 /health 在 2 秒内未回应，本轮执行使用 8092。
- 隔离运维备份回执丢失检查通过。POST /api/v1/ops/jobs 返回 HTTP 200，原请求重复核对同一任务 623c02c6-bc92-46ac-8d75-f7232632a199，浏览器取得执行成功及备份时间，随后从日志页重开同 ID 原任务。首个真实回执追踪号 7d1a609c-3310-4713-9b87-721f9c694b06。
- 隔离解包模式使用 5175 → 8092。在核实独立 MySQL 及媒体目录后停掉 8082，运维登录和日志仍可用。日志中断保留上次结果；任务轮询中断保留当前任务 ID 与阶段，恢复后继续查询。部署 9984c40b-6141-4716-b2a7-280ef48717bd，重启 328b4b7c-2509-470a-8ecf-8fa368265931，完整备份 8e3dea88-4a1e-4dd6-95ea-a3aba82e27e4，恢复 62bdd8f0-924e-4cd1-843f-f8968c376900 均为 SUCCEEDED。恢复预检与二次确认已走查，结果 businessVerification.status=VERIFIED，检查 13 个媒体引用，业务健康恢复 UP。
- A03 的 POST /api/v1/admin/cases/15/decisions 返回预期 409 REVISION_CONFLICT，追踪号 e8e60d8c-ed79-4faf-930f-e850075b9446。这是受控并发场景。同一案件随后回读闭案与审计。
- 上轮已知 O08 打包模式部署缺口继续保留。GET /api/v1/ops/jobs/72af8df3-918f-42f4-a2e9-e8ff36b05906，本轮回读为 200 OK、data.state=FAILED，末阶段 PREFLIGHT、data.errorCode=OPS_EXECUTION_FAILED，追踪号 aeadf5fb-36d1-47bb-ba2a-db849a32a12d。保留该任务作为打包失败证据，本轮解包任务另记。后端仍需定位打包模式预检失败原因。
- 真机、真实用户和第二设备离机签收待执行。

## 截图

- 案件列表 [1440px 改前](screenshots/20261117-console-visual/before-admin-list-1440.png) · [改后](screenshots/20261117-console-visual/after-admin-list-1440.png) · [390px 改前](screenshots/20261117-console-visual/before-admin-list-390.png) · [改后](screenshots/20261117-console-visual/after-admin-list-390.png)
- 案件详情 [390px 改前](screenshots/20261117-console-visual/before-admin-case-390.png) · [改后](screenshots/20261117-console-visual/after-admin-case-390.png)
- 运维日志 [1440px 改前](screenshots/20261117-console-visual/before-ops-logs-1440.png) · [改后](screenshots/20261117-console-visual/after-ops-logs-1440.png) · [768px 改前](screenshots/20261117-console-visual/before-ops-logs-768.png) · [改后](screenshots/20261117-console-visual/after-ops-logs-768.png) · [390px 改前](screenshots/20261117-console-visual/before-ops-logs-390.png) · [改后](screenshots/20261117-console-visual/after-ops-logs-390.png)
- 运维总览 [390px 改前](screenshots/20261117-console-visual/before-ops-overview-390.png) · [改后](screenshots/20261117-console-visual/after-ops-overview-390.png)

- 隔离执行 [恢复预检](screenshots/20261117-console-visual/O07-restore-preflight.png) · [轮询中断仍保留任务](screenshots/20261117-console-visual/O08-polling-interrupted-390.png) · [恢复终态](screenshots/20261117-console-visual/O08-task-restore.png)

## 复验入口

按 CONSOLE-INTEGRATION.md 启动 npm run dev:admin 和 npm run dev:ops。执行写操作前核对隔离数据库、媒体、控制目录和端口，使用 CONSOLE-UX-ACCEPTANCE-20261117.md 的运行流程。网页分别打开 5174 的社区管理和 5175 的独立运维，使用对应的测试身份。
