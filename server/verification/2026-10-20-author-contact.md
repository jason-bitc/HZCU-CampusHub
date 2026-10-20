# 第四阶段作者联系与业务审计实际验证

2026-10-20，Windows本机，Java21、Spring Boot4.1.1、MySQL8.0.45。分支feat/wc-author-contact，从第三阶段e04eb74185a4977fdd18111c9ae6fe43fae1e5dd继续。测试仅使用campushub_test及隔离临时文件，不修改本人真实资料、联系方式、密码或开发库，也不发送真实验证码。

## 完整执行结果

最终`mvn -Pmysql-it verify`于14:32:33 +08:00完成BUILD SUCCESS，用时约1分14秒。临时Maven设置与server/.m2-cache已缓存依赖用于离线解析，没有修改全局设置。单元与真实MySQL分开统计：

| 套件 | 数量 | 失败/错误/跳过 |
| --- | ---: | --- |
| 原单元29＋ContactInput4＋SafeFileLogs3 | 36 | 0/0/0 |
| 原文章读取MySQL | 11 | 0/0/0 |
| 原账号认证MySQL | 8 | 0/0/0 |
| 原图文创作MySQL | 12 | 0/0/0 |
| 新作者联系MySQL | 11 | 0/0/0 |
| 合计 | 78 | 0/0/0 |

案例与耗时见[test-cases.csv](author-contact-2026-10-20/test-cases.csv)，计数与时间见[test-result.json](author-contact-2026-10-20/test-result.json)。[本批证据目录](author-contact-2026-10-20)保存套件摘要与脱敏请求日志，原XML含机器配置不整体导出。原三批验证记录和原始证据未覆写。

## 新增场景与B4对应

| 实际场景 | 验收映射与结果 |
| --- | --- |
| restrictedRoutesCheckReaderBeforeTargetAndNeverCacheFailures | B4-01/07：新增接口无身份及无效会话401，认证后非法ID400；旧无真实作者演示404，原游客全文200；失败no-store及服务器trace一致 |
| realAuthorEntryWhitelistedHomeAndOnlyOwnCurrentPublicArticles | B4-02/04：三个随机普通账号；真实公开文章取得作者入口，资料缺省/专业null，主页白名单7字段；作者分页只有本人当前公开版本，无草稿/隐藏/下架/删除或他人内容；游客不增加身份字段 |
| contactDefaultsOffCanEnableThenImmediatelyCloseAndClear | B4-05：默认关闭、GET不创建设置；开启可见，关闭保留值仅本人可读，关闭后连续新请求仅enabled:false；可明确清空；未设联系仍可发文 |
| contactRevisionIsIndependentAndConcurrentOldCopiesCannotOverwrite | B4-06：两路旧联系修订并发只有200/409；同时完整保存基础资料200；后续资料修改不清空联系，读者只能改自己设置，身份邮箱不变 |
| strictContactInputPaginationAndNoOtherUsersWriteEndpoint | B4-06：危险/相对/含用户凭据的网页地址、控制字符、超限/未知类型、伪造作者字段拒绝；重复/未知分页参数400；他人写入口不存在；合法WEB保存与读取 |
| readerAndTargetCertificationAccountStateAndLogoutRechecked | B4-03/07：目标受限或认证撤销，入口/主页/文章/联系404，原公开全文仍200；访问者受限403先于目标查询；退出后401，重新登录恢复；普通账号无审计/运维读取入口 |
| LastPublicArticleControlsHomeAndContactWithoutHidingOwnSettings | B4-03：最后一篇公开文章下架后主页/联系/文章入口404，本人原资料和联系设置仍可管理；恢复原公开版本后重新可见 |
| successfulMutationsHaveTraceAuditAndStateRetriesOnlyRecordOneChange | B4-08：联系/资料及发布/下架/恢复/删除共6种事件可从HTTP traceId定位，内部actor/object/revision及SUCCESS匹配；发布回执重试和同终态操作不新增成功变更，旧联系修订409 |
| auditInsertFailureRollsBackContactProfilePublicationAndEveryStateChange | B4-08：真实MySQL触发器拒绝审计INSERT，联系、资料、发布/下架/恢复/删除均503；设置、公开版本、指针、发布回执及修订回滚；去除本次触发器后原标识可重试成功 |
| runtimeFilesRejectPayloadLeaksAndFileFaultDoesNotForgeAuditFailure | B4-09：实际日志及审计扫描测试密码/令牌/验证码/邮箱/联系方式/昵称/正文标记、路径/参数探针；运行文件被本次目录占位后可观测失败，DB审计仍一致提交；恢复原文件后正常追加，旧内容保留 |
| restartRetainsProfilesContactAuditsAndExpiredSessionsRemainRejected | B4-10：重启后原会话、资料、联系设置、全部审计保留，文件追加；时钟推进至到期后新受限请求401，原公开全文仍可游客读取；迁移1—5成功 |

SafeFileLogs单元另验证真实按大小轮转、最多指定存档数、重启追加、同目录第二写入者被拒绝、目录占位的配置异常不带路径/原异常、运行文件故障恢复及未知日志载荷丢弃。ContactInput单元验证200码点Unicode及2000码点WEB边界、关闭留值或清空、严格字段和普通USER守卫。

V3数据库当前只允许USER角色，未新建社区管理员或运维账号。其他角色被拒绝是在守卫单元中使用合成Identity验证；HTTP/MySQL实际验证的是普通账号不能通过新接口获得额外权限，不能据此宣称社区/运维角色系统已实现。

## 真实打包与文件证据

14:30:28 +08:00运行`./scripts/Verify-LogStorage.ps1`，在campushub_test和本次target隔离目录启动真实JAR。日志目录位置预置本次测试文件，启动明确报告LOG_STORAGE_UNAVAILABLE、退出码1且健康端口未就绪；该测试文件内容保持，脚本不停止其他应用或MySQL。结果见[batch4-log-storage-result.json](author-contact-2026-10-20/batch4-log-storage-result.json)。

应用内测试的实际文件输出见[batch4-runtime-sample.txt](author-contact-2026-10-20/batch4-runtime-sample.txt)，真实业务审计行见[batch4-audit-sample.json](author-contact-2026-10-20/batch4-audit-sample.json)。二者在测试清理前经过测试秘密值扫描，只含固定元数据，actor/object均为本次测试内部ID，审计时间使用受控UTC Clock以验证会话到期；文件事件时间为实际UTC运行时间。审计样本不包含原联系值、昵称、正文或邮箱。

默认文件日志启用、专属目录、10MiB文件、7个存档，配置见[LOGGING.md](../LOGGING.md)。文件运行故障与数据库审计不同：文件失败有计数、健康标记和静态错误提示，业务仍可带DB审计成功；数据库审计失败必须业务回滚。没有把文件输出故障称为业务审计丢失，也没有把DB成功称为文件已落盘。

## 修复与执行边界

初次普通沙箱单元运行在Windows临时目录遇到文件写权限，未将该次当作通过；切换到用户授权的隔离文件/本机数据库验证权限执行。新增测试初期修正了WEB地址边界长度和不可重复设置Logback事件消息的夹具；首次完整MySQL运行有2例因随机昵称超出既有40码点规则而400，改用合法长度后针对11例重跑通过。

打包检查发现日志目录失败只显示原通用STARTUP_FAILED；补充专用异常类型和统一启动分类后真实JAR复测通过。最后增加脱敏样本导出与扫描，并完成上述78项最终完整回归。没有通过关闭校验、跳过测试、修改历史迁移或模拟HTTP结果来满足验收。

测试用受控发送器及三个随机新账号，不向真实校园邮箱发信；受控触发器在finally移除，JUnit只清理本次临时目录。删除本次测试身份、设置、文章/版本及审计后，原文章基线、审计数量及联系设置数量与开始前一致。原V1—V4无Git内容差异，本轮摘要见[migration-sha256.csv](author-contact-2026-10-20/migration-sha256.csv)。

提交前只纳入本批源码、V5、测试、接口/Postman、日志说明、计划/追踪及本批记录；本机秘密、实际用户图片、运行目录、依赖与构建产物不入Git，开始前既有脏报告/设计/工具文件按摘要检查保留。原Postman43个请求保持，新增6个受限接口步骤，联系值/凭据均为空或示例。

作者页面、小程序复制/跳转、安全HTML渲染、真实设备、社区治理、运维查询/执行器、告警、完整日志系统和平台性能/压力验收均未在本批完成。数据库审计不是防数据库管理员篡改的独立不可变存储；文件轮转也不承诺崩溃事务性。学生交叉复核、教师评审、人时及评分待实际开展。
