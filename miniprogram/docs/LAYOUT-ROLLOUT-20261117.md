# G04 小程序布局推广验收

以 PR #47 的 `e0b8454c9de8404013314a980dd6aed222482048` 为改前基线。发现与个人中心样板见 [布局样板验收](LAYOUT-SAMPLES-20261117.md)。本轮沿用两页样板，改造其余阅读、创作和个人服务。分支为 `feat/ljh-miniprogram-visual-refresh`。 [PR #47](https://github.com/jason-bitc/HZCU-CampusHub/pull/47)

| 页面／WBS／需求 | 改前 | 改后与 375px 运行截图 |
| --- | --- | --- |
| M05–M06 长文及互动，5.4.2／5.8，FR-R05–R06、FR-I01–I04 | 多层资料与标签推迟正文，跟读和操作相隔较远 | 标题、作者、标签之后直接进入正文。互动反馈靠近底栏，目录仍实测 44×44。 [改前](layout-rollout-20261117/before-native-article-detail-375.jpg) · [改后](layout-rollout-20261117/after-native-article-detail-375.jpg) |
| M07 评论，5.8，FR-I01–I02、FR-M01 | 评论与编辑区权重接近 | 评论时间、作者与引用回复分层，评论表单独立。 [改前](layout-rollout-20261117/before-native-comments-375.jpg) · [改后](layout-rollout-20261117/after-native-comments-375.jpg) |
| M09 作者，5.8，FR-I05、FR-I07、FR-G05、FR-A05 | 作者信息与作品间切换较生硬 | 身份与联系在资料卡集中说明，作品沿用发现的经验卡顺序。 [改前](layout-rollout-20261117/before-native-author-375.jpg) · [改后](layout-rollout-20261117/after-native-author-375.jpg) |
| M17 收藏，5.7／5.8，FR-I04、FR-P01、FR-C08 | 卡片和取消收藏视觉权重接近 | 正常文章以标题、分类、更新时间与继续阅读排列，附属动作弱化。不可用内容继续按公开边界投影。 [改前](layout-rollout-20261117/before-native-favorites-375.jpg) · [改后](layout-rollout-20261117/after-native-favorites-375.jpg) · [有收藏的 320px](layout-rollout-20261117/after-native-favorites-320.jpg) |
| M18–M19 工作区，5.6／5.7，FR-P01、FR-C05、FR-C07–C08、FR-M04 | 标题、状态与新建分别占行，卡片动作并列 | 标题和新建对齐，一行状态导航，标题之后显示状态和时间，再列可用动作。 [改前](layout-rollout-20261117/before-native-workspace-375.jpg) · [改后](layout-rollout-20261117/after-native-workspace-375.jpg) |
| M20–M25 编辑、图片、发布、冲突，5.6，FR-C01–C09、FR-P03 | 写作提示和层层卡片先于正文，检查提示靠近底部 | 标题、正文优先。图片和发布分组，发布检查逐项定位，冲突独立展示两个版本。 [改前](layout-rollout-20261117/before-native-editor-375.jpg) · [改后](layout-rollout-20261117/after-native-editor-375.jpg) · [缺项](layout-rollout-20261117/M22-publish-checklist.jpg) · [真实 409](layout-rollout-20261117/M24-draft-conflict.jpg) |
| M26–M27 钱包与奖励，5.7，FR-G01–G02、FR-G04–G05 | 钱包及任务区缺少可扫描的时间层级 | 白色账户摘要、奖励、账目三段。账目以类型、时间与收支分列。 [改前](layout-rollout-20261117/before-native-wallet-375.jpg) · [改后](layout-rollout-20261117/after-native-wallet-375.jpg) |
| M28–M29 反馈与举报，5.9，FR-M01、FR-M07、FR-P02–P03、FR-C04 | 表单和案件结果提示间距松散 | 类型、说明、附图和提交顺排，处理记录显示时间与公开答复。表单对照为通用入口。 [表单改前](layout-rollout-20261117/before-native-feedback-form-375.jpg) · [改后](layout-rollout-20261117/after-native-feedback-form-375.jpg) · [进度改前](layout-rollout-20261117/before-native-feedback-375.jpg) · [改后](layout-rollout-20261117/after-native-feedback-375.jpg) |
| M30 限制与申诉，5.9，FR-A04、FR-M04、FR-P02 | 当前限制和记录的层级接近 | 两个独立入口，范围、原因、版本、原始答复按内容分组。 [改前](layout-rollout-20261117/before-native-appeals-375.jpg) · [改后](layout-rollout-20261117/after-native-appeals-375.jpg) |
| M31 通知，5.7／5.9，FR-P02、FR-A04、FR-M07 | 通知摘要、已读及跳转视觉相近 | 未读、处理结果和普通消息可辨；保留旧数据与目标跳转。 [改前](layout-rollout-20261117/before-native-notifications-375.jpg) · [改后](layout-rollout-20261117/after-native-notifications-375.jpg) |
| M10–M13、M15、M16、M32 登录、认证、个人资料、联系与设置，5.5／5.7，FR-A01–A05、FR-I06、FR-P01–P03 | 账户说明占首屏，资料与设置容易混在同一层 | 账户表单前移，认证状态独立。个人资料与联系保存反馈分别显示。 [账号改前](layout-rollout-20261117/before-native-account-375.jpg) · [改后](layout-rollout-20261117/after-native-account-375.jpg) · [认证改前](layout-rollout-20261117/before-native-certification-375.jpg) · [改后](layout-rollout-20261117/after-native-certification-375.jpg) · [资料改前](layout-rollout-20261117/before-native-profile-375.jpg) · [改后](layout-rollout-20261117/after-native-profile-375.jpg) · [设置改前](layout-rollout-20261117/before-native-settings-375.jpg) · [改后](layout-rollout-20261117/after-native-settings-375.jpg) |

## 复现及分层结果

1. 在微信开发者工具以实际业务模式打开 `miniprogram`，分别选 375px 和 320px。发现选 Java 岗位并加同专业筛选，滚动打开 2005 号长文，跳到最后一章、收藏后返回。校园测试账号在 375px 的真实请求回读收藏成功，返回保留两个筛选条件和滚动位置。320px 退出登录后再次打开文章、从末章进入登录，确认返回第 67 个标题、纵向位置 5315.5px。
2. 375px 工作区新建测试草稿，发布预览点击缺项，选图并保存。在另一会话保存同一草稿的较新修订后，用原编辑会话再保存。真实 8080 返回冲突，本机正文、新输入及图片引用保留。 [已上传](layout-rollout-20261117/M21-image-uploaded.jpg)
3. 从通知打开本人反馈，返回后核对已读时间；两项均由真实 API 回读。打开钱包、限制与申诉、资料和认证页核对状态层级。认证撤销账号本轮走查登录后未落在预期页面，撤销原因和复核答复的端到端回归待复跑。
4. 320px 胶囊实测 x227–314、y28–60，系统安全区下缘 568px。发现长标题、真实筛选无匹配、编辑器和底栏均用实际模拟器检查。 [发现空态](layout-rollout-20261117/after-native-discover-empty-320.jpg) · [编辑器](layout-rollout-20261117/after-native-editor-320.jpg) · [个人中心底栏](layout-rollout-20261117/after-native-me-bottom-320.jpg) · [登录后原章](layout-rollout-20261117/after-native-login-restore-320.jpg)
5. 自动化通过 59 项，工程 10、行为 16、会员 33。真实公开 API 检查 21 条，覆盖健康、分类、分页、组合筛选、详情及预期错误。模拟器尺寸与登录章节回归分别记录于本机证据目录 `tmp/layout-rollout-20261117`。

真机、真实邮箱、真实用户、实际扣币与第二设备签收待验收。
