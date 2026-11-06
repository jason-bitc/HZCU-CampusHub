# 第二阶段账号与校园邮箱认证接口

2026-10-12。基础路径 `/api/v1`，默认本机 `127.0.0.1:8080`，只做后端。游客文章合同继续见 [API.md](API.md)。所有成功及失败使用原有统一响应和追踪编号，认证接口设置 `Cache-Control: no-store`，不把敏感响应存入公共缓存。

## 邮箱、密码及请求约束

第一版仅接受`stu.hzcu.edu.cn`校园邮箱，去除首尾空格并转为小写。精确匹配域名，不固定学号位数；本轮允许ASCII字母、数字及常用点/加号/下划线/百分号/连字符，邮箱本地部分最多64字符，不能连续点。其他域名、域名伪装、控制字符和无效格式400。

应用登录密码为8—64个Unicode字符，UTF-8最多72字节；超过任一限制明确拒绝，不截断，不去除密码空格。BCrypt成本12，保存带`{bcrypt}`算法标识的哈希。它与邮箱客户端授权密码分别用途，不能共用发送配置字段。

请求使用`application/json`，所有列出的字段必须是字符串且全部提供；未知字段（含role/admin/verification等）、缺字段、非法JSON400，不支持的Content-Type415。POST身份请求体最多16KiB，含分块请求，超过413。验证码必须是6位数字字符串，挑战ID为服务器提供的UUID。不得将密码或验证码放在URL查询中。

## POST /auth/registration-codes

输入 `{"email":"YOUR_MAILBOX@stu.hzcu.edu.cn"}`。SMTP成功接受后HTTP 202返回 `data={"challengeId":"UUID","expiresAt":"UTC时间","resendAt":"UTC时间","notice":"..."}`，没有验证码或邮箱授权信息。客户端或用户收件尚待核对，202不表示已送达。

验证码5分钟有效；同邮箱同用途60秒内不可重发，每个滚动小时最多5次尝试（包括发信失败的尝试）。超限429。新邮件发送成功且数据库提交后，旧挑战失效；发送失败503 `MAIL_UNAVAILABLE`，失败挑战不能注册，旧成功挑战继续按原期限有效。数据库错误503 `SERVICE_UNAVAILABLE`。

摘要采用HMAC-SHA256绑定邮箱、用途、挑战ID和验证码；本机持久摘要密钥单独配置。邮件网络操作有超时，同邮箱同用途由数据库锁串行处理，无后台群发或自动重试。

## POST /auth/register

输入 `{"email":"...","password":"本机输入","challengeId":"UUID","code":"收到的6位数字"}`。成功HTTP 201返回本人 `userId,email,role=USER,accountState=ACTIVE,campusCertification=VALID,verifiedAt`，不自动登录，不返回令牌。

验证码必须属于同邮箱和REGISTER用途，处于成功发送状态且未到期。错误、过期、重放、用途不符或被替换返回400 `CODE_INVALID`；每个挑战最多5次错误，达到后锁定，正确码也不能再使用。错误尝试与状态更新实际提交。成功账号创建、校园认证及挑战消费在同一事务完成，唯一邮箱约束防止并发重复账号。已注册邮箱在验证正确后返回409 `ACCOUNT_EXISTS`。

注册只能创建普通用户，邮箱认证不证明经历或文章真实性，也不会创建社区或运维管理权限。本轮没有将文章演示数据自动关联为真实用户亲历。

## POST /auth/login

输入 `{"email":"...","password":"..."}`。成功HTTP 200返回 `data={"tokenType":"Bearer","accessToken":"不透明会话令牌","expiresAt":"UTC时间"}`。每次登录创建独立会话，使用256位随机令牌，数据库只保存SHA-256摘要。绝对有效期7天，不滑动续期。

邮箱不存在、密码错误、账号受限或校园认证已撤销统一401 `LOGIN_FAILED`。未知账号也执行密码匹配成本，接口不返回内部账号存在性原因。密码重置与会话创建对账号行加锁，避免旧密码核验后在重置完成后再创建有效会话。

## GET /me

请求头 `Authorization: Bearer <accessToken>`。有效会话且账号及校园认证有效时200返回本人标识、邮箱、普通角色和认证状态。没有、错误、撤销或到期的令牌401 `UNAUTHORIZED`；账号受限或校园认证撤销403 `ACCESS_RESTRICTED`。每次新请求从数据库重新检查状态，不信任客户端声明。

不提供查询他人身份的userId参数。公开文章接口继续返回游客字段，不会因为提交Bearer头新增作者邮箱、昵称、头像、联系方式或评论。

## POST /auth/logout

同Bearer头，成功200 `data={"loggedOut":true}`，只撤销当前会话。撤销后该令牌401，其他端会话仍有效。账号受限或认证撤销后仍可用尚未过期的本人会话退出。没有或失效会话401；此合同不将重复退出伪称新撤销成功。

## POST /auth/password-reset-codes

输入邮箱，HTTP 202返回与注册挑战相同的结构，notice始终为“请求已受理，若账号可重置，将收到验证码邮件”。独立PASSWORD_RESET用途及相同重发、小时限额、有效期和错误次数。

对于未注册的合法校园邮箱，响应结构相同，但不会发送邮件、建立账号或创建可用重置挑战，数据库状态为NOT_APPLICABLE。此202仅表示请求受理；已有账号的真实邮件发送失败仍503。当前同步发信实现的耗时及邮件服务故障可能提供侧信道，接口字段不会直接透露存在性；不宣称完全恒定时间或公网滥用防护已经验收。

## POST /auth/password-resets

输入email、password（新密码）、challengeId、code。正确独立重置挑战成功200 `data={"passwordReset":true}`；事务内更新哈希、消费挑战并撤销账号全部旧会话，要求重新登录。不恢复受限状态或已撤销校园认证，不改变角色。注册挑战不能用于密码重置，重置挑战不能注册。

## 错误与对接

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | INVALID_PARAMETER / CODE_INVALID | 字段格式错误或验证码不可用 |
| 401 | UNAUTHORIZED / LOGIN_FAILED | 会话或登录凭据不可用 |
| 403 | ACCESS_RESTRICTED | 当前账号或认证不能取得受限身份数据 |
| 409 | ACCOUNT_EXISTS | 已验证邮箱已经有账号 |
| 413 / 415 | REQUEST_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE | 内容大小或请求类型错误 |
| 429 | RATE_LIMITED | 同邮箱同用途请求太频繁 |
| 503 | MAIL_UNAVAILABLE / SERVICE_UNAVAILABLE | 邮件或数据库暂时不可用 |

前端收到令牌后放在适当的受保护存储，不写日志或URL；退出清理端侧本人资料，401重新登录，403说明状态不可用。验证码使用notice与expiresAt、resendAt显示必要反馈；失败不能显示发送成功。WorkBuddy后续完成页面、端侧安全及真机验证，本批未实现这些页面。
## 独立社区管理入口

2026-10-22第九阶段新增管理命名空间，见[COMMUNITY_API.md](COMMUNITY_API.md)及[本机初始化](COMMUNITY_ADMIN_SETUP.md)。上述普通校园账号合同及USER约束保持；普通注册不能授管理权，管理令牌不能替代校园用户令牌。
