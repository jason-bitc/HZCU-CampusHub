# 钱包奖励与文章投币API

2026-10-22。统一前缀`/api/v1`，所有接口需有效普通校园认证Bearer会话，成功HTTP200、`code=OK/data/traceId`；响应带no-store及X-Trace-Id。所有金额均为整数非现金币。规则和事务见[COIN_RULES.md](COIN_RULES.md)。

| 方法与路径 | 输入 | data主要字段 |
| --- | --- | --- |
| GET /me/coins | 无 | balance当前可消费余额；receivedContribution本人所收有效贡献 |
| GET /me/coin-ledger | page默认1，size默认20／最大100，snapshot可选非负长整数 | items、total、page、size、snapshot |
| GET /me/rewards | 无 | businessDate、serverTime、rules、login、reading、rewardedToday、remainingDailyReward、readingSessions |
| POST /me/rewards/login | `{ "requestId": "小写UUID" }` | 原成功事件回执 |
| POST /articles/{id}/reading-session | 无正文 | sessionId、articleId、businessDate、startedAt、claimableAt、requiredSeconds |
| POST /me/rewards/reading | `{ "sessionId": "服务端UUID", "requestId": "小写UUID" }` | 原成功事件回执 |
| GET /articles/{id}/coin-state | 无 | articleId、spent本人累计、remaining单篇剩余额度、eligible、reason |
| POST /articles/{id}/coins | `{ "amount": 1, "requestId": "小写UUID" }` | 原成功事件回执 |

GET与阅读开始均不支持正文，表中未列的查询参数拒绝。写JSON须application/json，最大4096字节；未知／重复字段、缺失值、额外身份或日期、非整数金额、小写UUID以外请求键均400。amount默认规则只允许1或2且累计不超2；数值超过当前规则额度为409 ARTICLE_COIN_LIMIT。分页page为1—1000000，size为1—100，重复参数拒绝。snapshot只控制本人的序列上界，不能用于读取他人账本。

回执字段为eventId、action（LOGIN_REWARD／READING_REWARD／ARTICLE_COIN）、businessDate、articleId（登录奖null）、sessionId（仅阅读奖非null）、delta（奖励为正、支出为负）、balanceAfter、walletRevision、occurredAt、rules。balanceAfter是事件提交时余额；重放后应另查/me/coins刷新当前值。事件无标题、正文、联系方式和作者身份，不因原文章后来不可用而泄漏隐藏内容。

领取和投币应在用户确认一次动作时生成requestId，网络重试沿用；下一独立动作使用新键。相同用户／动作的成功键同内容返回相同回执，不另扣发；同键改内容409。未提交的失败可以原键重试。跨午夜或重启重放仍返回旧事件。同日换键再次领已完成任务为409 REWARD_ALREADY_CLAIMED。

任务login／reading各含eligible、claimed，均为当前业务日。readingSessions只返回当天仍公开文章的最近100个本人会话；eligible按当天全部可用会话计算。开始相同文章当日不刷新计时；跨日重新开始，成功回执另按原事件重放。任务查询不分配奖励。币状态eligible表示目标及单篇额度可投，余额需另查；reason为ELIGIBLE、NO_REAL_AUTHOR、SELF_ARTICLE或ARTICLE_COIN_LIMIT。

账本首次查询返回snapshot，继续翻页时携带同一snapshot与size；新增事件不改变该快照的旧分页。省略snapshot会查询最新上界。只按本人sequence_id降序，total对应快照内条数。

| HTTP／业务码 | 条件 |
| --- | --- |
| 401 UNAUTHORIZED | 无效、退出或过期会话；先于目标和正文检查 |
| 403 ACCESS_RESTRICTED | 账号受限或校园认证失效 |
| 404 NOT_FOUND | 未知／不公开／无有效公开版本文章，或非本人阅读会话 |
| 400 INVALID_PARAMETER | 字段、类型、UUID、分页、正文格式不合法 |
| 413 REQUEST_TOO_LARGE | JSON超过4096字节 |
| 415 UNSUPPORTED_MEDIA_TYPE | 写入接口不支持的媒体类型 |
| 409 REQUEST_KEY_REUSED | 已成功请求键更改内容 |
| 409 REWARD_ALREADY_CLAIMED | 当日该任务已领，使用另一个键 |
| 409 LOGIN_REQUIRED_TODAY | 今日尚无成功登录 |
| 409 READING_NOT_READY | 服务端计时未达到该会话门槛 |
| 409 READING_DAY_EXPIRED | 未领阅读会话已跨业务日 |
| 409 SELF_ARTICLE／NO_REAL_AUTHOR | 本人文章／没有真实关联作者的文章不能收币 |
| 409 ARTICLE_COIN_LIMIT | 单次或累计超过当前每人每篇限额 |
| 409 INSUFFICIENT_COINS | 钱包不足，本次未扣款 |
| 503 SERVICE_UNAVAILABLE | 必要数据库或审计写入失败，事务回滚 |

没有撤回／返币／改余额接口。投币者名单和他人账本不公开。旧DEMO的固定币数不混入当前LIVE。荣誉、治理返还、页面对接另行推进。Postman集合只保存占位符，按已授权目标逐条执行。
