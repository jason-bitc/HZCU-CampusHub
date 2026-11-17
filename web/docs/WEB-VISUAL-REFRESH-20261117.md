# G04 网页读者、作者与个人服务视觉验收

基线为 main 134f8f75a0580eb567567415b4d4753f26618fc7，包含 PR #44。本轮另接入浅灰、白卡、蓝色操作与状态色变量，沿用现有 G04 标识和普通网页独立入口。

| 页面、WBS 与需求 | 改前 | 改后与复现 |
| --- | --- | --- |
| W01 发现，WBS 5.4.2，FR-R01–R04 | 发现结果全部纵排，搜索、条件和排序视觉轻重接近 | 搜索独立一行，桌面双列呈现卡片，窄屏单列、组合筛选默认收起。选 Java 岗位进入经验，再返回确认筛选、焦点和滚动。无公开内容与无匹配有各自提示。 |
| W02–W03 阅读、评论、作者与互动，WBS 5.4.2／5.8，FR-R05–R06、FR-I01–I04、FR-G03 | 正文卡提醒和阅读动作混用较重绿色，关联信息的主次接近 | 正文宽度上限 75ch，导航、目录、图片失败、评论和操作反馈沿用常用控件。登录后跳到末章并收藏，390px 检查目录、正文、评论和回顶位置。 |
| W05–W07、S03 编辑、发布和工作区，WBS 5.5／5.7，FR-C01–C09 | 编辑工具、发布及预览区域背景较近 | 正文写作、预览和表单划分明确，保留校验、键盘焦点、修订和备份。模拟图片回执丢失时继续输入，再用原请求回读。用另一会话推高修订，核对 409、合并并发布。 |
| W08–W10、S01/S04 收藏、个人资料、联系、通知、钱包、反馈、限制与申诉，WBS 5.7／5.9，FR-I04、FR-P01–P03、FR-A04–A05、FR-G04、FR-M01、FR-M04、FR-M07 | 顶部个人服务是文字链接，收藏在小视口通过横向表格访问 | 桌面居中显示资料与联系，常用个人服务入口用线性图标。窄屏收藏按行展示有名称的字段，保留跳转和移除。刷新通知失败时核对上一批通知和未读数仍在，重试后依接口回读。 |

## 执行与结果

1. 用本机测试业务服务 8080，从 web 运行 npm run dev，并访问 5173。浏览器拍摄 1440px、768px、390px。运行页合计 27 个页面与视口组合检查，无文档级横向溢出。
2. npm test 通过 24 项。npm run build 完成 TypeScript 检查、user、admin、ops 三入口打包。
3. 使用项目现有 web-ux-browser.mjs 在 Chrome 上通过 10 条场景。这批场景连接隔离 MySQL 的真实 8080，覆盖真实筛选、收藏、评论、上传、发布、409、下架和恢复、资料与联系保存、通知和申诉。通知失败和上传回执丢失由浏览器注入，随后恢复真实请求核对回执。
4. 真机、真实邮箱送达和真实用户验收待执行。纯视觉交接未在正式数据重放写操作。

## 实际截图

- 发现 [1440px 改前](screenshots/20261117-member-visual/before-reader-discover-1440.png) · [改后](screenshots/20261117-member-visual/after-reader-discover-1440.png) · [768px 改前](screenshots/20261117-member-visual/before-reader-discover-768.png) · [改后](screenshots/20261117-member-visual/after-reader-discover-768.png) · [390px 改前](screenshots/20261117-member-visual/before-reader-discover-390.png) · [改后](screenshots/20261117-member-visual/after-reader-discover-390.png)
- 长文 [390px 改前](screenshots/20261117-member-visual/before-reader-article-390.png) · [改后](screenshots/20261117-member-visual/after-reader-article-390.png)
- 编辑 [390px 改前](screenshots/20261117-member-visual/before-author-editor-390.png) · [改后](screenshots/20261117-member-visual/after-author-editor-390.png)
- 个人中心 [1440px 改前](screenshots/20261117-member-visual/before-reader-me-1440.png) · [改后](screenshots/20261117-member-visual/after-reader-me-1440.png)
