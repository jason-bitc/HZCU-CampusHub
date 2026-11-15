# 社区管理员网页对接说明

业务服务前缀为 `/api/v1/admin`，默认 `http://127.0.0.1:8080`。普通校园用户使用 `/api/v1/me`。校园认证撤销和申诉分别使用 V15、V16 数据库迁移。

响应沿用 `code/data/traceId`；失败使用真实 HTTP 状态及 `code/message/traceId`，不返回 `data`。管理响应 `Cache-Control: no-store`。管理令牌通过 `Authorization: Bearer …` 传递，禁止放在URL。

## 页面与接口

| 页面 | 接口 | 主要行为 |
|---|---|---|
| 管理登录 | `POST /admin/auth/login`、`POST /admin/auth/logout`、`GET /admin/me` | 独立社区身份，不复用校园账号或运维令牌 |
| 工作台 | `GET /admin/overview` | OPEN、PROCESSING、NEEDS_ACTION案件数与隐藏文章数 |
| 举报和反馈 | `GET /admin/cases`、`GET /admin/cases/{id}` | 快照分页、状态及负责人；完整协议见 COMMUNITY_API.md |
| 案件审计 | `GET /admin/cases/{id}/audit` | 分页查看案件流转、明确关联的内容处置与投币失效 |
| 案件处理 | `POST /admin/cases/{id}/claim`、`release`、`decisions` | 修订检查、成功幂等回执、公开答复与内部备注分离 |
| 原文和证据 | `GET /admin/cases/{id}/context`、`GET /admin/cases/{id}/media/{imageId}` | 案件限定的原公开版本和必要评论；不得借机读取草稿 |
| 文章巡查 | `GET /admin/articles`、`GET /admin/articles/{id}` | 仅已有公开版本的文章；可检查当前下架状态 |
| 评论巡查 | `GET /admin/articles/{id}/comments` | 该文章的评论，含当前状态和修订号 |
| 内容治理 | `POST /admin/articles/{id}/moderation`、`POST /admin/comments/{id}/moderation` | HIDE或RESTORE，有真实状态变更与事务审计 |
| 贡献治理 | `GET /admin/cases/{id}/contributions`、`POST /admin/cases/{id}/contributions/{eventId}/invalidation` | 在文章举报下定位投币、提交失效原因及查询审计。字段与错误见[贡献失效与追溯](COMMUNITY_API.md#贡献失效与追溯) |
| 分类维护 | `GET/POST /admin/categories`、`PUT /admin/categories/{id}`、`POST /admin/categories/{id}/merge` | 创建、改名、排序、启停及同类标签归并 |
| 治理记录 | `GET /admin/governance-events` | 对象、理由、操作者、前后状态、修订和时间 |
| 用户限制 | `GET /admin/users`、`GET/POST /admin/users/{id}/restrictions` | PUBLISH和COMMENT两类行为分别设置；不封锁阅读和本人申诉 |
| 校园认证 | `GET /admin/users/{id}/certification`、`POST /admin/users/{id}/certification/revocation` | 查看认证状态、修订号和最近撤销记录，提交撤销理由 |
| 申诉处理 | `GET /admin/appeals`、`POST /admin/appeals/{id}/decisions` | 处理行为限制与校园认证两类申诉，记录结论及答复 |
| 用户查看结果 | `GET /me/restrictions`、`GET/POST /me/appeals`、`GET /me/appeals/{id}` | 有效会话可查看本人的限制、申诉和复核答复 |

`/admin/me` 返回能力 `CASE_READ`、`CASE_HANDLE`、`CONTENT_MODERATE`、`CATEGORY_MANAGE`、`GOVERNANCE_READ`、`USER_RESTRICT`、`CERTIFICATION_REVOKE`、`APPEAL_HANDLE`、`CONTRIBUTION_INVALIDATE`。社区管理员共享这些社区能力，贡献处置由案件认领人执行，运维能力单独授权。

## 参数及返回结构

巡查、分类、治理记录、用户和申诉列表使用 `page=1`、`size=20`，最大 `size=100`。返回 `items/total/page/size`。这些列表是当前状态分页，不承诺案件列表的历史快照语义。

文章巡查另可传 `state=PUBLISHED|AUTHOR_OFFLINE|ADMIN_HIDDEN|DELETED`；分类列表另可传 `kind=JOB|MAJOR|COMPANY|TOPIC`。未知、重复的分页或筛选参数拒绝。`/admin/articles/{id}` 返回 `id/state/revision/versionId/title/body/bodyFormat`，其中正文来自公开版本，不是编辑中的草稿。

隐藏和恢复请求示例：

```json
{"action":"HIDE","expectedRevision":0,"requestId":"00000000-0000-4000-8000-000000000001","reason":"已核对广告引流内容"}
```

返回 `targetId/state/revision`。文章使用独立的治理修订号，初值0；评论沿用评论修订号，初值1。必须读取当前值，不把文章草稿修订号当作治理修订号。恢复文章回到管理员隐藏前的PUBLISHED或AUTHOR_OFFLINE，不把作者主动下架的文章自动发布。历史隐藏数据没有保存恢复上下文时拒绝恢复，不猜测状态。

从举报处理页发起隐藏或恢复时，可同时提供 `caseId` 和 `expectedCaseRevision`。该举报须与内容是同一对象，处于处理中或待治理，且由发起操作的管理员认领。成功回执补充 `caseId/caseRevision`，治理记录也保留这两个字段。案件与内容的修订号分别校验。日常巡查可沿用原请求格式。

分类创建请求：

```json
{"kind":"JOB","name":"后端开发","enabled":true,"displayOrder":10,"expectedRevision":0,"requestId":"00000000-0000-4000-8000-000000000002","reason":"补充岗位分类"}
```

更新请求字段相同，`expectedRevision` 使用当前值，分类 `kind` 固定。停用后仍保留历史文章关系。管理列表中的 `canonicalId` 为当前归入的分类 ID，未归并时为 `null`。

### 同义分类归并

向 `POST /admin/categories/{id}/merge` 提交待归并分类的 ID 和以下字段。归入的分类须属于同一类型，当前启用，并且是组内保留的分类。

```json
{"canonicalId":12,"expectedRevision":1,"expectedCanonicalRevision":0,"requestId":"00000000-0000-4000-8000-000000000008","reason":"两个专业标签指向同一专业"}
```

成功返回 `targetId/state/revision/canonicalId`，其中 `targetId` 是被归并的旧分类，状态为 `MERGED`。重复提交相同请求可取得原回执。两边修订有变化时返回 409 `REVISION_CONFLICT`。已归并的分类作为归并来源或编辑对象时返回 `CATEGORY_ALREADY_MERGED`，已停用或已归并的分类作为目标时返回 `CATEGORY_TARGET_UNAVAILABLE`。

若被保留的分类后来再归并，组内旧 ID 一起指向最终保留的分类。公开目录展示启用的保留分类，使用任一组内 ID 筛选均可找到同一组公开文章，文章和标签的原始关联保留。治理记录中的 `MERGE` 事件保存当次来源、目标、理由、操作者和时间。部署需应用 V17 数据库迁移。

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

同一行为限制版本只可同时存在一条待处理申诉。接受后校验原限制版本并解除对应限制。

## 校园认证申诉

用户先获取 `GET /me/certification`。当返回 `canAppeal=true` 时，将响应中的 `revision` 作为 `certificationRevision` 提交至 `POST /me/appeals`。

```json
{"certificationRevision":2,"requestId":"00000000-0000-4000-8000-000000000007","reason":"校园邮箱仍可使用，申请复核本次撤销"}
```

请求包含一种申诉对象，校园认证用 `certificationRevision`，行为限制用 `restrictionId`。首次提交返回 `id/revision/state`，初始状态为 `OPEN`。相同请求编号和内容重试时返回该申诉的当前状态。变更原请求内容返回 409 `REQUEST_KEY_REUSED`。认证有效、认证修订过期或该撤销版本已有申诉时，分别返回 409 `CERTIFICATION_NOT_REVOKED`、`REVISION_CONFLICT`、`CERTIFICATION_APPEAL_EXISTS`。

管理员在同一申诉列表处理两类申诉，通过 `subject` 区分 `CAMPUS_CERTIFICATION` 和 `BEHAVIOR_RESTRICTION`。认证申诉显示绑定的认证修订，以及有记录时的 `certificationRevocationReason/certificationRevokedAt`。管理列表另返回 `userId/decidedBy`。认证复核的 `ACCEPTED` 或 `REJECTED` 及 `reply` 随原申诉保存，更新本人通知。复核和邮箱验证可独立完成，恢复认证权限须经本人重新认证。已完成的申诉可继续查询。

## 校园认证撤销

先用 `GET /admin/users/{id}/certification` 读取 `userId/state/revision/verifiedAt/lastRevocation`。有撤销记录时，`lastRevocation` 提供撤销时的 `revision/adminId/reason/occurredAt`，包括用户后来重新认证成功的情形。

`POST /admin/users/{id}/certification/revocation` 接收以下字段。

```json
{"expectedRevision":1,"requestId":"00000000-0000-4000-8000-000000000006","reason":"校园邮箱归属需重新核验"}
```

成功响应为 `userId/state/revision`，状态为 `REVOKED`。相同管理员和请求编号可重读历史回执，当前状态以 GET 为准。修改原请求内容返回 `REQUEST_KEY_REUSED`，旧修订返回 `REVISION_CONFLICT`，已撤销的当前修订返回 `CERTIFICATION_ALREADY_REVOKED`，均为 HTTP 409。

认证更新、理由及操作者记录、站内通知和业务审计在同一事务保存。账号状态及原内容保持原状。具备有效会话的本人可查看认证结果，并通过原校园邮箱重新认证。管理员入口用于撤销，恢复通过[本人重新认证](AUTH_API.md#校园邮箱重新认证)执行。

## 案件闭环

案件沿用 OPEN、PROCESSING、NEEDS_ACTION、CLOSED 及认领规则。举报以 `GOVERNANCE_COMPLETED` 闭案时，可使用明确关联本案的 HIDE 事件或贡献失效记录。未绑定案件的巡查 HIDE 事件沿用原有的同一目标、发生于举报提交后规则。已经绑定其他案件的处置只用于其关联案件。缺少处置依据时返回 409 `GOVERNANCE_NOT_RECORDED`。贡献失效在提交时更新有效贡献，闭案记录案件结果。

案件审计支持 `page/size`。响应提供案件当前状态、修订、被举报对象和归属于本案的审计条目。历史巡查如未绑定案件，仍可从治理记录查看。部署需应用 V18 数据库迁移。[案件审计响应字段](COMMUNITY_API.md#案件审计)

## 幂等、失败与安全

- 每次实际动作生成小写UUID `requestId`。网络失败重试保留原请求及键；用户改变决定时生成新键。`traceId`只用于定位，不是幂等键。
- 同一管理员同一治理接口族，成功键重复返回原结果；改内容返回409 `REQUEST_KEY_REUSED`；旧修订返回409 `REVISION_CONFLICT`。前端应重新读取再请操作者确认，不盲目覆盖。
- 400非法字段、401社区会话无效、403行为受限、404对象不可访问、409状态冲突、503数据库/审计不可用分别展示。失败不提前移除列表项或显示“已完成”。
- 管理JSON字段严格按示例；身份、操作人及时间由服务端确定。文本理由和内部资料只返回给对应授权者，不进入运行日志。
- 分类、治理及限制均使用新增表/列，保留既有迁移。治理与审计事件同事务，审计失败回滚实际变更。

## 前端联调顺序

先完成管理员登录与工作台，再接案件列表/详情/上下文，然后巡查和治理、分类、限制与申诉。所有页面均处理加载、空列表、401退出、409刷新、503重试。前端不得生成“处理成功”的本地替代数据。

社区账号初始化见 [COMMUNITY_ADMIN_SETUP.md](COMMUNITY_ADMIN_SETUP.md)。接口不提供公开管理员注册。正式用户走查、管理网页联调、浏览器兼容和真机验收单独记录，后端自动化通过不代替这些验收。
