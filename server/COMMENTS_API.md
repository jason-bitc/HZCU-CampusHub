# 评论、回复与评论图片 API

2026-10-20，第五阶段后端合同。前缀 `/api/v1`，有效 Bearer 会话且 USER / ACTIVE / VALID。无需先发文章或建立作者主页。校园认证不表示内容真实性。原游客文章读取合同保持。

JSON沿用 `code/data/traceId` 或 `code/message/traceId`；所有本批响应（含图片和失败）均 `Cache-Control: no-store`，响应头 `X-Trace-Id`。先验证访问者身份，无会话401、受限身份403；再检查目标，不可见或非本人操作目标404。不要把令牌放进URL。

## 六个接口

| 方法与路径 | 成功结果 |
| --- | --- |
| GET /articles/{articleId}/comments?page=1&size=20 | 200，`{items,total,page,size}` 主评论页 |
| GET /comments/{rootCommentId}/replies?page=1&size=20 | 200，`{root,items,total,page,size}` 根节点与平铺回复页 |
| POST /articles/{articleId}/comments | 201，稳定创建回执 |
| DELETE /comments/{commentId} | 200，`{commentId,state:"DELETED"}` |
| POST /articles/{articleId}/comment-images | 200，图片元数据；multipart只有requestId和file |
| GET /media/comment-images/{imageId} | 200，真实JPEG/PNG字节，nosniff / inline |

文章ID、评论ID为严格正整数，图片ID和请求标识为小写UUID。分页默认1/20，size最大100；未知、重复查询参数400；其余接口不接受查询参数。回复接口只接受真正的根评论ID。

## 创建与回执

完整JSON，字段不能增减，body必须是字符串，imageIds必须是数组，replyToCommentId为null或正整数：

```json
{
  "requestId": "00000000-0000-4000-8000-000000000001",
  "body": "第一行\n第二行",
  "imageIds": [],
  "replyToCommentId": null
}
```

示例UUID仅说明格式，每次新的业务操作应生成新的随机UUID。同一业务重试保留原标识和内容。创建返回 `{commentId,articleId,rootCommentId,replyToCommentId}`，不返回正文或当前可见状态。主评论的rootCommentId就是自身ID；回复的根ID由服务端推导，客户端不得传入root或owner。replyToCommentId可指向同文章内当前可见的主评论或回复；跨文、未知和不可用的直接目标404。根节点已不可用时，仍可回复线程内另一条可见回复。

正文为PLAIN_TEXT，CRLF/CR规范化成LF，保留其他合法空白及换行；HTML字面量按文本保留，服务端不执行或渲染。除LF/TAB外的ISO控制字符、无效Unicode拒绝400。没有最低字数；只有空白且没有图片422 COMMENT_EMPTY。前端接入必须使用安全文本呈现。

创建标识在同一用户的全部文章范围内唯一；摘要包括文章、规范化正文、直接回复对象及**有序**图片ID列表。相同标识和规范化内容重复或并发请求201返回同一组ID，一次评论/图片绑定/成功审计；换正文、图片顺序、回复对象或文章409 REQUEST_KEY_REUSED。重复图片ID400。正文与图片至少一项有效，图片不存在或不属本人同文404，已经绑定另一评论409 IMAGE_ALREADY_USED。

已删除评论的旧创建回执仍返回稳定ID，不重新发布正文或图片；回执重放仍要求文章当前公开。创建和上传分别使用独立标识及回执空间。

## 分页、占位与评论者

主评论按createdAt、commentId降序；回复平铺按createdAt、commentId升序，不无限嵌套。一次请求在同一数据库读取快照中生成页和数量；offset跨多次请求不承诺并发新增时固定快照。

每项共有 `commentId,articleId,rootCommentId,replyToCommentId,available,canDelete,replyCount`。可见项再包含 `body,bodyFormat:"PLAIN_TEXT",images,commenter,createdAt`，回复项还有最小 `replyTo:{commentId,available,commenter?}`。images按提交顺序返回 `{imageId,url,mimeType,sizeBytes,width,height}`。

删除或ADMIN_HIDDEN项以available:false保留结构，**不含**正文、图片、commenter、创建时间、状态或隐藏原因；其正常回复仍可分页访问。根total统计所有主评论（含占位），根replyCount和回复页total统计该根全部回复（含占位）；单条回复的replyCount为0。正常回复若指向不可用对象，replyTo只返回ID和available:false。占位口径不等于可见内容数量。

commenter只含当前允许公开的昵称；当前有公开文章时才有authorId及可访问主页。未建资料使用“城院同学”。账号受限或认证失效时commenter为null，不公开受限资料或邮箱；原本可见的历史正文和图片仍按文章与评论自身状态处理。

## 删除与文章状态

仅本人可软删除，文章作者无权删除别人评论。他人/未知目标统一404；重复本人DELETED返回同一终态，不新增成功变更审计。删除不级联他人回复，不解除图片绑定，不恢复管理员隐藏内容。

读取、上传、新增与图片预览仅限当前公开文章（包括明确DEMO的旧无作者文章）。文章下架/隐藏/删除，旧入口404；恢复文章不恢复终态评论。本人仍可删除已知ID的本人评论，即使文章已不可见；返回只有ID和终态，不带隐藏内容。

## 评论图片与容量

multipart严格只有一个小写UUID `requestId` 和一个 `file`，不接收原文件名作为存储路径。成功返回图片元数据，同一用户同一上传标识跨所有文章唯一；同标识不同文章或文件409，相同文件重试返回同一ID。仅上传者且文章公开时可预览未附着图片。

采用独立comment_image表和受限URL，复用现有MediaStore校验/私有持久目录。只接受实际可解码JPEG/PNG并校验类型、尺寸、摘要和UUID路径。文章创作图片、他人图、本人其他文章图不能借用。绑定评论后永久保留关联；仅文章公开且评论VISIBLE时其他认证用户可读。评论删除/隐藏后，包括上传者在内旧URL均404，不能退回未附着预览。存储缺失或损坏503，不伪装404或空图。

| 本机变量 | 默认值与允许配置范围 |
| --- | --- |
| CAMPUSHUB_COMMENT_MAX_CODE_POINTS | 10000个Unicode码点；1–100000 |
| CAMPUSHUB_COMMENT_JSON_MAX_BYTES | 131072字节；1–4194304 |
| CAMPUSHUB_MEDIA_MAX_BYTES | 每文件10485760字节，沿用共享图片设置 |
| CAMPUSHUB_MULTIPART_MAX_BYTES | multipart整体12582912字节，沿用共享设置 |
| CAMPUSHUB_MEDIA_MAX_PIXELS | 25000000像素，沿用共享设置 |

没有固定业务图片张数上限，但JSON整体有界，SQL读取和绑定按每批100条处理；实际105张小图的同一评论验证见本批报告。超限413、非法类型/格式或图片内容按既有400/415/422分类。参数不是教师批准的最终容量，也不表示通过压力测试。

评论、创建回执、图片绑定与成功审计同一事务，审计失败503并回滚。文章状态变更与评论写入使用文章行锁序列化；文件/数据库不是分布式事务，明确回滚只清理本次新文件，提交结果未知保留待核查。历史未附着图清理、备份、治理接口及页面仍待后续。

配置与排查见[LOGGING.md](LOGGING.md)，实际测试见[第五阶段验证](verification/2026-10-20-comments.md)，手工请求见[Postman](postman/CampusHub.postman_collection.json)。
