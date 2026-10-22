# 全模块日志覆盖表

## 第八阶段新增八类动作

2026-10-22，当前共49类动作。以下COIN模块动作均有真实HTTP、文件摘要和两模式成功trace，逐条实见阶段见[本次矩阵](verification/coins-2026-10-22/route-matrix.csv)。历史第六、七阶段的原始证据保持。

| 路由 | 动作 |
| --- | --- |
| GET /api/v1/me/coins | COIN_BALANCE |
| GET /api/v1/me/coin-ledger | COIN_LEDGER |
| GET /api/v1/me/rewards | REWARDS_READ |
| POST /api/v1/me/rewards/login | LOGIN_REWARD |
| POST /api/v1/articles/{id}/reading-session | READING_START |
| POST /api/v1/me/rewards/reading | READING_REWARD |
| GET /api/v1/articles/{id}/coin-state | COIN_STATE |
| POST /api/v1/articles/{id}/coins | ARTICLE_COIN |

写入通过RequestTrace.write实际记录事务结果；奖励／投币用钱包修订关联最小审计，阅读开始以文章ID关联。失败无成功审计，无变化重放仅BUSINESS_NO_CHANGE。私人账本和回执正文不写日志。代表审计故障在Debug关联COIN、ARTICLE_COIN、AUDIT、BusinessAudit和CoinService，Release同码失败无新增诊断。

## 第七阶段新增六类动作

当前共41类动作：第六阶段35类基础继续回归，本批新增下表六类。事件来自真实HTTP与文件输出，两模式各有成功trace；没有将原第六阶段35类证据重写为当时已具备新互动。字段与排查见[LOGGING.md](LOGGING.md)，计数合同见[LIVE_COUNTS.md](LIVE_COUNTS.md)。

实际Debug/Release追踪见[新增矩阵](verification/interactions-2026-10-20/route-matrix.csv)与[本批报告](verification/2026-10-20-interactions.md)。

| 方法与路由 | 动作 / 模块 | Debug成功实见阶段 |
| --- | --- | --- |
| PUT /api/v1/articles/{id}/like | LIKE_SET / INTERACTION | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / RECEIPT / PERSIST / AUDIT / COMMIT / COMPLETE |
| DELETE /api/v1/articles/{id}/like | LIKE_CANCEL / INTERACTION | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / RECEIPT / PERSIST / AUDIT / COMMIT / COMPLETE |
| PUT /api/v1/articles/{id}/favorite | FAVORITE_SET / INTERACTION | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / RECEIPT / PERSIST / AUDIT / COMMIT / COMPLETE |
| DELETE /api/v1/articles/{id}/favorite | FAVORITE_CANCEL / INTERACTION | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / RECEIPT / PERSIST / AUDIT / COMMIT / COMPLETE |
| GET /api/v1/articles/{id}/interaction-state | INTERACTION_READ / INTERACTION | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / READ / COMPLETE |
| GET /api/v1/me/favorites | FAVORITES_LIST / INTERACTION | AUTHENTICATION / AUTHORIZATION / VALIDATION / READ / COMPLETE |

LIKE/FAVORITE审计objectId为文章ID，actorId与文章ID构成关系对象；修订上下文来自服务端已确认状态，不是客户端输入。失败与无变化不会新写成功审计。收藏列表只记录有限动作、阶段及actorId，不把收藏摘要/标题打入日志。

## 第六阶段35类基础覆盖（历史实见阶段保留）

2026-10-20，第六阶段。按现有路由枚举，35类动作均有本批真实HTTP成功请求及文件事件。以下阶段来自该成功请求实际DEBUG_STAGE，故不同路径未必经历所有阶段。代表性失败按模块覆盖，空白不表示该路由的全部失败已逐一测试。读取不写业务审计。

原始映射及traceId见[route-matrix.csv](verification/logging-modes-2026-10-20/route-matrix.csv)，全部本批请求见[覆盖记录](verification/logging-modes-2026-10-20/batch6-route-coverage.json)，检索对应[运行样本](verification/logging-modes-2026-10-20/batch6-runtime-sample.txt)；诊断与模式证据见[实际报告](verification/2026-10-20-logging-modes.md)。

| 方法与路由 | 动作 / 模块 | 成功实见阶段 | 本批代表性失败状态 |
| --- | --- | --- | --- |
| GET /api/v1/articles/{id}/author | ARTICLE_AUTHOR / AUTHOR | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / COMPLETE | — |
| GET /api/v1/articles/{id} | ARTICLE_DETAIL / READING | AUTHENTICATION / VALIDATION / TARGET / COMPLETE | 404 |
| POST /api/v1/me/articles/{id}/images | ARTICLE_IMAGE_UPLOAD / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / STORAGE / TRANSACTION / RECEIPT / PERSIST / COMMIT / COMPLETE | — |
| GET /api/v1/articles | ARTICLE_LIST / READING | AUTHENTICATION / VALIDATION / READ / COMPLETE | 400 |
| GET /api/v1/authors/{id}/articles | AUTHOR_ARTICLES / AUTHOR | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / READ / COMPLETE | — |
| GET /api/v1/authors/{id}/contact | AUTHOR_CONTACT / AUTHOR | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / COMPLETE | — |
| GET /api/v1/authors/{id} | AUTHOR_HOME / AUTHOR | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / COMPLETE | 404 |
| POST /api/v1/auth/login | AUTH_LOGIN / AUTH | AUTHENTICATION / VALIDATION / TRANSACTION / AUTHORIZATION / PERSIST / COMMIT / COMPLETE | 503 |
| POST /api/v1/auth/logout | AUTH_LOGOUT / AUTH | AUTHENTICATION / VALIDATION / PERSIST / COMPLETE | — |
| POST /api/v1/auth/password-resets | AUTH_PASSWORD_RESET / AUTH | AUTHENTICATION / VALIDATION / TRANSACTION / CODE_VERIFICATION / PERSIST / COMMIT / COMPLETE | — |
| POST /api/v1/auth/register | AUTH_REGISTER / AUTH | AUTHENTICATION / VALIDATION / TRANSACTION / CODE_VERIFICATION / PERSIST / COMMIT / COMPLETE | — |
| POST /api/v1/auth/registration-codes | AUTH_REGISTER_CODE / AUTH | AUTHENTICATION / VALIDATION / TRANSACTION / PERSIST / MAIL_SEND / COMMIT / COMPLETE | 429 |
| POST /api/v1/auth/password-reset-codes | AUTH_RESET_CODE / AUTH | AUTHENTICATION / VALIDATION / TRANSACTION / PERSIST / MAIL_SEND / COMMIT / COMPLETE | — |
| GET /api/v1/catalog | CATALOG / READING | AUTHENTICATION / VALIDATION / READ / COMPLETE | — |
| POST /api/v1/articles/{id}/comments | COMMENT_CREATE / COMMENT | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / RECEIPT / ATTACHMENT / STORAGE / PERSIST / AUDIT / COMMIT / COMPLETE | 503 |
| DELETE /api/v1/comments/{id} | COMMENT_DELETE / COMMENT | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / PERSIST / AUDIT / COMMIT / COMPLETE | — |
| GET /api/v1/media/comment-images/{imageId} | COMMENT_IMAGE_READ / COMMENT | AUTHENTICATION / AUTHORIZATION / VALIDATION / STORAGE / COMPLETE | — |
| POST /api/v1/articles/{id}/comment-images | COMMENT_IMAGE_UPLOAD / COMMENT | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / STORAGE / TRANSACTION / RECEIPT / PERSIST / COMMIT / COMPLETE | — |
| GET /api/v1/articles/{id}/comments | COMMENT_LIST / COMMENT | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / COMPLETE | — |
| GET /api/v1/comments/{id}/replies | COMMENT_REPLIES / COMMENT | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / COMPLETE | 404 |
| GET /api/v1/me/contact-settings | CONTACT_READ / AUTHOR | AUTHENTICATION / AUTHORIZATION / VALIDATION / READ / COMPLETE | — |
| PUT /api/v1/me/contact-settings | CONTACT_UPDATE / AUTHOR | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / PERSIST / AUDIT / COMMIT / COMPLETE | 409 |
| POST /api/v1/me/articles | DRAFT_CREATE / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / RECEIPT / PERSIST / COMMIT / COMPLETE | — |
| GET /api/v1/me/articles | DRAFT_LIST / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / READ / COMPLETE | — |
| GET /api/v1/me/articles/{id}/draft | DRAFT_READ / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TARGET / COMPLETE | 401 |
| PUT /api/v1/me/articles/{id}/draft | DRAFT_SAVE / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / ATTACHMENT / PERSIST / COMMIT / COMPLETE | 409 |
| GET /actuator/health | HEALTH / HEALTH | AUTHENTICATION / VALIDATION / COMPLETE | — |
| GET /api/v1/me | IDENTITY_READ / AUTH | AUTHENTICATION / VALIDATION / AUTHORIZATION / COMPLETE | — |
| GET /api/v1/media/images/{imageId} | IMAGE / MEDIA | AUTHENTICATION / VALIDATION / TARGET / STORAGE / COMPLETE | 404 |
| GET /api/v1/me/profile | PROFILE_READ / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / READ / COMPLETE | — |
| PUT /api/v1/me/profile | PROFILE_UPDATE / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / PERSIST / AUDIT / COMMIT / COMPLETE | 409 |
| DELETE /api/v1/me/articles/{id} | WRITING_DELETE / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / PERSIST / AUDIT / COMMIT / COMPLETE | — |
| POST /api/v1/me/articles/{id}/publish | WRITING_PUBLISH / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / RECEIPT / ATTACHMENT / STORAGE / PERSIST / AUDIT / COMMIT / COMPLETE | — |
| POST /api/v1/me/articles/{id}/restore | WRITING_RESTORE / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / PERSIST / AUDIT / COMMIT / COMPLETE | — |
| POST /api/v1/me/articles/{id}/unpublish | WRITING_UNPUBLISH / AUTHORING | AUTHENTICATION / AUTHORIZATION / VALIDATION / TRANSACTION / TARGET / PERSIST / AUDIT / COMMIT / COMPLETE | — |

代表性故障覆盖验证码发送、登录会话、读取连接、资料审计、草稿SQL、联系SQL、旧文章图片存储和评论审计；Debug有相同traceId的有限项目位置，Release同类HTTP/业务码保持且不增长调试/诊断。健康异常、其他验证码/账号校验、创作与评论详细权限仍执行原五批回归；本表没有声称每路由每种错误都出现。

安全字段只有有限动作/模块/阶段/错误码、已确认内部actor/author/article/comment ID和经过类型与范围校验的修订。原关键词、邮箱、验证码、令牌、联系方式、正文和SQL参数不进入事件。成功创建后但事务尚未提交的临时ID不代表提交事实。

COMMIT / ROLLBACK / TRANSACTION_UNKNOWN是事务结果；BUSINESS_CHANGE在提交后，业务审计同事务。固定时钟测试可有同一业务时间，不能据时间排序猜测最后审计，应通过回执/对象和traceId精确关联。

实现入口：[RequestTrace](src/main/java/cn/edu/hzcu/campushub/common/RequestTrace.java)、[SafeFileLogs](src/main/java/cn/edu/hzcu/campushub/audit/SafeFileLogs.java)、[LocalDiagnostics](src/main/java/cn/edu/hzcu/campushub/audit/LocalDiagnostics.java)；使用及排查见[LOGGING.md](LOGGING.md)。
