# 第三阶段作者资料与图文创作接口

2026-10-14。基础地址、游客筛选见[文章API](API.md)，登录及会话见[身份API](AUTH_API.md)。此处接口均以`/api/v1`开头。本人接口必须提供`Authorization: Bearer <本机登录令牌>`，禁止把令牌放在URL、正文或导出的集合中。每次请求重新检查账号ACTIVE、校园认证VALID和会话有效性；没有身份401，身份受限403，他人文章目标404。

JSON成功为`code/data/traceId`，失败为`code/message/traceId`，响应头X-Trace-Id对应服务器生成的追踪号。本人JSON采用no-store。字段名称、枚举大小写严格，完整写请求禁止未知字段，不能传入ownerUserId、role、认证状态或真实邮箱等身份控制字段。文章/分类ID为正整数，时间UTC ISO-8601。

## 本人资料

GET `/me/profile`返回`{revision,nickname,majorId,currentStage,bio}`。未设置时revision=0、nickname=城院同学、majorId=null、currentStage=UNSET、bio为空。默认资料读取不改写原账号。

PUT `/me/profile`完整替换以下字段：

```json
{"expectedRevision":0,"nickname":"城院同学","majorId":11,"currentStage":"ENROLLED","bio":""}
```

昵称去首尾空白后非空、最多40个Unicode码点、无控制字符；专业可为null，非null必须是catalog启用的MAJOR分类。阶段UNSET/ENROLLED/GRADUATED，简介最多1000码点。成功200返回新revision；旧修订409 REVISION_CONFLICT。资料不改变邮箱、角色、认证或文章的经历阶段；本批没有公开作者资料接口。

## 创建、列表与草稿

POST `/me/articles`，正文`{"requestId":"小写UUID"}`。成功201返回`{articleId}`；同一用户同一创建标识的重试返回同一文章ID，即使文章后来删除也不会重新创建。不同标识创建多篇独立空草稿。创建标识、上传标识、发布标识分别使用新的小写UUID（例如客户端生成的随机UUID），不要为不同操作复用一个标识。

GET `/me/articles?page=1&size=20`：默认20、最大100，page从1开始且不超过1000000。只接受page/size，每个参数一次。返回`{items:[{articleId,state,revision,title,updatedAt}],total,page,size}`，按草稿更新时间、文章ID降序。包含软删除状态记录便于确认操作结果，不含其他人的文章或原先owner为空的演示文章。

GET `/me/articles/{id}/draft`返回`{articleId,state,revision,content,images,referencedImageIds}`。content包含title/summary/body/tags/experienceStage/sourceType/sourceNote/sourceLinks；tags包含四组分类ID数组。images为该文章已上传图片元数据，referencedImageIds仅为Markdown实际图片引用。删除文章草稿404。管理员隐藏文章可由本人读取，但不能修改或发布。

PUT `/me/articles/{id}/draft`保存**完整草稿**，请求如下（与GET的嵌套content/tags结构不同）：

```json
{
  "expectedRevision":0,
  "title":"【DEMO】图文接口示例",
  "summary":"",
  "body":"# 示例\n\n原始Markdown正文。",
  "jobIds":[1],"majorIds":[11],"companyIds":[],"topicIds":[31],
  "experienceStage":"DEMO演示阶段",
  "sourceType":"DEMO",
  "sourceNote":"后端验证示例，不是真实求职经历",
  "sourceLinks":[{"label":"Java官方资料","url":"https://dev.java/learn/"}]
}
```

标题最多200码点、摘要1000、经历阶段64、来源说明2000；来源链接label非空最多200，url最多2000，只接受带主机且无用户凭据的http/https地址，后端不抓取。分类数组不能重复，必须匹配对应启用类别。未完成草稿允许空标题/正文/分类/经历阶段、sourceType=null。其他文字字段必须是字符串，链接必须是数组。保存成功200返回完整Draft，新revision=旧revision+1；409时重新读取并让用户处理差异，不用新修订号自动覆盖。

正文保留原始Markdown，UTF-8最多1MiB，写入JSON最多2MiB。正文超限413 CONTENT_TOO_LARGE，请求超限413 REQUEST_TOO_LARGE。草稿保存不会产生公开历史版本，也不会改变游客正在阅读的正文、分类、来源和图片权限。

## 上传与引用图片

POST `/me/articles/{id}/images`使用multipart/form-data，恰好两个part：文本`requestId`（小写UUID）和文件`file`，一次一张。成功200返回`{imageId,url,mimeType,sizeBytes,width,height}`。只接受实际有效JPEG/JPG、PNG，文件名扩展名须对应实际格式；声明MIME须对应实际格式，application/octet-stream可按实际内容识别。不接受SVG、伪装、损坏、路径形式文件名。

单图片最多10MiB，总multipart最多12MiB，最多2500万像素；没有每篇文章固定图片数量上限。真实文件验证成功、文件安装及元数据事务成功才返回结果。同文章同requestId同文件字节返回原图片；不同字节409 REQUEST_KEY_REUSED。原文件损坏或丢失503 STORAGE_UNAVAILABLE，不能用重试伪装成功。上传不递增草稿revision，也不自动在正文插入图片。

将返回的相对url原样写入Markdown：`![图片说明](/api/v1/media/images/<imageId>)`，也支持CommonMark引用式图片。服务端用[CommonMark AST](https://github.com/commonmark/commonmark-java)读取真实Image节点，代码块或转义示例不产生权限引用。内部图片必须属于当前用户的**同一篇文章**；其他人的图、同一人其他文章的图、未知图均404。内部URL不能带查询令牌。普通http/https外部图片地址可以保存，但后端不代理、验证或授权其外部内容，只有外部图片且没有可读文字不能满足发布的有效上传图片条件。其他相对路径或危险图片协议400。

GET `/media/images/{imageId}`成功返回实际图片二进制，带正确Content-Type/Length、`Cache-Control: no-store, max-age=0`、`Pragma: no-cache`、nosniff、inline及X-Trace-Id。每次访问核查数据库和文件摘要：只有当前PUBLISHED公开版本引用的图允许游客读取。私有、移出公开版本、下架、隐藏图片只允许当前有效且认证正常的本人预览；删除后本人也404。无预览身份、身份过期、他人猜图均404，损坏存储503。失败仍为统一JSON且no-store，避免缓存404影响后续发布访问。

前端私有预览应使用携带Bearer的下载请求生成Blob/临时显示资源；普通img标签不能附加Authorization，不可把令牌放进图片URL。状态变化后重新核查显示资源；服务器no-store不能删除已经下载到用户电脑的图片。Markdown安全HTML渲染、外链和外部图片策略仍需前端实现。

## 发布和文章状态

POST `/me/articles/{id}/publish`：`{"expectedRevision":1,"requestId":"小写UUID"}`，成功200返回`{articleId,versionId,revision,publishedAt,updatedAt}`，revision递增。要求非空标题、可读正文或同文章有效上传图、至少一个岗位和专业、明确经历阶段、来源FIRST_HAND/CURATED/DEMO；CURATED还须来源说明。缺项422 PUBLISH_INCOMPLETE，message列明缺项；无最低字数要求。摘要留空时按正文可读文字生成最多1000码点摘要（纯图片可为空）。

发布在事务内建立不可变公开版本、分类、来源链接、图片引用、首发/更新信息及重试回执，然后更新公开指针。同请求标识与原期望修订重试返回原版本回执；该标识配不同期望修订409。并发相同请求仅一个版本，并发不同请求使用旧revision产生409。下架/隐藏/删除后旧发布回执不用于恢复公开状态。再次编辑和发布同文章会创建新版本，旧版本不对游客提供访问入口。

GET `/articles`及`/articles/{id}`保持游客筛选和隐私合同，新增updatedAt。publishedAt是首次发布时间，修改及恢复不改变“最新”排序基础；updatedAt为当前公开版本发布时间。新文章互动计数0、origin=NONE，表示互动尚未实现；原演示计数DEMO保留。sourceType=DEMO标记内容来源，与计数origin独立。

POST `/me/articles/{id}/unpublish`或`/restore`正文`{"expectedRevision":2}`，返回`{articleId,state,revision}`。实际变更递增revision；重复目标状态成功返回当前状态和revision，不重新改变内容。下架只从PUBLISHED变为AUTHOR_OFFLINE，恢复只从AUTHOR_OFFLINE恢复原公开版本。编辑下架草稿后恢复仍公开旧版本，要公开修改需再次发布。ADMIN_HIDDEN不能编辑、发布、恢复；非本人404。

DELETE `/me/articles/{id}?expectedRevision=2`软删除，返回同一StateResult。实际删除递增revision，重复删除返回终态。本人可以删除隐藏文章，删除后不能保存、上传或发布；创建/删除重试不会新建文章。状态终结保留历史数据和文件，本批不进行长期文件回收。

## 配置和故障

默认存储server/.local-data/media，启动脚本固定server工作目录。可用CAMPUSHUB_MEDIA_ROOT配置专属持久目录；参数见[启动说明](README.md)。文件不入Git；重启必须保留原数据库和存储。503不能当作保存/上传/发布成功，409读取后确认，其他400修正参数；创建/上传/发布网络不确定时用原requestId重试。

已知长度超过multipart限值时在读正文前拒绝，Tomcat的Expect:100-continue推迟到读取时才响应；见[Tomcat官方HTTP连接器说明](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html)。未知长度仍由multipart解析器限额；不无限吞咽恶意超大请求体，客户端不遵守Expect或继续大量发送时可能出现连接关闭，应按未确认成功处理。
