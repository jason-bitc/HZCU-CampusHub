const catalog = {
  jobs: [
    { id: 1, name: 'Java后端开发（演示）' },
    { id: 2, name: '前端开发（演示）' },
    { id: 3, name: '软件测试（演示）' }
  ],
  majors: [
    { id: 11, name: '计算机科学与技术' },
    { id: 12, name: '软件工程' },
    { id: 13, name: '人工智能' }
  ],
  companies: [
    { id: 21, name: '示例科技A（虚构）' },
    { id: 22, name: '示例科技B（虚构）' }
  ],
  topics: [
    { id: 31, name: '准备路线' },
    { id: 32, name: '面试复盘' },
    { id: 33, name: '简历修改' }
  ]
};

const longBody = `# 先确认自己的起点

这份内容仅用于演示公开长文阅读，不代表真实校友经历。我把准备过程分成基础、项目和表达三个部分，每周只确认一个可以完成的目标。

## 基础：把知识用起来

前两周复习 Java 语法、数据结构和数据库，再通过一个小项目串联知识。遇到不熟悉的概念时，先记录问题，再回到官方文档核对。

> 经验内容可以自由阅读。真正重要的是适合自己的节奏，而不是机械复制他人的时间表。

### 一个可执行的小清单

1. 每周完成一个可展示的功能。
2. 为关键取舍写下两条理由。
3. 用一次完整复盘代替无目的刷题。

- 保留必要的休息时间。
- 不编造项目数据或面试结果。
- 对外部资料注明来源。

## 项目：说明取舍而不是堆名词

项目表达应当包含问题、方案、验证与不足。下面是一段代码展示，模板中的标签和脚本只会作为纯文本呈现，不会执行。

\`\`\`java
String message = "<script>alert('not executed')</script>";
System.out.println(message);
\`\`\`

![项目记录示例](https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1200&q=80)

## 面试：把复盘写成下一次行动

面试后记录没有回答清楚的问题，再决定补基础还是调整表达。长正文用于检查连续阅读时的段落宽度、标题层级、引用、列表、代码与图片布局。

![加载失败示例](https://invalid.campushub.local/missing.png)

### 结果与不足

本轮仍缺少真实业务规模下的验证，因此不会把演示结果写成已经通过性能或真机验收。`;

const articles = [
  {
    id: 1001,
    title: '【演示】Java后端实习准备路线',
    summary: '从Java基础到项目表达，整理一条可以按周执行的准备路线。',
    categories: { jobs: [catalog.jobs[0]], majors: [catalog.majors[0]], companies: [catalog.companies[0]], topics: [catalog.topics[0]] },
    experienceStage: '大三实习准备', sourceType: 'DEMO', publishedAt: '2026-10-08T02:00:00Z', updatedAt: '2026-10-08T02:00:00Z',
    counts: { effectiveCoins: 0, effectiveLikes: 4, origin: 'LIVE' }, body: longBody, bodyFormat: 'MARKDOWN',
    sourceNote: '公开课程笔记与个人演示整理，内容未作为真实亲历经历核验。',
    sourceLinks: [{ label: 'Markdown 语法参考', url: 'https://www.markdownguide.org/basic-syntax/' }]
  },
  {
    id: 1002,
    title: '【演示】第一次投递，我踩过的三个坑',
    summary: '简历准备、投递节奏与面试复盘，给第一次投递的同学参考。',
    categories: { jobs: [catalog.jobs[0]], majors: [catalog.majors[1]], companies: [], topics: [catalog.topics[2]] },
    experienceStage: '大三暑假', sourceType: 'DEMO', publishedAt: '2026-10-10T02:00:00Z', updatedAt: '2026-10-10T02:00:00Z',
    counts: { effectiveCoins: 0, effectiveLikes: 2, origin: 'LIVE' }, body: '## 简历只保留能解释的内容\n\n每一个项目点都应该能说明自己的行动和结果。\n\n## 复盘\n\n- 投递记录及时更新。\n- 面试问题当天整理。', bodyFormat: 'MARKDOWN', sourceNote: '演示内容。', sourceLinks: []
  },
  {
    id: 1003,
    title: '【演示】前端项目如何从页面列表走向完整任务闭环',
    summary: '把筛选、详情、失败恢复与验证证据串起来，而不是只完成静态页面。',
    categories: { jobs: [catalog.jobs[1]], majors: [catalog.majors[1]], companies: [catalog.companies[1]], topics: [catalog.topics[0]] },
    experienceStage: '大二暑期实践', sourceType: 'DEMO', publishedAt: '2026-10-12T02:00:00Z', updatedAt: '2026-10-12T02:00:00Z',
    counts: { effectiveCoins: 0, effectiveLikes: 7, origin: 'LIVE' }, body: '# 先定义任务\n\n页面完成不等于任务闭环。\n\n1. 写清输入。\n2. 处理失败。\n3. 保存验证证据。', bodyFormat: 'MARKDOWN', sourceNote: '演示内容。', sourceLinks: []
  },
  {
    id: 1004,
    title: '【演示】测试实习面试后的复盘框架',
    summary: '从用例设计、边界意识到表达结构整理复盘。',
    categories: { jobs: [catalog.jobs[2]], majors: [catalog.majors[0]], companies: [], topics: [catalog.topics[1]] },
    experienceStage: '大三实习准备', sourceType: 'DEMO', publishedAt: '2026-10-14T02:00:00Z', updatedAt: '2026-10-14T02:00:00Z',
    counts: { effectiveCoins: 0, effectiveLikes: 1, origin: 'LIVE' }, body: '## 用例设计\n\n先区分正常、边界与失败，再说明优先级。\n\n> 不要把未执行的检查写成通过。', bodyFormat: 'MARKDOWN', sourceNote: '演示内容。', sourceLinks: []
  },
  {
    id: 1005,
    title: '【演示】一段很长很长的经验标题用于检查连续换行和不同屏幕宽度下的阅读舒适度',
    summary: '长标题不应挤压标签或操作区域，摘要仍保持清楚的信息层级。',
    categories: { jobs: [catalog.jobs[1]], majors: [catalog.majors[2]], companies: [catalog.companies[0]], topics: [catalog.topics[1]] },
    experienceStage: '大四秋招', sourceType: 'DEMO', publishedAt: '2026-10-16T02:00:00Z', updatedAt: '2026-10-16T02:00:00Z',
    counts: { effectiveCoins: 0, effectiveLikes: 0, origin: 'LIVE' }, body: longBody, bodyFormat: 'MARKDOWN', sourceNote: '演示内容。', sourceLinks: []
  },
  {
    id: 1006,
    title: '【演示】从零整理一份项目表达提纲',
    summary: '背景、行动、验证和不足，四部分说清项目。',
    categories: { jobs: [catalog.jobs[0]], majors: [catalog.majors[1]], companies: [catalog.companies[0]], topics: [catalog.topics[0], catalog.topics[2]] },
    experienceStage: '大三实习准备', sourceType: 'DEMO', publishedAt: '2026-10-18T02:00:00Z', updatedAt: '2026-10-18T02:00:00Z',
    counts: { effectiveCoins: 0, effectiveLikes: 5, origin: 'LIVE' }, body: '## 背景\n\n为什么做。\n\n## 行动\n\n具体做了什么。\n\n## 验证\n\n怎样知道有效。', bodyFormat: 'MARKDOWN', sourceNote: '演示内容。', sourceLinks: []
  }
];

module.exports = { catalog, articles };
