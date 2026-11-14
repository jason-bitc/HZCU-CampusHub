# 站内通知 API

对应 FR-P02、TC-P02 和 WBS 5.8.2。网页与小程序共用 `/api/v1/me/notifications`。有效校园账号会话可访问本人通知。账号受限或校园认证失效时，会话仍有效的用户可读取本人通知摘要和标记已读，关联内容继续受原接口权限约束。响应使用 `Cache-Control: no-store`。

| 方法与路由 | 输入和返回 |
| --- | --- |
| GET /me/notifications | 可传 page、size，默认 1、20，最大 size 为 100。返回 items、total、unreadCount、page、size，按通知 ID 倒序。unreadCount 为本人全部未读数。 |
| PUT /me/notifications/{id}/read | 无查询参数和正文。返回 id、readAt。重复调用返回第一次标记时间。 |

接口沿用 `code/data/traceId` 响应。无效或过期会话返回 401，找不到通知或通知属于他人均返回 404，非法参数或带正文返回 400。

## 通知字段与跳转

每条通知含 `id`、`kind`、`message`、`outcome`、`createdAt`、`readAt`、`target`。未读时 `readAt=null`。摘要由固定模板生成，结果采用枚举值。评论正文、管理员原因、举报材料和联系方式通过其受权接口查看。

| kind | 接收者和事件 | target.type |
| --- | --- | --- |
| CAMPUS_VERIFIED | 本人完成校园邮箱认证 | CERTIFICATION |
| COMMENT_REPLY | 被直接回复的评论作者，跳过本人回复本人 | COMMENT |
| CASE_DECISION | 举报或反馈的提交者，管理员记录决定 | CASE |
| ARTICLE_MODERATION | 文章作者，文章隐藏或恢复 | ARTICLE |
| COMMENT_MODERATION | 评论作者，评论隐藏或恢复 | COMMENT |
| RESTRICTION_CHANGED | 受到发布或评论限制变化的用户 | RESTRICTION |
| APPEAL_DECIDED | 申诉人，申诉被接受或驳回 | APPEAL |
| CONTRIBUTION_INVALIDATED | 被取消有效贡献的文章作者 | ARTICLE |

目标包括 type、id、articleId、rootCommentId。CERTIFICATION 对应应用中的本人认证状态页。COMMENT 使用 articleId 和 rootCommentId 打开文章内的评论线程，id 是定位的评论。CASE 对应 `/me/cases/{id}`。RESTRICTION 和 APPEAL 对应本人限制及申诉列表。ARTICLE 对应公开文章。打开时仍需调用对应业务接口重新验证权限。

目标隐藏、删除、下架或受权限限制时，`target=null`。界面保留通知及结果，显示“相关内容暂不可查看”。历史通知的 outcome 表示事件发生时的结果，目标是否可打开按当前状态判断。

通知与业务变更同事务保存。重试已成功的业务请求会返回原回执，通知保持一条。V13 生效后开始记录通知。材料人工认证仍为后续范围。
