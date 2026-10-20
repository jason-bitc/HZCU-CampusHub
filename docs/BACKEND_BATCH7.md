# 第七阶段后端实施：点赞、收藏与LIVE计数

2026-10-20，基于c6ead4abd755f89809fea778f4837fdba50db493，分支feat/wc-likes-favorites。范围来自[计划](BACKEND_BATCH7_PLAN.md)与[追踪](BACKEND_BATCH7_TRACE.csv)，承接[第六阶段验收](BACKEND_BATCH6_ACCEPTANCE.md)。验收文档核对旧证据，没有被算成本批新增测试运行。

## 已实现与选择

- 六个受限接口：PUT/DELETE点赞、PUT/DELETE收藏、本人互动状态与收藏分页；所有请求无正文，身份沿用有效普通校园认证，失败no-store。明确设置而非toggle；每用户每文唯一状态，同状态不重复改修订/时间/成功审计。
- 收藏按最近激活时间及文章ID排序；公开项批量读取当前摘要，不可用项严格只有articleId与available:false。保留关系及本人取消能力；不可用文章点赞及取消点赞仍拒绝。
- V7新增article_like、article_favorite及必要约束/索引，扩展原审计动作/对象CHECK保留旧值，不修改V1—V6或旧数据。对象由actorId与文章ID共同定位，状态变化同审计事务；文章行锁和撤回/其他本文章写入排序。
- 当前公开列表、详情、作者文章及可用收藏摘要统一counts.origin=LIVE，点赞从active事实行聚合；effectiveCoins固定0，尚无投币/奖励。保留article原DEMO/NONE计数与内容sourceType，旧数字不参与当前数量或认可排序。最新排序与筛选合同保留。
- 复用第六阶段Debug/Release、事务回调及安全诊断，新增六类动作实际落盘，当前共41类。没有保存收藏内容快照、用户载荷或秘密到日志。

选择读取层聚合，避免冗余缓存与旧演示数混算。列表的单次GROUP BY聚合、收藏摘要及分类批量读取均无逐篇查点赞；没有借本批宣称压力/性能已达标。COUNT口径保留已提交且未取消的历史点赞，不因会话或认证后续失效隐式重写；治理失效策略另行定义。

## 合同及验证

[互动API](../server/INTERACTIONS_API.md)、[计数衔接](../server/LIVE_COUNTS.md)、[公开读取](../server/API.md)、[Postman](../server/postman/CampusHub.postman_collection.json)、[日志覆盖](../server/LOGGING_COVERAGE.md)及[实际验证](../server/verification/2026-10-20-interactions.md)共同交接。原55个Postman请求保留，追加6个只供手工操作的步骤。

本次完整回归49单元与76真实MySQL案例，共125项、零失败/错误/跳过；其中新增13个互动案例，原六批继续执行。受影响的旧读取计数/排序期望因明确LIVE合同而更新，并保留权限、过滤、分页及旧存储保护。未重写任何旧阶段实际报告。

王晨负责本批后端组织及接口交接，Codex辅助实现与自动验证；刘景浩复核、教师评审、人时及贡献待真实开展。仅campushub_test、随机身份、受控发送器及隔离目录；不发真实验证码、不修改本人开发资料、不清库。

本批未实施页面/真机、投币、奖励钱包、荣誉、通知、点赞者名单、治理或运维。当前后端通过不等于完整安全评估、平台性能/压力或整体项目验收。原报告/设计/工具及混合脏README/REQUIREMENTS保留，不混入代码提交；秘密、用户图、运行目录、依赖与target继续排除。正常同步工作分支，远端结果以实际核对为准。
