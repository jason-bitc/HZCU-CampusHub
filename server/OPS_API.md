# 独立运维网页接口与运行手册

2026-11-06。本机课堂环境：Java21、MySQL8、独立运维进程。业务服务默认8080，运维默认8091，仅监听127.0.0.1。运维与业务可使用同一构建JAR的不同入口，但属于不同进程；运维授权、任务及事件保存在独立控制目录，不依赖业务MySQL可用。

## 1 启动与准备

先按 [README.md](README.md) 准备业务数据库和本机配置，构建当前应用，至少正常启动一次使迁移完成，然后停止业务应用。独立运维只管理由自己启动且能核对 PID 与开始时刻的业务进程。

创建Git忽略的 `server/.env.ops.json`。路径替换为当前电脑实际位置；五个数据目录相互独立、不嵌套，不使用符号链接或其他项目目录。

```json
{
  "controlRoot":"C:/CampusHubLocal/control",
  "runtimeRoot":"C:/CampusHubLocal/runtime",
  "artifactRoot":"C:/CampusHubLocal/artifacts",
  "backupRoot":"C:/CampusHubLocal/backups",
  "mediaRoot":"C:/CampusHubLocal/media",
  "javaExecutable":"C:/path/to/jdk-21/bin/java.exe",
  "mysqlExecutable":"C:/path/to/mysql/bin/mysql.exe",
  "dumpExecutable":"C:/path/to/mysql/bin/mysqldump.exe",
  "businessEnvironment":"C:/path/to/HZCU-CampusHub/server/.env.local.json",
  "databaseName":"campushub_dev",
  "healthUrl":"http://127.0.0.1:8080/actuator/health",
  "allowedOrigin":"http://127.0.0.1:5173",
  "port":8091
}
```

业务配置必须为本机campushub_dev或campushub_test且与databaseName一致；数据库用户、密码从配置读取，SMTP授权码和摘要密钥仍从对应本机私密文件读取。密码不进入HTTP请求、命令参数、Git或运维日志。迁移前已有图片时，维护人员须在停机状态把完整图片目录迁至配置的mediaRoot，不能只改路径后把空目录当作原图片库。

初始化独立运维账号，不自动复用社区管理员：

```powershell
cd server
./scripts/Initialize-OpsAdmin.ps1
```

初始化使用隐藏密码输入。控制目录保存BCrypt密码摘要，不能提交Git。初始化或重设时需停止运维进程；没有默认密码。

运维停止时，维护人员通过本地CLI登记已审阅的构建包。release-20261106是自选且不可复用的版本标识：

```powershell
java -Dloader.main=cn.edu.hzcu.ops.OpsApplication -cp target/campushub-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --register .env.ops.json release-20261106 target/campushub-server-0.1.0.jar
./scripts/Start-Ops.ps1
```

登记生成application.jar、SHA256、当前Flyway模式指纹和版本标识。网页不能上传任意程序。当前仅允许迁移资源与运维构建完全相同的发布包，防止回滚代码时跨越未评审的数据库变更；带新迁移的正式升级需维护人员按维护流程执行，不能绕过此约束。

业务子进程固定使用 `-Xmx512m`，专属runtime目录与配置的media目录，日志位于runtime/.local-data/logs。原手工运行目录及文件保持不动。不要同时用原脚本和运维控制器启动同一业务目标。

## 2 页面与路由

前缀 `/api/v1/ops`，默认 `http://127.0.0.1:8091`。成功 `code/data/traceId`，失败 `code/message/traceId`，真实HTTP状态。Authorization仅接受独立 `ops_` 域令牌，固定8小时有效；退出及运维重启失效。

| 页面 | 方法/路径 | 数据 |
|---|---|---|
| 登录 | POST /auth/login | login、password；返回Bearer、accessToken、expiresAt |
| 退出 | POST /auth/logout | 空正文；loggedOut |
| 权限 | GET /me | OPS_ADMIN域及能力 |
| 总览 | GET /status | target、healthy、managedProcessRunning、release、recoveryRequired、recoveryIncident、dailyBackup |
| 发布包 | GET /artifacts | 最近100个已登记包的ID、SHA256和模式指纹 |
| 备份 | GET /backups | 按创建时间倒序列出当前库的最近100份完整清单，含ID、创建时间、快照时点、complete、模式指纹和withinRetentionWindow |
| 操作确认 | POST /confirmations | action、target、referenceId；返回确认ID、过期时间和影响；恢复操作另返回预检结果 |
| 创建任务 | POST /jobs | 原操作三字段、requestId、confirmationId；返回持久任务 |
| 任务列表 | GET /jobs | 最近100个任务，最新在前 |
| 任务详情 | GET /jobs/{id} | 状态、事件、结果或错误码、起止时间 |
| 日志 | GET /logs | 最近200条任务阶段和业务请求摘要，业务请求支持按追踪编号和错误状态筛选 |
| 进程存活 | GET /health | 仅公开独立运维自身UP；不代表业务健康 |

日志接口接受下文列出的查询参数，其余列表使用固定范围。全部管理响应no-store。未知身份、普通用户令牌、社区管理员令牌拒绝。网页通过Vite的 `/api/v1/ops` 独立代理访问8091，业务 `/api` 继续代理8080；需要改端口时使用CAMPUSHUB_OPS_TARGET和CAMPUSHUB_API_TARGET。不要把运维请求误发到业务8080。

### 错误日志查询

调用 `GET /logs?errorsOnly=true&limit=20` 查看最近的业务错误。要从响应的 `X-Trace-Id` 定位具体请求，附加 `traceId`，值使用小写 UUID。三个参数都可省略，`errorsOnly` 默认 `false`，设为 `true` 时筛选 HTTP 400–599，`limit` 默认 100，有效范围为 1–100。筛选只作用于 `businessRequests`，`taskEvents` 仍返回最近 200 条任务阶段。未知、重复或不合法参数返回 400。

接口从受控业务运行目录读取当前日志和最多 30 个轮转文件。每个文件只读尾部 256 KiB，返回查到的最新匹配记录，数组按从旧到新的写入顺序呈现。`businessLogCoverage` 包含读取文件数 `filesRead`、文件超出扫描上限的 `scanLimited` 和部分文件不可读的 `readIncomplete`。业务停机时仍可查历史日志和任务阶段。需要查看扫描窗口之外的记录时，由维护人员按运维手册在本机核查。

`businessRequests` 每条提供时间 `occurredAt`、追踪编号、动作、方法、状态、结果、耗时和可用的模块及错误码。状态和耗时沿用字符串字段。业务日志目录、原文、身份信息和 Debug 诊断文件均不通过接口返回。

## 3 确认与提交

动作只能为DEPLOY、RESTART、BACKUP、RESTORE，目标固定business。DEPLOY的referenceId为发布包ID，RESTORE为备份ID，其他两个动作传空字符串。前端先展示所选版本/备份、目标及停机/覆盖影响，由操作者明确确认后提交：

```json
{"action":"RESTART","target":"business","referenceId":""}
```

`POST /confirmations` 返回5分钟有效、绑定当前会话及原操作的confirmationId。随后 `POST /jobs`：

```json
{"action":"RESTART","target":"business","referenceId":"","requestId":"00000000-0000-4000-8000-000000000006","confirmationId":"服务端返回的确认ID"}
```

返回任务不代表执行完成。前端按id轮询，显示QUEUED、RUNNING、SUCCEEDED、FAILED或INTERRUPTED和阶段事件。同一requestId同语义重试返回原任务；改语义409。重复请求回执不要求重新确认；首次提交必须有有效确认。同一目标已有QUEUED/RUNNING时409 OPS_TARGET_BUSY。

登录失败窗口持久保存，15分钟5次失败后429，运维重启不清除窗口。只允许配置的浏览器Origin；未配置来源403。任何未知命令、目标、目录、SQL和额外JSON字段均不接受。

### 恢复预检

选择备份后，先提交 `POST /confirmations`，动作传 `RESTORE`，`referenceId` 传备份 ID。服务端在发放确认前检查数据库和 Flyway 模式、文件集合、SHA-256，以及备份内应用包和版本清单是否配套。预检只读取数据。备份不存在时返回 404 `OPS_BACKUP_NOT_FOUND`，文件或清单损坏时返回 409 `OPS_BACKUP_INVALID`，数据库或模式不匹配时返回 409 `OPS_BACKUP_INCOMPATIBLE`。

预检通过后，确认响应的 `restorePreview` 包含 `backupId/backupCreatedAt/backupSnapshotAt/backupReleaseId/affectedComponents/verifiedFileCount/mediaFileCount/businessSnapshotAvailable`。页面展示备份时间、备份版本和覆盖范围后，再由操作者确认提交。覆盖范围一定包含 `DATABASE`、`MEDIA`，带应用包时还包含 `RELEASE`，否则保持现有应用版本。

恢复任务记录确认时的 `backupManifestSha256`。任务在停机前和导入前核对清单摘要及文件。确认后清单有变化时，原任务记录 `OPS_BACKUP_CHANGED`，需要重新预检和确认。文件预检结果和实际恢复任务结果分别查询。

## 4 实际执行策略

| 操作 | 真正执行的步骤 | 成功依据 |
|---|---|---|
| DEPLOY | 校验登记包和模式；停止受控业务；保护备份；安装JAR与版本清单；启动 | 受控进程存活且业务健康UP，任务记录版本与保护备份 |
| RESTART | 核对PID+开始时刻；停止；重新启动 | 新进程及健康检查，不把“已发送重启请求”写成成功 |
| BACKUP | 停止受控写入；导出 MySQL；复制图片、应用包和版本；核对图片引用及文件摘要；恢复原运行状态 | 文件清单生成且数据库中的图片引用均可在副本中核对 |
| RESTORE | 校验备份；停止业务并保护当前状态；导入 MySQL；核对清单中的业务摘要和暂存图片；切换图片和应用版本；启动 | 完成有基线的业务数据核验、图片引用核对及健康检查。失败时尝试使用保护副本回退 |

业务处于维护窗口时，独立控制目录及任务查询保持可用。备份不包含密码或SMTP配置，维护人员须另行安全保存相同摘要密钥和连接配置。备份及恢复均只针对当前配置的固定业务数据库，不接收网页传入库名。

图片一致性核对覆盖文章图片、评论图片、举报与反馈附件，以及案件留存的图片记录。核对每条数据库记录对应文件的存在性、字节数和 SHA-256。同一图片若被多条记录引用，每条记录都必须与文件一致。新备份的清单和成功恢复任务的 `result` 使用 `verifiedMediaReferences` 记录核对的数据库记录数。

恢复任务在 `VERIFYING_MEDIA` 阶段完成 MySQL 记录与暂存图片的核对。出现 `OPS_MEDIA_REFERENCE_MISMATCH` 时，任务失败。若随后出现 `ROLLBACK_VERIFIED`，表示保护副本已完成回退核对。历史备份也会在恢复时重新核对。

### 恢复后的业务数据核验

新备份在业务停止后采集版本为 1 的 `businessSnapshot`，为文章、文章版本、草稿、分类、评论、币钱包、投币与奖励流水、社区案件共八张表记录行数和选定字段的 SHA-256。读取按主键顺序流式执行。清单中只记录统计和摘要，核验字段覆盖正文、版本引用、分类关系、余额、流水和案件状态等，超出这些字段的项目数据仍按验收用例抽查。

恢复导入后在 `VERIFYING_DATA` 阶段比对行数和摘要，然后核对媒体并启动。出现 `OPS_BUSINESS_SNAPSHOT_MISMATCH` 时尝试回退，原恢复任务保持失败。成功任务的 `result.businessVerification` 只返回 `status` 和 `checkedTables`。存在基线并全部匹配时显示 `VERIFIED`。历史清单缺少业务摘要时仍可按原流程恢复，结果显示 `UNAVAILABLE`，请按验收用例抽查关键样本。

恢复会保留替换前的media-held目录；清理前由维护人员核对保护备份和当前数据。备份目录与runtime/media需位于支持原子移动的相同本机卷，生产部署需另做验证。当前没有自动清理保护副本。

### 每日备份

在本机 `.env.ops.json` 添加 `"dailyBackupTime": "03:00"` 并重启独立运维，即可在北京时间每天 03:00 后检查是否执行备份。省略该字段或设置为 `null` 会关闭每日调度。选取可接受短暂停机的时间。运维进程每分钟检查一次，要求业务由独立运维启动且健康。部署、恢复或其他任务占用时当天稍后重试排队，待人工核查期间暂停。进程当天恢复运行且已过设定时间时，可以在条件满足后补做当天备份。

每日任务与手动备份共用互斥队列和执行流程。在备份任务入队时同时记录北京时间日期，同一天只入队一次，运维重启后仍生效。错过的日期不补做。当天任务失败时，可查看错误并按需发起手动备份。

`GET /status` 的 `dailyBackup` 提供 `enabled/time/timeZone/lastScheduledFor`。日期代表已入队，执行结果以 `GET /jobs/{id}` 为准。每日任务的 `actorDomain` 为 `OPS_SCHEDULER`，`scheduledFor` 记录北京时间日期。离机副本按维护流程另行保存。

### 备份保留与离机交接

`GET /backups` 以清单的 `createdAt` 为准，最近七份的 `withinRetentionWindow` 为 `true`。列表检查清单元数据，文件完整性以导出校验或恢复预检为准。后端保留其他快照和保护副本，维护人员应先复核依赖及离机副本，再安排清理。

维护人员在项目的 `server` 目录运行以下命令。先在另一台设备或移动介质上建立受限的交接目录，再用实际备份 ID 替换示例值。导出可在业务和独立运维运行期间执行。

```powershell
java '-Dloader.main=cn.edu.hzcu.ops.OpsApplication' -cp target/campushub-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --export-backup .env.ops.json BACKUP_ID E:/CampusHub-handoff
java '-Dloader.main=cn.edu.hzcu.ops.OpsApplication' -cp target/campushub-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --verify-backup-export E:/CampusHub-handoff/BACKUP_ID MANIFEST_SHA256
```

导出只复制快照清单及其文件。核对源快照和目标副本的文件集合及 SHA-256 后，才公布目标备份目录。同名目录保留原状。命令的 JSON 回执包含 `backupId/manifestSha256/backupCreatedAt/verifiedAt/verifiedFileCount`。把回执与介质交接记录另行保存，接收人用其中的 `manifestSha256` 在目标设备复核。独立复核只需程序包、副本路径和已记录摘要，无需配置文件或数据库连接。签收时由维护人员确认介质所在地，文件校验结果与恢复演练分别记录。

### 恢复演练计时

新备份在受控业务停止写入后、开始导出数据库前写下 `snapshotAt`，清单落盘前另记 `createdAt`。历史清单可继续预检和恢复，其快照时点显示为 `null`。

成功的 RESTORE 任务通过 `GET /jobs/{id}` 提供 `result.recoveryEvidence`，记录：

| 字段 | 计时口径 |
|---|---|
| `backupSnapshotAt`、`restoreStartedAt`、`serviceAvailableAt` | 所选备份的快照时点、执行停机前的时点、业务进程通过健康检查的时点 |
| `snapshotAgeMillis`、`rpoTargetMillis`、`rpoTargetStatus` | 快照到本次恢复操作开始的间隔，与 24 小时目标比较。快照时点缺失或晚于恢复开始时显示 `UNAVAILABLE` |
| `restoreElapsedMillis`、`rtoTargetMillis`、`rtoTargetStatus` | 用单调时钟测量停机、保护备份、导入、业务与媒体核对和健康恢复的总用时，与 10 分钟目标比较 |

达到目标显示 `WITHIN_TARGET`，超过显示 `EXCEEDS_TARGET`。失败或回退沿用任务状态和阶段记录。验收另记实际故障时点、发起恢复前的等待、恢复前后业务样本和离机交接，合并判断整场演练。

错误示例：401 OPS_UNAUTHORIZED、403 ORIGIN_FORBIDDEN、409 OPS_CONFIRMATION_INVALID/OPS_TARGET_BUSY/REQUEST_KEY_REUSED/OPS_BACKUP_INVALID/OPS_ARTIFACT_SCHEMA_CHANGE、429 OPS_RATE_LIMITED、503 OPS_SERVICE_UNAVAILABLE。执行中的错误记录到任务errorCode；HTTP200读到FAILED任务不能显示成功。

## 5 中断、回退和边界

部署和恢复在保护备份完成后，先把 `protectionBackupId` 和 `PROTECTION_READY` 阶段一起持久化，再进入 `INSTALLING` 或 `RESTORING`。任务详情的顶层字段在执行中、失败和中断时均可查询。成功任务的 `result.protectionBackupId` 也可继续读取。

执行器重启时，遗留的 `QUEUED`、`RUNNING` 变为 `INTERRUPTED`，保留原有阶段，`finishedAt` 记录重启时发现中断的时间。人工核查期间，运维总览的 `recoveryRequired` 为 `true`，`recoveryIncident` 提供 `jobId/action/state/errorCode/lastPhase/protectionBackupId`。中断早于保护备份完成时，保护备份编号为空。服务保持查询可用，待核查期间拦截新的变更和每日备份。

维护人员按任务编号核对业务进程、数据库、图片和保护备份，停运维后执行：

```powershell
java '-Dloader.main=cn.edu.hzcu.ops.OpsApplication' -cp target/campushub-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --reconcile .env.ops.json
```

工具展示待核查任务和保护备份编号。确认检查完成后输入 `RECONCILED`，任务详情记录 `reconciledAt`，运维解除拦截。核查完成后仍可从任务详情追溯原状态和阶段。

验证范围为 Windows 本机、真实 MySQL 及隔离业务 JVM。离机副本交接、跨主机部署、真实网页联调、RPO/RTO 计时、容量和安全专项验收仍按项目计划实施。
