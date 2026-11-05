const catalog = {
  jobs: [
    { id: 1, name: 'C++后端' },
    { id: 2, name: '前端' },
    { id: 3, name: '测试' }
  ],
  majors: [
    { id: 11, name: '软件工程' },
    { id: 12, name: '计算机科学与技术' },
    { id: 13, name: '人工智能' }
  ],
  companies: [
    { id: 21, name: '示例科技A（虚构）' },
    { id: 22, name: '示例科技B（虚构）' }
  ],
  topics: [
    { id: 31, name: '准备路线' },
    { id: 32, name: '面试复盘' }
  ]
};

const article1001Body = `## 01 先把基础补齐

我把八周准备分成基础、项目、简历、投递、笔试、面试、复盘和选择八个部分。每一周只确认一个可以完成的目标，避免把计划写成无法执行的愿望清单。

前两周先复习C++语法、数据结构、操作系统和网络基础。复习时不只看概念，而是把每个知识点放回一个可运行的小练习里，确认自己能解释输入、处理过程和结果。

第一周围绕语言基础和内存管理，整理指针、引用、常见容器和基础输入输出；第二周围绕数据结构和网络，手写常见结构的增删查过程，并解释时间复杂度。每天结束前留下三条记录：今天完成了什么、卡在哪里、明天第一步做什么。

## 02 用项目把知识串起来

第三周和第四周只做一个能讲清取舍的小项目。项目不需要堆很多技术名词，重点是能回答三个问题：为什么做、具体做了什么、怎样证明它有效。

- 背景：课程设计缺少一个能持续记录练习结果的工具。
- 行动：完成数据模型、命令行交互和文件保存。
- 验证：用固定输入检查输出，并记录一次失败修复过程。
- 不足：没有并发场景，也没有完整异常恢复。

项目表达不追求“大而全”，而是让面试官能沿着一个具体问题继续追问。每次修改代码都保留简短记录：修改前的问题、采用的办法、验证结果和仍未解决的限制。这样复盘时不会只记得最终结果，却说不出过程。

\`\`\`cpp
struct WeeklyGoal {
    std::string topic;
    std::string action;
    std::string evidence;
};

WeeklyGoal current{"项目", "补齐保存失败重试", "保留一次可复现记录"};
\`\`\`

![项目记录示例](https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1200&q=80)

## 03 简历只保留能解释的内容

第五周开始整理简历。每一行都对应一段真实经历，不把没有参与过的模块写成自己的成果。项目描述先写行动，再写结果，最后保留一个可继续追问的细节。

简历初稿只保留三类内容：基础知识、项目经历、与岗位相关的课程或练习。奖励、证书和无关经历如果无法服务岗位目标，就先不占用篇幅。

## 04 投递节奏要比情绪稳定

第六周开始投递。每次投递记录岗位、公司、时间、简历版本和后续结果，避免同一岗位重复投递或用错简历版本。

> 投递不是评价个人价值的唯一标准。没有回复时先看样本量是否足够，再调整简历和岗位匹配度，不把单次失败扩大成整体否定。

投递清单：

1. 确认岗位关键词和简历内容一致。
2. 记录使用的简历版本。
3. 一周后再整理未回复岗位。
4. 有笔试或面试后当天补充结果。

投递记录不需要复杂工具，一张表或一份笔记即可，但字段要稳定。建议至少包含公司名称、岗位名称、投递日期、简历版本、当前状态、下一步动作和最近更新时间。这样一周后复盘时能看到自己在哪些类型的岗位上反馈更集中。

## 05 笔试准备重在查漏补缺

第七周主要处理笔试。做题后把错误分为概念不清、边界遗漏、表达不完整三类，再决定是补基础、补练习还是调整答题顺序。

- 概念不清：回到教材或官方文档重新梳理。
- 边界遗漏：为同类题记录两个边界示例。
- 表达不完整：写出关键步骤，而不是只背结论。

## 06 面试后立刻做结构化复盘

面试结束当天记录没有答清的问题，并把它们拆成下一次可以完成的行动。复盘不是为了证明自己当时应该答对，而是避免同一问题在没有准备的情况下反复出现。

常见追问包括项目数据是否真实、为什么选择某个方案、失败时如何处理、结果怎样验证。回答时先说明自己的行动，再说明验证方式，不把团队结果都说成个人独立完成。

一个可用的回答顺序是：先给结论，再解释当时限制，然后说明自己做了什么，最后补充验证结果。这样即使答案不完美，也能让对方看到思考过程。复盘时把“答不上来”改成“下周能完成的补强项”，例如重新整理项目指标、补一次边界测试或重写一段更清楚的介绍。

## 07 结果选择与风险提示

第八周根据已有反馈选择继续投递或调整方向。选择时看三个因素：岗位内容是否匹配、成长空间是否明确、面试反馈是否集中且可改进。

![加载失败示例](https://invalid.campushub.local/missing.png)

这张图片故意使用不可访问地址，用于验证图片失败时不会导致整篇文章不可读，并且会显示独立的失败反馈和重试入口。

## 08 最后一页检查清单

- 基础知识能用自己的话解释。
- 项目背景、行动、验证和不足都说得清。
- 每次投递和面试都有记录。
- 复盘行动能在下一周执行。
- 没有把演示内容包装成真实结果。

如果只有两周时间，就优先保证项目表达、投递记录和面试复盘三件事能闭环；如果时间更充裕，再扩大基础练习和模拟面试的样本量。准备过程的价值不只在结果，也在于把下一次选择建立在可复查的记录上。

> 演示内容只用于界面验收，不代表真实校友经历。真实上线时应以后端发布的公开文章为准。`;

const articles = [
  {
    id: 1001,
    title: '从零准备C++后端实习的8周复盘',
    summary: '补基础、做项目，再到投递和面试。',
    categories: { jobs: [catalog.jobs[0]], majors: [catalog.majors[0]], companies: [], topics: [catalog.topics[0]] },
    experienceStage: '大二暑假', jobNature: '实习', baseLevel: '会C++语法，暂无实习',
    sourceType: 'FIRST_HAND', publishedAt: '2026-11-02T01:30:00Z', updatedAt: '2026-11-02T01:30:00Z',
    counts: { effectiveCoins: 12, effectiveLikes: 12, origin: 'LIVE' }, body: article1001Body, bodyFormat: 'MARKDOWN',
    sourceNote: '课程笔记整理', sourceLinks: [{ label: '课程笔记整理', url: 'https://example.com/campus-demo-notes' }]
  },
  {
    id: 1002,
    title: '第一次投递，我踩过的三个坑',
    summary: '简历准备、投递节奏与面试复盘。',
    categories: { jobs: [catalog.jobs[0]], majors: [catalog.majors[0]], companies: [catalog.companies[0]], topics: [catalog.topics[1]] },
    experienceStage: '大三暑假', jobNature: '实习', baseLevel: '做过课程项目，缺少实习经历',
    sourceType: 'FIRST_HAND', publishedAt: '2026-11-01T08:00:00Z', updatedAt: '2026-11-01T08:00:00Z',
    counts: { effectiveCoins: 12, effectiveLikes: 8, origin: 'LIVE' }, body: '## 投递前\n\n先确认岗位要求和简历内容一致。\n\n## 复盘\n\n- 每次投递记录岗位、时间和结果。\n- 面试问题当天整理。', bodyFormat: 'MARKDOWN',
    sourceNote: '个人投递记录（演示）', sourceLinks: []
  },
  {
    id: 1003,
    title: '前端项目如何从页面列表走向完整任务闭环',
    summary: '把筛选、详情、失败恢复与验证证据串起来。',
    categories: { jobs: [catalog.jobs[1]], majors: [catalog.majors[0]], companies: [catalog.companies[1]], topics: [catalog.topics[0]] },
    experienceStage: '大二暑期实践', jobNature: '项目实践', baseLevel: '完成前端课程设计',
    sourceType: 'CURATED', publishedAt: '2026-10-29T02:00:00Z', updatedAt: '2026-10-29T02:00:00Z',
    counts: { effectiveCoins: 7, effectiveLikes: 20, origin: 'LIVE' }, body: '## 先定义任务\n\n页面完成不等于任务闭环。\n\n1. 写清输入。\n2. 处理失败。\n3. 保存验证证据。', bodyFormat: 'MARKDOWN',
    sourceNote: '项目复盘资料（演示）', sourceLinks: [{ label: '需求原型覆盖说明', url: 'https://example.com/prototype-notes' }]
  },
  {
    id: 1004,
    title: '测试实习面试后的复盘框架',
    summary: '从用例设计、边界意识到表达结构整理复盘。',
    categories: { jobs: [catalog.jobs[2]], majors: [catalog.majors[1]], companies: [], topics: [catalog.topics[1]] },
    experienceStage: '大三实习准备', jobNature: '实习', baseLevel: '熟悉基础测试方法',
    sourceType: 'CURATED', publishedAt: '2026-10-26T02:00:00Z', updatedAt: '2026-10-26T02:00:00Z',
    counts: { effectiveCoins: 5, effectiveLikes: 6, origin: 'LIVE' }, body: '## 用例设计\n\n先区分正常、边界与失败，再说明优先级。\n\n> 不要把未执行的检查写成通过。', bodyFormat: 'MARKDOWN',
    sourceNote: '面试复盘模板（演示）', sourceLinks: []
  },
  {
    id: 1005,
    title: '一段很长很长的经验标题用于检查连续换行和不同屏幕宽度下的阅读舒适度',
    summary: '长标题不应挤压标签或操作区域，摘要仍保持清楚的信息层级。',
    categories: { jobs: [catalog.jobs[1]], majors: [catalog.majors[2]], companies: [catalog.companies[0]], topics: [catalog.topics[1]] },
    experienceStage: '大四秋招', jobNature: '校招', baseLevel: '有一段完整项目经历',
    sourceType: 'FIRST_HAND', publishedAt: '2026-10-21T02:00:00Z', updatedAt: '2026-10-21T02:00:00Z',
    counts: { effectiveCoins: 3, effectiveLikes: 9, origin: 'LIVE' }, body: article1001Body, bodyFormat: 'MARKDOWN',
    sourceNote: '长文排版演示', sourceLinks: [{ label: '长文资料', url: 'https://example.com/long-readability' }]
  },
  {
    id: 1006,
    title: '从零整理一份项目表达提纲',
    summary: '背景、行动、验证和不足，四部分说清项目。',
    categories: { jobs: [catalog.jobs[0]], majors: [catalog.majors[0]], companies: [catalog.companies[0]], topics: [catalog.topics[0]] },
    experienceStage: '大三实习准备', jobNature: '实习', baseLevel: '会Java基础，准备第一个项目',
    sourceType: 'FIRST_HAND', publishedAt: '2026-10-18T02:00:00Z', updatedAt: '2026-10-18T02:00:00Z',
    counts: { effectiveCoins: 9, effectiveLikes: 11, origin: 'LIVE' }, body: '## 背景\n\n为什么做。\n\n## 行动\n\n具体做了什么。\n\n## 验证\n\n怎样知道有效。', bodyFormat: 'MARKDOWN',
    sourceNote: '项目表达提纲（演示）', sourceLinks: []
  }
];

module.exports = { catalog, articles };
