<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { opsRequest, opsSession, loginOps } from "./ops-client";
import { actionLabels, jobLabels, phases } from "./ops-types";
import type {
  Artifact,
  Backup,
  Confirmation,
  Job,
  JobInput,
  OpsAction,
  OpsStatus,
} from "./ops-types";
import { ApiError, message } from "../lib/api";
import { dateText } from "../lib/content";
import ConsoleTable from "./ConsoleTable.vue";
const route = useRoute(),
  router = useRouter();
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
  task: "任务持久进度",
  exceptions: "任务异常与冲突操作",
  manual: "运维手册与交接",
};
const login = ref(""),
  password = ref(""),
  error = ref(""),
  busy = ref(false),
  status = ref<OpsStatus>(),
  jobs = ref<Job[]>([]),
  job = ref<Job>(),
  artifacts = ref<Artifact[]>([]),
  backups = ref<Backup[]>([]);
const logs = ref<{
  taskEvents: { jobId: string; phase: string; occurredAt: string }[];
  businessRequests: Record<string, string>[];
}>();
const reference = ref(""),
  confirmation = ref<Confirmation>(),
  acknowledged = ref(false),
  pending = ref<JobInput>(),
  now = ref(Date.now());
const pendingKey = "campushub.web.ops.private:operation";
let generation = 0,
  poll: ReturnType<typeof setTimeout> | undefined,
  clock: ReturnType<typeof setInterval> | undefined;
const action = computed<OpsAction>(
  () =>
    ({
      deploy: "DEPLOY",
      restart: "RESTART",
      backups: "BACKUP",
      restore: "RESTORE",
    })[page.value] as OpsAction,
);
const active = computed(() =>
  jobs.value.find((j) => ["QUEUED", "RUNNING"].includes(j.state)),
);
const ready = computed(() => opsSession.status === "ready");
const canAct = computed(
  () =>
    ready.value &&
    opsSession.me?.capabilities.includes(action.value) &&
    !!status.value &&
    !status.value.recoveryRequired &&
    !active.value,
);
const expired = computed(
  () =>
    !confirmation.value ||
    Date.parse(confirmation.value.expiresAt) <= now.value,
);
const validReference = computed(() =>
  action.value === "DEPLOY"
    ? artifacts.value.some((row) => row.id === reference.value)
    : action.value === "RESTORE"
      ? backups.value.some((row) => row.id === reference.value && row.complete)
      : true,
);
const frozen = computed(() => busy.value || !!pending.value);
function stopPoll() {
  clearTimeout(poll);
}
async function signIn() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    if (
      !/^[a-z0-9][a-z0-9._-]{2,63}$/.test(login.value) ||
      password.value.length < 8 ||
      password.value.length > 64 ||
      new TextEncoder().encode(password.value).length > 72
    )
      throw new Error("请填写有效运维账号及8–64字符密码，UTF-8不超过72字节。");
    await loginOps(login.value, password.value);
    password.value = "";
    await router.replace("/ops/overview");
  } catch (e) {
    error.value = describe(e);
  } finally {
    busy.value = false;
  }
}
function describe(e: unknown) {
  return `${message(e)}${e instanceof ApiError ? ` [${e.code}]` : ""}`;
}
async function load() {
  if (!ready.value || page.value === "login") return;
  const current = ++generation;
  stopPoll();
  busy.value = true;
  error.value = "";
  try {
    if (page.value === "task") {
      const value = await opsRequest<Job>(`/ops/jobs/${route.params.id}`);
      if (current !== generation) return;
      job.value = value;
      if (["QUEUED", "RUNNING"].includes(value.state) && !document.hidden)
        poll = setTimeout(() => void load(), 2000);
    } else {
      const [s, j, b, a] = await Promise.all([
        opsRequest<OpsStatus>("/ops/status"),
        opsRequest<{ items: Job[] }>("/ops/jobs"),
        opsRequest<{ items: Backup[] }>("/ops/backups"),
        opsRequest<{ items: Artifact[] }>("/ops/artifacts"),
      ]);
      if (current !== generation) return;
      status.value = s;
      jobs.value = j.items;
      backups.value = b.items;
      artifacts.value = a.items;
      if (page.value === "logs") {
        const l = await opsRequest<NonNullable<typeof logs.value>>("/ops/logs");
        if (current !== generation) return;
        logs.value = l;
      }
      if (
        page.value === "restore" &&
        !reference.value &&
        typeof route.query.backup === "string"
      )
        reference.value = route.query.backup;
    }
    const raw = sessionStorage.getItem(pendingKey);
    pending.value = raw ? (JSON.parse(raw) as JobInput) : undefined;
  } catch (e) {
    if (current === generation) {
      error.value = describe(e);
      if (page.value !== "task") status.value = undefined;
    }
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function prepare() {
  if (!canAct.value || frozen.value || !validReference.value) return;
  busy.value = true;
  error.value = "";
  confirmation.value = undefined;
  acknowledged.value = false;
  try {
    if (
      (action.value === "DEPLOY" || action.value === "RESTORE") &&
      !reference.value
    )
      throw new Error("请先选择已登记版本或完整备份。");
    const operation = {
      action: action.value,
      target: "business",
      referenceId: ["DEPLOY", "RESTORE"].includes(action.value)
        ? reference.value
        : "",
    };
    const result = await opsRequest<Confirmation>(
      "/ops/confirmations",
      "POST",
      operation,
    );
    if (
      result.action !== operation.action ||
      result.target !== operation.target ||
      result.referenceId !== operation.referenceId ||
      !Number.isFinite(Date.parse(result.expiresAt))
    )
      throw new Error("服务端返回的确认与所选操作不一致，请重新核对。");
    confirmation.value = result;
  } catch (e) {
    error.value = describe(e);
  } finally {
    busy.value = false;
  }
}
async function submit() {
  if (busy.value) return;
  if (!pending.value) {
    if (
      !confirmation.value ||
      expired.value ||
      !acknowledged.value ||
      !canAct.value
    )
      return;
    const c = confirmation.value;
    const value: JobInput = {
      action: c.action,
      target: "business",
      referenceId: c.referenceId,
      requestId: crypto.randomUUID(),
      confirmationId: c.confirmationId,
    };
    try {
      sessionStorage.setItem(pendingKey, JSON.stringify(value));
      pending.value = value;
    } catch {
      error.value =
        "无法保存原请求，尚未提交任务。请允许当前页面使用会话存储后重试。";
      return;
    }
  }
  busy.value = true;
  error.value = "";
  try {
    const result = await opsRequest<Job>("/ops/jobs", "POST", pending.value);
    sessionStorage.removeItem(pendingKey);
    pending.value = undefined;
    confirmation.value = undefined;
    await router.push(`/ops/tasks/${result.id}`);
  } catch (e) {
    error.value = `${describe(e)} 原操作、确认ID和请求键已保留；请先按原请求核对，不要重新创建任务。`;
  } finally {
    busy.value = false;
  }
}
async function reconcile() {
  if (busy.value || !pending.value) return;
  busy.value = true;
  error.value = "";
  try {
    const recent = await opsRequest<{ items: Job[] }>("/ops/jobs");
    jobs.value = recent.items;
    const match = recent.items.find(
      (row) => row.requestId === pending.value?.requestId,
    );
    if (
      match ||
      confirm(
        "最近任务中未找到这次请求。请核对目标、版本和时间。结束本机待确认请求不会撤销服务端任务，确认已核对？",
      )
    ) {
      sessionStorage.removeItem(pendingKey);
      pending.value = undefined;
      confirmation.value = undefined;
      acknowledged.value = false;
      if (match) await router.push(`/ops/tasks/${match.id}`);
    }
  } catch (e) {
    error.value = describe(e);
  } finally {
    busy.value = false;
  }
}
function changed() {
  confirmation.value = undefined;
  acknowledged.value = false;
}
function visibility() {
  if (document.hidden) stopPoll();
  else if (page.value === "task") void load();
}
onMounted(() => {
  void load();
  clock = setInterval(() => (now.value = Date.now()), 1000);
  document.addEventListener("visibilitychange", visibility);
});
watch(
  () => opsSession.status,
  () => {
    if (ready.value) void load();
    else {
      generation++;
      stopPoll();
      status.value = undefined;
      jobs.value = [];
      job.value = undefined;
      artifacts.value = [];
      backups.value = [];
      logs.value = undefined;
      pending.value = undefined;
      confirmation.value = undefined;
    }
  },
);
onBeforeUnmount(() => {
  generation++;
  stopPoll();
  clearInterval(clock);
  document.removeEventListener("visibilitychange", visibility);
});
</script>
<template>
  <div :class="`ops-page ops-${page}`">
    <h1 class="console-title">
      {{
        page === "task" ? `任务 ${route.params.id} · 持久进度` : titles[page]
      }}
    </h1>
    <p v-if="error" class="error" role="alert">
      {{ error }}
      <button v-if="ready && !pending" :disabled="busy" @click="load">
        重新读取
      </button>
    </p>
    <div v-if="page === 'login'" class="console-login-grid ops-login-grid">
      <form class="console-panel console-login-panel" @submit.prevent="signIn">
        <h2>仅限已授权的运维管理员</h2>
        <p v-if="ready">
          会话已核验。<RouterLink to="/ops/overview">进入服务总览</RouterLink>
        </p>
        <fieldset :disabled="busy">
          <label
            >运维账号 *<input
              v-model="login"
              name="ops-login"
              autocomplete="username"
              required
              maxlength="64"
              placeholder="请输入账号" /></label
          ><label
            >密码 *<input
              v-model="password"
              name="ops-password"
              type="password"
              autocomplete="current-password"
              required
              placeholder="请输入密码" /></label
          ><button class="console-primary">
            {{ busy ? "正在核验运维身份…" : "登录独立运维" }}
          </button>
        </fieldset>
      </form>
      <div class="console-login-aside">
        <p class="console-note">
          独立运维进程与授权不依赖业务MySQL；业务维护期间仍可查询持久任务。当前入口仅用于已授权维护人员。
        </p>
        <section class="console-panel">
          <h2>独立授权</h2>
          <p>普通校园与社区管理令牌不能获得运维权限。</p>
          <p>登录失败过多会触发15分钟限流，请核对本机维护凭据后再试。</p>
        </section>
      </div>
    </div>
    <section v-else-if="!ready" class="console-panel console-gate">
      <h2>
        {{
          opsSession.status === "checking"
            ? "正在核验运维会话"
            : "请先登录独立运维"
        }}
      </h2>
      <p>运维会话与其他访问端口独立。</p>
      <RouterLink
        v-if="opsSession.status === 'guest'"
        class="button console-primary"
        to="/ops/login"
        >登录独立运维</RouterLink
      >
    </section>
    <template v-else>
      <p v-if="busy && !job" role="status">正在读取或提交运维请求…</p>
      <section v-if="pending" class="console-note">
        <h2>原操作结果待确认</h2>
        <p>
          {{ actionLabels[pending.action] }} · business ·
          {{
            pending.referenceId || "当前受控版本"
          }}。原请求已保存，刷新页面不会生成第二个请求键。
        </p>
        <div class="console-actions">
          <button :disabled="busy" @click="submit">按原请求核对任务</button
          ><button :disabled="busy" @click="reconcile">
            读取任务并核对后结束
          </button>
        </div>
        <RouterLink to="/ops/logs">查看最近任务</RouterLink>
      </section>
      <p v-if="status?.recoveryRequired" class="console-error-note">
        控制器要求人工核查，已阻止新操作。请按手册核对数据库、图片、保护副本和进程，再由本机维护CLI确认；刷新不会解除维护状态。
      </p>
      <p v-if="active" class="console-warning">
        同一目标已有 {{ jobLabels[active.state] }} 任务。<RouterLink
          :to="`/ops/tasks/${active.id}`"
          >查看原任务</RouterLink
        >，当前不能交错发起操作。
      </p>
      <template v-if="page === 'overview'"
        ><div class="console-stat-grid ops-overview-stats">
          <section class="console-panel console-stat">
            <span>业务服务</span
            ><strong>{{
              status ? (status.healthy ? "健康" : "未通过健康检查") : "未读取"
            }}</strong>
            <p>
              {{
                status?.managedProcessRunning
                  ? "受控进程运行中"
                  : "未确认受控进程运行"
              }}
              · {{ status?.release.id || "未登记运行版本" }}
            </p>
          </section>
          <section class="console-panel console-stat">
            <span>独立运维</span
            ><strong>{{ status ? "可达" : "未读取" }}</strong>
            <p>业务健康与运维可达分别核对</p>
          </section>
          <section class="console-panel console-stat">
            <span>最近完整备份</span
            ><strong>{{
              backups.find((b) => b.complete)
                ? dateText(
                    backups
                      .filter((b) => b.complete)
                      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0]
                      ?.createdAt || "",
                  )
                : "暂无完整备份"
            }}</strong>
            <p>完整清单不等于已完成离机备份</p>
          </section>
        </div>
        <section class="console-panel ops-controlled">
          <h2>受控操作</h2>
          <div class="ops-operation-links">
            <RouterLink
              v-for="(label, path) in {
                deploy: '部署已准备版本',
                restart: '重启业务服务',
                backups: '创建一致性备份',
                restore: '校验并恢复备份',
              }"
              :key="path"
              class="button"
              :to="`/ops/${path}`"
              >{{ label }}</RouterLink
            >
          </div>
        </section></template
      >
      <section
        v-if="['overview', 'logs', 'exceptions'].includes(page)"
        class="console-panel ops-task-list"
      >
        <div class="console-filterbar">
          <h2>
            {{
              page === "exceptions"
                ? "失败、中断与冲突任务"
                : "最近任务（最多100条）"
            }}
          </h2>
          <button :disabled="busy" @click="load">刷新任务</button>
        </div>
        <div class="console-table-wrap">
          <table class="console-table">
            <thead>
              <tr>
                <th>任务编号 / 时间</th>
                <th>动作 / 目标</th>
                <th>阶段</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in jobs.filter(
                  (j) =>
                    page !== 'exceptions' ||
                    ['FAILED', 'INTERRUPTED', 'QUEUED', 'RUNNING'].includes(
                      j.state,
                    ),
                )"
                :key="row.id"
              >
                <td>
                  {{ row.id }}<small>{{ dateText(row.createdAt) }}</small>
                </td>
                <td>
                  {{ actionLabels[row.action] }} / {{ row.target
                  }}<small>{{ row.referenceId }}</small>
                </td>
                <td>
                  {{
                    phases[row.events.at(-1)?.phase || ""] ||
                    row.events.at(-1)?.phase ||
                    "等待执行"
                  }}
                </td>
                <td>
                  <span
                    class="console-badge"
                    :class="{
                      negative: ['FAILED', 'INTERRUPTED'].includes(row.state),
                    }"
                    >{{ jobLabels[row.state] }}</span
                  >
                </td>
                <td>
                  <RouterLink :to="`/ops/tasks/${row.id}`"
                    >查看原任务</RouterLink
                  >
                </td>
              </tr>
              <tr v-if="!jobs.length">
                <td colspan="5">暂无任务记录。</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section v-if="page === 'logs'" class="console-panel ops-logs-panel">
        <h2>必要请求字段与任务事件</h2>
        <ConsoleTable
          :columns="['时间', '任务', '阶段']"
          :rows="
            logs?.taskEvents.map((e) => [
              dateText(e.occurredAt),
              e.jobId,
              phases[e.phase] || e.phase,
            ]) || []
          "
          empty="暂无任务阶段日志。"
        /><ConsoleTable
          :columns="['追踪编号', '动作 / 模块', '状态 / 错误码', '耗时ms']"
          :rows="
            logs?.businessRequests.map((r) => [
              r.traceId || '—',
              `${r.action || '—'} / ${r.module || '—'}`,
              `${r.status || '—'} / ${r.code || '—'}`,
              r.durationMs || '—',
            ]) || []
          "
          empty="暂无业务安全请求日志。"
        />
        <p class="console-muted">
          仅展示合同提供的有限字段，不读取原始日志、SQL、凭据或运行目录。
        </p>
      </section>
      <template
        v-if="['deploy', 'restart', 'backups', 'restore'].includes(page)"
      >
        <section
          v-if="page === 'deploy'"
          class="console-panel ops-release-list"
        >
          <h2>选择已登记发布包</h2>
          <div class="console-table-wrap">
            <table class="console-table">
              <thead>
                <tr>
                  <th>发布包</th>
                  <th>SHA256</th>
                  <th>模式指纹</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in artifacts" :key="row.id">
                  <td>{{ row.id }}</td>
                  <td class="console-fingerprint">{{ row.sha256 }}</td>
                  <td class="console-fingerprint">
                    {{ row.schemaFingerprint }}
                  </td>
                  <td>
                    <button
                      :disabled="frozen"
                      @click="
                        reference = row.id;
                        changed();
                      "
                    >
                      选择
                    </button>
                  </td>
                </tr>
                <tr v-if="!artifacts.length">
                  <td colspan="4">暂无已登记包，请由维护人员在本机CLI登记。</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section
          v-if="['backups', 'restore'].includes(page)"
          class="console-panel ops-backup-list"
        >
          <h2>备份记录（最多100份）</h2>
          <div class="console-table-wrap">
            <table class="console-table">
              <thead>
                <tr>
                  <th>备份 / 时点</th>
                  <th>完整性清单</th>
                  <th>模式指纹</th>
                  <th>离机副本</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in backups" :key="row.id">
                  <td>
                    {{ row.id }}<small>{{ dateText(row.createdAt) }}</small>
                  </td>
                  <td>
                    {{ row.complete ? "清单标记完整" : "不完整"
                    }}<small>文件SHA256在执行前由服务端核验</small>
                  </td>
                  <td class="console-fingerprint">
                    {{ row.schemaFingerprint }}
                  </td>
                  <td>合同未提供</td>
                  <td>
                    <button
                      v-if="page === 'restore'"
                      :disabled="frozen || !row.complete"
                      @click="
                        reference = row.id;
                        changed();
                      "
                    >
                      选择此备份</button
                    ><RouterLink
                      v-else
                      :to="{ path: '/ops/restore', query: { backup: row.id } }"
                      >核对并恢复</RouterLink
                    >
                  </td>
                </tr>
                <tr v-if="!backups.length">
                  <td colspan="5">暂无备份记录。</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <div class="console-split console-followup">
          <section class="console-panel ops-deploy-confirm">
            <h2>{{ actionLabels[action] }}前确认</h2>
            <p>固定目标：城院帮业务服务（business）</p>
            <p>当前版本：{{ status?.release.id || "未登记" }}</p>
            <p v-if="['deploy', 'restore'].includes(page)">
              所选{{ page === "deploy" ? "版本" : "备份" }}：{{
                reference || "未选择"
              }}
            </p>
            <p class="console-warning">
              {{
                page === "restore"
                  ? "恢复会覆盖当前业务数据库、图片和备份版本。服务端先校验清单、模式和SHA256，并保存当前保护副本。"
                  : "业务可能短暂停止；独立运维保持可查询，完整备份包含数据库、图片和版本清单。"
              }}
            </p>
            <p v-if="page === 'restore'">
              当前未执行恢复前校验，不把“清单完整”显示成所有检查已通过。校验及保护副本阶段以原任务事件为准。
            </p>
            <button
              :disabled="!canAct || frozen || !validReference"
              @click="prepare"
            >
              读取操作确认
            </button>
            <section v-if="confirmation" class="console-confirmation">
              <h3>请确认实际影响</h3>
              <p>{{ confirmation.impact }}</p>
              <p>
                动作：{{ actionLabels[confirmation.action] }} · 目标：{{
                  confirmation.target
                }}
                · 引用：{{ confirmation.referenceId || "当前受控版本" }}
              </p>
              <p>
                确认有效至 {{ dateText(confirmation.expiresAt)
                }}{{ expired ? "（已过期，请重新读取）" : "" }}
              </p>
              <label class="console-check"
                ><input
                  v-model="acknowledged"
                  type="checkbox"
                  :disabled="frozen || expired"
                />我已核对目标、版本/备份及停机或覆盖影响</label
              ><button
                :class="
                  page === 'restore' ? 'console-danger' : 'console-primary'
                "
                :disabled="!acknowledged || expired || frozen || !canAct"
                @click="submit"
              >
                确认{{ actionLabels[confirmation.action] }}并创建任务
              </button>
            </section>
          </section>
          <p class="console-note">
            返回任务编号仅代表已受理。页面持续显示排队、执行、完成、失败或中断；HTTP200读取到失败任务也不会标记成功。重复请求保留原操作和请求键。
          </p>
        </div>
        <section
          v-if="page === 'backups'"
          class="console-panel ops-backup-policy"
        >
          <h2>策略与范围</h2>
          <p>
            当前支持手动完整备份。每日自动备份、保留调度与离机副本尚未接入；RPO/RTO目标仍需实测。
          </p>
        </section>
      </template>
      <template v-if="page === 'task' && job"
        ><section class="console-panel ops-task-summary">
          <span
            class="console-badge"
            :class="{ negative: ['FAILED', 'INTERRUPTED'].includes(job.state) }"
            >{{ jobLabels[job.state] }}</span
          >
          <h2>
            {{ actionLabels[job.action] }} · {{ job.target }}
            {{ job.referenceId }}
          </h2>
          <p>
            提交 {{ dateText(job.createdAt) }} · 开始
            {{ job.startedAt ? dateText(job.startedAt) : "尚未开始" }} · 结束
            {{ job.finishedAt ? dateText(job.finishedAt) : "尚未结束" }}
          </p>
        </section>
        <div class="console-split ops-task-split">
          <section class="console-panel ops-task-stages">
            <h2>执行阶段</h2>
            <ol>
              <li
                v-for="(event, index) in job.events"
                :key="index"
                class="complete"
              >
                <span class="ops-stage-dot"></span>
                <div>
                  <h3>{{ phases[event.phase] || event.phase }}</h3>
                  <p>{{ dateText(event.occurredAt) }}</p>
                </div>
              </li>
            </ol>
            <p v-if="!job.events.length">任务已登记，尚无执行阶段。</p>
          </section>
          <section class="console-panel ops-task-result">
            <h2>结果与操作</h2>
            <h3
              :class="
                job.state === 'SUCCEEDED'
                  ? 'console-positive'
                  : 'console-warning-text'
              "
            >
              {{ jobLabels[job.state] }}
            </h3>
            <p v-if="job.errorCode" class="error">
              错误码：{{ job.errorCode }}
            </p>
            <template v-if="job.result"
              ><p v-if="job.result.healthy !== undefined">
                业务健康检查：{{ job.result.healthy ? "通过" : "未通过" }}
              </p>
              <p v-if="job.result.backupId">
                完整备份：{{ job.result.backupId }}
              </p>
              <p v-if="job.result.protectionBackupId">
                保护备份：{{ job.result.protectionBackupId }}
              </p>
              <p v-if="job.result.referenceId">
                引用：{{ job.result.referenceId }}
              </p></template
            >
            <p v-if="job.state === 'INTERRUPTED'">
              执行器中断，禁止自动重做。请按手册完成本机核查。
            </p>
            <p v-if="['QUEUED', 'RUNNING'].includes(job.state)">
              每2秒查询原任务，业务停止不会改变任务编号。切到后台时暂停轮询。
            </p>
            <button :disabled="busy" @click="load">刷新原任务状态</button>
            <p>
              <RouterLink to="/ops/exceptions">查看失败与冲突任务</RouterLink>
            </p>
            <RouterLink to="/ops/manual">查看维护手册</RouterLink>
          </section>
        </div></template
      >
      <template v-if="page === 'manual'"
        ><section class="console-panel ops-manual-table">
          <h2>选择正确的恢复目标</h2>
          <ConsoleTable
            :columns="['需要处理的情况', '应使用的流程', '核对重点']"
            :rows="[
              [
                '回到同模式旧代码',
                '选择CLI已登记发布包',
                '数据库模式指纹和运行版本',
              ],
              [
                '数据回到备份时点',
                '备份校验与数据库、图片、版本恢复',
                '覆盖影响、保护副本及任务健康检查',
              ],
            ]"
          />
        </section>
        <section class="console-panel ops-manual-checklist">
          <h2>维护与交接清单</h2>
          <ol>
            <li>
              先按OPS_API.md配置五个独立目录、业务私密配置和固定本机目标，完成迁移及初始化独立运维账号。
            </li>
            <li>
              CLI登记已审阅JAR后启动运维进程；只管理自身持有PID与开始时刻的业务，不接管手工启动的进程。
            </li>
            <li>
              部署、重启、备份或恢复：核对目标与引用，读取确认、明确确认，再持续查询同一任务。
            </li>
            <li>
              失败或中断：保留原任务和保护副本，核对数据库、图片、版本和进程；recoveryRequired只能由本机CLI人工核查后解除。
            </li>
            <li>
              运维自身或整机不可达：按离线手册处理，网页不提供任意命令、SQL或路径。
            </li>
          </ol>
          <p>
            每日调度、离机备份、跨主机部署及RPO/RTO计时尚未完成，不能按“健康”状态推定它们已验收。
          </p>
        </section></template
      >
    </template>
  </div>
</template>
