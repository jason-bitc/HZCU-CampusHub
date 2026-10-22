# 第八阶段后端范围验收记录

G04，2026年10月22日。对象为 `feat/wc-coins-ledger`，提交 `1e436c287b5a91170facc6237139f1fc726fe769`。

## 验收结论

非现金奖励、本人币账本和文章投币通过本轮范围核验，可以进入举报、反馈与社区管理权限基础开发。本次核对当前源码、接口规则、实际测试证据、输入与构建校验及GitHub远端；没有重新启动应用或运行测试，不增加历史通过次数。

| 核对项 | 本次结果 | 证据位置 |
| --- | --- | --- |
| 本机与GitHub | 本机HEAD和本次origin工作分支实时查询均为1e436c2完整提交；server无未提交变更 | Git记录与2026-10-22远端查询 |
| 逐案证据 | 147个不同的套件/方法组合全部PASS，53 unit、94 mysql、21个套件；失败/错误/跳过为0 | `coins-2026-10-22/test-cases.csv`、`test-result.json`、套件摘要 |
| 测试输入对应 | 测试时记录的99个输入文件SHA与当前文件逐项相等 | `tested-inputs-sha256.csv`与本次本机校验 |
| 奖励与日期 | 默认登录2币、阅读180秒1币；任务/用户/业务日期唯一，上海时区；179/180秒、午夜和重复领取有实际用例 | CoinRules、CoinService、V8；原CoinsMySqlIT与验证报告 |
| 幂等与并发 | 成功键持久回执；相同键改内容冲突；文章→本人钱包锁序，锁内复查回执、余额和累计额度 | CoinService、CoinRepository；原12并发及交错案例 |
| 账目与审计 | 保存的5个测试用户余额均等于账本SUM，经济事件数均等于成功审计数；写入、事件、审计失败全回滚 | `batch8-reconciliation.json`及原故障案例 |
| 权限与私人记录 | 八类接口受认证限制；本人账本和最小回执不泄露隐藏正文、标题或他人信息；作者贡献不铸成消费余额 | AuthRequestFilter、CoinController、CoinRepository、COINS_API |
| 真实币数与排序 | 保存的16篇文章实际HTTP有效币数均等于事实SUM；币→赞→首发→ID；历史DEMO不混算 | ArticleRepository、`batch8-live-count-examples.json`、LIVE_COUNTS |
| 日志与模式 | 八类新动作各有Debug和Release成功trace，共16行；两模式8个成功及1个故障的合同相等 | `route-matrix.csv`、`batch8-mode-contracts.json` |
| 数据与迁移保护 | V1—V8八文件SHA相等；原版本、文章及表数量保护快照通过；两个启动脚本和JAR与实际运行记录匹配 | `migration-sha256.csv`、`batch8-historical-data-protection.json`、`batch6-script-modes.json` |

实际完整回归结束于2026-10-22 20:15:35 +08:00，使用campushub_test、受控邮件与隔离文件。构建commit=unknown是提交前工作树记录；本次以99个输入校验对应当前代码，不将其改称提交后新增实测。详细结果见[第八阶段实际验证](../server/verification/2026-10-22-coins.md)。

## 衔接与保留范围

用户本次选择先推进“举报、反馈与社区管理权限基础”。现有材料只有最高称号城院之星，完整称号与门槛仍须补齐；荣誉暂不猜测实现。第八阶段成功投币不自行撤回，治理失效和返还须后续独立设计。

本批核验不代替页面、真机、完整性能/压力/稳定性或课程整体验收，也不代填学生实际人时和交叉复核。历史迁移、原测试证据、正式报告、现有混合脏文档及本人资料继续保留。
