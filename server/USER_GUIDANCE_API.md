# 双端规则与写作提示 API

规则说明对应 FR-P03，写作提示对应 FR-C09。网页和小程序使用同一份内容。

| 方法与路径 | 访问条件 | data |
| --- | --- | --- |
| GET /api/v1/rules | 公开 | reading、certification、contact、coins、hints |
| GET /api/v1/me/writing-prompts | 有效普通账号会话、账号 ACTIVE、校园认证 VALID | optional、items |

两条接口均无查询参数和正文。响应沿用 `code/data/traceId`。写作提示使用本人接口的 `Cache-Control: no-store`。

## 规则与状态提示

`reading.free` 表示公开文章免费阅读。`certification` 提供邮箱域和验证方式。`contact.voluntary` 与 `contact.external` 表示自愿站外联系。`coins.valueType` 为 `NON_CASH`，`coins.rules` 与 `GET /api/v1/me/rewards` 的当前规则结构及数值一致。是否已领取和何时可领由本人奖励接口返回。

`hints` 以状态码为键，值为 `{message,nextAction}`。页面按实际结果选用提示。

| 实际结果 | 提示键 | 页面行为 |
| --- | --- | --- |
| 文章检索成功且 total=0 | NO_ARTICLE_RESULTS | 保留当前条件，提供修改与重置入口 |
| 请求未取得服务器响应 | NETWORK_ERROR | 保留当前输入与条件，提供重试 |
| 登录会话无效 | UNAUTHORIZED | 提供登录入口 |
| 账号或认证状态受限 | ACCESS_RESTRICTED | 引导查看本人账号或校园认证状态 |
| 发布或评论受限 | BEHAVIOR_RESTRICTED | 引导查看本人处理记录 |
| 内容不可用或不可访问 | NOT_FOUND | 提示不可用并返回列表 |
| 数据库、存储或邮件暂不可用 | SERVICE_UNAVAILABLE、STORAGE_UNAVAILABLE、MAIL_UNAVAILABLE | 保留输入，按接口约定重试 |
| 奖励或投币条件未满足 | LOGIN_REQUIRED_TODAY、READING_NOT_READY、REWARD_ALREADY_CLAIMED、INSUFFICIENT_COINS、ARTICLE_COIN_LIMIT | 按 nextAction 引导登录、阅读、查看奖励或投币状态 |

写入重试遵循原业务接口的请求编号与修订要求。搜索末页之外可能出现 `items=[]` 且 `total>0`，页面应调整页码。提示中的 `nextAction` 为页面动作标识，由客户端映射到具体入口。

## 写作提示

`optional=true`。每条提示包含 `id`、`title`、`question`，涵盖当时基础、准备过程、投递与面试、结果复盘及资料来源。编辑器可按需展开，展开和关闭均保留已输入正文。草稿和发布继续使用原接口字段。
