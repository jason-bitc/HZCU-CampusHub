# 第七阶段后端范围验收记录

G04，2026年10月22日。对象为 `feat/wc-likes-favorites`，提交 `321f9a8c0885f07f899098d600710025bde6b60c`。

## 结论与核对方法

点赞、收藏与LIVE点赞统计通过本轮范围核验，可衔接非现金激励后端。核对内容为当前源码、接口合同、原实际运行证据、迁移和构建文件校验及GitHub远端；本次规划对话没有重新启动应用或执行测试，不新增测试通过次数。

| 检查项 | 核对结果 | 证据位置 |
| --- | --- | --- |
| 分支与远端 | 本机HEAD与本次 `git ls-remote` 查询的origin工作分支均为321f9a8完整提交 | Git本机记录、2026-10-22远端查询 |
| 逐案结果 | 125个不同的套件/方法组合全部PASS；49 unit、76 mysql，与摘要一致 | `server/verification/interactions-2026-10-20/test-cases.csv`、`test-result.json` |
| 明确状态与并发 | PUT/DELETE明确设置；文章行锁排序状态变化；相同状态不追加修订、收藏时间和成功审计 | InteractionService、InteractionRepository；原InteractionsMySqlIT并发/事务记录 |
| 权限与不可用收藏 | 先鉴权；本人收藏独立读取；不可用项仅articleId和available，保留本人取消能力 | AuthRequestFilter、INTERACTIONS_API；原权限/占位案例 |
| 真实计数与排序 | 源码由active点赞事实聚合；公开获币仍为0；旧DEMO/NONE不混算；最新排序独立 | ArticleRepository、LIVE_COUNTS；`batch7-live-count-examples.json` |
| 历史数据 | 原测试保存的前后演示计数快照matched=true | `batch7-historical-counter-snapshot.json` |
| 事务审计 | 变更与数据库审计同事务；运行成功消息在提交后记录；原故障用例覆盖回滚 | BusinessAudit、InteractionService；原审计/状态失败案例 |
| Debug/Release | 六类新动作均有两模式成功traceId；保存的HTTP合同对照一致 | `route-matrix.csv`、`batch7-mode-contracts.json` |
| 迁移与构建保护 | V1—V7七个文件SHA均匹配原记录；两个启动脚本和本机JAR均匹配实际执行记录；server当前无未提交变更 | `migration-sha256.csv`、`batch6-script-modes.json`、本机SHA与Git核对 |

原验证执行结束时间为2026-10-20 18:38:15 +08:00；测试库为campushub_test，使用受控邮件和隔离文件。完整记录见[第七阶段实际验证](../server/verification/2026-10-20-interactions.md)。本次没有将构建commit=unknown的历史测试标为提交后重新运行。

## 后续衔接

下一阶段可将effectiveCoins从当前固定0衔接为真实有效投币聚合，保留LIVE来源、真实点赞、过滤与确定排序。旧演示字段、历史迁移、已提交报告、用户数据及现有未提交文档继续保留。

本结论只覆盖本批后端。页面、真机、完整性能/压力、荣誉、社区治理与运维仍按各阶段开展；不代填两位学生的实际复核、人时或课程验收。
