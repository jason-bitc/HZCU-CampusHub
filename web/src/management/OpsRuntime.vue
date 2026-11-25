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
import { ApiError } from "../lib/api";
import { dateText } from "../lib/content";
import ConsoleTable from "./ConsoleTable.vue";
import ConsoleConfirm from "./ConsoleConfirm.vue";
import ConsoleReadState from "./ConsoleReadState.vue";
import CopyValue from "./CopyValue.vue";
import { consoleError, opsLogQuery } from "./console-ux";
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
const loading = ref(false);
const jobsLoaded = ref(false);
const backupsLoaded = ref(false);
const artifactsLoaded = ref(false);
const readError = ref("");
const logError = ref("");
const lastTrace = ref("");
const backupReceipt = ref<Backup>();
const backupReadError = ref("");
const logTrace = ref(
  typeof route.query.traceId === "string" ? route.query.traceId : "",
);
const logOnlyErrors = ref(false);
const logLimit = ref(100);
const submittedLogQuery = ref("");
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
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
    !readError.value &&
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
    error.value = consoleError(e);
  } finally {
    busy.value = false;
  }
}
function nextPoll(delay: number) {
  if (
    !document.hidden &&
    (!job.value || ["QUEUED", "RUNNING"].includes(job.value.state))
  )
    poll = setTimeout(() => void load(), delay);
}
async function loadLogs(current: number) {
  const query = opsLogQuery(
    logTrace.value,
    logOnlyErrors.value,
    logLimit.value,
  );
  logError.value = "";
  try {
    const data = await opsRequest<NonNullable<typeof logs.value>>(
      `/ops/logs?${query}`,
    );
    if (current !== generation) return;
    logs.value = data;
    submittedLogQuery.value = query;
  } catch (e) {
    if (current === generation) logError.value = consoleError(e);
  }
}
async function load() {
  if (!ready.value || page.value === "login" || loading.value) return;
  const current = ++generation;
  stopPoll();
  loading.value = true;
  readError.value = "";
  try {
    const raw = sessionStorage.getItem(pendingKey);
    pending.value = raw ? (JSON.parse(raw) as JobInput) : undefined;
    if (page.value === "task") {
      let trace = "";
      const value = await opsRequest<Job>(
        `/ops/jobs/${route.params.id}`,
        "GET",
        undefined,
        false,
        false,
        (id) => {
          trace = id;
        },
      );
      if (current !== generation) return;
      job.value = value;
      lastTrace.value = trace;
      nextPoll(2000);
      const backupId = value.result?.backupId;
      if (backupId) {
        backupReadError.value = "";
        try {
          const list = await opsRequest<{ items: Backup[] }>("/ops/backups");
          if (current === generation)
            backupReceipt.value = list.items.find(
              (item) => item.id === backupId,
            );
        } catch (e) {
          if (current === generation) backupReadError.value = consoleError(e);
        }
      }
      return;
    }
    const [s, j, b, a] = await Promise.allSettled([
      opsRequest<OpsStatus>("/ops/status"),
      opsRequest<{ items: Job[] }>("/ops/jobs"),
      ["overview", "backups", "restore"].includes(page.value)
        ? opsRequest<{ items: Backup[] }>("/ops/backups")
        : undefined,
      page.value === "deploy"
        ? opsRequest<{ items: Artifact[] }>("/ops/artifacts")
        : undefined,
    ]);
    if (current !== generation) return;
    if (s.status === "fulfilled") status.value = s.value;
    if (j.status === "fulfilled") {
      jobs.value = j.value.items;
      jobsLoaded.value = true;
    }
    if (b.status === "fulfilled" && b.value) {
      backups.value = b.value.items;
      backupsLoaded.value = true;
    }
    if (a.status === "fulfilled" && a.value) {
      artifacts.value = a.value.items;
      artifactsLoaded.value = true;
    }
    const errors = [s, j, b, a].flatMap((result) =>
      result.status === "rejected" ? [consoleError(result.reason)] : [],
    );
    readError.value = [...new Set(errors)].join(" ");
    if (page.value === "logs") await loadLogs(current);
    if (
      page.value === "restore" &&
      !reference.value &&
      typeof route.query.backup === "string"
    )
      reference.value = route.query.backup;
  } catch (e) {
    if (current !== generation) return;
    readError.value = consoleError(e);
    if (
      page.value === "task" &&
      !(e instanceof ApiError && [401, 403, 404].includes(e.status))
    )
      nextPoll(4000);
  } finally {
    if (current === generation) loading.value = false;
  }
}
async function prepare() {
  if (!canAct.value || frozen.value || loading.value || !validReference.value)
    return;
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
    error.value = consoleError(e);
  } finally {
    busy.value = false;
  }
}
async function submit() {
  if (busy.value || loading.value) return;
  if (!pending.value) {
    if (
      !confirmation.value ||
      expired.value ||
      !acknowledged.value ||
      !canAct.value
    )
      return;
    const c = confirmation.value;
    const snapshot = c.restorePreview;
    const review = await modal.value?.ask({
      title: "确认" + actionLabels[c.action] + "？",
      detail: [
        "目标 " + c.target,
        c.action === "RESTORE"
          ? "备份 " + c.referenceId
          : "构建版本 " +
            (c.referenceId || status.value?.release.id || "当前未登记版本"),
        c.impact,
        snapshot
          ? "覆盖范围 " +
            snapshot.affectedComponents.join("、") +
            "。预检核对文件 " +
            snapshot.verifiedFileCount +
            " 份。"
          : "",
        c.action === "BACKUP" ? "范围包括数据库、媒体文件及版本清单。" : "",
      ]
        .filter(Boolean)
        .join("\n"),
      action: "执行" + actionLabels[c.action],
      danger: c.action !== "BACKUP",
    });
    if (
      !review ||
      confirmation.value !== c ||
      expired.value ||
      !canAct.value ||
      busy.value ||
      pending.value
    )
      return;
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
    error.value = `${consoleError(e)} 原操作、确认ID和请求键已保留；请先按原请求核对，不要重新创建任务。`;
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
      (await modal.value?.ask({
        title: "结束本机待确认任务？",
        detail:
          "最近任务中未找到本次请求。请核对目标、版本和时间。结束本机记录后，服务端已受理的任务仍会继续。",
        action: "结束本机记录",
      }))
    ) {
      sessionStorage.removeItem(pendingKey);
      pending.value = undefined;
      confirmation.value = undefined;
      acknowledged.value = false;
      if (match) await router.push(`/ops/tasks/${match.id}`);
    }
  } catch (e) {
    error.value = consoleError(e);
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
      jobsLoaded.value = backupsLoaded.value = artifactsLoaded.value = false;
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
    <ConsoleConfirm ref="modal" />
    <h1 class="console-title">
      {{
        page === "task" ? `任务 ${route.params.id} · 持久进度` : titles[page]
      }}
    </h1>
    <ConsoleReadState
      v-if="page !== 'task'"
      :loading="loading"
      :error="readError"
      :stale="!!status || !!jobs.length"
      label="正在读取运维记录…"
      @retry="load"
    />
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
            ><strong
              class="ops-health"
              :data-health="
                status ? (status.healthy ? 'up' : 'down') : 'unknown'
              "
              >{{
                status ? (status.healthy ? "健康" : "未通过健康检查") : "未读取"
              }}</strong
            >
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
      <section v-if="page === 'logs'" class="console-panel ops-logs-panel">
        <form class="console-filterbar" @submit.prevent="load">
          <label class="console-inline-label"
            >追踪号<input
              v-model="logTrace"
              placeholder="小写 UUID"
              pattern="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
          /></label>
          <label class="console-inline-label"
            >条数<select v-model.number="logLimit">
              <option :value="20">20</option>
              <option :value="50">50</option>
              <option :value="100">100</option>
            </select></label
          >
          <label class="console-check"
            ><input v-model="logOnlyErrors" type="checkbox" />仅错误请求</label
          >
          <button :disabled="loading">查询日志</button>
        </form>
        <ConsoleReadState
          :error="logError"
          :stale="!!logs"
          :loading="loading"
          label="正在读取日志…"
          @retry="load"
        />
        <p
          v-if="
            logs &&
            (logError ||
              submittedLogQuery !==
                opsLogQuery(logTrace, logOnlyErrors, logLimit))
          "
          class="hint"
        >
          下方记录来自上次成功查询。
        </p>
        <h2>必要请求字段与任务事件</h2>
        <div v-if="logs" class="ops-log-groups">
          <section aria-labelledby="ops-task-log-heading">
            <h3 id="ops-task-log-heading">任务阶段日志</h3>
            <ol
              v-if="logs.taskEvents.length"
              class="ops-log-stream"
              aria-labelledby="ops-task-log-heading"
              tabindex="0"
            >
              <li
                v-for="(event, index) in logs.taskEvents"
                :key="index"
                class="ops-log-record"
              >
                <div class="ops-log-meta">
                  <time :datetime="event.occurredAt">{{
                    dateText(event.occurredAt)
                  }}</time>
                  <span class="console-log-badge">{{
                    phases[event.phase] || event.phase
                  }}</span>
                </div>
                <CopyValue :value="event.jobId" label="任务 ID" />
              </li>
            </ol>
            <p v-else class="ops-log-empty">暂无任务阶段日志。</p>
          </section>
          <section aria-labelledby="ops-request-log-heading">
            <h3 id="ops-request-log-heading">业务安全请求日志</h3>
            <ol
              v-if="logs.businessRequests.length"
              class="ops-log-stream"
              aria-labelledby="ops-request-log-heading"
              tabindex="0"
            >
              <li
                v-for="(request, index) in logs.businessRequests"
                :key="index"
                class="ops-log-record"
              >
                <div class="ops-log-meta">
                  <strong
                    >{{ request.action || "—" }} /
                    {{ request.module || "—" }}</strong
                  >
                  <span
                    class="console-log-badge"
                    :class="{
                      'console-log-error': Number(request.status) >= 400,
                    }"
                  >
                    {{ request.status || "—" }} / {{ request.code || "—" }}
                  </span>
                  <span v-if="request.durationMs"
                    >{{ request.durationMs }} ms</span
                  >
                </div>
                <CopyValue
                  v-if="request.traceId"
                  :value="request.traceId"
                  label="追踪编号"
                />
                <span v-else>追踪编号 —</span>
              </li>
            </ol>
            <p v-else class="ops-log-empty">暂无业务安全请求日志。</p>
          </section>
        </div>
        <p class="console-muted">
          仅展示合同提供的有限字段，不读取原始日志、SQL、凭据或运行目录。
        </p>
      </section>
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
          <table class="console-table console-responsive-table" role="table">
            <thead role="rowgroup">
              <tr role="row">
                <th scope="col" role="columnheader">任务编号 / 时间</th>
                <th scope="col" role="columnheader">动作 / 目标</th>
                <th scope="col" role="columnheader">阶段</th>
                <th scope="col" role="columnheader">状态</th>
                <th scope="col" role="columnheader">操作</th>
              </tr>
            </thead>
            <tbody role="rowgroup">
              <tr
                role="row"
                v-for="row in jobs.filter(
                  (j) =>
                    page !== 'exceptions' ||
                    ['FAILED', 'INTERRUPTED', 'QUEUED', 'RUNNING'].includes(
                      j.state,
                    ),
                )"
                :key="row.id"
              >
                <td data-label="任务编号 / 时间" role="cell">
                  {{ row.id }}<small>{{ dateText(row.createdAt) }}</small>
                </td>
                <td data-label="动作 / 目标" role="cell">
                  {{ actionLabels[row.action] }} / {{ row.target
                  }}<small>{{ row.referenceId }}</small>
                </td>
                <td data-label="阶段" role="cell">
                  {{
                    phases[row.events.at(-1)?.phase || ""] ||
                    row.events.at(-1)?.phase ||
                    "等待执行"
                  }}
                </td>
                <td data-label="状态" role="cell">
                  <span
                    class="console-badge"
                    :data-state="row.state"
                    :class="{
                      negative: ['FAILED', 'INTERRUPTED'].includes(row.state),
                    }"
                    >{{ jobLabels[row.state] }}</span
                  >
                </td>
                <td data-label="操作" role="cell">
                  <RouterLink :to="`/ops/tasks/${row.id}`"
                    >查看原任务</RouterLink
                  >
                </td>
              </tr>
              <tr role="row" v-if="jobsLoaded && !jobs.length">
                <td class="console-empty-cell" role="cell" colspan="5">
                  暂无任务记录。
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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
            <table class="console-table console-responsive-table" role="table">
              <thead role="rowgroup">
                <tr role="row">
                  <th scope="col" role="columnheader">发布包</th>
                  <th scope="col" role="columnheader">SHA256</th>
                  <th scope="col" role="columnheader">模式指纹</th>
                  <th scope="col" role="columnheader">操作</th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                <tr role="row" v-for="row in artifacts" :key="row.id">
                  <td data-label="发布包" role="cell">{{ row.id }}</td>
                  <td
                    data-label="SHA256"
                    role="cell"
                    class="console-fingerprint"
                  >
                    {{ row.sha256 }}
                  </td>
                  <td
                    data-label="模式指纹"
                    role="cell"
                    class="console-fingerprint"
                  >
                    {{ row.schemaFingerprint }}
                  </td>
                  <td data-label="操作" role="cell">
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
                <tr role="row" v-if="artifactsLoaded && !artifacts.length">
                  <td class="console-empty-cell" role="cell" colspan="4">
                    暂无已登记包，请由维护人员在本机CLI登记。
                  </td>
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
            <table class="console-table console-responsive-table" role="table">
              <thead role="rowgroup">
                <tr role="row">
                  <th scope="col" role="columnheader">备份 / 时点</th>
                  <th scope="col" role="columnheader">完整性清单</th>
                  <th scope="col" role="columnheader">模式指纹</th>
                  <th scope="col" role="columnheader">保留参考</th>
                  <th scope="col" role="columnheader">操作</th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                <tr
                  role="row"
                  v-for="row in backups"
                  :key="row.id"
                  :class="{ 'console-match': row.id === route.query.backup }"
                >
                  <td data-label="备份 / 时点" role="cell">
                    {{ row.id }}<small>{{ dateText(row.createdAt) }}</small>
                  </td>
                  <td data-label="完整性清单" role="cell">
                    {{ row.complete ? "清单标记完整" : "不完整"
                    }}<small>文件SHA256在执行前由服务端核验</small>
                  </td>
                  <td
                    data-label="模式指纹"
                    role="cell"
                    class="console-fingerprint"
                  >
                    {{ row.schemaFingerprint }}
                  </td>
                  <td data-label="保留参考" role="cell">
                    {{
                      row.withinRetentionWindow === true
                        ? "最近七份"
                        : row.withinRetentionWindow === false
                          ? "早期快照"
                          : "待核对"
                    }}
                  </td>
                  <td data-label="操作" role="cell">
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
                <tr role="row" v-if="backupsLoaded && !backups.length">
                  <td class="console-empty-cell" role="cell" colspan="5">
                    暂无备份记录。
                  </td>
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
              {{
                confirmation?.restorePreview
                  ? "服务端已返回备份预检结果，请核对覆盖范围。"
                  : "请选择备份并读取恢复预检。"
              }}
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
              <dl v-if="confirmation.restorePreview">
                <dt>备份快照</dt>
                <dd>
                  {{
                    dateText(
                      confirmation.restorePreview.backupSnapshotAt ||
                        confirmation.restorePreview.backupCreatedAt,
                    )
                  }}
                </dd>
                <dt>备份版本</dt>
                <dd>
                  {{
                    confirmation.restorePreview.backupReleaseId ||
                    "沿用当前版本"
                  }}
                </dd>
                <dt>覆盖范围</dt>
                <dd>
                  {{
                    confirmation.restorePreview.affectedComponents
                      .map(
                        (item) =>
                          ({
                            DATABASE: "数据库",
                            MEDIA: "图片",
                            RELEASE: "应用版本",
                          })[item] || item,
                      )
                      .join("、")
                  }}
                </dd>
                <dt>已完成文件核对</dt>
                <dd>
                  {{ confirmation.restorePreview.verifiedFileCount }}
                  个，其中媒体
                  {{ confirmation.restorePreview.mediaFileCount }} 个
                </dd>
                <dt>业务核验基线</dt>
                <dd>
                  {{
                    confirmation.restorePreview.businessSnapshotAvailable
                      ? "已包含"
                      : "暂无，恢复后需抽查"
                  }}
                </dd>
              </dl>
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
          <p v-if="status?.dailyBackup?.enabled">
            每日备份 {{ status.dailyBackup.time }}（{{
              status.dailyBackup.timeZone
            }}）。 最近入队
            {{
              status.dailyBackup.lastScheduledFor || "暂无"
            }}，执行结果见任务。
          </p>
          <p v-else-if="status?.dailyBackup">当前环境未开启每日自动备份。</p>
          <p>离机副本由维护人员导出和验收，步骤见运维手册。</p>
        </section>
      </template>
      <ConsoleReadState
        v-if="page === 'task'"
        :error="readError ? (job ? '当前进度待确认。 ' : '') + readError : ''"
        :stale="!!job"
        :loading="loading && !job"
        label="正在读取原任务…"
        @retry="load"
      />
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
            当前阶段
            {{
              phases[job.events.at(-1)?.phase || ""] ||
              job.events.at(-1)?.phase ||
              "等待执行"
            }}
            · 最近更新
            {{
              dateText(
                job.finishedAt ||
                  job.events.at(-1)?.occurredAt ||
                  job.startedAt ||
                  job.createdAt,
              )
            }}
          </p>
          <p><CopyValue :value="job.id" label="任务编号" /></p>
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
              执行阶段 {{ job.events.at(-1)?.phase || "等待执行" }} · 错误码
              <CopyValue :value="job.errorCode" label="错误码" />
            </p>
            <p v-if="lastTrace">
              本次查询追踪号 <CopyValue :value="lastTrace" label="追踪号" />
            </p>
            <p v-if="job.protectionBackupId || job.result?.protectionBackupId">
              保护备份：{{
                job.protectionBackupId || job.result?.protectionBackupId
              }}
            </p>
            <template v-if="job.result"
              ><p v-if="job.result.healthy !== undefined">
                业务健康检查：{{ job.result.healthy ? "通过" : "未通过" }}
              </p>
              <p v-if="job.result.backupId">
                完整备份
                <CopyValue :value="job.result.backupId" label="备份编号" />
                <RouterLink
                  :to="{
                    path: '/ops/backups',
                    query: { backup: job.result.backupId },
                  }"
                  >核对备份时间与保留状态</RouterLink
                >
              </p>
              <p v-if="backupReceipt">
                备份时间 {{ dateText(backupReceipt.createdAt) }} ·
                {{
                  backupReceipt.withinRetentionWindow === true
                    ? "最近七份"
                    : backupReceipt.withinRetentionWindow === false
                      ? "早期快照"
                      : "保留状态待核对"
                }}
              </p>
              <p v-else-if="backupReadError" class="error" role="alert">
                备份清单读取失败。{{ backupReadError }}
              </p>
              <p v-if="job.result.verifiedMediaReferences !== undefined">
                已核对媒体引用 {{ job.result.verifiedMediaReferences }} 条
              </p>
              <p v-if="job.result.businessVerification">
                业务数据核对
                {{
                  job.result.businessVerification.status === "VERIFIED"
                    ? "通过"
                    : "暂无基线"
                }}
                ·
                {{ job.result.businessVerification.checkedTables.length }} 张表
              </p>
              <p v-if="job.result.recoveryEvidence">
                快照距恢复开始
                {{
                  job.result.recoveryEvidence.snapshotAgeMillis === null
                    ? "暂无数据"
                    : `${Math.round(job.result.recoveryEvidence.snapshotAgeMillis / 1000)} 秒`
                }}。 恢复到服务可用耗时
                {{
                  job.result.recoveryEvidence.restoreElapsedMillis === null
                    ? "暂无数据"
                    : `${Math.round(job.result.recoveryEvidence.restoreElapsedMillis / 1000)} 秒`
                }}。
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
            <button :disabled="loading" @click="load">刷新原任务状态</button>
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
