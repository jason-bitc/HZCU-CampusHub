# 分支命名与协作约定

G04，2026年10月11日。会议原先讨论按姓名首字母使用wc、ljh；会后经组长授权，完善为按任务划分工作分支。这里只记录协作约定，不表示远程仓库或分支已创建。

主分支main保存经复核的可交付内容。工作分支采用“类型/姓名首字母-任务名”，类型保留feat、fix、docs三种；负责人标识wc对应王晨，ljh对应刘景浩。使用小写英文和连字符，任务名短而具体。

| 分支例子 | 含义 |
| --- | --- |
| main | 可交付的主分支 |
| feat/wc-email-auth | 王晨完成邮箱认证功能 |
| feat/ljh-web-editor | 刘景浩完成网页编辑器 |
| fix/wc-login-error | 王晨修复登录问题 |
| docs/ljh-test-plan | 刘景浩整理测试计划 |

一次分支聚焦一项任务或一组相关变更，两人可以分别推进自己的当前任务。提交后发起合并请求，由另一成员复核；王晨协调合并到main。合并后清理已完成的工作分支，下一任务从最新main创建分支；Git提交和合并记录保留个人贡献证据。

这是本组约定，具体前缀并非Git强制标准。GitHub官方建议使用简短、能描述工作的分支名，按无关变更分别建分支，通过合并请求复核，完成后删除工作分支。

参考资料：[GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow)，[Git分支名称格式](https://git-scm.com/docs/git-check-ref-format)。
