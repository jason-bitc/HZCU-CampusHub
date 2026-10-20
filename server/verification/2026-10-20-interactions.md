# 第七阶段实际验证：点赞、收藏与LIVE数量

2026-10-20，分支feat/wc-likes-favorites，基线c6ead4abd755f89809fea778f4837fdba50db493。本文只记录后端实际执行；第六阶段验收文档是当时的证据核对，不作为本批新增运行，也不代填学生复核/人时/教师评审。

## 最终完整回归

Java21、Spring Boot4.1.1、真实MySQL campushub_test，执行mysql-it profile的verify。最终结束时间 **2026-10-20T18:38:15.8456212+08:00**，BUILD SUCCESS。49单元、76真实MySQL，共125项，失败/错误/跳过均0。构建version=0.1.0、commit=unknown，未提交工作树测试不冒用父提交。

原六批112个案例重新执行，包含49单元及63MySQL；本批新增InteractionsMySqlIT13个方法，并扩展原RequestTraceTest的新动作断言，未按断言数或请求数额外计数。旧计数/排序期望按明确LIVE合同调整，迁移断言扩展到1—7；权限、组合过滤、分页、旧数据与文件保护没有删减。

摘要与逐案：[test-result.json](interactions-2026-10-20/test-result.json)、[test-cases.csv](interactions-2026-10-20/test-cases.csv)。同目录套件.txt及受控system-output来自最后完整运行，不导出XML系统属性、本机配置或绝对路径。原日志模式脚本/JAR检查也在这次回归重跑，安全结果见[本次脚本摘要](interactions-2026-10-20/batch6-script-modes.json)，10个细项仍只属于原有一个测试方法。

## B7场景与实际方法

| 编号 | 本次实际覆盖 |
| --- | --- |
| B7-01 | 空读/不存在取消不创建关系或成功审计；本人文章点赞、收藏独立，状态转变与四次审计对照 |
| B7-02 | 同一设置12个并发请求，唯一行/单次修订/成功审计；三用户真实累计与本人取消；收藏重试不刷时间、取消再收藏新时间 |
| B7-03 | 六类入口无认证/退出/过期401、受限账号/认证撤销403；身份先于错误目标；userId/role/正文/未知重复参数拒绝，真实chunked multipart也不接受 |
| B7-04 | 本人收藏分页、时间/ID确定次序，其他人列表独立；不可用项恰为articleId/available，隐藏标题正文不泄漏；本人可取消不可用收藏，重复取消无新审计 |
| B7-05 | 下架/隐藏/删除/无有效已发布版本拒绝新增和点赞取消；恢复不生成关系；持有真实文章锁让撤回先提交，晚到点赞/收藏404且没有成功审计 |
| B7-06 | 真实数量逐篇等于active事实COUNT；旧DEMO高数不混入；所有读取入口LIVE/获币0，sourceType仍DEMO；旧计数前后快照相等 |
| B7-07 | 多用户改变真实认可顺序，点赞/收藏不改变最新排序；组合筛选及稳定分页正确；作者文章与可用收藏摘要同口径 |
| B7-08 | 审计INSERT失败及状态INSERT/UPDATE失败：新增、取消、时间、修订和真实数量回滚；解除本次故障重试成功 |
| B7-09 | 重启保留关系、激活时间、审计及不可用占位；会话过期不暗改历史状态；原六批完整回归、V1—V6和旧资料保护 |
| B7-10 | 新六类动作真实文件输出，两模式各有成功trace；受控失败合同一致，Debug代码位置可关联、Release不增长调试/诊断；API/Postman同步 |

13个InteractionsMySqlIT方法与CSV对应：

1. authenticationPrecedesTargetsAndBodyOrIdentityOverridesAreRejected
2. emptyReadsAndAbsentCancellationDoNotCreateRelationsOrAudits
3. ownArticleLikesAndFavoritesAreIndependentAuditedStateTransitions
4. parallelIdenticalSettingsHaveOneRevisionAuditAndFavoriteActivationTime
5. multipleUsersAccumulateLikesAndRestrictionsDoNotRewriteHistory
6. privateFavoritePagesUseActivationTimeAndStableArticleId
7. unavailableFavoritesAreMinimalCancellableAndRestoredFromCurrentPublication
8. hiddenDeletedAndInvalidPublicVersionsDenyLikesButPreserveFavoritePlaceholders
9. liveCountsReplaceDemoNumbersAcrossReadPathsAndRecognitionSorting
10. auditAndStateFailuresRollBackRowsCountsTimesAndSuccessEvents
11. articleLockOrdersWithdrawalAndLateWritesWithoutFalseAudits
12. allSixActionsWorkInBothModesWhileReleaseAddsNoDebugOrDiagnostics
13. restartRetainsStateActivationAndAuditAndExpiredSessionsCannotOperate

## 计数衔接的可复查证据

[历史计数快照](interactions-2026-10-20/batch7-historical-counter-snapshot.json)保存测试开始和清理后的旧文章编号/存储获币/点赞/来源，matched为真实比较结果，不含用户身份或正文。旧20/30、5/80等DEMO数字完全保留；不是通过归零旧列或写伪造关系实现LIVE。

[真实数量示例](interactions-2026-10-20/batch7-live-count-examples.json)在关系尚有效时，从实际HTTP响应提取数字和来源，并同时记录真实SQL COUNT。包括旧1001/1004/1006及本次新文章：origin=LIVE、effectiveCoins=0、effectiveLikes=factLikes，内容sourceType=DEMO。原1004的高演示点赞不影响当前真实0，1001/1006依真实点赞重排；同点赞按首发/ID确定。例子不导出昵称、标题、正文、邮箱或收藏内容。

现行合同与旧存储区分见[LIVE_COUNTS.md](../LIVE_COUNTS.md)。收藏摘要和分类按一页批量查询，公开计数使用一次GROUP BY聚合；本批没有性能/压力测量或N+1查询计时数字。

## 审计、日志与模式

[审计样本](interactions-2026-10-20/batch7-audit-sample.json)只含有限动作、内部标识、修订/状态、结果和traceId。LIKE/FAVORITE的objectId为文章ID，actorId共同定位关系；真实变化修订+1，同状态无新成功审计。不存在取消不建行，不靠计数减法制造负值。故障通过限定本次用户/文章的触发器注入，SQLState45000/vendor1644，finally移除。

[运行样本](interactions-2026-10-20/batch7-runtime-sample.txt)、[诊断样本](interactions-2026-10-20/batch7-diagnostic-sample.txt)和[六类矩阵](interactions-2026-10-20/route-matrix.csv)来自真实HTTP。矩阵为每类提供Debug、Release成功trace；写操作优先选择真实状态变化，另有无变化样本。Debug失败能关联INTERACTION、LIKE_SET、SERVICE_UNAVAILABLE、AUDIT及BusinessAudit/InteractionService项目行；Release相同失败不再新增DEBUG_STAGE或诊断字节，旧文件保留。对照见[mode-contracts.json](interactions-2026-10-20/batch7-mode-contracts.json)。

发生故障时日志中可能有事务内新修订，但ROLLBACK和无成功审计才是事实，不能拿PERSIST或新修订当提交。原线程复用、并发关联、文件轮转/故障与脚本环境恢复场景继续回归；不把一次请求产生多条日志算成多次测试。

## 数据保护、执行修正与边界

仅campushub_test、三个随机测试身份、受控邮件和独立目录；没有真实验证码外发或本人开发资料修改。ADMIN_HIDDEN通过本次文章数据夹具设置，不代表治理接口已实现。故障和清理限定本次归属，关系/审计先清、文章版本及身份随后清；原文章状态/计数、原版本内容、评论和媒体数量基线相等，自增/序列正常推进，不清库。

首轮专项有一项夹具试图将无公开版本草稿直接置为PUBLISHED，被既有ck_public_version拒绝，未记为通过。随后保留该约束断言，使用本次合法但未发布的版本检验不可见边界，没有关闭约束、改旧迁移或降低权限。首次完整125项通过后补充实际计数快照，再执行最终完整回归，本文及CSV采用最后结果。测试清理通过，没有遗留本次账号或关系。

V7已真实应用并通过Flyway验证；V1—V6内容不改，[迁移SHA](interactions-2026-10-20/migration-sha256.csv)保留七版本。原阶段报告与证据不覆盖；原55个Postman请求保持，新增6个手工步骤。混合脏报告、设计和工具另按开始摘要保护，秘密/真实运行目录/用户图/依赖/target不入Git。

没有前端按钮/真机证明，也未实现投币奖励钱包、荣誉、通知、点赞者名单、治理或运维；不代表完整安全、平台压力/性能或整体项目验收。历史有效点赞不因认证后续失效自动重写是本批明确口径，未来治理另行设计。跨请求offset页不承诺并发变化下固定快照；所有并发交错、崩溃和备份恢复不在此宣称完成。
