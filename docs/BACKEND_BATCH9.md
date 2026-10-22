# 第九阶段后端实施 举报反馈与社区管理权限基础

2026-10-22，feat/wc-community-foundation，基线1e436c287b5a91170facc6237139f1fc726fe769。按[计划](BACKEND_BATCH9_PLAN.md)实施，承接[第八阶段核验](BACKEND_BATCH8_ACCEPTANCE.md)。

## 已实现

- 认证普通用户提交FEEDBACK／REPORT及私人证据图，文章／评论目标与公开版本由服务端固定；本人快照分页、最小目标标识、公开答复与状态历史。
- 独立社区管理账号及摘要会话，15分钟5次失败限制、8小时有效期、退出／停用／重设失效；整个/admin前缀先鉴权，普通与管理Bearer隔离。USER约束和既有AuthService.me保持。
- 管理案件列表、详情、认领／释放／处理及案件限定上下文和媒体。OPEN→PROCESSING，反馈ANSWERED／RECORDED和举报NO_VIOLATION／DUPLICATE可CLOSED，REQUIRES_GOVERNANCE只到NEEDS_ACTION。负责人、修订及幂等回执防止覆盖，内部备注与公开答复分开。
- V9新增独立管理、案件、历史、成功回执及证据／上下文关系。文章→用户和案件→管理账号锁序，历史序列在事务后段串行提交。列表按历史上界固定成员和状态；必要写入、绑定、历史、回执与成功审计同事务，文件失败不假成功。
- 原文下架／软删除后管理仍可查看案件保存的曾公开版本、可见评论快照及原引用图；不读取未公开草稿或任意媒体。私人证据无固定业务张数上限，复用原图片安全和请求技术边界。
- 审计actor_domain区分USER／COMMUNITY_ADMIN，旧记录默认USER；文件请求／提交后变更／访问摘要带主体域。新增15类业务动作及ADMIN_OTHER有限兜底，Release／Debug模式保持。

## 实测与对接

最终完整回归于2026-10-22T22:27:34.0702657+08:00结束：61 unit＋113真实MySQL＝174项，失败／错误／跳过0，原147项重跑，新增8单元及19MySQL。实际逐案、B9-01—12、30行双模式trace及原数据保护见[本批验证](../server/verification/2026-10-22-community.md)。

交接入口为[API](../server/COMMUNITY_API.md)、[权限与状态机](../server/COMMUNITY_RULES.md)、[本机初始化](../server/COMMUNITY_ADMIN_SETUP.md)、[Postman](../server/postman/CampusHub.postman_collection.json)和[日志覆盖](../server/LOGGING_COVERAGE.md)。Postman保留原70请求，追加17个手工步骤覆盖15类动作及两种快照后续页，共87请求，私人变量为空。

王晨协调共享后端与接口交接，刘景浩保留正式WBS中社区模块及管理网页主责，WorkBuddy后续接页面。自动实现与测试不改变学生负责人、人时、预算、贡献和整个5.9完成状态。真实演示管理账号由用户通过隐藏输入工具初始化，当前没有默认口令或自动学生提权。

本批不执行内容隐藏／封禁、账号申诉、人工认证、分类维护、通知、币贡献失效／返还、荣誉、运维或网页。NEEDS_ACTION为后续真实治理交接；闭案不代表已删除、封禁或退币。性能压力、真机与整体评审按正式计划开展。仅明确审阅文件纳入本批提交，原混合脏文档及独立deliverables保持。
