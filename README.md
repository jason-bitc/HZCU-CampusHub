# 城院帮软件工程课程项目

浙大城市学院软件工程课程设计，G04。王晨为组长兼项目负责人，刘景浩为成员；开发、文档与评审由两人协作。

当前目录用于课程材料维护与重新实施。旧工程已于2026年10月10日移除；2026年10月12日按用户确认开始新Java后端，首批仅“后端基础＋文章读取”，小程序页面后续由WorkBuddy推进。

## 当前材料入口

- [首批后端源码与启动说明](server/README.md)
- [文章读取接口文档](server/API.md)与[Postman集合](server/postman/CampusHub.postman_collection.json)
- [本轮开发决策与需求追踪](docs/BACKEND_BATCH1.md)
- [提前开展的基础验证](server/verification/2026-10-12.md)
- [第二阶段账号与校园邮箱认证](docs/BACKEND_BATCH2.md)、[身份接口](server/AUTH_API.md)和[实际验证](server/verification/2026-10-12-auth.md)
- [第三阶段作者资料与图文创作](docs/BACKEND_BATCH3.md)、[创作接口](server/AUTHORING_API.md)、[需求增量追踪](docs/BACKEND_BATCH3_TRACE.csv)和[实际验证](server/verification/2026-10-14-authoring.md)

- [教师邮件提交包](deliverables/SE2026-G04-项目计划交付包.zip)（67页PPT、项目计划、可行性报告及排期图表）

- [需求分析与规格说明入口](docs/REQUIREMENTS.md)
- [需求分析完整交付包](deliverables/城院帮需求分析材料G04.zip)
- [项目计划入口](docs/PROJECT_PLAN.md)
- [正式交付材料说明](deliverables/G04-项目计划/请先阅读.md)
- [项目计划PDF](deliverables/G04-项目计划/城院帮项目计划G04.pdf)与[Word](deliverables/G04-项目计划/城院帮项目计划G04.docx)
- [完整项目汇报PPT](deliverables/G04-项目计划/城院帮项目汇报G04.pptx)
- [可行性分析报告](deliverables/G04-项目计划/城院帮可行性分析报告G04.pdf)
- [详细周计划](deliverables/G04-项目计划/详细周计划G04.md)
- [豆包PPT提示词](deliverables/G04-项目计划/豆包PPT制作提示词.md)
- [产品范围](PRODUCT.md)

## 文件组织

| 位置 | 内容 |
| --- | --- |
| deliverables/G04-需求分析 | 需求报告、SRS、追踪表、待确认事项和可编辑模型 |
| deliverables/G04-项目计划 | 当前正式报告、PPT、排期和评审材料 |
| deliverables/G04-审查结果 | 项目计划及PPT审查记录 |
| docs | 计划入口与参考资料 |
| server | 本轮Java后端、迁移、测试、接口文档与验证记录 |
| planning-tools/G04 | 文档、图表和排期制作工具 |
| archive/项目历史资料-20261010.zip | 历史文档、旧汇报材料及历史验收记录 |
| archive/资料整理记录-20261010.json | 删除明细、归档文件及校验结果 |

## 当前产品方向

面向本校学生的免费实习与求职经验社区，支持岗位、专业和公司筛选、经验分享、评论交流、非现金激励与作者自愿站外联系。计划交付小程序、读者作者网页、社区管理网页和独立运维网页。

2026年10月12日用户确认本轮采用Java 21 / Spring Boot 4.1.1，不融合C++服务器。现有正式报告保留已形成的交付基线，正式设计评审、整体排期与完整测试计划仍按课程阶段确认；本轮开发和基础验证单独记录。

历史材料保留原相对路径和校验摘要；恢复时按需从归档包提取。实际会议记录与成员评价仍以双方确认的事实为准。

## 版本控制与团队协作

Git管理版本，GitHub用于协作，王晨负责仓库及合并协调。使用main与按任务命名的工作分支，格式为类型/姓名首字母-任务名。示例为feat/wc-email-auth、feat/ljh-web-editor；另有fix和docs类型。约定见[Git协作说明](docs/GIT_COLLABORATION.md)。会议原首字母方案与会后完善的规则分别记录。

## 当前AI开发工具

Codex/GPT辅助实现框架和具体代码，WorkBuddy用于小程序及相关页面，豆包用于PPT。模块负责人和交叉复核责任仍由两位学生承担。
