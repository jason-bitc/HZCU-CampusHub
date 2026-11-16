# 城院帮 HZCU CampusHub

G04，浙大城市学院软件工程课程项目。校园实习与求职经验社区，免费阅读、非现金激励、自愿站外联系。

## 当前团队入口

- [正式项目文档](docs/project/README.md)：项目计划、可行性、SRS、模型、原型、会议记录、PPT与原生MPP。
- [GitHub与SourceTree详细协作步骤](docs/GITHUB_COLLABORATION.md)。
- [前端技术变更](docs/FRONTEND_TECH_STACK.md)：原生微信小程序；独立Vue 3、TypeScript、Vite网页；Java/Spring Boot与MySQL后端。
- [后端运行与接口入口](server/README.md)、[管理端交接清单](docs/ADMIN_OPS_BACKEND_HANDOFF.md)。

两人直接维护`docs/project`，通过任务分支、提交、推送和Pull Request协作。`deliverables`只保留本机历史导出，不再作为共享文档编辑源；提交历史保存每次变化，不给当前文件不断追加“最终版”等名称。

## 当前材料与代码状态

正式材料及其版本以docs/project中的当前文件为准。资料含计划目标与尚待验证项目，不表示应用已整体验收。

2026年11月6日本任务从main的c5b57e7建立集成分支`codex/wc-admin-ops-integration`，引入399dcbb的后端模块，补齐社区治理、限制申诉和独立运维接口。原生小程序与网页沿用主分支现有工程，仅补充运维代理配置。集成分支经复核合并后main才包含这些后端更新；不按分支Ahead数判断是否已合并。

## 日常文档更新

1. 从已更新的main创建自己的任务分支，先确定本次文件负责人。
2. 修改`docs/project`中的当前文件，同步Word、PDF、Markdown与相关图表。
3. 在仓库根目录运行下列命令，或按详细指南通过SourceTree提交和推送。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Sync-ProjectDocs.ps1 -Commit -Push -Message "docs(wc): update SRS publishing requirements"
```

4. 在GitHub创建PR，由另一成员复核后，组长协调合并。
5. 双方拉取合并后的main。仅保存或仅本地Commit都不会同步给另一人。

脚本限定文档相关路径，不执行全仓库暂存、强推、自动合并或应用测试。GitHub Actions检查文件完整性和配套文件存在性；文档内容、排版和实际验收事实仍由两人复核。
