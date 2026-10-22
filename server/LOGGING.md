# 本批运行日志与业务审计

2026-10-22第九阶段：新增15类社区业务动作及ADMIN_OTHER有限兜底，原49类动作继续回归。actorDomain明确USER／COMMUNITY_ADMIN，caseId关联案件；数据库审计的actor_domain默认保留旧USER语义，管理变更显式使用COMMUNITY_ADMIN。管理上下文／媒体成功访问有CASE_ACCESS最小摘要。正文、答复、内部备注、理由、截图、邮箱、密码和令牌均不记入运行文件。默认Release与Debug规则保持，最新矩阵见[日志覆盖](LOGGING_COVERAGE.md)。

2026-10-22第八阶段新增COIN模块8类动作，当前共49类。钱包、账本、奖励状态、登录奖、阅读开始／领取、本人投币状态和投币均接入请求trace及安全字段白名单。阅读开始、奖励与支出的成功审计和业务写入同事务，运行成功变更在提交后记录。Debug有COMMIT／ROLLBACK及安全代码位置，Release合同相同且不增长调试／诊断。最新证据见[第八阶段验证](verification/2026-10-22-coins.md)。下文各批说明保留其历史范围。

第七阶段新增INTERACTION模块六类动作，当前覆盖41类操作；第六阶段模式及历史35类证明保留。新增动作与实际双模式trace见[覆盖表](LOGGING_COVERAGE.md)和[第七阶段记录](verification/2026-10-20-interactions.md)，计数语义见[LIVE_COUNTS.md](LIVE_COUNTS.md)。

2026-10-20。审计是数据库内的成功变更记录，运行日志用于关联请求和定位失败；二者有不同故障边界。独立运维查询网页、治理日志、执行器、完整备份和全平台日志验收仍待后续实施。

## 第六阶段统一运行模式

同一份Java业务代码和同一JAR，重启切换，默认Release。在server目录执行：

```powershell
./scripts/Start-Server.ps1 -Mode Debug -Port 8080 -Profile demo
./scripts/Start-Server.ps1 -Mode Release -Port 8080 -Profile demo
```

先停止本项目旧进程再运行另一模式。Debug只开启专用SafeDebug记录器的有限阶段事件和受控诊断，不对整包或第三方库开启DEBUG/TRACE。Release关闭这些新增细节，保留必要运行摘要、分类错误、事务提交后的业务变更及原数据库审计。日志模式与数据Profile、库名和认证权限独立；不是JVM远程调试，也不开放日志接口。

脚本优先级为显式-Mode → 被Git忽略的.env.local.json中的CAMPUSHUB_RUN_MODE → 原进程环境 → 默认release。显式参数在本机配置加载后应用，结束或失败均恢复修改前的环境；不把Debug遗留给下一次启动。未提供-Mode时保留本机配置。大小写不敏感，Java属性同时去除两端空白；其他值明确CONFIG_INVALID失败。

直接JAR通过同一个属性或等价环境变量，命令行属性按Spring配置规则优先：

```powershell
# 先在当前进程设置数据库、认证密钥、SMTP及专属存储配置；直接JAR不自动加载.env.local.json。
java -jar target/campushub-server-0.1.0.jar --campushub.run-mode=debug --spring.profiles.active=demo
java -jar target/campushub-server-0.1.0.jar --campushub.run-mode=release --spring.profiles.active=demo
# 等价变量：CAMPUSHUB_RUN_MODE=debug 或 release
```

旧CAMPUSHUB_LOG_DIAGNOSTICS / campushub.logs.diagnostics-enabled仅兼容读取，值不再控制诊断；旧值true时启动摘要legacyDiagnosticsIgnored=true。明确Release时也不会被它打开。只需设置一个模式，不要同时调多个开关。第五阶段历史报告保留当时布尔行为，不能据此推断本批有效模式。

Debug与CAMPUSHUB_LOG_ENABLED=false冲突会拒绝就绪；目录不可写、独占锁冲突或诊断目标不安全同样失败。Debug启动前检查诊断目标可写，不改旧内容；不得只凭参数认定成功，核对安全摘要的mode、fileLogs、diagnostics。Release允许关闭文件日志，此时仍有安全控制台摘要和数据库审计。启动失败只显示安全分类，不打印原异常或配置。

## 配置与文件

| 环境变量/本机JSON键 | 默认值 | 说明 |
| --- | --- | --- |
| CAMPUSHUB_RUN_MODE | release | debug/release；统一控制安全DEBUG与诊断 |
| CAMPUSHUB_LOG_ENABLED | true | false只关闭本批文件输出，不关闭数据库业务审计或原控制台 |
| CAMPUSHUB_LOG_ROOT | .local-data/logs | 启动脚本固定server工作目录，默认server/.local-data/logs |
| CAMPUSHUB_LOG_MAX_BYTES | 10485760 | 每个运行文件10MiB；配置范围1024字节至100MiB |
| CAMPUSHUB_LOG_ARCHIVES | 7 | 保留7个轮转文件；允许1至30 |
| CAMPUSHUB_LOG_DIAGNOSTICS | false | 旧兼容键，忽略其控制作用，见统一模式说明 |
| CAMPUSHUB_RUNTIME_ENV | local | 本机应用只接受local/test，启动记录标识，不自动选择数据库 |

目录须为本项目专属持久目录，文件及整个.local-data不入Git。运行文件campushub-runtime.log，轮转文件campushub-runtime.1.log至指定上限；下一条事件将超限时同步轮转，旧尾部按保留数量替换。同一目录用runtime.lock独占，多个进程请配置不同目录，不能同时共享写入。重启追加原日志，不在启动时清空。

仅本项目固定事件模板进入文件；每行UTF-8、UTC时间、服务器traceId和白名单字段。未知模板/外部依赖事件被丢弃，不把格式化用户文本或Throwable堆栈转存到文件。文件不等于完整Tomcat、操作系统或数据库日志。实现基于[Logback AppenderBase](https://logback.qos.ch/manual/appenders.html)的同步事件入口，固定字段和文件写入在本项目内实现。

```text
2030-01-01T00:00:00Z traceId=<服务器UUID> event=REQUEST action=AUTHOR_CONTACT method=GET status=200 result=OK durationMs=2
2030-01-01T00:00:00Z traceId=<服务器UUID> event=BUSINESS_CHANGE action=CONTACT_UPDATE objectType=CONTACT beforeRevision=0 afterRevision=1
```

动作是代码定义的类别，不是URL。401/403/404记DENIED、409记CONFLICT、其他错误记FAILED；业务无变化重试另记BUSINESS_NO_CHANGE。异常只保存类别和类名，SMTP失败仅记MAIL分类，不保存异常消息。控制台仍输出本项目脱敏请求、变更和错误事件，现有SMTP/数据库故障分类保留。不要开启第三方SQL参数或HTTP正文调试日志，也不要将秘密作为日志消息传入。

## 故障策略

启动时检查目录、既有目标文件、符号链接路径、独占锁及实际临时探针写入；无效或不可写则以脱敏LOG_STORAGE_UNAVAILABLE异常阻止该进程就绪。只清理由本次检查创建的探针，不清除已有文件。不要为通过验证清空目录或删除其他进程的锁。

运行中文件不可写时不假称日志成功：记录失败计数及健康标记，首个失败向标准错误输出静态RUNTIME_LOG_UNAVAILABLE与安全traceId，不输出真实路径、输入或异常消息。后续写入成功恢复文件健康标记，历史失败计数保留；内部状态供后续运维接入，本批没有公共查询接口。文件失败不回滚一个已经由数据库事务保证的业务审计，服务请求可继续成功；数据库审计失败则业务返回503并回滚。

轮转或进程崩溃期间不承诺文件系统事务性，运行日志也不替代数据库审计；同一操作崩溃后可能缺少运行日志，但不能用文件存在代替数据库提交事实。保留数按本项目专属固定文件名执行，配置调小前需考虑已有文件和历史保留，完整容量监控、灾难恢复、归档及备份后续设计。

## 成功审计

第四阶段V5仅新增author_contact_setting、business_audit及作者公开查询索引，V1—V4不改。当时成功事件动作限本人联系/资料修改和文章发布/下架/恢复/删除。第五阶段V6扩展CHECK保留旧值，新增COMMENT_CREATE、COMMENT_DELETE、COMMENT和VISIBLE；V1—V5原文件不改。业务事务提交前写审计，运行成功事件在提交后记录，失败或回滚没有成功事件。审计保存事件ID、UTC时间、traceId、内部actorId、动作、对象类型/ID、前后revision/状态、SUCCESS；不存在用户内容JSON字段。

HTTP请求traceId可在business_audit与文件请求日志间关联；框架直接调用且没有HTTP上下文时生成内部事件追踪号。账号邮箱不是公开作者标识，也不是审计字段。没有普通账号或角色绕过接口可以读取全部审计；管理及运维权限系统并未由本批创建。

## 验证边界

测试使用campushub_test、受控邮件和本次临时目录；验证真实文件写入、按大小轮转、重启追加、独立目录阻断、运行文件故障及恢复，并扫描密码、验证码、令牌、联系值、昵称和正文标记。数据库触发器使审计INSERT失败，验证资料、联系、发布与状态操作回滚。记录中应区分这些实际场景与尚未实施的运维查询、安全告警、平台性能/压力和设备验收。

## 第五阶段安全诊断

请求事件扩展module、固定code与有限stage；评论六类动作为COMMENT_LIST / COMMENT_REPLIES / COMMENT_CREATE / COMMENT_DELETE / COMMENT_IMAGE_UPLOAD / COMMENT_IMAGE_READ。阶段包含认证、授权、输入、目标、回执、图片附着、存储、持久化、审计与完成。内部articleId/commentId仅在数据库确认后记录；修订冲突可带经过类型与范围校验的expectedRevision/actualRevision。用户原始URL、任意消息、请求体不作为事件字段。

默认Release不创建诊断文件；第五阶段使用布尔键开启的入口已由第六阶段统一Mode取代。确需定位服务器5xx时以-Mode Debug重启。仅限维护人员可读的专属日志目录。诊断文件campushub-diagnostic.log及campushub-diagnostic.1.log等沿用相同大小/保留配置，独立轮转；两类日志各默认最多8×10MiB，合计约160MiB，另有锁文件。单条事件超限保留前部并加truncated=true。切回Release重启后不再增长旧诊断文件，不自动删除旧文件。

仅记录异常类名、最多4层原因、最多8个本项目代码位置（类/方法/文件基名/行号）；数据库补充校验过的SQLState和数值厂商码。**不记录异常消息、完整堆栈、SQL及参数、绝对配置路径、邮件异常文本、请求正文或秘密**。400/404/409不生成诊断堆栈。诊断文件写入失败与运行文件健康独立；下一条运行日志成功不能掩盖诊断失败。累积失败计数保留，静态标准错误标记含channel和安全traceId；恢复成功可修复健康状态，不影响数据库事务事实。

启动事件包含version、commit、environment、fileLogs、diagnostics。Spring Boot build-info提供应用版本（当前0.1.0）；提交默认unknown。构建时只有明确知道对应源码提交才传入 `-Dcampushub.build.commit=<40位小写提交号>`；缺失或非法值均unknown，不能拿父提交冒充未提交源码版本。未启用文件日志时控制台仍显示安全启动事件，有效diagnostics为false。

## 按traceId排查

1. 保存响应头X-Trace-Id（JSON同名字段traceId）。本机用 `Select-String -LiteralPath .local-data/logs/campushub-runtime.log -SimpleMatch '<实际traceId>'` 检索；轮转后按实际存档文件检索。不要粘贴正文、密码或令牌作为检索词。
2. 看action、module、status、code、stage及已有内部ID。成功变更可在有数据库维护权限的本机环境以**绑定参数**查询business_audit的trace_id，核对SUCCESS及前后修订；文件事件不代替数据库提交证据，没有面向普通用户的审计接口。
3. 5xx且需要代码位置时开启本地诊断、重现一次受控问题，按相同traceId查诊断文件，再关闭并重启。无诊断文件先检查有效开关和文件健康，勿直接开启SQL参数或HTTP正文日志。

| 示例 | 定位方式与边界 |
| --- | --- |
| 评论401/403 | COMMENT模块，AUTHENTICATION/AUTHORIZATION阶段和固定错误码；检查会话/账号认证，无需诊断堆栈 |
| 创作或联系修订409 | CONFLICT、REVISION_CONFLICT及已校验的期望/实际修订；重新读取本人当前版本再决定操作，不盲目重试覆盖 |
| 数据库审计503 | COMMENT_CREATE、SERVICE_UNAVAILABLE、AUDIT阶段；相同traceId的诊断可定位BusinessAudit.java行号，SQLState与vendor辅助分类，不包含原SQL文本 |
| 图片存储503 | STORAGE_UNAVAILABLE和STORAGE阶段；检查专属目录权限/挂载、文件是否保留，现有MediaStore仅抛安全分类，底层IO原消息不保留；不得为恢复删库或移除他人图片 |

本批实际数据库故障为隔离测试触发器的SQLState45000/vendor1644，并非真实生产事故；实例可定位源码位置，不能从该码推断用户内容。真实HTTP关联、开/关/恢复、六类动作落盘、嵌套消息脱敏及轮转/输出故障见[第五阶段验证](verification/2026-10-20-comments.md)。完整告警、远程日志与运维治理仍待后续。

## 旧模块的排查与事务语义

第六阶段把旧AUTH / OWN_ARTICLES等总类细分为验证码、注册、登录、重置、退出、资料读写、草稿各动作及发布状态等；完整35类路由和实际事件见[覆盖表](LOGGING_COVERAGE.md)。内部ID在数据库确认后加入，错误响应不因此公开新字段；MDC/ThreadLocal及临时异常引用在请求结束清理。事务后事件使用捕获的安全快照，记录后恢复接收线程原上下文。

Debug的DEBUG_STAGE表明经过的阶段，TRANSACTION与PERSIST不表示已成功。COMMIT / ROLLBACK / TRANSACTION_UNKNOWN由事务完成结果决定；邮件失败但FAILED验证码状态已提交时可以同时看到COMMIT和HTTP503，这是持久化结果与邮件结果的区别。BUSINESS_CHANGE仍只在业务事务提交后输出，数据库审计仍同事务；BUSINESS_NO_CHANGE仅表示幂等比较没有变更，不冒充新成功审计。

| 旧模块案例 | 按traceId操作 |
| --- | --- |
| 登录503 / 验证码邮件503 | 区分AUTH_LOGIN的PERSIST与AUTH_REGISTER_CODE / AUTH_RESET_CODE的MAIL_SEND。Debug诊断定位AuthRepository或AuthService，邮件只保留安全分类/代码位置；不记录收件地址、验证码或外部错误消息。需要网络/SMTP维护时另外在本机核查配置，不把密码贴入日志。 |
| 资料/草稿/联系409或503 | PROFILE_UPDATE / DRAFT_SAVE / CONTACT_UPDATE带已校验的期望/实际修订。409重新读取当前值；503看PERSIST还是AUDIT，Debug可定位WritingRepository、AuthorRepository或BusinessAudit。回滚时没有新成功审计，不能用旧页面值盲目覆盖。 |
| 公开读取503 | ARTICLE_LIST / ARTICLE_DETAIL / CATALOG，先区分输入失败与事务连接失败；数据库在进入@Transactional服务前就失败时可能保留VALIDATION阶段，诊断中的项目控制器位置和安全原因类型可指向连接入口。原测试只切断本应用连接，不停MySQL。 |
| 旧文章图片503 | IMAGE与STORAGE阶段，Debug诊断可定位MediaStore；检查专属对象是否完整以及目录可用性。图片响应仍按原公开/本人权限判断，Debug不会开放私有图。 |

模式切换、跨模块对照、脚本真实启动、线程复用、文件故障与本次完整回归证据见[第六阶段记录](verification/2026-10-20-logging-modes.md)。上述内容是有限排障能力；未来异步任务仍需显式传递安全快照，不能默认ThreadLocal会跨线程传播。

## 第七阶段点赞与收藏

LIKE_SET / LIKE_CANCEL / FAVORITE_SET / FAVORITE_CANCEL / INTERACTION_READ / FAVORITES_LIST均接入文件白名单，模块为INTERACTION。仅记录已确认内部actorId/articleId、安全阶段/错误码及关系修订；本人收藏的标题、摘要、来源、状态列表或查询内容不写日志。无请求体接口也拒绝把userId/role作为身份来源。

真实状态变更以LIKE/FAVORITE对象审计，objectId为文章ID，actorId与文章ID共同标识关系。before/after修订递增1，ENABLED/DISABLED表示关系有效/取消。不存在关系取消及重复目标状态只有BUSINESS_NO_CHANGE，不生成成功变更审计，不刷新收藏时间。请求修订上下文为服务端读到的基准与本事务确认值，调用方没有提交expectedRevision。

Audit或状态SQL故障503时，Debug可按traceId找到AUDIT/PERSIST及BusinessAudit/InteractionService/InteractionRepository的有限项目位置，Release保持相同错误合同并停止调试新增。日志中临时的新修订不表示提交，须结合ROLLBACK/COMMIT与数据库审计判断；只读请求不强制写数据库审计。
