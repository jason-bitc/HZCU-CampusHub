# 本机社区管理账号初始化与重设

本批管理账号独立于校园普通账号。迁移不创建默认账号，不将任何学生账号自动提升；普通注册和邮箱认证不授管理权限。真实演示账号由用户在本机显式建立，自动验证只使用campushub_test内的随机身份。

在本机完成原server配置并至少启动一次当前应用，确保V9已迁移。脚本不创建或重置数据库，也不隐式执行迁移。需要本机Java21、Maven和已授权MySQL连接配置，库名仅campushub_dev或campushub_test、主机仅127.0.0.1或localhost。

```powershell
cd C:/Users/wangc/Desktop/ZUCC-CampusHub/server
./scripts/Initialize-CommunityAdmin.ps1
```

输入自选管理login及两次新密码。login为3—64位小写字母／数字／点／短横线／下划线，首字符是字母或数字。密码沿用8—64字符且UTF-8不超过72字节。密码提示使用Read-Host -AsSecureString，明文只在本次进程内经标准输入交给Java工具，不放命令参数、源码、迁移或文档；脚本结束释放隐藏输入对象并恢复环境。不要把密码粘贴到聊天或导出Postman变量。

工具打包当前代码，通过同一JAR内的本机工具入口，在指定库内按login创建账号；已存在时重设该账号密码并启用。该账号所有旧管理会话撤销，登录失败窗口清除。密码以与普通账号一致的BCrypt cost12编码保存，输出只有LOCAL_SETUP、目标内部ID和安全完成信息，不显示login、密码、摘要或令牌。

初始化仅授本批CASE_READ／CASE_HANDLE能力，不授普通USER、运维、内容隐藏、账号封禁或返币能力。账号有效性、会话到期及退出在每次管理请求校验。管理登录API只登录，不注册；停用后的会话不能继续访问。

本机凭据工具没有实际运行人员会话，不把本机配置操作冒记成普通用户或管理员的在线案件操作；输出标记LOCAL_SETUP。在线案件变更则在数据库成功审计中明确记录COMMUNITY_ADMIN主体域。

构建失败会在送出密码前停止，诊断位于被Git忽略的target/admin-setup-build.log；配置或输入失败只返回ADMIN_LOCAL_SETUP_FAILED安全提示。首次缺少依赖时Maven可能需要下载。工具不替用户选择默认login或密码，不执行真实演示账号初始化作为测试。
