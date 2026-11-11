# 城院帮 PowerDesigner 模型转换

2026年11月8日。与统一SRS的数据模型配套。

| 原生文件 | 查阅内容 |
| --- | --- |
| [概念数据模型 CDM](城院帮概念数据模型G04.cdm) | 27个实体、134个字段、28条概念联系。关系属性记录两端参与数量与业务约束。 |
| [逻辑数据模型 LDM](城院帮逻辑数据模型G04.ldm) | 由 PowerDesigner 原生生成，再校核连接字段。复用 MediaRef 和 Reaction，保留27个实体、134个字段与30条逻辑联系。 |
| [MySQL 物理模型 PDM 候选](城院帮MySQL物理模型候选G04.pdm) | 由 LDM 原生生成，包含27张候选表及30条引用，适合查看表、列和引用的转换结果。 |

[ER图与数据结构 PDF](城院帮ER图与数据结构G04.pdf)包含13张逻辑分区图、134个字段及11项结构式和9条格式规则。

桌面端可在 CDM 中选择 **Tools → Generate Logical Data Model**，在 LDM 中选择 **Tools → Generate Physical Data Model**。转换后校核关联实体、已有字段、逻辑标识及引用条件。本机16.5的 MySQL 资源为 MySQL 5.0。PDM 候选用于设计复核，实际库表以 MySQL 8 的迁移与验证记录为准。作者荣誉主标识、邮件挑战的注册前可选关联及物理列类型需在设计复核中确认。

[参与约束](../ER联系与参与约束G04.csv) · [数据元素字典](../数据元素字典G04.csv) · [结构与格式规则](../数据结构与正则规则G04.csv)

[SAP PowerDesigner 原生模型生成说明](https://help.sap.com/docs/SAP_POWERDESIGNER/abd3434b4987485c92057ab9392aadbe/c7e23f946e1b1014a934d77198144b68.html)
