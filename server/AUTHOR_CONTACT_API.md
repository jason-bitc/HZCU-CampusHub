# 第四阶段作者与联系接口

2026-10-20。基础地址默认`http://127.0.0.1:8080`，以下均加`/api/v1`。本批新增6个接口，全部要求当前有效Bearer会话、USER角色、ACTIVE账号及VALID校园认证；使用原[身份API](AUTH_API.md)，不建立第二套认证。不接受令牌URL参数，成功与失败均no-store及Pragma:no-cache。

沿用`code/data/traceId`成功结构及`code/message/traceId`失败结构，X-Trace-Id由服务器生成并与JSON一致。先验证访问者，再处理目标ID或查询作者。无登录/会话失效401，访问者受限403，目标不可见404，参数错误400，旧修订409，数据库或审计不可用503。关闭联系方式是成功结果，不用404暗示保存的隐藏值。

## 作者入口和主页

GET `/articles/{articleId}/author`返回`{authorId,nickname}`，articleId为正整数。文章须仍PUBLISHED且指向有效公开版本，作者须真实关联并为当前ACTIVE/VALID/USER。原演示没有真实作者关联，返回404；不将其绑定给任何真实用户。该接口不接受查询参数。

GET `/authors/{authorId}`不接受查询参数，返回：

```json
{
  "authorId":123,
  "nickname":"城院同学",
  "major":{"id":11,"name":"计算机科学与技术"},
  "currentStage":"ENROLLED",
  "bio":"",
  "campusCertification":"VALID",
  "publicArticleCount":2
}
```

未填写资料时默认昵称城院同学、major=null、currentStage=UNSET、bio为空。当前阶段沿用UNSET/ENROLLED/GRADUATED，校园认证仅代表校园关联，不代表履历核验。主页不含联系值、邮箱、认证时间、密码、角色、完整用户对象、头像或荣誉。返回的专业标签是当前基础资料，不替代各篇文章自己的经历背景。

作者必须ACTIVE、VALID、USER，并至少有一篇当前公开文章；未知、无公开文章、受限或认证失效一律404。最后一篇下架/隐藏/删除后，新请求不能继续读取主页、作者文章或联系入口；本人仍通过原/me/profile和/me/contact-settings管理设置。目标作者不可见不改变原免费文章读取合同。

GET `/authors/{authorId}/articles?page=1&size=20`返回原文章列表结构`{items,total,page,size}`，列表字段与[文章读取API](API.md)一致。只列该作者当前PUBLISHED公开版本，按首次publishedAt、文章id降序；恢复和修改草稿不刷新首发排序。默认20、最大100，page从1至1000000。只接受page/size，每参数一次；未知参数、sort、分类筛选、重复参数或非法值400。末页后items为空但total不变。单次读取在REPEATABLE_READ内保持目标可见性、数量和内容快照一致；跨请求offset分页不承诺固定快照。

游客原`GET /articles`及`/articles/{id}`的结构保持，不随Authorization返回作者资料，游客图片权限沿用第三阶段。旧计数DEMO、新文章NONE/0保留，没有新增阅读奖励或互动计数。

## 本人独立联系设置

GET `/me/contact-settings`无查询参数，返回`{revision,enabled,type,value}`。无设置时revision=0、enabled=false、type/value=null，读取不建立数据库记录。本人可以看到关闭后仍保存的值，仅本人接口返回这个隐藏值。

PUT `/me/contact-settings`完整替换且不接受查询参数，正文恰好四字段：

```json
{"expectedRevision":0,"enabled":true,"type":"WECHAT","value":"作者自选的微信号"}
```

type为WECHAT、QQ或WEB。微信/QQ作为普通文本展示复制，不验证其真实归属或承诺站外联系效果；本批不做二维码或联系图片。value拒绝控制字符、首尾Unicode空白去除后非空；文本最多200个Unicode码点，WEB最多2000码点（按去空白前长度限制）。WEB仅接受带有效主机且无用户凭据的绝对http/https地址，后端不抓取或跳转。以上是当前技术默认参数，不代表教师已批准。

关闭可保留合法type/value：`{"expectedRevision":1,"enabled":false,"type":"WECHAT","value":"原值"}`；明确清空则将type/value同时设null。开启必须有合法的非空值；只填一个null、空值、控制字符、未知类型、字符串形式enabled、额外userId/ownerUserId/role字段均400。不能通过作者ID修改其他人，也没有PUT /authors/{id}/contact接口。

成功200返回本人Contact，新revision=旧revision+1。每个被接受的完整替换记录一次修订和成功审计；同旧expectedRevision重试409，不重复变更。并发旧修订只有一个成功，另一方409，需重新读取后由用户决定，不自动覆盖。资料revision和联系revision完全独立，原PUT /me/profile不需增加字段且不会清空联系设置。创作JSON大小上限仍2MiB；联系值使用更小的上述字段限制。

## 认证读者查看联系方式

GET `/authors/{authorId}/contact`无查询参数，先检查访问者及目标作者可见性，再根据当前独立设置返回：

```json
{"enabled":false}
```

或`{"enabled":true,"type":"WEB","value":"https://example.test/"}`。关闭响应**没有**type/value字段，也没有revision、隐藏值长度、修改时间等可推断信息。

每次请求在新的READ_COMMITTED只读事务内用单条SQL检查目标可见性和实时开关，不读取文章公开快照、不做联系值缓存。更新成功后才启动的新请求看到已提交的新设置；并发的在途请求按其读取时点完成。客户端在切换页面或刷新时重新请求，不把联系内容存到长期缓存。已复制、截图或下载到站外的信息不能收回。

前端对微信/QQ做普通文本展示、复制，对WEB验证协议并让用户自行打开；不要用危险HTML插入值，不用服务端任意重定向。网页、小程序复制与跳转交互、安全渲染和真机效果仍待WorkBuddy接入验证。

## 审计与运行日志

本批成功审计覆盖CONTACT_UPDATE、PROFILE_UPDATE、ARTICLE_PUBLISH、ARTICLE_UNPUBLISH、ARTICLE_RESTORE、ARTICLE_DELETE。事件UUID、UTC时间、traceId、内部操作者和对象ID、动作、前后修订/有限状态及SUCCESS写入business_audit，且与业务变更同事务。审计INSERT失败返回503，业务、版本指针、回执和修订一并回滚。发布标识重试、已到目标状态的下架/恢复/删除不新增成功变更审计；运行日志标记BUSINESS_NO_CHANGE。联系/资料旧修订返回409，不另造成功记录。

审计不存联系方式、昵称旧值/新值、正文、图片、密码、邮箱、令牌或原请求；没有向普通用户开放审计/日志查询或下载接口。actorId和objectId仅作为内部历史标识，审计不随账号或文章级联删除。

运行日志只记录固定动作类别、方法白名单、状态、结果、耗时、异常类型和追踪号，未知路由记OTHER；不拼接路径、查询参数、输入值或异常消息。成功业务运行事件在事务提交后产生。文件日志配置、轮转、故障和保留边界见[LOGGING.md](LOGGING.md)，本批实现及实际证据见[第四阶段记录](../docs/BACKEND_BATCH4.md)。
