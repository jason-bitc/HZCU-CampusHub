# 第六阶段后端范围验收记录

G04，2026年10月20日。对象为 `feat/wc-logging-modes` 的提交 `c6ead4abd755f89809fea778f4837fdba50db493`。

## 结论与方法

日志专项通过本轮范围核验，可进入下一阶段。已核对实现、使用说明、逐案测试索引、两模式对照、真实脚本/JAR证据、文件校验和GitHub远端。本次规划对话未重新启动应用或执行测试；结果来自本批已保存的实际执行记录，不是新增实测或两位学生已经完成交叉复核的证明。

| 检查项 | 本次核对结果 | 证据 |
| --- | --- | --- |
| B6-01/02：模式与优先级 | RunMode默认release，接受规范化debug/release，非法值拒绝；旧诊断布尔不决定有效模式。显式脚本Mode在配置加载后覆盖，finally恢复环境 | RunMode.java、Start-Server.ps1、Use-LocalConfig.ps1及脚本检查JSON |
| B6-03：真实启动 | JSON中3次真实JAR/脚本启动健康通过，4次受控返回边界与3次真实JAR拒绝明确区分；Port和demo数据Profile保留 | `batch6-script-modes.json`共10条检查，不计作10个额外JUnit方法 |
| B6-04：Debug/Release一致 | 8类受控失败的HTTP/业务码逐项一致；Debug诊断和Release历史保留/停止新增有实际案例；LocalDiagnostics只按RunMode启用 | `batch6-mode-contracts.json`、LocalDiagnostics.java及回归案例 |
| B6-05：旧模块覆盖 | route-matrix.csv有35种不同动作，每条都有成功traceId；覆盖账号、读取、创作、作者联系、评论与健康 | `server/LOGGING_COVERAGE.md`、route-matrix.csv及实际日志样本 |
| B6-06/07：事务、上下文与隐私 | 已有事务回滚/提交、并发关联、线程复用及嵌套异常脱敏案例；未通过普通用户接口开放日志 | 本批实际报告与对应单元/MySQL案例 |
| B6-08：文件行为 | 旧文件轮转、追加、独占、故障与恢复用例继续执行；两模式专门核验关闭后不删除或增长旧诊断内容 | 文件与诊断测试、日志样本及实际报告 |
| B6-09：完整回归 | 逐行核对112条全部PASS：49条unit、63条mysql；与test-result.json一致，无失败/错误/跳过 | test-cases.csv、test-result.json及套件摘要 |
| 迁移与构建一致 | 当前V1—V6的6个摘要全部匹配；相对33f21f4未改迁移；启动脚本、配置加载脚本及本机JAR的SHA与实测副本一致 | migration-sha256.csv、脚本JSON的byteIdenticalHashes |
| GitHub同步 | 实时只读查询origin工作分支，远端与本机HEAD均为c6ead4abd755f89809fea778f4837fdba50db493 | refs/heads/feat/wc-logging-modes |
| B6-10：使用说明 | 两条Mode命令及版本、目录、优先级、traceId检索和旧模块排查例已有文档与真实启动证据 | `server/LOGGING.md`、`server/README.md` |

证据目录为 `server/verification/logging-modes-2026-10-20/`，实际报告为 [2026-10-20-logging-modes.md](../server/verification/2026-10-20-logging-modes.md)。源码类位置以报告及覆盖表链接为准。

## 可用方式与边界

在server目录启动，同一JAR切换模式须重启：

```powershell
./scripts/Start-Server.ps1 -Mode Debug
./scripts/Start-Server.ps1 -Mode Release
```

默认Release，正常运行日志和业务审计保留。Debug启用本项目受控阶段事件和诊断；这不是JVM远程调试开关，也不改变数据Profile、权限或业务规则。

本次没有发现阻止进入下一阶段的范围内证据缺口。生产部署、完整日志平台/告警、运维网页、压力/性能验收、崩溃时文件可靠性及所有并发交错仍不是本批完成结论。构建提交为unknown是已有实测事实，不用父提交冒充未提交测试工作树版本。学生复核、教师评审、实际人时和贡献分不在此代填。

下一阶段计划见 [点赞与收藏](BACKEND_BATCH7_PLAN.md)。
