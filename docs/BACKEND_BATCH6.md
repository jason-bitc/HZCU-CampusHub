# 第六阶段实施记录：统一日志运行模式

2026-10-20，基于33f21f45217cbe5de33502eaf953c31f2a13f019，分支feat/wc-logging-modes。依据[本批计划](BACKEND_BATCH6_PLAN.md)和[需求追踪](BACKEND_BATCH6_TRACE.csv)，只完善现有模块日志与调试，不增加社区功能、页面或数据库迁移。

## 实现与行为

同一JAR的campushub.run-mode / CAMPUSHUB_RUN_MODE接受debug/release，默认release；脚本增加-Mode，保留Port与数据Profile。显式Mode在本机配置加载后生效，正常返回或异常均恢复原环境。旧诊断布尔键仅兼容读取，不再决定诊断；Release不能被旧true打开，启动摘要标识legacyDiagnosticsIgnored。

Debug只启用专用SafeDebug记录器的固定阶段事件和受控诊断文件。Release不再新增DEBUG_STAGE或诊断细节，必要运行记录和既有数据库审计保留。Debug与文件日志关闭冲突明确失败；运行/诊断目标、权限与独占保护在就绪前检查。切回Release保留历史文件，无日志下载或权限绕过入口。

现有35类接口动作覆盖账号、读取、资料、草稿图片发布状态、作者联系、评论与健康。RequestTrace保留有限阶段、安全错误码、已确认的内部actor/author/article/comment ID与修订；没有记录邮箱、令牌、联系值、正文或原始关键词。覆盖明细及实际成功/代表性失败证据见[LOGGING_COVERAGE.md](../server/LOGGING_COVERAGE.md)。

写事务另有DEBUG事务结果COMMIT / ROLLBACK / TRANSACTION_UNKNOWN；这些表示事务结果，不能把“验证码发送失败状态成功持久化”的COMMIT当作邮件发送成功。业务成功审计仍在原事务内，业务变更运行记录仍在提交后。提交回调捕获安全追踪快照，并恢复接收线程原有上下文；请求finally清理MDC/ThreadLocal及临时异常引用。

旧模块5xx沿用有限原因链和项目代码位置，邮件及图片分类异常可临时保留原原因供安全提取，结束即清理，不输出原消息/SQL或完整堆栈。资料、草稿、联系、登录会话、邮件、读取连接与图片存储等代表性故障在两种模式对照；详细结果只以[实际验证](../server/verification/2026-10-20-logging-modes.md)为准。

## 使用与边界

现行命令、配置优先级、文件容量与三个旧模块排查例见[LOGGING.md](../server/LOGGING.md)和[README](../server/README.md)。保留V1—V6、原各批证据及既有未提交报告/设计/工具；本机配置、真实运行目录、依赖、用户图及target不纳入提交。

本机Release模式是当前应用正常运行的日志配置，不代表已完成生产部署、安全评估、平台监控告警、治理网页或性能/压力验收。文件日志故障可导致记录缺失，不能代替数据库事务审计；仍没有完整备份/灾难恢复方案。王晨负责本批组织与后端交接，Codex辅助实施和自动验证；学生交叉复核、教师评审及实际人时/贡献待真实开展。
