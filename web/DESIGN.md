---
name: 城院帮 Web
description: 延续 W01/W05 原型的青绿校园经验阅读与 Markdown 创作界面。
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
rounded:
  field: "6px"
  control: "7px"
  note: "8px"
  panel: "12px"
spacing:
  compact: "8px"
  small: "12px"
  item: "18px"
  group: "20px"
  section: "24px"
  panel: "26px"
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
---

# Design System: 城院帮 Web

## Overview

延续既有 W01/W05 原型，以清楚的中文标题、组合筛选、完整正文和可核对的发布状态组织校园经验。青绿用于导航、操作与分类，浅灰承托白色内容区；不另设品牌或装饰性视觉主题。

当前系统以 `src/style.css` 和 Vue 组件为依据，截图见 `docs/screenshots/20261106-web-foundation/`。本文件覆盖已实现的阅读、账号、工作区与创作界面；W04/W09/W10、评论等后续界面不据此视为完成，截图也不代表全原型或真机验收。

**Key Characteristics:**

- 左宽内容、右窄辅助，窄屏按阅读与操作顺序重排。
- 中文系统字体、明确边界、紧凑表单与充分正文行距。
- 标题、来源、分类与状态各有层级，使用真实内容字段。
- Markdown 原文与安全预览同步可见，发布须明确确认。

## Colors

单一青绿强调色搭配中性背景，状态提示另用语义色；上方 frontmatter 是公共颜色值的依据。

### Primary

- **青绿 `primary`** 用于主操作、链接、选中导航、分类文字与键盘焦点；主按钮悬停使用 `primary-hover`。
- **浅青绿 `soft`** 用于辅助说明底色，不承担正文主内容的背景。

### Neutral

- **页面 `page` / 内容 `surface`** 区分工作背景与可阅读、可操作的内容区。
- **正文 `text` / 次要文字 `muted`** 区分主信息与摘要、提示、元信息。
- **边界 `border` / 控件边界 `control-border`** 分隔卡片、内容段与表单输入。

错误、成功、待确认分别使用 `error-*`、`success-*`、`notice-*` 的文字与底色，并配合文字说明和描边。修订差异采用浅琥珀背景与“有差异”提示，不仅依赖颜色。

**The Status Rule.** 颜色配合明确状态词；选中、错误、保存结果和修订冲突均须能从文字识别。

## Typography

正文、标题与控件共用 frontmatter 的系统字体栈，保留中文系统字体的可读性。Markdown 源码使用 Consolas、Microsoft YaHei、monospace；渲染后的代码使用 Consolas、SFMono-Regular、monospace。

- **页面标题** 使用 `headline`；窄屏降为（25px）。阅读文章标题桌面为（32px），窄屏为（27px）。标题使用均衡换行，避免窄屏末行孤字。
- **区块标题** 使用 `title`。经验卡片标题桌面为（22px），窄屏为（19px），先于摘要与分类呈现。
- **正文** 保持常规字重。阅读正文使用 `reading`，窄屏为（15px）；摘要通常为（14–16px）。
- **标签与元信息** 分别使用 `label`、`metadata`。来源、时间、修订与计数低于标题层级，数字采用等宽数字。
- **编辑原文** 使用（14px、1.8 行高），安全预览正文使用（14px），保留完整内容与 Markdown 标题层级。

**The Metadata Rule.** 来源放在标题下方的内容信息区，与时间、计数一起呈现；不得提升为标题上方的装饰性标语。

## Layout

桌面页头与主内容居中，最大宽度（1440px）。页头左右内边距（36px），主内容内边距（38px 36px 64px）；布局以（18–26px）的间距分隔表单组、卡片与栏。

- **发现与阅读** 使用可收缩的宽正文栏加窄辅助栏。发现辅助栏常规宽（300px），阅读辅助栏（280px），栏间距（26px）；超宽屏（1600px 起）分别增为（340px / 320px）。阅读辅助栏桌面随滚动停留，距顶部（24px）。
- **创作** 主编辑区加（300px）发布信息栏，间距（24px）。编辑区内原文与安全预览等宽并排，间距（20px）；原文可纵向调整，预览可独立滚动。
- **工作区与资料** 桌面为两列。筛选项桌面为四列；账号页采用介绍区与固定宽度表单的双栏。
- **1100px 以下** 页面左右内边距降为（24px）；筛选变两列，辅助栏收窄，原文与预览先变为上下排列。
- **760px 以下** 页面左右内边距降为（18px）；导航换至页头第二行，页面标题与操作纵向排列。阅读、编辑、账号、资料、工作区与冲突比较均为单列。发现页辅助栏隐藏；阅读辅助区移到正文之后；创作依次为原文、完整预览、发布信息。预览取消高度上限。

所有栅格内容允许收缩。长文本可换行，代码块、表格和长比较字段在自身区域滚动，图片按容器宽度缩放。

## Elevation & Depth

常规内容区保持平面，以背景、描边和留白区分层级。仅文章目录浮层使用轻阴影（`0 8px 25px #234b3420`）；发布确认对话框使用深绿半透明遮罩（`#152a2066`）。悬停不抬升卡片，卡片仅改变边框颜色。

## Shapes

内容区使用 `panel` 圆角与细描边，窄屏圆角收为（9px）。输入使用 `field`、按钮与预览区使用 `control`、辅助说明与目录使用 `note`。工作区卡片用（2px）描边强调文章边界；冲突和恢复区用（2px）琥珀描边突出待处理状态。

## Components

### Buttons

主按钮使用青绿底与白字，次按钮白底描边，文字按钮保持透明底。普通按钮最小高度（44px），字重（600）；编辑工具按钮为紧凑例外（36px）。禁用按钮降低不透明度并显示不可用光标。链接悬停显示下划线；交互控件获得键盘焦点时显示（3px）青绿轮廓，偏移（3px）。

### Cards / Containers

经验卡片按标题、摘要、分类、来源与时间计数排列；摘要最多显示三行，不加虚构封面、作者或统计。分类为可换行的青绿文字，不渲染成按钮。工作区卡片先显示文章状态与修订，再显示标题、更新时间和操作。

### Inputs / Fields

输入、选择与文本域均为白底描边，通常最小高度（44px），标签位于控件上方。禁用字段使用浅灰背景；多选分类以浅底紧凑选项搭配原生复选框。检索、筛选与正文编辑保留可见名称或辅助技术标签。

### Navigation

页头左侧为“城院帮”，随后为主导航，账号入口靠右。当前导航使用青绿文字、加粗与底部细线。移动端隐藏品牌副行，主导航保持完整文字并换行展示。页面提供键盘可达的“跳到主要内容”入口。

### Markdown / Table of contents

正文保留标题、段落、列表、引用、表格、代码与图片的层级。原始 HTML 按文字显示；编辑预览与阅读共用安全渲染组件。阅读目录入口位于正文右上角（44px × 44px），第一段预留空间；展开目录在正文上方显示，可滚动并按标题层级缩进。

### Revision / Publish states

修订冲突以“本机编辑 / 服务器版本”展示人类可读字段、版本号和差异标记；桌面并排，窄屏上下排列。保存、上传、恢复与发布状态以文字提示并提供相应操作。发布确认使用原生对话框，保留返回编辑与明确确认两项操作。

## Do's and Don'ts

### Do:

- **Do** 沿用青绿、浅灰、白色内容区与中文系统字体，新增界面先复用现有控件和间距。
- **Do** 保持完整正文和完整编辑预览，先校验窄屏标题换行、卡片边界与操作顺序。
- **Do** 同时用文字与视觉样式表达选中、禁用、错误、差异和待确认状态。

### Don't:

- **Don't** 发明新品牌、装饰性封面、身份、统计或本轮未实现功能。
- **Don't** 用 JSON 或内部字段名称替代面向作者的修订比较，也不自动覆盖冲突内容。
- **Don't** 将颜色作为唯一状态线索，或用固定宽度让正文和操作溢出页面。
