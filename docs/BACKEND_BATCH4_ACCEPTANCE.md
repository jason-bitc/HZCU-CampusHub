# 第四阶段后端范围验收记录

G04，2026年10月20日。审核对象为 `feat/wc-author-contact` 的提交 `57baa4becb9e02c1c829998742070827a72e9947`。

## 结论

本批后端范围通过证据核验，可以进入下一批开发。核验方式是读取本批计划、实现、接口、测试逐项记录与原始摘要，抽查权限、实时联系查询及事务审计代码，并实时读取GitHub分支提交。此次总规划对话没有重新运行应用、单元测试或数据库测试；下列测试结果来自本批开发已保存的实际执行证据。

这不是教师评审结论、两位学生已经交叉复核的记录，也不是整个平台最终验收。

## 范围与证据

| 检查项 | 本次核对结果 | 证据位置 |
| --- | --- | --- |
| B4-01/02/04：受限作者入口、主页、公开文章及游客兼容 | 新增6个接口；游客原列表和详情没有作者资料；作者列表只查当前公开版本 | `server/AUTHOR_CONTACT_API.md`；`author/AuthorController.java`、`AuthorRepository.java`；新增MySQL案例 |
| B4-03/07：访问者、目标作者和会话限制 | 每请求通过既有认证守卫；目标异常404；原公开正文读取合同保持 | `auth/AuthRequestFilter.java`；作者状态与会话案例 |
| B4-05/06：联系开关与独立修订 | 独立设置表和revision；关闭响应不含type/value；查询使用新READ_COMMITTED事务及单条实时SQL | `author/AuthorService.java`、`AuthorRepository.java`；联系与并发案例 |
| B4-08：审计与业务一致 | 审计写入要求现有可写事务；成功运行事件在提交后产生；所列失败注入覆盖业务回滚 | `audit/BusinessAudit.java`；实际MySQL审计回滚案例 |
| B4-09：日志与隐私 | 脱敏样本、文件故障、轮转及真实JAR启动失败检查有记录 | `server/LOGGING.md`；`batch4-log-storage-result.json`及日志样本 |
| B4-10：重启与原三批回归 | 已有最终回归含原读取、账号、创作及新作者联系套件；重启场景有单独案例 | `server/verification/2026-10-20-author-contact.md` |
| 实际案例与数量 | 逐行核验CSV共78条、全部PASS，其中36条unit、42条mysql；与test-result.json一致 | `server/verification/author-contact-2026-10-20/test-cases.csv`及`test-result.json` |
| 迁移保护 | V1—V5当前SHA256与本批记录均一致；V1—V4相对第三阶段无内容修改 | 同目录`migration-sha256.csv`及Git差异 |
| GitHub同步 | 实时读取origin的`feat/wc-author-contact`，提交与本机及远端跟踪分支一致 | `57baa4becb9e02c1c829998742070827a72e9947` |

源码位置均相对于 `server/src/main/java/cn/edu/hzcu/campushub/`，除已写明server前缀者。核对过程未改源码、数据库、真实账号、原报告或历史迁移。

## 保留事项

- 页面、复制与跳转、安全HTML呈现和微信真机联调未验收。
- 社区治理、运维查询和执行器、完整告警、平台性能与压力目标未在本批完成。
- 校园认证只表示校园关联；主页尚不包含头像、荣誉或履历核验。
- 学生复核、实际人时和贡献评分待真实开展，不由本次核验代填。
- 原报告、设计和工具的既有未提交修改继续保留，下一批不能混入代码提交。

后续开发见 [第五阶段计划](BACKEND_BATCH5_PLAN.md)，范围为评论、回复、评论图片与本人删除，接入现有权限和审计。
