# Git协作约定

请按[GitHub与SourceTree详细操作指南](GITHUB_COLLABORATION.md)执行。

Git记录本机修改历史；GitHub保存已推送的提交并提供PR复核；SourceTree为Git图形客户端。保存文件、本机Commit、Push和另一成员Pull是不同步骤。

main保存经复核的团队成果。工作分支采用`类型/姓名首字母-任务名`，例如`docs/wc-srs-revision`、`docs/ljh-prototype-notes`、`feat/ljh-web-editor`。每次独立任务新建分支，不能把wc／ljh作为永久个人孤岛。

王晨统筹范围、进度、版本与合并；刘景浩共同维护开发与文档。每项修改由另一人复核。文档和代码均使用同一远端仓库，旧交付目录中的独立Git历史不删除，但不作为新的共享入口。
