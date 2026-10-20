# 公开文章接口合同 v1（第七阶段真实计数增量）

默认本机地址 `http://127.0.0.1:8080`。本文接口无需登录，仅游客可读字段。所有时间为UTC ISO-8601，文章/分类ID为正整数。

本文件继续描述公开读取合同；身份接口见[AUTH_API.md](AUTH_API.md)，第三阶段认证作者写接口见[AUTHORING_API.md](AUTHORING_API.md)。游客列表/详情新增updatedAt，不增加真实作者私有资料。

2026-10-20第七阶段：counts结构保留，当前统计统一LIVE，旧DEMO/NONE存储值保留但不混入真实数量。点赞/收藏为单独认证接口，见[INTERACTIONS_API.md](INTERACTIONS_API.md)及[计数衔接](LIVE_COUNTS.md)。历史验证记录不因此追记为当时已实现LIVE。

## 响应与追踪

成功为 `{"code":"OK","data":{},"traceId":"UUID"}`，失败为 `{"code":"NOT_FOUND","message":"内容不存在或不可用","traceId":"UUID"}`。成功不含message，失败不含data。响应头 `X-Trace-Id` 与正文相同，由服务器生成，不信任请求头输入。HTTP 400为参数错误，404为路径或内容不可用，405为方法错误，503为数据库服务不可用，500为未预期处理错误。

## GET /actuator/health

真实应用及数据库健康检查。HTTP 200返回 `data={"status":"UP"}`，失败HTTP 503返回统一失败响应。只公开必要状态，没有内部组件或数据库连接信息。

## GET /api/v1/catalog

返回启用的分类，固定次序为display_order及id升序。

```json
{"code":"OK","data":{"jobs":[{"id":1,"name":"Java后端开发（演示）"}],"majors":[],"companies":[],"topics":[]},"traceId":"UUID"}
```

此处仅显示结构，实际演示分类为2岗位、2专业、2公司、3主题。文章已有分类即使后续禁用仍保留标签；catalog只提供当前可选分类。

## GET /api/v1/articles

| 参数 | 约束与语义 |
| --- | --- |
| keyword | 可选，去首尾空白后最多100个UTF-16字符；禁止控制字符；空白同不筛选；检索标题及摘要，不检索正文 |
| jobId / majorId / companyId / topicId | 可选正整数，多个条件同时满足；不存在或类别不匹配得到空列表 |
| sort | 默认recognized，另支持latest，大小写严格 |
| page | 从1开始，默认1，上限1000000 |
| size | 默认20，1至100，超过上限返回400 |

重复参数、未知参数、空数字、不合法数字或排序均400。关键词 `%`、`_`、`!`作为字面字符匹配；单引号等通过SQL参数绑定。采用MySQL utf8mb4_0900_ai_ci的大小写/重音比较规则。所有过滤在排序前生效，不放宽条件。

recognized为有效获币（本批固定0）、真实有效点赞、首次发布时间、文章id依次降序；latest为首次发布时间、文章id降序。当前counts.origin统一LIVE，effectiveLikes只统计真实未取消点赞，不加旧DEMO值或收藏数。publishedAt保持首发；updatedAt记录当前公开版本发布时间，不因点赞/收藏更新。列表项如下，数量0仅示例，实际按事实读取；categories内四类均为数组，公司可为空。

```json
{
  "id": 1001,
  "title": "【演示】Java后端实习准备路线",
  "summary": "演示从Java基础到项目表达的准备路线。",
  "categories": {"jobs":[{"id":1,"name":"Java后端开发（演示）"}],"majors":[{"id":11,"name":"计算机科学与技术"}],"companies":[{"id":21,"name":"示例科技A（虚构）"}],"topics":[{"id":31,"name":"准备路线"}]},
  "experienceStage": "大三实习准备",
  "sourceType": "DEMO",
  "publishedAt": "2026-10-08T02:00:00Z",
  "updatedAt": "2026-10-08T02:00:00Z",
  "counts": {"effectiveCoins":0,"effectiveLikes":0,"origin":"LIVE"}
}
```

data结构为 `{"items":[列表项],"total":6,"page":1,"size":20}`，无结果或超过末页返回 `items=[]`，total仍为匹配总数。列表不返回全文、内部状态、版本id、作者账号或身份资料。每次读取在MySQL REPEATABLE_READ只读事务中保持计数、行和分类快照一致；稳定数据下分页无重复遗漏，跨请求期间数据改变时不承诺offset分页固定快照。

示例 `/api/v1/articles?keyword=Java&jobId=1&majorId=11&companyId=21&topicId=31&sort=latest&page=1&size=20`。

## GET /api/v1/articles/{id}

data包含与列表项相同的字段，并增加 `body`（原始Markdown）、`bodyFormat=MARKDOWN`、`sourceNote`、`sourceLinks=[{"label":"来源名","url":"https://..."}]`。sourceType允许FIRST_HAND亲历、CURATED资料整理、DEMO演示；当前演示集均为DEMO。

只读取PUBLISHED文章指向的已发布版本。新增未发布修订不替换公开正文或标签。id格式错误400；不存在、草稿、作者下架、管理员隐藏、删除统一404，不返回原因、标题、摘要或正文。

演示公开ID为1001至1006；非公开2001至2004。`1001`含独立未发布修订供边界验证。没有任何作者昵称、头像、账号标识、身份材料、独立联系方式或评论字段；正文内自行公开的文字不删改。

## WorkBuddy对接

先请求catalog生成筛选选项，切换筛选或排序时把page重设为1；依次请求列表及详情。没有结果、网络失败和503分别显示；404按内容不可用处理。保留traceId用于报错定位。内容sourceType=DEMO仍明确“演示”，counts.origin=LIVE表示当前真实聚合；获币固定0不表示钱包/奖励已实现。发布、认证、评论及点赞收藏可按各自后端合同接入，须实际成功后更新界面；投币尚未实现。游客响应不含liked/favorited，必须通过本人互动接口读取。

此合同只说明后端已提供的读取能力。Markdown的安全渲染、外链自愿打开、移动设备地址配置与真机验证由后续前端实现和联调记录证明。
