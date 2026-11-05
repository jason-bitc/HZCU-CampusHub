# 城院帮 GitHub与SourceTree操作指南

G04，2026年11月5日。王晨的GitHub账号为`jason-bitc`，刘景浩为`WLZZ233`。

项目仓库：[jason-bitc/HZCU-CampusHub](https://github.com/jason-bitc/HZCU-CampusHub)。正式文档在`docs/project`，不再通过ZIP作为日常协作入口。

## 1 先理解保存 提交 推送 拉取

|操作|结果|另一人能看到吗|
|---|---|---|
|保存Word或Markdown|只改变本机文件|不能|
|Commit 提交|在本机Git留下版本|不能|
|Push 推送|将提交上传到GitHub的工作分支|能，需查看或拉取该分支|
|创建PR|提出把工作分支并入main的请求|能复核，但main仍未改变|
|Merge 合并|复核后的改动进入main|能从main拉取|
|Pull 拉取|把远端提交取回当前本机分支|本机文件随之更新|

保存文件不会实时上传。完成一组相关修改并确认后再Commit和Push，可保留清楚的修改记录。本项目AI文档更新也遵循这套步骤，不建立每次按保存就自动推送的后台监听。

## 2 本次首次接入的状态

文档首次上传到`docs/wc-documents-sync`，PR合并后main才会拥有这些文档。合并前，双方都可以检出该远端文档分支查看。

本次接入不自动合并后端代码。11月5日核对时，main只有初始提交，后端在`feat/wc-community-foundation`。需要代码时另行检出或复核相应代码PR，不能仅拉取main就认为已有全部后端。

如果你已有`C:\Users\wangc\Desktop\HZCU-CampusHub`并且里面有未提交改动，先保留原目录，不点击“丢弃全部”、不强制切换分支。可以另选空目录克隆一份团队工作副本，先按下列流程熟悉文档协作，再协调代码分支。

## 3 王晨先核对仓库协作者

1. 王晨登录GitHub，打开[仓库访问设置](https://github.com/jason-bitc/HZCU-CampusHub/settings/access)。
2. 在`Settings → Collaborators`或`Collaborators & teams`中找到`WLZZ233`。
3. 已是可写协作者则无需再次邀请；如果只有公共仓库读取权限，点击`Add people`，搜索`WLZZ233`，确认正确账号后邀请。
4. 刘景浩使用`WLZZ233`登录GitHub，在通知或邀请页面点击接受。
5. 接受后，刘景浩可以把自己的任务分支推送到同一仓库。公共仓库能Clone不代表已经拥有写权限。

Git身份与GitHub登录不同：提交者姓名和邮箱写在Commit里；是否能Push取决于登录账户和仓库授权。两人分别使用自己的身份和GitHub账号，不共用组长账户或授权码。

## 4 两人首次用SourceTree克隆

1. 打开SourceTree，选择`Clone／克隆`。
2. 源地址填写`https://github.com/jason-bitc/HZCU-CampusHub.git`。
3. 目标路径选一个新的空目录，例如`D:\Projects\HZCU-CampusHub`。不要选组长已有脏工作区，也不要选旧交付包的内部目录。
4. 点击Clone，完成后双击仓库书签打开。
5. 如果尚未合并首次文档PR，在左侧展开`Remotes → origin`，找到`docs/wc-documents-sync`，右键选择检出并建立同名本地分支。此时即可看到`docs/project`。
6. 如果已合并PR，直接检出`main`并点击Pull，选择远端`origin`和分支`main`。
7. 在文件管理器打开`docs/project/README.md`，确认可以看到SRS、计划、可研、原型和排期目录。

Windows命令行对应操作：

```powershell
git clone https://github.com/jason-bitc/HZCU-CampusHub.git
cd HZCU-CampusHub
git fetch origin
# 首次文档PR尚未合并时，只需执行一次：
git switch --track origin/docs/wc-documents-sync
```

已克隆过就不要再次Clone覆盖目录；在原仓库执行Fetch并检出相应分支即可。

## 5 配置自己的提交身份

SourceTree通常在仓库设置或工具选项中提供用户名和邮箱。请取消强制使用别人的默认身份，分别配置。

王晨可以在仓库终端执行：

```powershell
git config user.name "王晨"
git config user.email "32401211@stu.hzcu.edu.cn"
```

刘景浩可以执行：

```powershell
git config user.name "刘景浩"
git config user.email "32401205@stu.hzcu.edu.cn"
```

如果希望GitHub把提交关联到个人头像，应使用本人GitHub已验证的邮箱或GitHub设置页给出的个人no-reply邮箱；本指南不推测no-reply地址。公开仓库的提交邮箱会显示在提交记录中，可自行选择使用已验证的no-reply邮箱。

## 6 每次开始修改前

以下日常步骤从首次文档PR已经合并开始。

1. SourceTree先看“文件状态”。若有未保存或未提交修改，先完成本次提交或暂存自己的修改，不强制覆盖。
2. 双击左侧`main`切换到主分支。
3. 点击`Fetch`，然后`Pull`，远端选`origin`、分支选`main`。不要勾选强制覆盖。
4. 点击`Branch／分支`，从更新后的main创建本次个人任务分支。
5. 王晨例子：`docs/wc-srs-revision`；刘景浩例子：`docs/ljh-prototype-notes`。

```powershell
git switch main
git pull --ff-only origin main
git switch -c docs/ljh-prototype-notes
```

不同任务换新分支；不把`wc`或`ljh`当长期不合并的独立主分支。

## 7 到哪里编辑以及同步哪些文件

|修改对象|编辑位置|同次需要检查的材料|
|---|---|---|
|项目计划／可研／SRS|`docs/project/reports`|Word、Markdown、PDF、文档版本和相关引用|
|需求模型与字典|`docs/project/models`|可编辑源、展示图、字典、追踪和SRS|
|排期分工|`docs/project/schedule`|任务源、WBS、MPP、甘特、日期、人时、负责人和人工预算|
|会议与评价|`docs/project/meetings-research`|实际日期、上周进展、问题、下周责任和评分依据|
|原型|`docs/project/prototypes`|页面、需求覆盖和反馈记录，不虚构用户确认|
|汇报|`docs/project/presentations`|PPT、PDF、讲稿、页码和提示词|

Word是现有正式报告排版源，Markdown是可审阅文字副本，PDF是阅读与提交版。只改一个格式不算完成同步。图表与MPP更新后需要重新导出展示文件。

Word、PPT、MPP不适合像代码一样自动合并内容。同一个二进制文件约定一人编辑，另一人复核；双方可以并行改不同文件。若同一文件都改了，不直接选“用我的覆盖他的”，先保留两份并确认正文差异，再由负责人整理合并版本。

## 8 提交前检查并推送 有两种操作方式

### 方式A SourceTree提交和推送

1. 完成正文与配套导出，打开本次修改的页面检查；关闭Word和Project，避免带入`~$`临时文件。
2. 在仓库终端运行下列命令，只刷新校验记录并检查，不提交或推送：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Sync-ProjectDocs.ps1 -RefreshManifest
```

3. 回到SourceTree点击“文件状态／Commit”。检查本次改动，仅暂存相关文档及`MANIFEST.sha256.csv`；不要选择所有未知文件。
4. 写具体提交说明，例如`docs(ljh): clarify author draft recovery flow`。不要只写“修改”“最终版”。
5. 点击Commit。此时仅存本机。
6. 点击Push，远端选`origin`，只勾选当前任务分支，远端分支保持同名，然后确认Push。不要Force Push。
7. 在GitHub切换到这个分支，查看最新提交与文档，确认上传成功。

### 方式B 用本项目同步脚本

在当前任务分支的仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Sync-ProjectDocs.ps1 -Commit -Push -Message "docs(ljh): clarify author draft recovery flow"
```

脚本刷新哈希、检查配套文件、只暂存限定文档路径、提交并推送当前分支。脚本不会重排Word、重新导出PDF、替你判定需求正确性或合并main。main、游离HEAD或已有其他暂存内容时会拒绝自动提交，便于先检查。

只检查已有文件：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Sync-ProjectDocs.ps1
```

## 9 在GitHub创建PR并交叉复核

1. Push后打开仓库，点击`Compare & pull request`，或`Pull requests → New pull request`。
2. `base`选`main`，`compare`选本人任务分支。
3. 点`Files changed`检查：只能出现本任务文档和必要配套，不应带入无关代码、密码或旧交付包。
4. 填标题和PR说明。模板中勾选真实完成项，写清修改哪些需求号、是否影响计划与模型、实际检查了哪些页面。
5. 选择另一成员复核：王晨的PR请`WLZZ233`复核，刘景浩的PR请`jason-bitc`复核。
6. 文档检查工作流应为通过状态；它检查完整性，不代替人工阅读。
7. Reviewer在`Files changed → Review changes`中给出意见或Approve。作者不能用自己的批准代替另一成员复核。
8. 有修改意见则在同一分支继续Commit和Push，PR自动更新，不另建重复PR。
9. 意见处理后，组长核对并点击`Merge pull request`，通常采用Create a merge commit以保留协作历史。
10. 合并后可删除已完成远端任务分支；不要删除尚未合并的任务。

需要查看Word、PPT或MPP时下载文件，用相应应用打开；Markdown可以在线读并查看逐行差异，PDF可预览或下载。PR中的“文件发生变化”不等于已经核验其排版和模型。

## 10 另一人怎样取得更新

已合并main：SourceTree切到main，点击Pull，选择`origin/main`。

```powershell
git switch main
git pull --ff-only origin main
```

尚未合并但需要查看对方草稿：先Fetch，在`Remotes/origin`检出对方工作分支，不要把未经复核的草稿当main成果。

```powershell
git fetch origin
git switch --track origin/docs/wc-srs-revision
```

分支已在本机存在时，改用`git switch docs/wc-srs-revision`，随后`git pull --ff-only`。`pull origin main`会把main合入当前分支，不会自动替你切换分支，所以先看左侧或`git branch --show-current`。

## 11 常见问题

|现象|先检查和处理|
|---|---|
|我改了文件，伙伴看不到|是否Commit后又Push；双方是否查看同一分支|
|Pull后仍是旧文档|是否在正确仓库、正确分支；新PR是否已合并；是否仍打开旧交付目录|
|Push 403／permission denied|是否登录本人正确账号；`WLZZ233`是否接受协作者邀请；不要向聊天粘贴令牌|
|Push rejected／non-fast-forward|先Fetch并核对远端新增提交，按具体冲突处理，不强推|
|切分支提示本地改动会覆盖|保留文件，完成本次提交或暂存自己的工作；不点丢弃全部|
|Word／MPP冲突|保留双方版本，由文件负责人整合，重新导出并复核|
|CI提示哈希不符|先确认修改确实是本次内容并完成配套文件，再刷新清单、Commit和Push|
|CI通过但文档有错|CI只查完整性，按人工评审修订同一PR|
|提示GitHub repository already exists|使用现有仓库Clone或Add existing，不新建同名仓库|

不要在仓库中保存GitHub密码、令牌、SMTP授权码、数据库密码或实际运行日志。账户认证通过本机Git凭据管理器或SourceTree账号连接完成。

## 12 本次接入后的日常分工

王晨负责文档基线、计划与SRS统筹、PR合并协调；刘景浩共同维护原型、网页／管理相关需求与验证材料。每次周会决定下一周具体文件负责人，实际贡献以提交、评审及产物记录为证，不按AI使用次数算贡献。

本对话后续更新正式文档时，按本目录规则完成配套修订、检查、Commit和Push到个人文档分支；若推送失败，会保留本机成果并明确报告，不只提供ZIP。课堂提交需要的PDF或ZIP可在验收版本上按需导出，团队内部日常仍以GitHub为准。

## 参考资料

- [GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow)：分支、提交、推送、PR与复核合并。
- [GitHub邀请个人仓库协作者](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/inviting-collaborators-to-a-personal-repository)。
- [Atlassian SourceTree提交推送拉取](https://support.atlassian.com/sourcetree/kb/commit-push-and-pull-a-repository-on-sourcetree/)：本文采用其基础操作，具体按钮译名可随客户端版本变化。
- [GitHub大文件说明](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github)：本项目共享文档暂设单文件25MiB上限，当前无需引入Git LFS；大型原始扫描件与历史ZIP不纳入。
