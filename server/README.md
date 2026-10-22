# 城院帮首批后端

2026-10-22第八阶段：每日登录2币、服务端阅读计时180秒领取1币、全账号每天合计最多3币；每人每篇累计最多投2币，不自行撤回。本人钱包、稳定分页账本、任务状态和成功幂等回执形成闭环，作者贡献独立于可消费余额。公开LIVE获币改为有效投币事实SUM。对接见[币与奖励API](COINS_API.md)、[规则与事务](COIN_RULES.md)，验证见[第八阶段记录](verification/2026-10-22-coins.md)。以下按日期保留各批历史范围。

2026-10-20第七阶段：新增点赞/取消、收藏/取消、本人互动状态及收藏分页，公开计数改为LIVE真实聚合，旧DEMO/NONE原值不改。合同见[互动API](INTERACTIONS_API.md)、[计数衔接](LIVE_COUNTS.md)和[本批验证](verification/2026-10-20-interactions.md)。V7仅新增关系及审计兼容扩展；投币奖励、页面与真机未由本批完成。

2026-10-20第六阶段：同一JAR以 `./scripts/Start-Server.ps1 -Mode Debug` 或 `-Mode Release` 切换日志，默认Release。原Port与数据Profile参数继续使用；模式不选择数据库。各旧模块的动作、阶段与安全代码位置诊断已扩展，见[日志说明](LOGGING.md)、[覆盖表](LOGGING_COVERAGE.md)及[第六阶段验证](verification/2026-10-20-logging-modes.md)。第五阶段旧诊断布尔键仅兼容读取，不再控制诊断。

2026-10-20第五阶段增量：认证评论、平铺回复、本人删除、独立评论图片与安全本地诊断已实现。协议见[评论API](COMMENTS_API.md)、[日志排查](LOGGING.md)、[第五阶段实施](../docs/BACKEND_BATCH5.md)及[实际验证](verification/2026-10-20-comments.md)。V6只扩展评论结构和审计约束，不修改V1—V5；页面、治理及性能验收仍待后续。以下日期对应各历史阶段，不代表当前互动接口尚未实现。

2026-10-20第四阶段增量：认证作者入口、作者主页/公开文章分页、独立联系设置、事务审计和脱敏轮转文件日志已加入当前分支。协议见[作者联系API](AUTHOR_CONTACT_API.md)、[日志配置](LOGGING.md)、[实施记录](../docs/BACKEND_BATCH4.md)，实际执行证据另列[本批验证](verification/2026-10-20-author-contact.md)。以下原阶段启动说明保留，其日期代表当时范围。

2026-10-14，Java 21 / Spring Boot 4.1.1 / Maven / Spring JDBC / Flyway / MySQL 8。当前包含游客文章读取、统一账号校园认证及第三阶段本人资料、Markdown图文草稿、图片上传、发布和文章管理。默认监听本机 `127.0.0.1:8080`。前端及互动写接口后续实现。

## 本机启动

在项目 `server` 目录执行。首次需要Java 21、Maven 3.6.3以上、运行中的MySQL 8，以及用户授权的本机连接账号。应用只允许库名 `campushub_dev` / `campushub_test`，不会自动创建或重置数据库。

1. 按 `.env.example` 设置当前进程环境变量，或创建Git忽略的 `.env.local.json`。JSON示例只用占位符，替换为本机值。

```json
{
  "CAMPUSHUB_DB_HOST": "127.0.0.1",
  "CAMPUSHUB_DB_PORT": "3306",
  "CAMPUSHUB_DB_NAME": "campushub_dev",
  "CAMPUSHUB_DB_USERNAME": "YOUR_AUTHORIZED_USER",
  "CAMPUSHUB_DB_PASSWORD": "SET_LOCALLY",
  "CAMPUSHUB_TEST_DB_USERNAME": "YOUR_AUTHORIZED_TEST_USER",
  "CAMPUSHUB_TEST_DB_PASSWORD": "SET_LOCALLY"
}
```

2. 若两个库尚不存在且本机账号有创建权限，运行以下显式初始化工具。它只执行这两个固定库名的 `CREATE DATABASE IF NOT EXISTS`，保留已有数据。已有库无需重建；没有创建权限时由库管理员建立这两个空库。

```powershell
./scripts/Initialize-Database.ps1
```

3. 配置真实邮件与本机摘要密钥。SMTP客户端授权密码放在Git忽略的 `server/.env.smtp.txt`，不放在源码或命令参数中。本机脚本读取这个文件到子进程环境，退出脚本后恢复原环境。JSON本机配置可追加MAIL_HOST、MAIL_PORT、MAIL_FROM、MAIL_USERNAME、MAIL_TLS_MODE（SSL或强制STARTTLS）和MAIL_TIMEOUT_MS等 `.env.example`列出的键。发送地址需与授权用户名相同，不支持明文SMTP。

```powershell
./scripts/Initialize-AuthKey.ps1
```

此命令仅在不存在时生成32字节随机密钥并保存到Git忽略的 `.env.auth-key.txt`，不要在重启时删除或重新生成，否则现存验证码摘要将不能核验。现有本机数据库配置保持原内容；加载器已支持新增邮件键。已验证用户本人的SMTP候选为smtp.qiye.163.com:465、SSL；其他账户不能据此声称可用。

4. 构建并启动。

```powershell
mvn -B -ntp '-Dmaven.repo.local=.m2-cache' package
./scripts/Start-Server.ps1
```

使用当前电脑已有Maven设置下载正式版依赖。`.m2-cache` / `target` / 本机配置均被Git忽略。运行窗口按Ctrl+C停止本项目应用。

5. 请求 `http://127.0.0.1:8080/actuator/health`，得到HTTP 200且 `code=OK` / `data.status=UP` 才表示应用及数据库检查通过。文章和身份接口分别见 [API.md](API.md) / [AUTH_API.md](AUTH_API.md)，共用[Postman集合](postman/CampusHub.postman_collection.json)。健康UP不代表邮件已送达；SMTP发送失败返回503，但公开文章读取继续可用。

## 数据与演示边界

默认 `demo` 配置加载V1文章结构、V2演示数据、V3身份结构及V4资料/草稿/图片结构。原六篇公开文章、四篇非公开文章和未发布修订保留，不绑定给真实作者。V4允许真实用户创建新的文章；新互动计数0且origin=NONE，与原固定DEMO计数区分，没有奖励钱包、点赞或投币记录。游客响应不返回真实账号邮箱或本人资料；正文中作者自愿公开的文字保持原文。

V1创建文章、正文版本、分类、正文版本分类关联、资料链接，均使用InnoDB。公开版本引用通过复合外键限定为该文章自己的版本；后续草稿可以有独立版本。V2是有版本号的迁移，只执行一次，不在启动逻辑重复插入。Flyway启动时验证已应用迁移的校验值；迁移错误会阻止启动，禁止自动baseline和clean。不要修改已经执行的迁移，需要变化时新增版本。

`-Profile schema-only` 用于尚未加载V2的空库，只创建结构。已经加载V2的演示库继续使用demo，不切换配置移除已应用迁移，也不删除数据来切换。本轮只使用独立开发及测试库。

## 自动化验证

```powershell
./scripts/Test-MySql.ps1
```

脚本执行 `mvn -Pmysql-it verify`，单元测试与独立MySQL集成测试分别写入 `target/surefire-reports` 和 `target/failsafe-reports`。集成测试强制库名campushub_test，通过本应用专属TCP转发器模拟真实连接故障，关闭本应用测试连接后检查503和恢复；不停止MySQL服务。迁移校验与重启保留检查都在测试库进行。没有凭据时显式失败，不跳过后宣称通过。测试库必须仅供本轮演示测试使用，测试预期为固定演示数据；不自动清空或修复已有不匹配数据。

普通 `mvn package` 只执行单元测试，不代表MySQL验证。第一阶段历史执行记录保留在 [文章验证记录](verification/2026-10-12.md)，第二阶段执行与真实SMTP证据见 [身份验证记录](verification/2026-10-12-auth.md)。本轮是提前开展的基础验证，不是整个课程项目的功能、性能、压力或稳定性验收。

身份集成测试注入受控邮件发送器与可调Clock，只在campushub_test创建随机前缀的测试邮箱。完成后删除本次测试创建的身份记录，保留已有文章、迁移及非测试账号。不向任何学生真实发信；自动化通过不等于SMTP或用户收件通过。缺少测试库凭据显式失败，不使用H2。

真实邮件验证工具 `./scripts/Verify-RealEmail.ps1 -Step Request` 只支持用户自己的固定收件邮箱，启动18081端口的本项目验证进程并发送一次真实注册邮件。首次注册验证需将用户选择的应用密码置于Git忽略 `.env.account-test.json`、实际收到的验证码置于 `.env.received-code.txt`，运行 `-Step Complete` 完成注册、登录、本人状态和退出核验，运行 `-Step Stop` 结束该工具创建的进程。已有账号不重复使用首次注册流程；找回密码按AUTH_API.md使用独立重置接口。不得把验证码、密码或返回令牌导出到Postman集合或Git。

打包后可以运行 `./scripts/Smoke-Local.ps1`，在18080端口两次启动本项目JAR，验证开发库健康及读取数据保留，然后停止脚本创建的进程。它不停止MySQL或其他项目，结果保存在 `target/smoke-result.json`。

## 故障定位与日志

日常运行使用 `./scripts/Start-Server.ps1 -Mode Release -Port 8080 -Profile demo`；需要排障时停止本项目进程，再以 `-Mode Debug` 重启。Debug要求文件日志可用，启动摘要须显示mode=debug、diagnostics=true。切回Release重启保留旧诊断文件，但不再新增诊断/DEBUG_STAGE；必要INFO/WARN/ERROR及数据库审计继续。模式、优先级、直接JAR等价命令及旧模块排查示例见[LOGGING.md](LOGGING.md)。

缺配置 `CONFIG_INVALID`、连接认证失败 `DB_AUTH_FAILED`、连接故障 `DB_CONNECTION_FAILED`、迁移错误 `MIGRATION_FAILED` 会以非零退出码阻止应用就绪。先检查本机变量、授权账号、端口、库及Flyway结构，不重置MySQL密码或他人数据库。失败日志只打印分类与异常类型，不打印密码、SQL参数、请求正文或配置值；请求日志以追踪编号、方法、状态和耗时定位。不要使用Maven调试输出或将本机配置贴入公共问题报告。

运行时数据库不可达返回HTTP 503，接口响应保持统一失败格式。健康端点不公开数据库版本、账号、连接串或内部组件；其他Actuator端点未公开。配置默认只绑定本机，无浏览器跨域开放；WorkBuddy小程序本机联调按其工具配置开发环境地址，正式域名和云部署另行设计。

安全呈现Markdown和正文图片、资料链接由后续前端承担，需要禁用危险HTML、校验链接协议；后端保留原始Markdown并提供上传及权限下载，没有编辑器、HTML渲染或图片代理。创作请求、修订冲突及私有图片预览见[AUTHORING_API.md](AUTHORING_API.md)，实际验证见[第三阶段记录](verification/2026-10-14-authoring.md)。

## 图片存储与第三阶段验证

默认持久目录为server/.local-data/media，Start-Server.ps1固定工作目录为server。原文件存于objects，操作临时文件存于temporary；服务端UUID决定路径，不以客户端文件名决定访问位置。整个.local-data/被Git忽略。重启需保留原数据库、摘要密钥和图片目录；只拷贝JAR或只备份数据库不能恢复图片。

第四阶段默认同时写入server/.local-data/logs；专属目录独占锁、每文件10MiB、保留7个轮转文件，配置见LOGGING.md和.env.example。运行目录必须可写，已有实例占用同一日志目录时新进程不会就绪，可配置另一专属目录。日志和审计都不提供给普通用户下载；业务审计与业务状态同库同事务，备份时需一并保留，完整备份恢复流程仍待设计。

可在本机JSON配置或进程环境中指定CAMPUSHUB_MEDIA_ROOT到专属目录，不能指向其他项目或符号链接路径。目录创建失败阻止就绪，运行时无法写入/文件损坏503；没有自动删除历史或未引用图片。上传明确回滚只清理本操作的新文件，提交状态未知时保留待核查，完整备份和崩溃恢复后续设计。

| 本机变量 | 默认值 |
| --- | --- |
| CAMPUSHUB_MEDIA_MAX_BYTES | 10485760，JPEG/PNG每文件10MiB |
| CAMPUSHUB_MULTIPART_MAX_BYTES | 12582912，总multipart12MiB |
| CAMPUSHUB_MEDIA_MAX_PIXELS | 25000000 |
| CAMPUSHUB_BODY_MAX_BYTES | 1048576，正文UTF-8 1MiB |
| CAMPUSHUB_JSON_MAX_BYTES | 2097152，创作JSON 2MiB |
| CAMPUSHUB_COMMENT_MAX_CODE_POINTS | 10000，普通文本Unicode码点 |
| CAMPUSHUB_COMMENT_JSON_MAX_BYTES | 131072，评论JSON 128KiB |

调大配置需评估相互关系和实际资源，不能设置无限请求/文件大小。文章没有固定业务图片张数限制，一次接口只上传一张。二进制图片每次重新验证权限，采用no-store/nosniff，私有预览不能将Bearer写入URL。

Test-MySql.ps1增加真实MySQL及独立临时目录的创作场景，用随机测试账号和受控发送器，不向真实校园邮箱发信。测试后只清理本次生成记录，原账号及演示基线保留。普通package仍只代表单元测试与打包。

`./scripts/Verify-Authoring.ps1`用于已经完成注册的用户本人开发账号，私下读取.env.account-test.json，需先在target准备authoring-fixture.png这张PNG（任意符合上限的测试图片）。它会保留一篇明确标记DEMO的公开图文和一篇独立空草稿，不改原资料或旧文章，不发邮件；运行前应确认希望新增这些验证内容。结果target/authoring-result.json不含密码或令牌，私有操作ID记录.env.authoring-verification.json被忽略。日常发布按API操作，无需反复运行这个创建工具。

一次创作验证后可以运行`./scripts/Smoke-Local.ps1 -Authoring`，使用已有私有操作ID及应用密码，两次重启并重新登录，检查保留的草稿、本人资料、DEMO公开版本及图片摘要一致。此模式不创建新文章或发邮件；若已手工编辑或删除该DEMO，它会如实失败，勿为通过测试重置真实数据。普通Smoke不需要本人密码。

## 交接责任

王晨负责工程基础、接口及任务管理；刘景浩参与接口复核、测试场景及启动说明检查。代码由Codex辅助实施，本记录不代表两位成员已实际复核或执行测试。学生复核证据和课程设计评审另行留存。当前实现选择Java，OP12已记录用户实现决策，正式设计评审状态未代替确认。
