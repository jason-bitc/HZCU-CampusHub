# 独立运维网页接口与运行手册

2026-11-06。本机课堂环境：Java21、MySQL8、独立运维进程。业务服务默认8080，运维默认8091，仅监听127.0.0.1。运维与业务可使用同一构建JAR的不同入口，但属于不同进程；运维授权、任务及事件保存在独立控制目录，不依赖业务MySQL可用。

## 1 启动与准备

先按 [README.md](README.md)准备业务数据库和本机配置，构建迁移V1—V11的应用，至少正常启动一次使迁移完成，然后停止业务应用。独立运维只管理由自己启动并持有PID及开始时刻的业务进程；不会停止其他手工启动的进程。

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
| 总览 | GET /status | target、healthy、managedProcessRunning、release、recoveryRequired |
| 发布包 | GET /artifacts | 最近100个已登记包的ID、SHA256和模式指纹 |
| 备份 | GET /backups | 最近100份清单的ID、时间、complete、模式指纹 |
| 操作确认 | POST /confirmations | action、target、referenceId；返回确认ID、过期时间和影响 |
| 创建任务 | POST /jobs | 原操作三字段、requestId、confirmationId；返回持久任务 |
| 任务列表 | GET /jobs | 最近100个任务，最新在前 |
| 任务详情 | GET /jobs/{id} | 状态、事件、结果或错误码、起止时间 |
| 日志 | GET /logs | 任务事件与业务安全请求日志的有限字段，不返回原始日志文本 |
| 进程存活 | GET /health | 仅公开独立运维自身UP；不代表业务健康 |

以上列表当前不接收查询参数。全部管理响应no-store。未知身份、普通用户令牌、社区管理员令牌拒绝。网页通过Vite的 `/api/v1/ops` 独立代理访问8091，业务 `/api` 继续代理8080；需要改端口时使用CAMPUSHUB_OPS_TARGET和CAMPUSHUB_API_TARGET。不要把运维请求误发到业务8080。

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

## 4 实际执行策略

| 操作 | 真正执行的步骤 | 成功依据 |
|---|---|---|
| DEPLOY | 校验登记包和模式；停止受控业务；保护备份；安装JAR与版本清单；启动 | 受控进程存活且业务健康UP，任务记录版本与保护备份 |
| RESTART | 核对PID+开始时刻；停止；重新启动 | 新进程及健康检查，不把“已发送重启请求”写成成功 |
| BACKUP | 停止受控写入；mysqldump；复制完整图片及当前JAR/版本；生成校验清单；原先在运行则启动 | SQL、图片、版本和全部文件SHA256清单齐全后标完整 |
| RESTORE | 校验备份、模式、文件集合与SHA256；停止业务；保护当前状态；恢复MySQL、图片、备份版本；启动 | 数据恢复命令成功且健康检查通过；失败执行保护副本回退 |

业务处于维护窗口时，独立控制目录及任务查询保持可用。备份不包含密码或SMTP配置，维护人员须另行安全保存相同摘要密钥和连接配置。备份及恢复均只针对当前配置的固定业务数据库，不接收网页传入库名。

恢复会保留替换前的media-held目录；清理前由维护人员核对保护备份和当前数据。备份目录与runtime/media需位于支持原子移动的相同本机卷，生产部署需另做验证。当前没有自动清理保护副本。

错误示例：401 OPS_UNAUTHORIZED、403 ORIGIN_FORBIDDEN、409 OPS_CONFIRMATION_INVALID/OPS_TARGET_BUSY/REQUEST_KEY_REUSED/OPS_BACKUP_INVALID/OPS_ARTIFACT_SCHEMA_CHANGE、429 OPS_RATE_LIMITED、503 OPS_SERVICE_UNAVAILABLE。执行中的错误记录到任务errorCode；HTTP200读到FAILED任务不能显示成功。

## 5 中断、回退和边界

运维崩溃后遗留QUEUED/RUNNING一律记INTERRUPTED并置recoveryRequired，禁止自动重做恢复。维护人员检查业务进程、数据库、图片和保护备份，停运维后执行：

```powershell
java -Dloader.main=cn.edu.hzcu.ops.OpsApplication -cp target/campushub-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --reconcile .env.ops.json
```

工具展示状态并要求本人输入RECONCILED确认已完成核查，留存核对时间后才解除拦截。实际无法判断时保留维护状态，不以刷新网页抹去失败。

本轮验证范围为Windows本机、真实MySQL及隔离业务JVM。未完成的总体需求包括每日备份调度、离机副本交接、跨主机部署、真实网页联调、RPO/RTO计时、容量/压力和安全专项验收。它们仍按项目计划验收，本接口实现不把整机故障保护或整个运维工作包标为完成。
