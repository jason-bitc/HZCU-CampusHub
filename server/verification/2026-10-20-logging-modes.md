# 第六阶段实际验证：Debug/Release与全模块日志

2026-10-20，分支feat/wc-logging-modes，源码基线33f21f45217cbe5de33502eaf953c31f2a13f019。本文记录实际执行，学生复核、教师评审、人时及贡献未代填。

## 完整回归结果

Java21、Spring Boot4.1.1、真实MySQL campushub_test，执行mysql-it profile的verify，**2026-10-20T15:59:33.6390271+08:00**结束，BUILD SUCCESS，耗时2分43秒。

| 类型 | 数量 | 失败/错误/跳过 |
| --- | ---: | --- |
| 单元 | 49 | 0 / 0 / 0 |
| 真实MySQL / HTTP | 63 | 0 / 0 / 0 |
| 合计 | 112 | 0 / 0 / 0 |

原五批56项MySQL全部继续执行：文章11、账号8、作者联系11、图文创作12、评论14。新增LoggingModesMySqlIT7项；单元原43项继续，加RunModeTest3项及RequestTraceTest3项。旧诊断测试改为显式统一模式，保留原off/on/off及有限原因/轮转/故障断言，没有跳过既有用例。构建version=0.1.0、commit=unknown；本次测试在未提交工作树执行，不拿父提交冒充本次构建。

逐案与摘要：[test-cases.csv](logging-modes-2026-10-20/test-cases.csv)、[test-result.json](logging-modes-2026-10-20/test-result.json)。同目录保存各套件摘要及仅受控项目事件的system-output，未导出XML系统属性或本机秘密。35条动作、一次方法内的10个脚本检查和日志行数均**不额外计算为JUnit测试次数**。

## B6-01至B6-10

| 编号 | 实际证明 |
| --- | --- |
| B6-01 | RunModeTest大小写、两端空白、非法值；真实JAR默认Release、显式Debug/Release启动摘要；非法直接JAR非零退出CONFIG_INVALID且未就绪 |
| B6-02 | 显式Debug覆盖隔离本机release，Release覆盖本机debug及旧diagnostics=true；真实进程停止后及受控成功/失败返回均检查模式/诊断/数据库环境恢复 |
| B6-03 | 同一个实际JAR、逐字节相同脚本的三次真实启动健康200；保留随机Port与-Profile demo；Debug+文件关闭、诊断目标被目录占用均拒绝就绪 |
| B6-04 | 八类同等受控5xx在两模式逐一比对HTTP/业务码一致；Debug可关联安全源码位置，Release旧诊断及DEBUG_STAGE数量不增长；身份及其他权限合同执行原回归 |
| B6-05 | 现有35类动作均有真实HTTP成功和文件事件；各模块代表性失败及实际阶段、traceId见覆盖表/CSV；不是只在控制台增加名称 |
| B6-06 | 实际MySQL审计失败回滚、资料/草稿修订不变；并发相同评论仅一条成功审计；通过实际回执关联提交事件；单线程复用异常清理及延后快照恢复单元；两条HTTP工作线程处理24个并发读取，ID不串请求 |
| B6-07 | 运行/诊断样本扫描随机密码、邮箱、验证码、令牌、正文、联系值、关键词、SQL故障消息；嵌套异常有限链单元继续执行；真实JAR输出另扫描测试连接秘密/认证密钥/受控邮件配置 |
| B6-08 | 原SafeFileLogsTest实际轮转/追加/独占/运行文件故障，LocalDiagnosticsTest诊断轮转与独立故障；本批HTTP运行/诊断分别被阻挡、标记失败并恢复；Release保留旧诊断字节 |
| B6-09 | 上述112项完整回归，Flyway V1—V6验证、无新增或修改迁移；各测试只清本次对象，原文章、NONE/DEMO计数及审计/评论/图片数量基线一致；原脏文件与提交边界另核对 |
| B6-10 | 两种Mode命令通过真实脚本/JAR执行，当前LOGGING.md及覆盖表提供路径、优先级、检索、版本和账号/创作联系/读取图片排查例；默认Release与旧布尔规则一致 |

## 专项7个方法

1. everyExistingRouteHasAnActualSafeFileEvent：真实注册验证码、注册登录及重置退出、本人身份/资料、创建列表草稿保存图片发布/撤回/恢复/删除、作者入口/主页/文章/联系、评论六类、分类文章健康，总35类操作。
2. expectedFailuresHaveCodesPhasesAndValidatedContextOnly：401/400/404/409/429，修订期望/实际、认证优先和未知目标不记已存在ID。
3. oldModuleFailuresHaveSameBusinessResultsInDebugAndRelease：八类邮件、会话SQL、读取连接、资料审计、草稿SQL、联系SQL、图片损坏、评论审计的5xx对照；解除故障不改业务状态。
4. committedAuditAndConcurrentRequestsKeepTheirOwnTrace：相同评论并发幂等、审计/事务提交与回执trace精确对应，以及24请求的跨路由上下文隔离。
5. separateFileFaultsRemainObservableAndRecoverWithoutBusinessChanges：本次私有运行文件/诊断文件分别被目录阻挡；健康和累积失败可观测，恢复后写入正常。
6. actualJarScriptsModePriorityAndEnvironmentRestoration：下述10个细项。
7. releaseRetainsHistoryAndExportsOnlyScannedEvidence：切回Release保留诊断历史、停止新增细节，保留正常读取及INFO，扫描后导出。

HTTP二进制可能先于外层过滤器最终落盘返回，本批采集器短时等待对应完成事件；没有把尚未来得及写出的行误当不存在，也没有构造日志代替请求。

## 真实脚本与JAR边界

为避免修改用户本机配置，将实际Start-Server.ps1、Use-LocalConfig.ps1与打包JAR复制到JUnit隔离目录，逐字节核对并保存SHA256；只在该目录写campushub_test配置。没有复制真实用户图、运行日志或SMTP授权文件。

[script-modes.json](logging-modes-2026-10-20/batch6-script-modes.json)含10个检查：三次真实JAR脚本启动（Debug / Release / 无Mode默认Release），四次脚本返回边界（两模式×受控成功/失败原生入口），三次真实JAR拒绝（非法模式 / Debug关闭文件 / 诊断目标阻挡）。三次真实服务均健康200，读取原演示文章200；只停止本次Java子进程，脚本处理非零退出并恢复环境。成功返回分支用受控原生入口验证，与真实服务启动证据分别标明，不将其冒充JAR正常退出。

实际端口随机选择以避开已有服务；数据Profile为demo，未在已有演示库切换schema-only。两模式同一JAR，测试只读该真实进程的健康及原文章，不请求邮件或改变真实账号。配置值及私有进程输出不导出，JSON只含安全结果/开关/哈希。

## 可检索的跨模块诊断

[运行样本](logging-modes-2026-10-20/batch6-runtime-sample.txt)与[诊断样本](logging-modes-2026-10-20/batch6-diagnostic-sample.txt)可用以下traceId核对，位置为本次有限项目帧，不是完整堆栈：

| 动作 | 实际traceId | 安全定位 |
| --- | --- | --- |
| AUTH_REGISTER_CODE | e1082600-f34c-4bad-9229-55afa166528d | MAIL_UNAVAILABLE / MAIL_SEND / AuthService |
| AUTH_LOGIN | 0fbcb260-b70d-4caa-8d9e-03d688298905 | SERVICE_UNAVAILABLE / PERSIST / AuthRepository |
| ARTICLE_LIST | 1bd16b07-87cb-42af-a357-e2d0e951f52e | SERVICE_UNAVAILABLE / 进入事务前连接失败 / ArticleController |
| PROFILE_UPDATE | 186aac87-0023-4251-b851-c9ae5601ad89 | SERVICE_UNAVAILABLE / AUDIT / BusinessAudit |
| DRAFT_SAVE | 120f6f09-6088-4e70-ab30-adfc60db240e | SERVICE_UNAVAILABLE / PERSIST / WritingRepository |
| CONTACT_UPDATE | 500b2f59-f31c-42b2-a75e-6fb0dd4ca90c | SERVICE_UNAVAILABLE / PERSIST / AuthorRepository |
| IMAGE | ae2b03fd-385e-4cfd-9ea3-e2cc07930e99 | STORAGE_UNAVAILABLE / STORAGE / MediaStore |
| COMMENT_CREATE | dc0b520a-7804-4b90-89ed-4aae7e3d0575 | SERVICE_UNAVAILABLE / AUDIT / CommentService |

SQL触发器限定本次user/article，SQLState45000/vendor1644；读取故障只切断本应用TCP转发连接，不停止MySQL；媒体只损坏并恢复本次图片。原始SQL/消息、外部邮件原文和配置路径不写诊断。两模式对照码见[mode-contracts.json](logging-modes-2026-10-20/batch6-mode-contracts.json)。完整矩阵见[LOGGING_COVERAGE.md](../LOGGING_COVERAGE.md)。

## 数据保护、修复记录与限制

只使用campushub_test、受控邮件和随机身份/独立目录，不发真实验证码或修改本人开发资料。受控触发器finally移除；只按本次user/article归属清理，保留原基线。序列和自增正常推进，不清库。原V1—V6无Git内容差异，摘要见[migration-sha256.csv](logging-modes-2026-10-20/migration-sha256.csv)，原批次证据不覆盖。

最初专项运行修正了采集器对二进制响应完成时机的假设与PowerShell测试探针变量作用域；之后七个场景通过，但清理因密码重置challenge外键顺序失败。已先删除本次challenge/gate再删用户，单独核对并移除该失败运行遗留的测试账号158：测试邮箱格式/固定测试时间匹配、其余业务依赖为零后，仅清本次剩余行。最终以上完整运行清理成功。失败运行没有当作通过，未关闭验证或修改旧迁移来掩盖问题。

文件系统崩溃、提交结果未知、所有生产并发交错、平台性能/压力、远程日志/告警、运维网页和生产部署未在本批完成。TRANSACTION_UNKNOWN为明确的设计分支，本批没有伪造真实数据库未知提交结果。跨线程快照机制有单元证明，不意味着已实现异步业务任务。Release是日志模式，不是整体安全或上线验收。
