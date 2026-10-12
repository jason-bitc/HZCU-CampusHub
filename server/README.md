# 城院帮首批后端

2026-10-12，Java 21 / Spring Boot 4.1.1 / Maven / Spring JDBC / Flyway / MySQL 8。当前仅后端基础与游客文章读取，默认监听本机 `127.0.0.1:8080`。不含前端、认证、发布或互动写接口。

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

3. 构建并启动。

```powershell
mvn -B -ntp '-Dmaven.repo.local=.m2-cache' package
./scripts/Start-Server.ps1
```

使用当前电脑已有Maven设置下载正式版依赖。`.m2-cache` / `target` / 本机配置均被Git忽略。运行窗口按Ctrl+C停止本项目应用。

4. 请求 `http://127.0.0.1:8080/actuator/health`，得到HTTP 200且 `code=OK` / `data.status=UP` 才表示应用及数据库检查通过。接口和Postman集合见 [API.md](API.md) / [postman集合](postman/CampusHub.postman_collection.json)。

## 数据与演示边界

默认 `demo` 配置加载V1结构和V2演示数据。六篇公开文章、四篇非公开文章、一个未发布修订版本；所有内容和计数均为明确标注的演示数据。没有真实用户、作者账号、奖励钱包、点赞记录、投币事件或审核材料。数据库不放身份和联系方式字段；正文中作者自愿公开的文字保持原文。

V1创建文章、正文版本、分类、正文版本分类关联、资料链接，均使用InnoDB。公开版本引用通过复合外键限定为该文章自己的版本；后续草稿可以有独立版本。V2是有版本号的迁移，只执行一次，不在启动逻辑重复插入。Flyway启动时验证已应用迁移的校验值；迁移错误会阻止启动，禁止自动baseline和clean。不要修改已经执行的迁移，需要变化时新增版本。

`-Profile schema-only` 用于尚未加载V2的空库，只创建结构。已经加载V2的演示库继续使用demo，不切换配置移除已应用迁移，也不删除数据来切换。本轮只使用独立开发及测试库。

## 自动化验证

```powershell
./scripts/Test-MySql.ps1
```

脚本执行 `mvn -Pmysql-it verify`，单元测试与独立MySQL集成测试分别写入 `target/surefire-reports` 和 `target/failsafe-reports`。集成测试强制库名campushub_test，通过本应用专属TCP转发器模拟真实连接故障，关闭本应用测试连接后检查503和恢复；不停止MySQL服务。迁移校验与重启保留检查都在测试库进行。没有凭据时显式失败，不跳过后宣称通过。测试库必须仅供本轮演示测试使用，测试预期为固定演示数据；不自动清空或修复已有不匹配数据。

普通 `mvn package` 只执行单元测试，不代表MySQL验证。真实执行状态见 [验证记录](verification/2026-10-12.md)。本轮是提前开展的基础验证，不是整个课程项目的功能、性能、压力或稳定性验收。

打包后可以运行 `./scripts/Smoke-Local.ps1`，在18080端口两次启动本项目JAR，验证开发库健康及读取数据保留，然后停止脚本创建的进程。它不停止MySQL或其他项目，结果保存在 `target/smoke-result.json`。

## 故障定位与日志

缺配置 `CONFIG_INVALID`、连接认证失败 `DB_AUTH_FAILED`、连接故障 `DB_CONNECTION_FAILED`、迁移错误 `MIGRATION_FAILED` 会以非零退出码阻止应用就绪。先检查本机变量、授权账号、端口、库及Flyway结构，不重置MySQL密码或他人数据库。失败日志只打印分类与异常类型，不打印密码、SQL参数、请求正文或配置值；请求日志以追踪编号、方法、状态和耗时定位。不要使用Maven调试输出或将本机配置贴入公共问题报告。

运行时数据库不可达返回HTTP 503，接口响应保持统一失败格式。健康端点不公开数据库版本、账号、连接串或内部组件；其他Actuator端点未公开。配置默认只绑定本机，无浏览器跨域开放；WorkBuddy小程序本机联调按其工具配置开发环境地址，正式域名和云部署另行设计。

安全呈现Markdown和正文图片、资料链接由后续前端承担，需要禁用危险HTML、校验链接协议；本轮返回原始Markdown，没有编辑器、HTML渲染、图片代理或上传能力。

## 交接责任

王晨负责工程基础、接口及任务管理；刘景浩参与接口复核、测试场景及启动说明检查。代码由Codex辅助实施，本记录不代表两位成员已实际复核或执行测试。学生复核证据和课程设计评审另行留存。当前实现选择Java，OP12已记录用户实现决策，正式设计评审状态未代替确认。
