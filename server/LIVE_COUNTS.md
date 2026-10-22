# 当前LIVE统计与历史合同

2026-10-22第八阶段：所有公开列表、详情、认证作者文章分页和可用收藏摘要的effectiveCoins统一为`coin_event`中`action='ARTICLE_COIN' AND effective=TRUE`的`SUM(-delta)`，无事实则0。认可排序为有效获币、有效点赞、文章首发时间、文章ID依次降序；最新排序和筛选沿用原合同。币和赞分别按文章聚合后LEFT JOIN，一次查询返回整页，不逐文读币。旧article字段、旧DEMO内容、历史迁移和报告保留。钱包余额与作者贡献不由旧DEMO字段初始化。见[币API](COINS_API.md)。

## 第七阶段历史合同

2026-10-20。这是本批明确的读取统计和认可排序合同变更，原阶段报告保留当时DEMO/NONE语义。

| 项目 | 原读取合同 | 第七阶段当前合同 |
| --- | --- | --- |
| counts字段 | effectiveCoins / effectiveLikes / origin | 三个字段名称与结构保持 |
| 数量事实 | article中固定DEMO数字，新作者NONE/0 | article_like中active=true的真实用户关系 |
| effectiveLikes | 固定演示值 | 活跃点赞行COUNT，不加旧值、不加收藏数 |
| effectiveCoins | 固定演示值或0 | 当前统一0，投币/奖励尚未实施 |
| origin | DEMO / NONE | LIVE，表示本期实际互动聚合来源 |
| sourceType | FIRST_HAND / CURATED / DEMO内容来源 | 保持独立，与计数来源没有绑定 |
| 认可排序 | 演示获币、点赞、首发、ID | `(0,真实点赞,首发时间,文章ID)`依次降序 |
| 最新排序 | 首发、ID降序 | 保持，不因点赞/收藏刷新 |

适用公开文章列表、详情、认证作者文章分页和可用收藏项内当前文章摘要。游客只得公开聚合，不随Authorization附加liked/favorited或用户身份；本人状态必须通过认证专用接口读取。普通用户无点赞者名单、他人收藏或历史行为查询。

旧article.effective_coins/effective_likes/count_origin完全保留，包括DEMO高数字和NONE/0。V7没有UPDATE旧字段或导入伪造点赞；初始没有真实点赞时，当前返回LIVE/0，旧示例1001的存储20/30不会变成真实20/30。原sourceNote内关于“演示常量”的历史文字也不改，当前统计以counts.origin和此合同为准。

COUNT只看关系active，不因会话到期、退出或认证后续失效自动失效。文章不可见时拒绝公开读取；已有关系仍保留，治理策略后续单独定义。账号有效性守卫继续限制新操作。

查询使用一次按article_id聚合的LEFT JOIN，未点赞COALESCE为0；认可排序用该真实值，不引用a.effective_*旧列。分类过滤先于排序，最新排序不引用关系时间。收藏页先取本人关系分页，再批量加载当前公开摘要和分类，最多100条，不逐篇查点赞。没有引入冗余计数缓存，审计失败回滚active事实后，下一次公开数量自然恢复。

旧测试有依据地改动：ArticleMySqlIT无真实点赞时认可顺序与首发/ID顺序一致，Java关键词结果据此排序，详情origin改LIVE；AuthoringMySqlIT新文章origin从NONE改LIVE。保留旧权限、组合过滤、分页、非公开404及历史存储快照断言，并新增多用户点赞改变认可排序/不改变最新排序的真实场景。V1—V6与旧证据文件不重写。

前端接入时区分内容DEMO与统计LIVE，不能继续把所有DEMO内容的当前点赞当演示数；有效获币0不表示钱包或贡献体系已实现。点赞与收藏采用明确PUT/DELETE，并单独读取本人状态。具体合同见[INTERACTIONS_API.md](INTERACTIONS_API.md)，实际验证见[本批报告](verification/2026-10-20-interactions.md)。平台性能、压力、UI和真机仍待验证。
