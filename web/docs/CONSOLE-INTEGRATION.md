# 社区管理与独立运维网页接入

2026-11-07。后端合同来自 [PR #8](https://github.com/jason-bitc/HZCU-CampusHub/pull/8) 的 `b487c3b561225e07c98be7fe1df56db559929836`。对应 `server/ADMIN_API.md`、`COMMUNITY_API.md`、`OPS_API.md` 和实际 Controller。旧原型验收记录保留在 `MANAGEMENT-VERIFICATION-20261106.md`。

## 访问边界

| 页面 | 本机端口 | API 目标 |
| --- | --- | --- |
| 普通用户 | 5173 | `/api/v1/` 的普通业务接口 → 8080 |
| 社区管理 | 5174 | `/api/v1/admin/` → 8080 |
| 独立运维 | 5175 | `/api/v1/ops/` → 8091 |

普通页不挂载管理路由和组件，也不代理两个管理 API。构建分别输出 `dist/user`、`dist/admin`、`dist/ops`。本机三个端口均绑定回环。运维 `allowedOrigin` 需要与打开页面的 Origin 一致。社区及运维令牌在各自端口独立核验，失效或退出清理所属私有状态。

## 页面与合同

| 原型 | Hash 路径 | 当前行为 |
| --- | --- | --- |
| A01 | `/admin/login` | 社区身份独立登录、退出、401 和刷新恢复 |
| A02 | `/admin/cases` | 真实总览、案件类型与状态、快照分页；负责人作用于当前页 |
| A03 | `/admin/cases/:id` | 固定公开版本、受控证据、认领/释放/决定。举报闭案需要服务端核对真实隐藏事件 |
| A04 | `/admin/content` | 公开版本巡查、文章状态筛选、评论分页、隐藏与恢复。巡查正文使用受限 Markdown，案件图片经本案授权下载 |
| A05 | `/admin/accounts` | 发布及评论限制分别设置与解除。接受或驳回行为限制申诉 |
| A06 | `/admin/categories` | 岗位、专业、公司和主题创建、改名、排序及启停。合并、删除和历史批量映射待合同 |
| A07 | `/admin/contributions` | 贡献失效、返还和正式荣誉规则待接口，执行禁用 |
| A08 | `/admin/audit` | 内容与分类的事务治理记录，案件历史及申诉答复在对应页面 |
| O01 | `/ops/login` | 独立登录、会话恢复、退出及限流反馈 |
| O02 | `/ops/overview` | 业务健康、受控进程、当前版本、最近完整备份和原任务 |
| O03 | `/ops/logs` | 持久任务与服务端筛出的安全日志字段 |
| O04 | `/ops/deploy` | 选择本机 CLI 登记的发布包，确认停机影响并创建部署任务 |
| O05 | `/ops/restart` | 固定 business 目标与重启确认 |
| O06 | `/ops/backups` | 手动完整备份、实际清单、恢复入口 |
| O07 | `/ops/restore` | 完整备份选择、覆盖影响与保护副本提示；文件、模式及 SHA256 由服务端预检查 |
| O08 | `/ops/tasks/:id` | 可恢复的任务编号、阶段、结果与错误码。排队或执行时轮询，切到后台暂停 |
| O09 | `/ops/exceptions` | 失败、中断、正在执行的冲突目标与人工核查入口 |
| O10 | `/ops/manual` | 代码回退、数据恢复、原任务和本机手册操作 |

普通用户从个人中心进入 `/me/appeals`，查询本人限制和复核答复。每个写操作带原对象、修订与幂等键，结果不确定时保留原语义，刷新后可核对原请求。运维确认绑定固定动作、目标和引用，最终结果以独立服务任务记录为准。恢复要求人工核查时阻止新操作。

`?preview=1` 只展示带标记的原型示例，保持独立授权状态及零 API 调用。日常联调访问默认实际模式。

## 运维启动实测差异

PR #8 构建包通过 `PropertiesLauncher` 启动独立运维时，登录与状态读取成功，但部署预检查报 `OPS_EXECUTION_FAILED`。本机 JFR 观察到该执行路径的 JDBC `No suitable driver`。隔离联调将同一 JAR 的 `BOOT-INF/classes` 和 `BOOT-INF/lib` 解到临时目录，用 Java 类路径启动 `cn.edu.hzcu.ops.OpsApplication`，业务仍由原 JAR 启动。启动方式和验证结果单独记录，后端分支未随前端改动。

当前合同只覆盖本机固定业务目标。每日备份调度、离机副本、RPO/RTO计时及实际部署环境验收另行安排。运行命令见 [网页说明](../README.md)，验证与截图见 [联调记录](CONSOLE-VERIFICATION-20261107.md)。
