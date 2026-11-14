# 举报反馈与社区管理API

2026-11-06扩展：当前社区管理员巡查、治理、分类、行为限制和申诉见[ADMIN_API.md](ADMIN_API.md)。下文保留基础案件协议；GOVERNANCE_COMPLETED结论及当前能力按扩展说明执行。

2026-10-22，第九阶段。前缀/api/v1，保留code/data/traceId和X-Trace-Id；成功JSON为HTTP200，失败有固定HTTP状态、code和message，无data。私人和管理响应均no-store。权限、状态机与分页见[COMMUNITY_RULES.md](COMMUNITY_RULES.md)，本机管理账号工具见[初始化说明](COMMUNITY_ADMIN_SETUP.md)。

## 普通校园用户

须有效普通USER会话、ACTIVE账号与VALID认证；管理令牌不能用于这些入口。未知身份参数和多余字段拒绝。

| 方法与路由 | 输入及结果 |
| --- | --- |
| POST /me/cases | application/json；提交后返回caseId、revision:1、state:OPEN |
| GET /me/cases | page默认1、size默认20／最大100、snapshot可选；本人快照列表 |
| GET /me/cases/{id} | 本人提交内容、附件、目标最小标识、当前状态、公开处理历史 |
| POST /me/case-images | multipart，恰有requestId和file两个part；返回id、mime、sizeBytes、width、height |
| GET /media/case-images/{id} | 仅上传者读取，不因案件绑定而向其他普通用户公开 |

提交JSON必须恰含以下七项。无目标的反馈也明确传两个null。

```json
{
  "requestId": "小写UUID",
  "kind": "REPORT",
  "category": "ADVERTISEMENT",
  "targetType": "ARTICLE",
  "targetId": 1001,
  "description": "说明具体问题",
  "imageIds": []
}
```

kind为FEEDBACK或REPORT。FEEDBACK类别BUG／SUGGESTION／OTHER，可无目标或关联ARTICLE／COMMENT；REPORT类别ADVERTISEMENT／MISLEADING_CONTENT／OTHER，必须有可见目标。targetId为相应文章或评论正整数ID。articleId、commentId和提交时公开版本由服务端固定，客户端不能传articleVersionId、userId、status、assignee或角色。

说明不能为空白、无最低字数，CRLF统一LF；默认最多10000 Unicode码点，可配置。imageIds是按顺序排列的不重复小写UUID数组，可为空，无固定业务张数上限。JSON默认2MiB，单文件大小／像素／multipart技术边界沿用媒体设置。图必须由本人经本模块上传、未绑定且文件可校验。未知或他人图404，已绑定其他案件409 IMAGE_ALREADY_USED。文章／评论图片UUID不可当证据借用。

本人详情只包含本人description、images及公开history。目标只有targetType/articleId/commentId，不返回原文、原评论、曾公开版本、原文图片、内部备注、管理员名称／身份等。自己的说明和证据在原目标下架后仍可查看；他人案件404。上传成功不等同案件提交成功。

## 独立社区管理

登录请求不携带普通校园身份，也不创建管理员。

| 方法与路由 | 输入及结果 |
| --- | --- |
| POST /admin/auth/login | application/json，恰含login、password；返回Bearer、accessToken、expiresAt |
| POST /admin/auth/logout | 无参数／正文；退出当前管理会话 |
| GET /admin/me | adminId、actorDomain:COMMUNITY_ADMIN、capabilities，含 CONTRIBUTION_INVALIDATE |
| GET /admin/cases | page、size、snapshot、kind、state；按历史快照筛选全部案件 |
| GET /admin/cases/{id} | 当前案件、提交者内部ID、受理人内部ID、证据、完整处理时间线及提交时版本ID |
| GET /admin/cases/{id}/context | 案件固定的原公开文章版本、必要评论快照、当前文章状态、关联媒体ID |
| GET /admin/cases/{id}/media/{imageId} | 只读本案证据或本案保存的原公开目标图片 |
| POST /admin/cases/{id}/claim | expectedRevision、requestId |
| POST /admin/cases/{id}/release | expectedRevision、requestId、reason非空 |
| POST /admin/cases/{id}/decisions | expectedRevision、requestId、outcome、reply非空、internalNote可为空字符串 |

所有管理写JSON字段恰按表列出。revision为正整数，requestId小写UUID；管理主体和负责人均由当前会话决定。成功流转返回最小caseId、revision、state。内部备注和释放理由仅在管理历史中出现，公开reply与它们分开。管理详情没有邮箱、密码、令牌、认证材料或私人账本。

管理上下文的articleVersionId固定为举报时的公开版本，后续改稿、下架、隐藏或软删除不改成当前草稿。评论快照含TARGET／ROOT／REPLY_TO角色；提交时不可见的必要父上下文只保存标识和available:false，body为空。context.articleAvailable标明保存版本能否读取，已不存在的真实资料不补造。media列出本案准许的image ID及来源，客户端应使用本案/admin/cases/{id}/media/{imageId}构造图片请求，不依赖旧公开链接访问下架内容。该入口不浏览其他图片或任意版本。

管理login仅允许小写字母、数字、点、短横线、下划线，3—64字符且首字符为字母或数字；密码沿用8—64字符且UTF-8不超过72字节，BCrypt cost12。管理令牌带adm_域前缀、只存摘要，固定8小时有效；退出、过期、停用或本机重设失效。每个规范登录名的摘要对应持久失败窗口，15分钟最多5次失败，第6次起429；未知和既有账号均按同样规则和通用错误处理。

## 幂等与稳定分页

普通提交键在同一用户范围唯一。规范化内容、目标和图片顺序相同，重复／并发／重启只产生一个案件；成功后目标不可用仍返回原OPEN／1回执。换内容409 REQUEST_KEY_REUSED。失败未提交无成功记录，可原键重试。

管理键在同一管理员范围唯一并包含动作、caseId、expectedRevision及全部语义字段。同键相同请求返回原成功回执；改变任何内容409。新键使用旧revision为409 REVISION_CONFLICT。相同管理员已认领PROCESSING案件时，新键及当前revision可以返回无变化回执，不增加历史或审计。

列表返回items、total、page、size、snapshot，page为1—1000000、size为1—100。首次省略snapshot取得已提交历史上界；后续携带同一snapshot、size及筛选。按caseId降序，状态与受理人按快照内最后历史事件确定，后续新增和处理变化不挤动旧页；详情展示当前状态。未知／重复参数拒绝。snapshot不授予其他人的案件权限。

## 固定失败结果

| HTTP及code | 含义 |
| --- | --- |
| 401 UNAUTHORIZED／403 ACCESS_RESTRICTED | 沿用普通账号身份和认证守卫 |
| 401 ADMIN_UNAUTHORIZED | 管理会话无效、错域、过期、退出或停用 |
| 401 ADMIN_LOGIN_FAILED | 管理账号／密码错误或不可登录，通用提示 |
| 429 ADMIN_RATE_LIMITED | 持久登录失败窗口达到限制 |
| 404 NOT_FOUND | 未知、越权或当前不能作为首次目标的对象／附件 |
| 400 INVALID_PARAMETER | 字段、类别、目标、修订、UUID、分页或图片格式非法 |
| 413 REQUEST_TOO_LARGE／IMAGE_TOO_LARGE | 超过请求或图片技术边界 |
| 415 UNSUPPORTED_MEDIA_TYPE | 写入媒体类型不受支持 |
| 409 REQUEST_KEY_REUSED／REVISION_CONFLICT | 成功键改内容／新键旧修订 |
| 409 CASE_ASSIGNED／CASE_NOT_ASSIGNEE | 他人已认领／当前人不是受理人 |
| 409 CASE_STATE_CONFLICT／CASE_OUTCOME_INVALID | 非法状态流转／结论与案件类型不符 |
| 409 IMAGE_ALREADY_USED | 本人图已绑定其他案件 |
| 503 SERVICE_UNAVAILABLE／STORAGE_UNAVAILABLE | 必需数据库／审计或文件失败，不能假称成功 |

除精确POST /admin/auth/login外，整个/admin前缀，包括错误ID和未知子路径，均先验证管理身份。普通校园Bearer不授管理权，管理Bearer不授普通用户或运维权。

## 贡献失效与追溯

| 方法与路由 | 用途及返回 |
| --- | --- |
| GET /admin/cases/{caseId}/contributions | 社区管理员从文章举报查询对应文章的投币。接受 page（默认 1）和 size（默认 20，最大 100），按流水倒序返回 items、total、page、size。 |
| POST /admin/cases/{caseId}/contributions/{eventId}/invalidation | 认领人标记指定投币失效。JSON 须含 expectedRevision、requestId、reason。成功返回 caseId、eventId、effective（false）。 |

列表中的每项含 eventId、amount、occurredAt、effective。已有处置时，invalidation 含实际处置的 caseId、adminId、reason 和 occurredAt。仅社区管理员可查，列表省去投币者身份和余额。案件须为指向文章的举报，投币须属于该文章；其他案件类型、未知或错配目标返回 404。

写入要求案件处于 PROCESSING 或 NEEDS_ACTION，由当前认领人提交案件修订号及非空原因（最多 2000 个字符）。失效状态、关联处置记录和业务审计在同一事务提交。成功后可用 GOVERNANCE_COMPLETED 关闭关联案件。一次处置处理一笔投币，案件修订号由原案件流转更新。

| 返回码 | 含义 |
| --- | --- |
| 409 REVISION_CONFLICT | 案件修订已变化，重新读取后再提交 |
| 409 CASE_NOT_ASSIGNEE／CASE_STATE_CONFLICT | 需认领人处理，且案件处于可处置状态 |
| 409 REQUEST_KEY_REUSED | 同一管理员在贡献失效接口复用已成功的请求编号，且请求内容不同 |
| 409 CONTRIBUTION_ALREADY_INVALID | 使用新请求编号时，该笔贡献已失效 |
| 503 SERVICE_UNAVAILABLE | 必要的数据库写入或审计失败，事务回滚，可按原请求重试 |

同一管理员使用原编号和相同内容重试会返回原回执，案件关闭后也可重放成功回执。失效只影响文章有效计数、认可排序和荣誉计算，读者余额与原流水继续用于账本核对。

数据库迁移为 V12。现有部署按运维维护流程升级数据库和执行器后，再使用网页发布同一数据库版本的构建。
