<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import ConsoleTable from "./ConsoleTable.vue";
import IntegrationNotice from "./IntegrationNotice.vue";
const route = useRoute();
const preview = computed(() => route.query.preview === "1");
const page = computed(() =>
  route.path.includes("/tasks/")
    ? "task"
    : route.path.split("/").pop() || "overview",
);
const titles: Record<string, string> = {
  login: "独立运维登录",
  overview: "服务总览",
  logs: "业务停止期间的日志与任务",
  deploy: "部署已准备的项目版本",
  restart: "重启业务服务",
  backups: "一致性备份",
  restore: "备份校验与恢复确认",
  task: "任务 DEMO-T108 · 持久进度",
  exceptions: "任务异常与冲突操作",
  manual: "运维手册与交接",
};
const title = computed(() =>
  page.value === "task" && !preview.value
    ? `任务 ${route.params.id} · 持久进度`
    : titles[page.value] || "独立运维",
);
function link(path: string) {
  return { path, query: preview.value ? { preview: "1" } : {} };
}
const taskColumns = ["编号", "动作 / 目标", "状态", "开始时间", "操作"];
const recentTasks = [
  ["DEMO-T102", "完整备份 / 城院帮业务", "已完成", "08:00", "查看任务"],
  ["DEMO-T099", "部署 / DEMO-v0.1", "已完成", "昨日20:10", "查看运行核对"],
];
const backups = [
  [
    "DEMO-B1104 / 今日08:00",
    "数据 + 图片 + 版本清单",
    "通过",
    "已有副本",
    "查看 / 恢复",
  ],
  [
    "DEMO-B1103 / 昨日08:00",
    "数据 + 图片 + 版本清单",
    "通过",
    "已有副本",
    "查看 / 恢复",
  ],
  ["DEMO-B1102 / 前日08:00", "缺少部分图片", "失败", "不可用", "查看失败原因"],
];
const taskStages = [
  ["已接收 / 授权核验", "任务已登记，重复请求返回本编号"],
  ["排队 / 目标互斥", "等待冲突任务结束"],
  ["当前状态保护", "保护副本已完成"],
  ["恢复数据与图片", "正在执行，尚未确认成功"],
  ["核对与开放业务", "等待关键数据、图片及健康检查"],
];
</script>
<template>
  <div :class="`ops-page ops-${page}`">
    <h1 class="console-title">{{ title }}</h1>
    <template v-if="page === 'login'"
      ><div class="console-login-grid ops-login-grid">
        <form class="console-panel console-login-panel" @submit.prevent>
          <h2>仅限已授权的运维管理员</h2>
          <fieldset disabled>
            <label
              >运维账号 *<input
                name="ops-login"
                autocomplete="off"
                placeholder="请输入账号" /></label
            ><label
              >密码 *<input
                name="ops-password"
                type="password"
                autocomplete="off"
                placeholder="请输入密码" /></label
            ><button class="console-primary">登录独立运维</button>
          </fieldset>
          <p v-if="!preview" class="hint">
            独立运维授权接口尚未开发，此表单不会收集或提交凭据。
          </p>
        </form>
        <div class="console-login-aside">
          <p class="console-note">
            此入口的授权与任务记录独立于业务服务。业务进程停止或数据库维护时仍应可查询；整机故障按手册处理。
          </p>
          <section class="console-panel">
            <h2>{{ preview ? "授权失败" : "授权待接入" }}</h2>
            <p class="console-negative console-emphasis">
              {{ preview ? "此账号不具备运维权限。" : "尚未核验运维身份。" }}
            </p>
            <p>普通校园认证与社区管理身份<br />不会自动获得运维权限。</p>
            <RouterLink
              v-if="!preview"
              class="button"
              :to="link('/ops/overview')"
              >查看待接入页面</RouterLink
            >
          </section>
        </div>
      </div></template
    >
    <template v-else
      ><IntegrationNotice
        :feature="page === 'manual' ? '运维任务与执行' : title"
        :preview="preview"
      />
      <template v-if="page === 'overview'"
        ><div class="console-stat-grid ops-overview-stats">
          <section
            v-for="(label, index) in ['业务服务', '独立运维', '最近完整备份']"
            :key="label"
            class="console-panel console-stat"
          >
            <span>{{ label }}</span
            ><strong>{{
              preview ? ["运行中", "可达", "今日 08:00"][index] : "未读取"
            }}</strong>
            <p>
              {{
                preview
                  ? [
                      "运行版本 DEMO-v0.1",
                      "任务记录可查询",
                      "数据、图片与清单一致",
                    ][index]
                  : "等待真实状态接口"
              }}
            </p>
          </section>
        </div>
        <section class="console-panel ops-controlled">
          <h2>受控操作</h2>
          <div class="ops-operation-links">
            <RouterLink
              v-for="(action, index) in [
                '部署已准备版本',
                '重启业务服务',
                '创建一致性备份',
                '校验并恢复备份',
              ]"
              :key="action"
              class="button"
              :to="
                link(
                  [
                    '/ops/deploy',
                    '/ops/restart',
                    '/ops/backups',
                    '/ops/restore',
                  ][index] || '/ops/overview',
                )
              "
              >{{ action }}</RouterLink
            >
          </div>
        </section>
        <section class="console-panel ops-recent">
          <h2>最近任务</h2>
          <ConsoleTable
            :columns="taskColumns"
            :rows="preview ? recentTasks : []"
            :targets="['/ops/tasks/DEMO-T108', '/ops/tasks/DEMO-T108']"
          />
        </section>
        <p class="console-muted console-followup">
          同一业务目标的冲突操作互斥；只提供预定义动作，不提供任意命令或SQL输入框。
        </p></template
      >
      <template v-else-if="page === 'logs'"
        ><p v-if="preview" class="console-error-note">
          业务服务已停止。独立运维在线；以下日志与任务来自独立持久记录，仍可查询。
        </p>
        <section class="console-panel ops-task-list">
          <h2>任务查询</h2>
          <ConsoleTable
            :columns="['任务编号', '动作', '执行阶段', '状态', '操作']"
            :rows="
              preview
                ? [
                    [
                      'DEMO-T105',
                      '重启业务',
                      '启动后健康核对',
                      '执行中',
                      '查看原任务',
                    ],
                    [
                      'DEMO-T104',
                      '数据库恢复',
                      '保护当前状态',
                      '排队',
                      '查看等待原因',
                    ],
                  ]
                : []
            "
            :targets="['/ops/tasks/DEMO-T108', '/ops/exceptions']"
          />
        </section>
        <section class="console-panel ops-logs-panel">
          <h2>必要错误日志</h2>
          <template v-if="preview"
            ><p>10:02:16&nbsp; DEMO-T105&nbsp; 业务进程停止已确认</p>
            <p>10:02:19&nbsp; DEMO-T105&nbsp; 新进程已启动，等待健康核对</p>
            <p>
              10:02:24&nbsp; DEMO-T105&nbsp; 核对尚未完成；任务仍执行中
            </p></template
          >
          <p v-else class="console-muted">
            尚未接入独立日志存储，无法读取真实日志。
          </p>
          <p class="console-positive">
            日志显示时间、任务标识与阶段；不显示密码、令牌或认证材料。
          </p>
          <RouterLink :to="link('/ops/exceptions')"
            >查看异常与冲突状态</RouterLink
          >
        </section></template
      >
      <template v-else-if="page === 'deploy'"
        ><section class="console-panel ops-release-list">
          <h2>选择发布包</h2>
          <ConsoleTable
            :columns="['发布包', '运行目标', '校验', '兼容性', '可用操作']"
            :rows="
              preview
                ? [
                    [
                      'DEMO-v0.2 / 已准备',
                      '城院帮业务服务',
                      '清单完整',
                      '兼容当前数据版本',
                      '选择',
                    ],
                    [
                      'DEMO-v0.0 / 历史',
                      '城院帮业务服务',
                      '清单缺失',
                      '未通过',
                      '拒绝使用',
                    ],
                  ]
                : []
            "
          />
        </section>
        <div class="console-split console-followup">
          <section class="console-panel ops-deploy-confirm">
            <h2>部署前确认</h2>
            <p>目标：城院帮业务服务</p>
            <p>
              当前：{{ preview ? "DEMO-v0.1" : "未读取" }} → 待部署：{{
                preview ? "DEMO-v0.2" : "未选择"
              }}
            </p>
            <p>影响：业务短暂停止；部署前创建保护备份。</p>
            <p>完成条件：启动、健康检查与运行版本核对。</p>
            <div class="console-actions">
              <button class="console-primary" disabled>确认发起部署</button
              ><RouterLink class="button" :to="link('/ops/overview')"
                >取消</RouterLink
              >
            </div>
          </section>
          <p class="console-note">
            只有已准备且校验通过的项目包可选择。校验失败不执行；部署失败保留日志、保护状态与后续处置入口。
          </p>
        </div></template
      >
      <template v-else-if="page === 'restart'"
        ><section class="console-panel ops-restart-confirm">
          <h2>重启确认</h2>
          <div class="ops-target">
            <p class="console-muted">受控目标</p>
            <h3 class="console-big">城院帮业务服务</h3>
            <p class="console-muted">
              当前运行版本：{{ preview ? "DEMO-v0.1" : "未读取" }}
            </p>
          </div>
          <p class="console-warning">
            重启仅作用于项目业务服务。独立运维继续查询原任务；短时无法阅读或提交内容属于预期影响。
          </p>
          <h3>执行步骤</h3>
          <ol class="ops-horizontal-steps">
            <li
              v-for="(step, index) in [
                '停止业务',
                '确认停止',
                '启动业务',
                '健康核对',
              ]"
              :key="step"
            >
              <span>{{ index + 1 }}</span
              >{{ step }}
            </li>
          </ol>
          <div class="console-actions">
            <button class="console-primary" disabled>确认发起重启</button
            ><RouterLink class="button" :to="link('/ops/overview')"
              >取消</RouterLink
            >
          </div>
        </section>
        <p class="console-muted console-followup">
          停止失败、启动失败或结果不确定时显示真实阶段；“请求已接收”不显示为重启成功。
        </p></template
      >
      <template v-else-if="page === 'backups'"
        ><div class="ops-heading-action">
          <button class="console-primary" disabled>创建完整备份</button>
        </div>
        <section class="console-panel ops-backup-list">
          <h2>备份记录</h2>
          <ConsoleTable
            :columns="['备份标识 / 时点', '范围', '完整性', '离机副本', '操作']"
            :rows="preview ? backups : []"
            :targets="['/ops/restore', '/ops/restore', '/ops/exceptions']"
          />
        </section>
        <section class="console-panel ops-backup-policy">
          <h2>策略与范围</h2>
          <p>运行期间至少每天一次；重要部署、恢复与数据变更前额外备份。</p>
          <p class="console-muted">
            保留最近7份成功备份，至少1份离机副本。RPO≤24小时、RTO≤10分钟为待实测目标。
          </p>
          <p class="console-muted">
            仅同机另一个目录不算离机保护；电脑关机期间不承诺定时任务运行。
          </p>
          <p class="console-positive">
            创建备份也需确认目标、范围与预计影响，然后进入可持续查询的任务。
          </p>
        </section></template
      >
      <div
        v-else-if="page === 'restore'"
        class="console-split ops-restore-split"
      >
        <section class="console-panel ops-restore-confirm">
          <h2>待恢复备份 {{ preview ? "DEMO-B1104" : "未选择" }}</h2>
          <h3>备份时点：{{ preview ? "11月4日08:00" : "未读取" }}</h3>
          <ul class="ops-restore-checks">
            <li
              v-for="check in [
                '完整性清单',
                '业务数据与图片匹配',
                '版本兼容性',
                '当前状态保护副本',
              ]"
              :key="check"
            >
              <svg v-if="preview" viewBox="0 0 20 20" aria-label="原型示例通过">
                <path
                  d="m3 10 5 5 9-11"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                /></svg
              ><span v-else class="ops-unknown-check">—</span>{{ check }}：{{
                preview ? "通过" : "未检查"
              }}
            </li>
          </ul>
          <p class="console-warning">
            恢复会覆盖当前业务数据和关联图片。进入维护状态后执行，完成后须核对关键记录与图片。
          </p>
          <button class="console-danger" disabled>确认覆盖并发起恢复</button>
        </section>
        <section class="console-panel ops-restore-rejected">
          <h2>未通过校验的状态</h2>
          <span class="console-badge negative">不可恢复</span>
          <h3 class="console-negative">
            {{ preview ? "示例：图片清单不完整" : "恢复条件尚未核验" }}
          </h3>
          <p class="console-muted">
            缺失、损坏、版本不兼容，或<br />当前状态保护失败时，禁止恢复。
          </p>
          <button disabled>恢复不可用</button>
          <p class="console-muted">
            失败后保存保护副本和日志。<br />未经核对，不继续不一致写入。
          </p>
          <p class="console-muted">在此取消不会改变当前数据。</p>
          <RouterLink class="button" :to="link('/ops/backups')"
            >取消并返回备份列表</RouterLink
          >
        </section>
      </div>
      <template v-else-if="page === 'task'"
        ><section class="console-panel ops-task-summary">
          <span class="console-badge">{{
            preview ? "执行中" : "尚未读取"
          }}</span>
          <h2 class="console-big">
            动作：{{ preview ? "恢复备份 DEMO-B1104" : "未读取" }}
          </h2>
          <p class="console-muted">
            目标：城院帮业务服务 ·
            {{
              preview
                ? "提交时间：10:05 · 当前阶段：恢复数据与图片"
                : "任务查询接口待接入"
            }}
          </p>
        </section>
        <div class="console-split ops-task-split">
          <section class="console-panel ops-task-stages">
            <h2>执行阶段</h2>
            <ol>
              <li
                v-for="(stage, index) in taskStages"
                :key="stage[0]"
                :class="{ complete: preview && index < 4 }"
              >
                <span class="ops-stage-dot"></span>
                <div>
                  <h3>{{ stage[0] }}</h3>
                  <p>{{ preview ? stage[1] : "尚未取得该阶段的真实状态" }}</p>
                </div>
              </li>
            </ol>
          </section>
          <section class="console-panel ops-task-result">
            <h2>结果与操作</h2>
            <h3 class="console-warning-text">
              当前结果：{{ preview ? "尚未完成" : "未读取" }}
            </h3>
            <p class="console-muted">
              页面刷新后仍可按编号查询。<br />业务停止不影响这条任务记录。
            </p>
            <p class="console-muted">
              同目标部署、重启、恢复等<br />冲突操作按互斥规则处理。
            </p>
            <button disabled>刷新原任务状态</button>
            <p class="console-positive">核对通过后才显示成功。</p>
            <RouterLink :to="link('/ops/exceptions')"
              >查看失败与冲突处理</RouterLink
            >
          </section>
        </div></template
      >
      <div
        v-else-if="page === 'exceptions'"
        class="console-split console-equal"
      >
        <section class="console-panel ops-exception-panel">
          <h2>任务失败 / 中断待核查</h2>
          <span class="console-badge negative">{{
            preview ? "中断待核查" : "状态未读取"
          }}</span>
          <h3 class="console-big">
            {{ preview ? "DEMO-T108 · 恢复任务" : "待核查任务" }}
          </h3>
          <p class="console-muted">
            最后阶段：{{ preview ? "恢复数据与图片" : "未读取" }}
          </p>
          <p class="console-muted">
            原因：{{
              preview
                ? "执行器中断，最终结果尚未确认。"
                : "待独立任务查询接口提供。"
            }}
          </p>
          <p class="console-muted">
            保护副本：{{ preview ? "DEMO-P108，已保留。" : "未读取" }}
          </p>
          <h3 class="console-negative">
            {{ preview ? "业务保持维护状态" : "业务状态尚未核验" }}
          </h3>
          <p class="console-muted">先核对当前数据和图片，再按手册处理。</p>
          <p class="console-muted">本次记录不会默认标成成功。</p>
          <div class="ops-exception-links">
            <RouterLink class="button" :to="link('/ops/tasks/DEMO-T108')"
              >查询原任务与保护副本</RouterLink
            ><RouterLink class="button" :to="link('/ops/manual')"
              >查看失败处置手册</RouterLink
            >
          </div>
        </section>
        <section class="console-panel ops-exception-panel">
          <h2>冲突动作被阻止</h2>
          <span class="console-badge warning">{{
            preview ? "目标繁忙" : "互斥状态未读取"
          }}</span>
          <h3 class="console-big">
            {{ preview ? "同一目标已有恢复任务" : "冲突任务待核验" }}
          </h3>
          <p class="console-muted">
            正在尝试：{{ preview ? "部署 DEMO-v0.2" : "未发起操作" }}
          </p>
          <p class="console-muted">
            冲突任务：{{ preview ? "DEMO-T108" : "未读取" }}
          </p>
          <p class="console-muted">处置：先查看原任务，不交错执行。</p>
          <RouterLink
            class="button console-primary"
            :to="link('/ops/tasks/DEMO-T108')"
            >查看正在执行的原任务</RouterLink
          ><button disabled>提交部署：暂不可用</button>
          <p class="console-muted">
            重复提交返回已有任务；<br />执行失败不能通过重新点击掩盖原结果。
          </p>
        </section>
      </div>
      <template v-else-if="page === 'manual'"
        ><section class="console-panel ops-manual-table">
          <h2>选择正确的恢复目标</h2>
          <ConsoleTable
            :columns="['需要处理的情况', '应使用的流程', '核对重点']"
            :widths="['31.8584%', '35.3982%', '32.7434%']"
            :row-height="83"
            :rows="[
              [
                '只回到旧代码版本',
                '版本回退：选择已准备发布包',
                '数据版本兼容与运行版本',
              ],
              [
                '业务数据需要回到备份时点',
                '备份校验与数据图片恢复',
                '覆盖影响、保护副本、数据图片一致性',
              ],
            ]"
          />
        </section>
        <section class="console-panel ops-manual-checklist">
          <h2>另一位组员的操作清单</h2>
          <ol>
            <li>冷启动：检查环境与运行配置，分别启动业务与独立运维。</li>
            <li>
              部署或重启：确认目标、版本、影响，追踪同一任务并核对运行结果。
            </li>
            <li>
              备份恢复：校验清单，保护当前状态，在维护状态执行，再核对业务与图片。
            </li>
            <li>
              失败处置：保留任务、日志和保护副本，按已确认方案回退或恢复。
            </li>
            <li>入口不可达：整机或运维自身故障按离线维护手册处理。</li>
          </ol>
          <p class="console-positive">
            课堂以本机可重复运行为目标；可选云环境分别记录。真实认证与演示身份分开标记。
          </p>
          <p v-if="!preview" class="hint">
            以上为原型流程说明；实际启动脚本、服务位置及恢复执行参数待后端交付补充。
          </p>
        </section></template
      >
    </template>
  </div>
</template>
