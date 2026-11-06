---
name: 城院帮 Web
description: 延续 W01–W10 校园阅读创作与 A01–A08/O01–O10 独立管理原型的分作用域青绿界面。
colors:
  primary: "#0d6b57"
  primary-hover: "#095441"
  page: "#f3f6f4"
  surface: "#fff"
  text: "#202b27"
  muted: "#5c6a63"
  border: "#c6d3cc"
  soft: "#eaf3ee"
  control-border: "#aabcb1"
  button-text: "#263a30"
  error-text: "#8b2828"
  error-surface: "#fff0ed"
  success-text: "#11583e"
  success-surface: "#e4f2e9"
  notice-text: "#705617"
  notice-surface: "#fff7dd"
  console-primary: "#0f6e56"
  console-primary-hover: "#095943"
  console-admin-sidebar: "#163e36"
  console-ops-sidebar: "#18312f"
  console-nav-text: "#b4cdc6"
  console-nav-hover: "#204e43"
  console-nav-active: "#275a4e"
  console-footer-text: "#d3e5df"
  console-page: "#f2f3f5"
  console-text: "#20262e"
  console-muted: "#606772"
  console-border: "#e3e7eb"
  console-soft: "#e8f4ef"
  console-note-text: "#17735a"
  console-control-hover: "#edf4f0"
  console-table-header: "#eaf0ed"
  console-table-stripe: "#fafbfc"
  console-error-text: "#c54646"
  console-error-surface: "#fceced"
  console-warning-text: "#a27410"
  console-warning-surface: "#fff4d5"
  console-integration-text: "#785b16"
  console-integration-border: "#eadbb1"
  favorites-border: "#d6dfd9"
typography:
  body:
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif'
    lineHeight: 1.65
  headline:
    fontSize: "30px"
    lineHeight: 1.45
    letterSpacing: "-0.5px"
  title:
    fontSize: "19px"
    lineHeight: 1.5
  label:
    fontSize: "14px"
    fontWeight: 600
  metadata:
    fontSize: "12px"
  reading:
    fontSize: "16px"
    lineHeight: 1.9
  code-rendered:
    fontFamily: 'Consolas, "SFMono-Regular", monospace'
  code-source:
    fontFamily: 'Consolas, "Microsoft YaHei", monospace'
    fontSize: "14px"
    lineHeight: 1.8
  console-body:
    fontFamily: '"Microsoft YaHei", "PingFang SC", sans-serif'
    fontSize: "14px"
    lineHeight: 1.6
  console-brand:
    fontSize: "23px"
    fontWeight: 700
    lineHeight: "34px"
    letterSpacing: "-0.4px"
  console-headline:
    fontSize: "22px"
    fontWeight: 700
    lineHeight: 1.5
    letterSpacing: "0"
  console-title:
    fontSize: "15px"
    fontWeight: 700
    lineHeight: 1.5
  console-article-title:
    fontSize: "20px"
    lineHeight: 1.5
  console-result-title:
    fontSize: "17px"
  console-label:
    fontSize: "13px"
    fontWeight: 400
  console-table:
    fontSize: "12px"
    lineHeight: 1.6
  console-badge:
    fontSize: "11px"
  console-mobile-mode:
    fontSize: "10px"
  console-stat:
    fontSize: "30px"
  console-ops-stat:
    fontSize: "26px"
rounded:
  field: "6px"
  control: "7px"
  note: "8px"
  panel: "12px"
  console-badge: "5px"
  console-control: "8px"
spacing:
  compact: "8px"
  small: "12px"
  item: "18px"
  group: "20px"
  section: "24px"
  panel: "26px"
  console-inline: "16px"
  console-column: "20px"
  console-content-inset: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.control}"
    padding: "9px 18px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.button-text}"
    rounded: "{rounded.control}"
    padding: "9px 18px"
  panel:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
    padding: "26px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.field}"
    padding: "10px 12px"
  console-button-primary:
    backgroundColor: "{colors.console-primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.console-control}"
    padding: "7px 16px"
  console-button-primary-hover:
    backgroundColor: "{colors.console-primary-hover}"
  console-button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.console-text}"
    rounded: "{rounded.console-control}"
    padding: "7px 16px"
  console-input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.console-text}"
    rounded: "{rounded.console-control}"
    padding: "9px 12px"
  console-panel:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
    padding: "14px 15px"
  console-admin-sidebar:
    backgroundColor: "{colors.console-admin-sidebar}"
    textColor: "{colors.console-nav-text}"
    width: "208px"
  console-ops-sidebar:
    backgroundColor: "{colors.console-ops-sidebar}"
    textColor: "{colors.console-nav-text}"
    width: "208px"
  console-topbar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.console-muted}"
    height: "66px"
---

# Design System: 城院帮 Web

## Overview

延续既有 W01/W05 原型，以清楚的中文标题、组合筛选、完整正文和可核对的发布状态组织校园经验。青绿用于导航、操作与分类，浅灰承托白色内容区；不另设品牌或装饰性视觉主题。

当前系统以 `src/style.css`、`src/management/management.css` 和对应 Vue 组件为依据。公共阅读创作界面保留原有视觉，收藏使用 W06 式语义表格；社区管理与独立运维采用用户选定的 A01–A08/O01–O10 原型。控制台以独立深绿导航、白顶栏和表格面板组织信息，不改变公共界面的主色。

本文件记录页面与交互的实装规则，不代表所有后端已完成。现有社区案件接口已适配，真实管理员尚未联调；后续治理与运维后端由用户稍后开发。明确的 `?preview=1` 才显示带标记的只读原型示例，且不调用 API；实际接入模式只显示可核验数据、读取失败或待接入状态。页面、截图和合同模拟均不等于真实管理员或运维验收。

**Key Characteristics:**

- 发现为单栏；阅读保留右侧辅助区，创作保留原文、预览与发布信息。
- 中文系统字体、明确边界、紧凑表单与充分正文行距。
- 标题、来源、分类与状态各有层级，使用真实内容字段。
- Markdown 原文与安全预览同步可见，发布须明确确认。
- 管理与运维使用独立侧栏，示例、真实回执与待接入状态可直接辨认。

## Colors

公共界面与控制台分别使用青绿强调色搭配中性背景，状态提示另用语义色；上方 frontmatter 按作用域登记颜色。

### Primary

- **青绿 `primary`** 用于主操作、链接、选中导航、分类文字与键盘焦点；主按钮悬停使用 `primary-hover`。
- **浅青绿 `soft`** 用于辅助说明底色，不承担正文主内容的背景。
- **控制台青绿 `console-primary`** 用于社区与运维的主操作、链接、筛选和正向状态，悬停使用 `console-primary-hover`；收藏分类导航也沿用此原型青绿。
- **社区深绿 `console-admin-sidebar` / 运维深绿 `console-ops-sidebar`** 分别标识两类入口。导航以 `console-nav-text` 呈现，悬停和当前项分别使用 `console-nav-hover`、`console-nav-active`，当前项同时加粗并改为白字。

### Neutral

- **页面 `page` / 内容 `surface`** 区分工作背景与可阅读、可操作的内容区。
- **正文 `text` / 次要文字 `muted`** 区分主信息与摘要、提示、元信息。
- **边界 `border` / 控件边界 `control-border`** 分隔卡片、内容段与表单输入。
- **控制台 `console-page` / `console-text` / `console-muted`** 限于控制台背景、正文和辅助文字；白色面板与顶栏复用 `surface`，边界使用 `console-border`。
- **表格 `console-table-header` / `console-table-stripe`** 用于控制台与收藏表格的表头和隔行底色；收藏面板另用 `favorites-border`。

错误、成功、待确认分别使用 `error-*`、`success-*`、`notice-*` 的文字与底色，并配合文字说明和描边。修订差异采用浅琥珀背景与“有差异”提示，不仅依赖颜色。

控制台提示与徽标使用 `console-soft`、`console-error-*`、`console-warning-*`，待接入说明使用 `console-integration-*`。正向颜色只能表达真实回执或明确标注的原型状态；实际运维未接入时不以绿色数字、完成徽标或示例健康状态暗示系统运行正常。

**The Scope Rule.** `console-*` 只定义控制台及已明确采用相同原型样式的收藏组件；公共 `primary` 不做全局替换。

**The Status Rule.** 颜色配合明确状态词；选中、错误、保存结果和修订冲突均须能从文字识别。

## Typography

正文、标题与控件共用 frontmatter 的系统字体栈，保留中文系统字体的可读性。Markdown 源码使用 Consolas、Microsoft YaHei、monospace；渲染后的代码使用 Consolas、SFMono-Regular、monospace。

控制台使用 `console-body` 的中文字体栈。页面标题使用 `console-headline`，面板标题使用 `console-title`，关联文章和结果标题分别使用 `console-article-title`、`console-result-title`；控件和正文保持（14px），字段标签与顶栏信息使用 `console-label`。表格、提示及辅助信息使用（12px），徽标使用 `console-badge`；窄屏模式入口使用 `console-mobile-mode`。统计数字采用 `console-stat`，运维统计采用 `console-ops-stat`；这些字号来自原型实装，不把公共文章标题整体缩小为控制台字号。

- **页面标题** 使用 `headline`；窄屏降为（25px）。阅读文章标题桌面为（32px），窄屏为（27px）。标题使用均衡换行，避免窄屏末行孤字。
- **区块标题** 使用 `title`。经验卡片标题桌面为（22px），窄屏为（19px），先于摘要与分类呈现。
- **正文** 保持常规字重。阅读正文使用 `reading`，窄屏为（15px）；摘要通常为（14–16px）。
- **标签与元信息** 分别使用 `label`、`metadata`。来源、时间、修订与计数低于标题层级，数字采用等宽数字。
- **编辑原文** 使用（14px、1.8 行高），安全预览正文使用（14px），保留完整内容与 Markdown 标题层级。

**The Metadata Rule.** 来源放在标题下方的内容信息区，与时间、计数一起呈现；不得提升为标题上方的装饰性标语。

`docs/MANAGEMENT-DETECTOR-20261106.json` 保存的是本次登记前已有的（141 项）检测结果，其中（139 项 advisory）涉及新增原型颜色、字号与圆角未登记，（2 项 warning）涉及旧有 Markdown 渲染代码和源码编辑器的 Consolas 字体。本文件有意登记实际使用的两套视觉值及代码专用字体；Consolas 仅用于代码，不提升为标题或界面正文用字。这里没有重新运行扫描，也不表示告警已清零。

## Layout

桌面页头与主内容居中，最大宽度（1440px）。页头左右内边距（36px），主内容内边距（38px 36px 64px）；布局以（18–26px）的间距分隔表单组、卡片与栏。

- **发现** 使用可收缩的单一主内容列，检索筛选、结果与翻页按顺序排列；所有尺寸均不渲染发现右侧辅助栏。
- **阅读** 使用宽正文栏加（280px）辅助栏，栏间距（26px）；超宽屏（1600px 起）辅助栏增为（320px）。阅读辅助栏桌面随滚动停留，距顶部（24px）。
- **创作** 主编辑区加（300px）发布信息栏，间距（24px）。编辑区内原文与安全预览等宽并排，间距（20px）；原文可纵向调整，预览可独立滚动。
- **工作区与资料** 桌面为两列。筛选项桌面为四列；账号页采用介绍区与固定宽度表单的双栏。
- **1100px 以下** 页面左右内边距降为（24px）；筛选变两列，辅助栏收窄，原文与预览先变为上下排列。
- **760px 以下** 页面左右内边距降为（18px）；导航换至页头第二行，页面标题与操作纵向排列。阅读、编辑、账号、资料、工作区与冲突比较均为单列。阅读辅助区移到正文之后；创作依次为原文、完整预览、发布信息。预览取消高度上限。

控制台以（1440 × 960px）原型为桌面几何基准，独立于公共内容的居中容器。固定深绿侧栏宽（208px），白顶栏高（66px），正文在侧栏右侧保留（32px）左内边距，因此桌面内容起点为（240px）。侧栏各有六项导航；主区按原型使用表格、统计区、双栏上下文与表单、确认区及阶段列表，不另建通用仪表盘构图。面板的内部留白与高度由页面内容及当前 CSS 决定，不将局部校正中的高度当作复用 token。

控制台在（1100px 以下）收窄内边距、允许表格改为自然列宽；在（760px 以下，包括 390px）侧栏默认折叠，由“菜单”展开，顶栏降为（60px），主区使用（16px）左右内边距，双栏与统计区改为单列。控制台与收藏表格分别在自身容器横向滚动，窄屏最小表宽分别为（620px / 640px）；页面本身不承担表格横向滚动。导航换页后折叠菜单。

所有栅格内容允许收缩。长文本可换行，代码块、表格和长比较字段在自身区域滚动，图片按容器宽度缩放。

## Elevation & Depth

常规内容区保持平面，以背景、描边和留白区分层级。仅文章目录浮层使用轻阴影（`0 8px 25px #234b3420`）；发布确认对话框使用深绿半透明遮罩（`#152a2066`）。悬停不抬升卡片，卡片仅改变边框颜色。

控制台保持相同的平面原则，以深色固定导航、白顶栏、浅灰背景、白面板与细描边建立层级；按钮明确取消阴影。展开的窄屏侧栏通过覆盖层级呈现，不为普通表格和面板增加浮起阴影。

## Shapes

内容区使用 `panel` 圆角与细描边，窄屏圆角收为（9px）。输入使用 `field`、按钮与预览区使用 `control`、辅助说明与目录使用 `note`。工作区卡片用（2px）描边强调文章边界；冲突和恢复区用（2px）琥珀描边突出待处理状态。

控制台保留原型面板的 `panel` 圆角，控件和提示使用 `console-control`，导航、表头外角及待接入提示使用（7px），模式标签与状态徽标使用 `console-badge`。任务阶段以圆点或圆形序号表达顺序；圆形阶段标记不作为普通容器的圆角规则。收藏面板保留（12px）圆角。

## Components

### Buttons

主按钮使用青绿底与白字，次按钮白底描边，文字按钮保持透明底。普通按钮最小高度（44px），字重（600）；编辑工具按钮为紧凑例外（36px）。禁用按钮降低不透明度并显示不可用光标。链接悬停显示下划线；交互控件获得键盘焦点时显示（3px）青绿轮廓，偏移（3px）。

控制台按钮最小高度（38px），使用 `console-button-*` 的内边距、圆角与颜色；危险动作使用浅红底与红字。禁用动作保留明确原因，待开发后端不产生成功回执。只读原型为保持原型观感可保留完整不透明度，但控件仍禁用，并由顶栏模式标记及页尾说明告知示例性质。

### Cards / Containers

经验卡片按标题、摘要、分类、来源与时间计数排列；摘要最多显示三行，不加虚构封面、作者或统计。分类为可换行的青绿文字，不渲染成按钮。工作区卡片先显示文章状态与修订，再显示标题、更新时间和操作。

### Inputs / Fields

输入、选择与文本域均为白底描边，通常最小高度（44px），标签位于控件上方。禁用字段使用浅灰背景；多选分类以浅底紧凑选项搭配原生复选框。检索、筛选与正文编辑保留可见名称或辅助技术标签。

控制台字段使用 `console-input`，最小高度（40px）；横排筛选控件为（36px），文本域可纵向调整。实际运维登录表单整体禁用，界面说明授权待接入；不采集或提交账号密码。

### Navigation

页头左侧为“城院帮”，随后为主导航，账号入口靠右。当前导航使用青绿文字、加粗与底部细线。移动端隐藏品牌副行，主导航保持完整文字并换行展示。页面提供键盘可达的“跳到主要内容”入口。

社区管理与运维各使用独立侧栏和白顶栏，侧栏当前项以浅一阶深绿底、白字及加粗表示；图标为内联线性 SVG。顶栏明确区分“原型示例 · 只读”、未登录、已核验管理身份与运维待接入。校园账号与社区管理账号的会话、请求和退出清理彼此独立，不以校园登录恢复管理身份，不以社区身份声称获得运维授权。

### Favorites table

收藏以“内容 / 当前状态 / 最后更新 / 允许的操作”四列语义表格组织，表头有列作用域；可用内容提供“继续阅读”和“取消收藏”。表格上方呈现真实总数、最近收藏排序及每页（5 / 10 / 20 条）选择，默认（10 条）。`page` 与 `size` 保存在 URL，返回或刷新恢复该页；修改条数回到第一页，末页最后一条取消后回退到有效末页。

不可用收藏只显示“此收藏暂不可用”、状态、空时间和取消操作，不保留历史标题、摘要、正文或更新时间；恢复公开后重新读取当前公开版本。读取中、空列表、读取失败与取消结果不确定分别显示文字状态与可执行的恢复入口，取消失败不能提前移除表格行或显示已成功。收藏数据属于本人校园会话。

### Community / Operations panels

社区案件使用列表、受控上下文、处理表单与历史记录；处理前后保留案件状态、修订及可核对的回执。请求结果不确定时保留原请求键、原修订与输入，冻结新处理并提供按原请求核对和读取最新状态的入口，不自动覆盖待确认输入。

后续内容巡查、账号与申诉、分类维护、贡献荣誉、全局治理审计，以及部署、重启、备份、恢复、持久任务与异常页保留原型结构。只有明确只读模式显示示例人物、数字、运行结果及阶段；实际模式中未提供的数据标为待接入或未核验，执行按钮保持禁用。治理与运维状态不能借用校园账号数据或示例值填充。A07 的贡献双栏和 O10 的运维手册表格加核对清单按内容组织，不将临时内部留白固化为全局规则。

### Markdown / Table of contents

正文保留标题、段落、列表、引用、表格、代码与图片的层级。原始 HTML 按文字显示；编辑预览与阅读共用安全渲染组件。阅读目录入口位于正文右上角（44px × 44px），第一段预留空间；展开目录在正文上方显示，可滚动并按标题层级缩进。

### Revision / Publish states

修订冲突以“本机编辑 / 服务器版本”展示人类可读字段、版本号和差异标记；桌面并排，窄屏上下排列。保存、上传、恢复与发布状态以文字提示并提供相应操作。发布确认使用原生对话框，保留返回编辑与明确确认两项操作。

## Do's and Don'ts

### Do:

- **Do** 沿用青绿、浅灰、白色内容区与中文系统字体，新增界面先复用现有控件和间距。
- **Do** 保持完整正文和完整编辑预览，先校验窄屏标题换行、卡片边界与操作顺序。
- **Do** 同时用文字与视觉样式表达选中、禁用、错误、差异和待确认状态。
- **Do** 保留公共与控制台两套有作用域的颜色和字体层级；按原型使用管理侧栏、顶栏、表格及阶段列表。
- **Do** 在（390px）宽度下折叠控制台侧栏，让表格在容器内滚动，并保留收藏翻页和取消入口。
- **Do** 明示原型示例与实际接入状态；只将真实响应作为授权、处理成功或运行健康的依据。

### Don't:

- **Don't** 发明新品牌、装饰性封面、身份、统计或本轮未实现功能。
- **Don't** 用 JSON 或内部字段名称替代面向作者的修订比较，也不自动覆盖冲突内容。
- **Don't** 将颜色作为唯一状态线索，或用固定宽度让正文和操作溢出页面。
- **Don't** 恢复发现页右栏，或因此移除阅读侧栏、编辑原文、安全预览及发布信息结构。
- **Don't** 暴露不可用收藏的历史信息，或混用校园、社区管理与运维身份。
- **Don't** 为消除 detector 告警重做用户选定原型，或把页面、模拟合同及既有真实回归扩写为真实管理员/运维验收。
