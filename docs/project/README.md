# 城院帮正式项目文档

本目录是团队共同维护的当前文档入口。2026年11月5日从11月4日需求阶段最终成果接入Git，导入映射见[IMPORT.json](IMPORT.json)。历史交付包、原始课件与国标扫描件继续保留本地，不重复提交。

|目录|内容与主要源文件|
|---|---|
|[reports](reports)|项目计划V1.8、可行性V1.6、统一SRS V1.5.1，含Word、Markdown和PDF；需求分析过程和完整模型均在同一SRS正文|
|[models](models)|ER、DFD、STD、IPO、功能层次、字典、正则、追踪及可编辑draw.io/dot|
|[schedule](schedule)|WBS、原生MPP、甘特图、工程网络、任务日期及人时预算|
|[prototypes](prototypes)|现有概念稿、功能线框和全需求原型；用途与覆盖记录分别保留|
|[meetings-research](meetings-research)|10月31日与11月5日会议、91.2／91.3综合评价、11月5日分项初评、反馈记录和网络调研来源|
|[presentations](presentations)|需求汇报PPT、PDF、讲稿及豆包调整提示词|

## 总体设计阶段

| 文档 | PDF | Word | Markdown |
| --- | --- | --- | --- |
| 总体设计报告 V1.2.1 | [PDF](reports/城院帮总体设计报告G04.pdf) | [Word](reports/城院帮总体设计报告G04.docx) | [Markdown](reports/城院帮总体设计报告G04.md) |
| 系统说明 V1.0 | [PDF](reports/城院帮系统说明G04.pdf) | [Word](reports/城院帮系统说明G04.docx) | [Markdown](reports/城院帮系统说明G04.md) |
| 用户手册 V1.1 | [PDF](reports/城院帮用户手册G04.pdf) | [Word](reports/城院帮用户手册G04.docx) | [Markdown](reports/城院帮用户手册G04.md) |
| 系统测试计划 V2.2 | [PDF](reports/城院帮系统测试计划G04.pdf) | [Word](reports/城院帮系统测试计划G04.docx) | [Markdown](reports/城院帮系统测试计划G04.md) |
| 详细实现计划 V1.1 | [PDF](reports/城院帮详细实现计划G04.pdf) | [Word](reports/城院帮详细实现计划G04.docx) | [Markdown](reports/城院帮详细实现计划G04.md) |
| 数据库设计结果 V1.0 | [PDF](reports/城院帮数据库设计结果G04.pdf) | [Word](reports/城院帮数据库设计结果G04.docx) | [Markdown](reports/城院帮数据库设计结果G04.md) |

- [可编辑总体设计图](models/总体设计G04/城院帮总体设计图G04.drawio)与对应图像。
- [课件与页码对照](models/总体设计G04/总体设计课件与页码对照G04.csv)、[实现阶段评审证据](models/总体设计G04/实现阶段评审证据对照G04.csv)。
- [DFD 处理分解](models/总体设计G04/DFD处理与程序分解G04.csv)、[详细实现任务与交接](models/总体设计G04/详细实现任务与交接G04.csv)。
- [物理字段字典](models/总体设计G04/物理字段字典G04.csv)、[物理键约束和索引](models/总体设计G04/物理键约束和索引G04.csv)。

## 详细设计阶段

[详细设计报告 V1.0 PDF](reports/城院帮详细设计报告G04.pdf) · [Word](reports/城院帮详细设计报告G04.docx) · [Markdown](reports/城院帮详细设计报告G04.md)

- [可编辑设计图](models/详细设计G04/城院帮详细设计图G04.drawio) · [第 6 章课件覆盖与页码](models/详细设计G04/课件覆盖与页码G04.csv)
- [需求与设计追踪](models/详细设计G04/需求与设计追踪G04.csv) · [构件反向追踪](models/详细设计G04/构件反向追踪G04.csv) · [基本路径复核](models/详细设计G04/基本路径复核G04.csv) · [修订登记](models/详细设计G04/详细设计修订记录G04.csv)

## V1.8 项目计划与实现阶段

[项目计划 PDF](reports/城院帮项目计划G04.pdf) · [Word](reports/城院帮项目计划G04.docx) · [Markdown](reports/城院帮项目计划G04.md)

- 课程周期为 2026 年 9 月 16 日至 2027 年 1 月 6 日。[16 个课程节点与提交对照](schedule/课程里程碑对照G04.csv)
- [原生 MPP](schedule/城院帮项目计划G04.mpp) · [Project 详细甘特图](schedule/Project详细甘特图G04.pdf) · [分阶段阅读图](schedule/分阶段详细甘特图G04.pdf)
- [设计与实现测试甘特](schedule/设计与实现测试甘特G04.pdf) · [WBS 树](schedule/WBS树状结构图G04.pdf) · [工程网络](schedule/工程网络与阶段图表G04.pdf)
- [详细周计划](schedule/详细周计划G04.pdf) · [周资源负荷](schedule/周资源负荷G04.csv) · [计划评审页码](schedule/计划阶段评审页码对照G04.csv) · [实现评审证据](models/总体设计G04/实现阶段评审证据对照G04.csv)

| 测试文档 | PDF | Word | Markdown |
| --- | --- | --- | --- |
| 系统测试计划 | [PDF](reports/城院帮系统测试计划G04.pdf) | [Word](reports/城院帮系统测试计划G04.docx) | [Markdown](reports/城院帮系统测试计划G04.md) |
| 单元与白盒测试用例 | [PDF](reports/城院帮单元与白盒测试用例G04.pdf) | [Word](reports/城院帮单元与白盒测试用例G04.docx) | [Markdown](reports/城院帮单元与白盒测试用例G04.md) |
| 集成与系统测试用例 | [PDF](reports/城院帮集成与系统测试用例G04.pdf) | [Word](reports/城院帮集成与系统测试用例G04.docx) | [Markdown](reports/城院帮集成与系统测试用例G04.md) |
| 测试结果缺陷与复测记录 | [PDF](reports/城院帮测试结果缺陷与复测记录G04.pdf) | [Word](reports/城院帮测试结果缺陷与复测记录G04.docx) | [Markdown](reports/城院帮测试结果缺陷与复测记录G04.md) |
| 目标环境与用户验收记录 | [PDF](reports/城院帮目标环境与用户验收记录G04.pdf) | [Word](reports/城院帮目标环境与用户验收记录G04.docx) | [Markdown](reports/城院帮目标环境与用户验收记录G04.md) |

[用例追踪、历史证据、登记表与白盒源图](models/实现测试G04)

目录支持按章和评审事项跳转。可行性八步骤、总体设计九步、测试取证和详细周计划的评审节点均可从二级目录查阅。汇报引用采用 PDF 页码，Word 目录按本机分页更新。

## 更新约定

- 直接编辑本目录文件，不再把修改只留在本机`deliverables`或重新上传一个ZIP。
- 正式报告以Word为排版源，Markdown供差异审阅，PDF供阅读和提交。先约定本次正文编辑源，再同步其余格式，避免两种正文分别修改。
- 修改图表时同时维护可编辑源与展示文件；影响需求时同步字典和追踪，影响排期时同步任务源、MPP和甘特。
- 修改Word、PPT、MPP前在PR或任务中说明负责人。同一二进制文件同一时段由一人编辑，另一人复核。
- 会议与用户调研按实际记录；当前没有真实用户访谈与任务走查，不从计划推算完成、人时或评分分项。
- V1.8 计划 275.85 人时，原生 Project 仍提示过载。周资源负荷列明需追加的投入，实际由周会及日志确认。

[完整操作指南](../GITHUB_COLLABORATION.md)。所有共享文件的校验记录为`MANIFEST.sha256.csv`，刷新方法见指南。目录内旧任务记录引用的历史版本作为输入保留，不等于当前文件版本。

编制依据：GB/T 8567—2006、GB/T 9385—2008以及软件工程课程需求阶段课件与评审表；相应原始文件保存在组长本机参考文档目录。仓库只接入本组成果，不重新分发原始教材和课件。

## 周会模板

11月5日修正会议纪要封面与结构；[可填写模板及结构说明](templates/模板结构与使用说明.md)已入库。当前10月31日纪要为V1.3，空白模板为V1.1。


## 统一需求主文档

需求阶段主件为[城院帮软件需求规格说明书G04.pdf](reports/城院帮软件需求规格说明书G04.pdf)，[Word](reports/城院帮软件需求规格说明书G04.docx)可编辑。第6章ER、第7章DFD、第8章完整数据字典、第11章状态图、第13章非功能需求及附录B评审对应均可通过目录跳转。原两份独立过程/模型报告已合并，旧原件从Git提交7ffe55a追溯；CSV和可编辑图保留为附件。

[11项评审与PDF页码定位](models/统一SRS评审条目定位G04.csv)。

ER图使用1:1、1:N、M:N；[28项联系与参与约束](models/ER联系与参与约束G04.csv)与SRS表6-1同步。
