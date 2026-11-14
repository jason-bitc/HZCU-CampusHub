# 城院帮后端与独立运维

2026-11-06。Java21 / Spring Boot4.1.1 / Spring JDBC / Flyway / MySQL8。接口已覆盖校园邮箱账号、文章阅读与创作、图片、作者联系、评论、点赞收藏、奖励投币、举报反馈及社区管理。独立运维使用同一JAR的另一个入口进程，默认8091；业务默认8080。

## 对接入口

| 模块 | 文档 |
|---|---|
| 公共文章与分类 | [API.md](API.md) |
| 校园账号 | [AUTH_API.md](AUTH_API.md) |
| 草稿与图文发布 | [AUTHORING_API.md](AUTHORING_API.md) |
| 作者与联系 | [AUTHOR_CONTACT_API.md](AUTHOR_CONTACT_API.md) |
| 评论与图片 | [COMMENTS_API.md](COMMENTS_API.md) |
| 点赞与收藏 | [INTERACTIONS_API.md](INTERACTIONS_API.md) |
| 奖励、币与账本 | [COINS_API.md](COINS_API.md) |
| 本人通知和已读状态 | [NOTIFICATIONS_API.md](NOTIFICATIONS_API.md) |
| 双端规则说明与可选写作提示 | [USER_GUIDANCE_API.md](USER_GUIDANCE_API.md) |
| 私人举报反馈及基础案件 | [COMMUNITY_API.md](COMMUNITY_API.md) |
| 社区管理员网页与治理 | [ADMIN_API.md](ADMIN_API.md) |
| 独立运维网页与实际执行 | [OPS_API.md](OPS_API.md) |
| 调试日志 | [LOGGING.md](LOGGING.md) |
| 手工接口调试 | [Postman集合](postman/CampusHub.postman_collection.json) |

数据库迁移包含 V10 内容治理、V11 行为限制与申诉、V12 贡献失效和 V13 站内通知。现有部署按 [运维维护流程](OPS_API.md) 升级数据库和执行器。

## 本机准备

进入server目录，安装Java21、Maven和MySQL8。应用仅允许campushub_dev或campushub_test，不自动重建、清空或重设数据库。

1. 参考[.env.example](.env.example)在本机建立被Git忽略的.env.local.json，设置授权的数据库账号。集成测试另需CAMPUSHUB_TEST_DB_USERNAME、CAMPUSHUB_TEST_DB_PASSWORD。
2. 数据库尚不存在时，由授权维护人员执行scripts/Initialize-Database.ps1；已有数据库不重建。
3. 执行scripts/Initialize-AuthKey.ps1生成持久摘要密钥。SMTP客户端授权码存本机.env.smtp.txt，不进入Git或聊天。
4. 构建并启动业务服务：

```powershell
mvn -B -ntp '-Dmaven.repo.local=.m2-cache' package
./scripts/Start-Server.ps1 -Mode Release
# 需要详细代码位置诊断时换成 -Mode Debug
```

健康检查为http://127.0.0.1:8080/actuator/health。HTTP200且data.status=UP表示业务服务及数据库检查通过，不表示真实邮件已送达。默认demo配置加载既有演示数据；不要修改已执行迁移或切换profile删除数据。

校园用户、社区管理员、运维账号分别授权。社区账号由[scripts/Initialize-CommunityAdmin.ps1](scripts/Initialize-CommunityAdmin.ps1)隐藏输入初始化。独立运维的配置、账号、版本登记与启动遵循[OPS_API.md](OPS_API.md)；不要同时手工启动和由运维控制同一业务目标。

## 前端联调

web的Vite代理将/api/v1/ops转发8091，将其余/api转发8080；分别用CAMPUSHUB_OPS_TARGET、CAMPUSHUB_API_TARGET调整。原生小程序使用业务服务地址，不调用运维接口。管理员令牌只能在对应域使用。

请先取得本集成分支或合并后的main；仅拉取此前不含server的旧main不能获得后端。另一台电脑须自行配置授权数据库和演示账号，不复制组长私密配置、真实令牌、日志或数据库导出到GitHub。

## 验证

```powershell
./scripts/Test-MySql.ps1
```

需要mysql.exe、mysqldump.exe在PATH中。该脚本执行单元及真实campushub_test集成测试；新增运维测试实际启动专属临时业务JVM并执行测试库备份恢复，请独占测试库、不要放入正式数据。不会停止MySQL服务、原开发业务进程或操作campushub_dev。

单元报告在target/surefire-reports，MySQL和实际运维报告在target/failsafe-reports。mvn package仅代表单元测试及打包，不能称MySQL或实际运维测试通过。运行日志、SQL备份、配置和target均不提交Git。

本轮不是前端界面开发；网页、小程序真机、外部用户走查、性能压力、每日备份调度、离机副本及完整恢复时限仍按项目计划分别验收。
