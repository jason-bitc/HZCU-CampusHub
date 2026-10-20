# 本批运行日志与业务审计

2026-10-20。审计是数据库内的成功变更记录，运行日志用于关联请求和定位失败；二者有不同故障边界。独立运维查询网页、治理日志、执行器、完整备份和全平台日志验收仍待后续实施。

## 配置与文件

| 环境变量/本机JSON键 | 默认值 | 说明 |
| --- | --- | --- |
| CAMPUSHUB_LOG_ENABLED | true | false只关闭本批文件输出，不关闭数据库业务审计或原控制台 |
| CAMPUSHUB_LOG_ROOT | .local-data/logs | 启动脚本固定server工作目录，默认server/.local-data/logs |
| CAMPUSHUB_LOG_MAX_BYTES | 10485760 | 每个运行文件10MiB；配置范围1024字节至100MiB |
| CAMPUSHUB_LOG_ARCHIVES | 7 | 保留7个轮转文件；允许1至30 |

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

V5仅新增author_contact_setting、business_audit及作者公开查询索引，V1—V4不改。成功事件动作限本人联系/资料修改和文章发布/下架/恢复/删除。业务事务提交前写审计，运行成功事件在提交后记录，失败或回滚没有成功事件。审计保存事件ID、UTC时间、traceId、内部actorId、动作、对象类型/ID、前后revision/状态、SUCCESS；不存在用户内容JSON字段。

HTTP请求traceId可在business_audit与文件请求日志间关联；框架直接调用且没有HTTP上下文时生成内部事件追踪号。账号邮箱不是公开作者标识，也不是审计字段。没有普通账号或角色绕过接口可以读取全部审计；管理及运维权限系统并未由本批创建。

## 验证边界

测试使用campushub_test、受控邮件和本次临时目录；验证真实文件写入、按大小轮转、重启追加、独立目录阻断、运行文件故障及恢复，并扫描密码、验证码、令牌、联系值、昵称和正文标记。数据库触发器使审计INSERT失败，验证资料、联系、发布与状态操作回滚。记录中应区分这些实际场景与尚未实施的运维查询、安全告警、平台性能/压力和设备验收。
