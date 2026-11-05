# 城院帮 HZCU CampusHub

G04，浙大城市学院软件工程课程项目。校园实习与求职经验社区，免费阅读、非现金激励、自愿站外联系。

## 当前团队入口

- [正式项目文档](docs/project/README.md)：项目计划、可行性、SRS、模型、原型、会议记录、PPT与原生MPP。
- [GitHub与SourceTree详细协作步骤](docs/GITHUB_COLLABORATION.md)。
- [前端技术变更](docs/FRONTEND_TECH_STACK.md)：原生微信小程序；独立Vue 3、TypeScript、Vite网页；Java/Spring Boot与MySQL后端。

两人直接维护`docs/project`，通过任务分支、提交、推送和Pull Request协作。`deliverables`只保留本机历史导出，不再作为共享文档编辑源；提交历史保存每次变化，不给当前文件不断追加“最终版”等名称。

## 当前材料与代码状态

本次接入2026年11月4日需求阶段最终材料：计划V1.7、可研V1.6、SRS V1.4、分析V1.6、模型V1.3；10月31日会议纪要已于11月5日按模板修订为V1.3。资料含计划目标与尚待验证项目，不表示应用已整体验收。

2026年11月5日检查时，远端main只有初始提交；已有后端位于`feat/wc-community-foundation`，参考提交`399dcbb0009fa9ae4ac557fb7e9296775fcd7be9`。本次文档PR不合并后端代码。取得后端源码时先核对相应分支，代码合并另行交叉复核，不按分支Ahead数决定合并顺序。

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
