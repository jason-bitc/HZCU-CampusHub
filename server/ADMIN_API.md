# 社区管理员网页对接说明

业务服务前缀为 `/api/v1/admin`，默认 `http://127.0.0.1:8080`。普通校园用户使用 `/api/v1/me`。当前接口基线为迁移 V1—V12。

响应沿用 `code/data/traceId`；失败使用真实 HTTP 状态及 `code/message/traceId`，不返回 `data`。管理响应 `Cache-Control: no-store`。管理令牌通过 `Authorization: Bearer …` 传递，禁止放在URL。

## 页面与接口

| 页面 | 接口 | 主要行为 |
|---|---|---|
| 管理登录 | `POST /admin/auth/login`、`POST /admin/auth/logout`、`GET /admin/me` | 独立社区身份，不复用校园账号或运维令牌 |
| 工作台 | `GET /admin/overview` | OPEN、PROCESSING、NEEDS_ACTION案件数与隐藏文章数 |
| 举报和反馈 | `GET /admin/cases`、`GET /admin/cases/{id}` | 快照分页、状态及负责人；完整协议见 COMMUNITY_API.md |
| 案件处理 | `POST /admin/cases/{id}/claim`、`release`、`decisions` | 修订检查、成功幂等回执、公开答复与内部备注分离 |
| 原文和证据 | `GET /admin/cases/{id}/context`、`GET /admin/cases/{id}/media/{imageId}` | 案件限定的原公开版本和必要评论；不得借机读取草稿 |
| 文章巡查 | `GET /admin/articles`、`GET /admin/articles/{id}` | 仅已有公开版本的文章；可检查当前下架状态 |
| 评论巡查 | `GET /admin/articles/{id}/comments` | 该文章的评论，含当前状态和修订号 |
| 内容治理 | `POST /admin/articles/{id}/moderation`、`POST /admin/comments/{id}/moderation` | HIDE或RESTORE，有真实状态变更与事务审计 |
| 贡献治理 | `GET /admin/cases/{id}/contributions`、`POST /admin/cases/{id}/contributions/{eventId}/invalidation` | 在文章举报下定位投币、提交失效原因及查询审计。字段与错误见[贡献失效与追溯](COMMUNITY_API.md#贡献失效与追溯) |
| 分类维护 | `GET/POST /admin/categories`、`PUT /admin/categories/{id}` | 岗位、专业、公司、主题；创建、改名、排序、启停 |
| 治理记录 | `GET /admin/governance-events` | 对象、理由、操作者、前后状态、修订和时间 |
| 用户限制 | `GET /admin/users`、`GET/POST /admin/users/{id}/restrictions` | PUBLISH和COMMENT两类行为分别设置；不封锁阅读和本人申诉 |
| 申诉处理 | `GET /admin/appeals`、`POST /admin/appeals/{id}/decisions` | ACCEPTED或REJECTED；接受申诉只解除对应版本的限制 |
| 用户查看结果 | `GET /me/restrictions`、`GET/POST /me/appeals` | 仅本人限制和申诉，不公开他人信息 |

`/admin/me` 返回能力 `CASE_READ`、`CASE_HANDLE`、`CONTENT_MODERATE`、`CATEGORY_MANAGE`、`GOVERNANCE_READ`、`USER_RESTRICT`、`APPEAL_HANDLE`、`CONTRIBUTION_INVALIDATE`。社区管理员共享这些社区能力，贡献处置由案件认领人执行，运维能力单独授权。

## 参数及返回结构

巡查、分类、治理记录、用户和申诉列表使用 `page=1`、`size=20`，最大 `size=100`。返回 `items/total/page/size`。这些列表是当前状态分页，不承诺案件列表的历史快照语义。

文章巡查另可传 `state=PUBLISHED|AUTHOR_OFFLINE|ADMIN_HIDDEN|DELETED`；分类列表另可传 `kind=JOB|MAJOR|COMPANY|TOPIC`。未知、重复的分页或筛选参数拒绝。`/admin/articles/{id}` 返回 `id/state/revision/versionId/title/body/bodyFormat`，其中正文来自公开版本，不是编辑中的草稿。

隐藏和恢复请求示例：

```json
{"action":"HIDE","expectedRevision":0,"requestId":"00000000-0000-4000-8000-000000000001","reason":"已核对广告引流内容"}
```

返回 `targetId/state/revision`。文章使用独立的治理修订号，初值0；评论沿用评论修订号，初值1。必须读取当前值，不把文章草稿修订号当作治理修订号。恢复文章回到管理员隐藏前的PUBLISHED或AUTHOR_OFFLINE，不把作者主动下架的文章自动发布。历史隐藏数据没有保存恢复上下文时拒绝恢复，不猜测状态。

分类创建请求：

```json
{"kind":"JOB","name":"后端开发","enabled":true,"displayOrder":10,"expectedRevision":0,"requestId":"00000000-0000-4000-8000-000000000002","reason":"补充岗位分类"}
```

更新请求字段相同，`expectedRevision` 使用当前值；分类kind不可改变。停用不删除历史文章关系。当前没有同义词合并、历史关联批量映射或分类删除接口。

行为限制请求：

```json
{"scope":"COMMENT","enabled":true,"expectedRevision":0,"requestId":"00000000-0000-4000-8000-000000000003","reason":"经核对多次发布广告"}
```

返回 `restrictionId/revision/enabled`。初次设置使用0，以后使用该用户该scope的当前修订。PUBLISH限制同时约束发布及作者恢复上架，不影响草稿保存；COMMENT限制新评论和回复，不影响读取和本人申诉。解除限制不恢复隐藏文章或评论。限制与实际写入通过用户行锁串行校验，避免仅在页面禁用按钮。

用户申诉请求：

```json
{"restrictionId":1,"requestId":"00000000-0000-4000-8000-000000000004","reason":"请求复核对应处理"}
```

管理员决定：

```json
{"outcome":"ACCEPTED","expectedRevision":1,"requestId":"00000000-0000-4000-8000-000000000005","reply":"已复核并解除该项限制"}
```

同一限制版本不能有两个OPEN申诉。接受申诉时校验原限制版本，不能解除后来新设的限制。当前申诉用于上述行为限制；未来人工认证材料申诉不在本轮实现范围。

## 案件闭环

案件沿用 OPEN、PROCESSING、NEEDS_ACTION、CLOSED 及认领规则。举报以 `GOVERNANCE_COMPLETED` 闭案时，须已有针对目标且发生于举报后的 HIDE 事件，或已有直接关联本案的贡献失效记录。缺少处置依据时返回 409 `GOVERNANCE_NOT_RECORDED`。贡献失效在提交时更新有效贡献，闭案只记录案件结果。

## 幂等、失败与安全

- 每次实际动作生成小写UUID `requestId`。网络失败重试保留原请求及键；用户改变决定时生成新键。`traceId`只用于定位，不是幂等键。
- 同一管理员同一治理接口族，成功键重复返回原结果；改内容返回409 `REQUEST_KEY_REUSED`；旧修订返回409 `REVISION_CONFLICT`。前端应重新读取再请操作者确认，不盲目覆盖。
- 400非法字段、401社区会话无效、403行为受限、404对象不可访问、409状态冲突、503数据库/审计不可用分别展示。失败不提前移除列表项或显示“已完成”。
- 管理JSON字段严格按示例；身份、操作人及时间由服务端确定。文本理由和内部资料只返回给对应授权者，不进入运行日志。
- 分类、治理及限制均使用新增表/列，保留既有迁移。治理与审计事件同事务，审计失败回滚实际变更。

## 前端联调顺序

先完成管理员登录与工作台，再接案件列表/详情/上下文，然后巡查和治理、分类、限制与申诉。所有页面均处理加载、空列表、401退出、409刷新、503重试。前端不得生成“处理成功”的本地替代数据。

社区账号初始化见 [COMMUNITY_ADMIN_SETUP.md](COMMUNITY_ADMIN_SETUP.md)。接口不提供公开管理员注册。正式用户走查、管理网页联调、浏览器兼容和真机验收单独记录，后端自动化通过不代替这些验收。
