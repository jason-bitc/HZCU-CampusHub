# 点赞、收藏与本人互动 API

2026-10-20，第七阶段。前缀 `/api/v1`；有效普通USER会话、ACTIVE账号、VALID校园认证，沿用Bearer。先鉴权再查目标，无有效会话401，受限身份403，未知/不可用404。全部新增接口及失败no-store，成功200，沿用code/data/traceId与X-Trace-Id。

## 六个接口

| 方法与路径 | data |
| --- | --- |
| PUT /articles/{articleId}/like | `{articleId,liked:true,effectiveLikes}` |
| DELETE /articles/{articleId}/like | `{articleId,liked:false,effectiveLikes}` |
| PUT /articles/{articleId}/favorite | `{articleId,favorited:true}` |
| DELETE /articles/{articleId}/favorite | `{articleId,favorited:false}` |
| GET /articles/{articleId}/interaction-state | `{articleId,liked,favorited}`，只读本人 |
| GET /me/favorites?page=1&size=20 | `{items,total,page,size}`，只读本人 |

以上请求均**无请求体**，包括`{}`、JSON null、空白或multipart都拒绝；不传userId、role、目标布尔值、revision或UUID。HTTP方法就是明确目标状态，不使用toggle。除收藏分页外不接收查询参数。ID为严格正整数，分页默认1/20、最大size100、page上限1000000；未知/重复参数或非法输入400，不支持的方法按既有405处理。

示例（令牌只在本机请求头中使用，不入URL或导出集合）：

```text
PUT /api/v1/articles/1001/like
Authorization: Bearer <本机有效令牌>
无正文
```

不要求评论者/读者先建立作者主页；作者可以按普通规则给本人文章点赞。公开无真实作者的DEMO文章同样可产生真实关系，不虚构作者。点赞不扣币、不自动收藏；收藏不增加公开认可数，两个状态相互独立。

## 幂等、时间与可见性

每个用户每篇文章分别只有一条点赞状态和一条收藏状态。相同设置重复或并发成功返回相同目标状态，不刷新修订、收藏时间或成功审计。不存在时取消公开文章关系返回false且不创建行；点赞总数不负数。不同用户按各自有效点赞累计，取消只影响本人。

effectiveLikes是该文章本次事务内的真实总数，其他用户后续操作可能改变它，不能将它当永久固定回执。对立设置按文章行锁确定提交先后，最后提交的明确状态生效；不承诺把跨设备网络到达顺序还原为用户原始点击顺序。

点赞设置、取消、收藏设置及本人状态读取要求文章当前PUBLISHED且有本文章有效已发布公开版本。下架/隐藏/删除或没有有效公开版本统一404。账号后续受限会阻止新请求，本批不因认证撤销、退出或会话到期自动重写历史有效点赞。

本人可取消已知的不可用收藏，不返回隐藏内容；重复取消同一历史关系仍返回false。不可用文章且本人从未建立收藏关系时404。文章仍公开但本人没有关系时取消false。这个例外只适用于取消收藏；不可用文章不能取消点赞。

## 本人收藏分页

只返回active收藏，按最近一次false→true的activated_at、articleId降序；同状态重试不刷时间，取消再收藏使用新的激活时间。相同时间以文章ID保证顺序。

可用项为 `{articleId,available:true,article:文章当前公开摘要}`，article结构与[公开文章API](API.md)列表相同，包括当前LIVE counts，不带全文或旧版本。不可用项**严格只有** `{articleId,available:false}`；不含旧标题、摘要、正文、来源、时间、内部状态或治理原因。total包含不可用占位；末页items为空但total不变。恢复公开后以当前公开版本重新生成摘要，已取消的收藏不会自动恢复。

单次请求在REPEATABLE_READ中读取关系、摘要、分类及计数；每页最多100项，摘要/分类批量查询。跨请求期间新增、取消或可见性变化可能影响offset分页位置，不承诺固定历史快照。没有读取他人收藏、收藏人数或点赞者名单的接口。

## 真实数量与审计

公开列表、详情、作者文章及可用收藏摘要的counts结构保留，当前统一 `origin:"LIVE"`、`effectiveCoins:0`，effectiveLikes从article_like中active行聚合；不叠加旧DEMO值，不按收藏数排序。[计数衔接说明](LIVE_COUNTS.md)解释旧存储与新读取合同。

V7新增article_like、article_favorite及审计动作/对象兼容扩展，不改V1—V6。状态变化和成功审计同事务，LIKE_SET / LIKE_CANCEL / FAVORITE_SET / FAVORITE_CANCEL使用objectType LIKE/FAVORITE、objectId文章ID，actorId与文章ID共同标识关系；revision仅内部使用。重复状态只有BUSINESS_NO_CHANGE，无新成功审计。审计/状态SQL失败503并回滚关系和真实数量，解除本次故障后可重试。

写操作先锁文章，和文章撤回及其他本文章互动写入序列化，再比较状态、持久化和审计。成功以数据库提交为准，不以预先经过PERSIST或分配ID为准。

日志模块INTERACTION，六类动作LIKE_SET / LIKE_CANCEL / FAVORITE_SET / FAVORITE_CANCEL / INTERACTION_READ / FAVORITES_LIST。模式沿用[LOGGING.md](LOGGING.md)；修订上下文的expectedRevision是服务端读到的基准，actualRevision是本事务确认值，不是客户端提交参数。Debug有安全阶段/代码诊断，Release保留必要摘要而不追加调试细节。所有模式都不记录载荷、邮箱、令牌、联系值或收藏内容快照。

手工请求：[Postman](postman/CampusHub.postman_collection.json)；实际证明：[第七阶段验证](verification/2026-10-20-interactions.md)。页面按钮状态、移动端交互、安全渲染、真机、投币奖励/钱包/荣誉、通知、治理及运维均待后续，不由本批后端测试代替。
