> 2026年10月10日整理说明：项目现位于 C:\Users\wangc\Desktop\ZUCC-CampusHub。历史输入和制作检查记录已集中打包为 archive/项目历史资料-20261010.zip，包内保留原相对路径。需要重新制作时，先提取所需历史输入并核对脚本中的工程路径。当前正式交付材料已经保留。

# G04文档与图表制作工具

这些工具生成规划材料与排期图，不修改产品代码，也不执行真实业务测试。

## 输入与输出

正文和细节来源保存在`archive/项目计划历史/20261001/deck4/round4/materials`；任务责任与集中开发安排来源保存在`archive/项目计划历史/20261001/deliverables/G04-project-plan-20261001`。原资料保持可恢复。

当前输出统一为`deliverables/G04-项目计划`。任务数据、95项子任务、14周安排及Project条目为各报告和图表的共同数据源。日期与工期以原生MPP及计算核查为准。

## 使用顺序

1. `fuse_g04_data.py`生成共同任务数据、周计划及Project XML源。
2. `fuse_native_project.ps1`调用本机Microsoft Project生成MPP、实际任务资源核查与甘特PDF。
3. `fuse_g04_charts.py`绘制阶段甘特、箭线式工程网络、计算结果及draw.io源。
4. `fuse_g04_documents.py`生成项目计划与可行性Word及Markdown。
5. `export_fused_documents.ps1`使用本机Word更新目录并导出PDF。
6. `fuse_g04_prompt.py`生成豆包PPT提示词与完整周计划附录。
7. `render_pdf_windows.ps1`将指定PDF逐页渲染为检查图片，逐页检查后再交付。
8. `verify_and_package.py`核对任务日期、工期、时差、资源与文件摘要，提交文档Git基线并生成恢复bundle和交付ZIP。

使用Codex提供的Python与Node运行时、本机Word和Microsoft Project。当前Graphviz绘图依赖保存在`.codex-build/node_modules`，制作及分页检查文件保存在`.codex-build/fused-qa`。脚本使用本机工程绝对路径，搬迁目录时先更新ROOT和运行时位置。重新制作会覆盖当前输出，应先提交文档版本或复制归档。

任务负责人是学生。资源占用用于排期冲突检查，与成员贡献比例无关。生成与静态核查不能代替真实运行、会议记录或成员评价。

当前订阅预算见“图表与排期/订阅预算数据.json”。Project导入后通过固定成本字段明确写入预算，并检查数值和显示值，避免金额单位转换影响最终MPP。报告采用A4、正文12pt、表格10.5pt；Word负责更新目录和统一PDF导出。


## 可行性报告维护

可行性正文源为feasibility_content.md。完整项目计划生成后，先执行improve_feasibility.py以保留研究内容和3张模型图，再执行export_feasibility.ps1更新Word目录和导出PDF。使用本机Word导出并将PDF逐页渲染检查；seal_feasibility.py核对教师16项、技术8项、版式及原计划排期未被改变，最后执行verify_and_package.py更新恢复资料与交付包。当前报告26页，其他计划文件不因本次内容优化重排。
