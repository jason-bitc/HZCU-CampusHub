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
import { consoleError, opsLogQuery } from "./console-ux";
import ConsoleConfirm from "./ConsoleConfirm.vue";
import CopyValue from "./CopyValue.vue";
import { dateText, shortDateText } from "../lib/content";
import ConsolePageHeader from "./ConsolePageHeader.vue";
import ConsolePager from "./ConsolePager.vue";
import ConsoleSteps from "./ConsoleSteps.vue";
const route = useRoute(),
  router = useRouter();
const page = computed(() =>
  route.path.includes("/tasks/")
    ? "task"
    : route.path.split("/").pop() || "overview",
);
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
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
const loading = ref(false);
const readError = ref("");
const logError = ref("");
const backupReadError = ref("");
const backupReceipt = ref<Backup>();
const lastTrace = ref("");
const logTrace = ref("");
const logOnlyErrors = ref(false);
const logLimit = ref(50);
const submittedLogQuery = ref("");
const reference = ref(""),
  confirmation = ref<Confirmation>(),
  acknowledged = ref(false),
  pending = ref<JobInput>(),
  now = ref(Date.now()),
  backupConfirm = ref(false);
const filters = ref({ action: "", state: "", id: "" }),
  logFilters = ref({ source: "", level: "", ref: "" }),
  taskPage = ref(1);
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
const lastCompleteBackup = computed(() =>
  backups.value
    .filter((b) => b.complete)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0],
);
const filteredJobs = computed(() =>
  jobs.value.filter((row) => {
    if (
      page.value === "exceptions" &&
      !["FAILED", "INTERRUPTED", "QUEUED", "RUNNING"].includes(row.state)
    )
      return false;
    if (filters.value.action && row.action !== filters.value.action)
      return false;
    if (filters.value.state && row.state !== filters.value.state) return false;
    if (
      filters.value.id &&
      !row.id.toLowerCase().includes(filters.value.id.toLowerCase())
    )
      return false;
    return true;
  }),
);
const pagedJobs = computed(() =>
  filteredJobs.value.slice((taskPage.value - 1) * 20, taskPage.value * 20),
);
const logRows = computed(() => {
  const rows: {
    time: string;
    source: string;
    level: string;
    summary: string;
    ref: string;
  }[] = [];
  for (const e of logs.value?.taskEvents || [])
    rows.push({
      time: dateText(e.occurredAt),
      source: "任务事件",
      level: phases[e.phase] || e.phase,
      summary: `任务 ${e.jobId} 进入「${phases[e.phase] || e.phase}」`,
      ref: e.jobId,
    });
  for (const r of logs.value?.businessRequests || [])
    rows.push({
      time: "—",
      source: "业务请求",
      level: `${r.status || "—"} / ${r.code || "—"}`,
      summary: `${r.action || "—"} / ${r.module || "—"} · 耗时 ${
        r.durationMs || "—"
      }ms`,
      ref: r.traceId || "—",
    });
  const f = logFilters.value;
  return rows.filter(
    (row) =>
      (!f.source ||
        row.source === (f.source === "task" ? "任务事件" : "业务请求")) &&
      (!f.level || row.level.toLowerCase().includes(f.level.toLowerCase())) &&
      (!f.ref || row.ref.toLowerCase().includes(f.ref.toLowerCase())),
  );
});
const entries = [
  {
    to: "/ops/deploy",
    title: "部署与重启",
    desc: "选择已登记发布包或重启业务进程，先读取确认回执。",
  },
  {
    to: "/ops/backups",
    title: "备份与恢复",
    desc: "创建完整备份，或校验备份后恢复到备份时点。",
  },
  {
    to: "/ops/tasks",
    title: "任务记录",
    desc: "按原任务编号续查排队、执行、成功与失败任务。",
  },
  {
    to: "/ops/manual",
    title: "回退与交接",
    desc: "区分回退与恢复，核对离机副本检查清单。",
  },
];
const manualItems = [
  "按 OPS_API.md 配置五个独立目录、业务私密配置和固定本机目标，完成迁移及初始化独立运维账号。",
  "CLI 登记已审阅 JAR 后启动运维进程；只管理自身持有 PID 与开始时刻的业务，不接管手工启动的进程。",
  "部署、重启、备份或恢复：核对目标与引用，读取确认、明确确认，再持续查询同一任务。",
  "失败或中断：保留原任务和保护副本，核对数据库、图片、版本和进程；recoveryRequired 只能由本机 CLI 人工核查后解除。",
  "运维自身或整机不可达：按离线手册处理，网页不提供任意命令、SQL 或路径。",
];
const componentLabels: Record<string, string> = {
  DATABASE: "数据库",
  MEDIA: "图片",
  RELEASE: "应用版本",
};
const jobTone = (state: string) =>
  (
    {
      QUEUED: "is-neutral",
      RUNNING: "is-running",
      SUCCEEDED: "is-positive",
      FAILED: "is-negative",
      INTERRUPTED: "is-negative",
    } as Record<string, string>
  )[state] || "is-neutral";
function stateBanner(state: string): { tone: string; text: string } {
  const code = job.value?.errorCode;
  switch (state) {
    case "QUEUED":
      return {
        tone: "is-warning",
        text: "任务已受理，尚未开始执行。按原任务编号续查，不要重复创建任务。",
      };
    case "RUNNING":
      return {
        tone: "is-info",
        text: "任务执行中。每 2 秒查询原任务，切到后台时暂停轮询。",
      };
    case "SUCCEEDED":
      return {
        tone: "is-success",
        text: "任务执行成功。以下回执由服务端任务记录回读，仅代表本次任务结果。",
      };
    case "FAILED":
      return {
        tone: "is-error",
        text: `任务执行失败${code ? `（${code}）` : ""}。保留原任务记录，核对后按手册处理，不要自动重做。`,
      };
    case "INTERRUPTED":
      return {
        tone: "is-error",
        text: "执行器中断，禁止自动重做。请按手册完成本机核查。",
      };
    default:
      return {
        tone: "is-neutral",
        text: (jobLabels as Record<string, string>)[state] || state,
      };
  }
}
function eventTone(index: number) {
  if (!job.value || index !== job.value.events.length - 1) return "";
  if (["FAILED", "INTERRUPTED"].includes(job.value.state)) return "is-negative";
  if (job.value.state === "RUNNING") return "is-active";
  return "";
}
const phaseText = (row: Job) =>
  phases[row.events.at(-1)?.phase || ""] ||
  row.events.at(-1)?.phase ||
  "等待执行";
const backupWindow = (row: Backup) =>
  row.withinRetentionWindow === true
    ? "最近七份"
    : row.withinRetentionWindow === false
      ? "早期快照"
      : "待核对";
const protectionBackup = computed(
  () => job.value?.protectionBackupId || job.value?.result?.protectionBackupId,
);
const selectedBackup = computed(() =>
  backups.value.find((row) => row.id === reference.value),
);
const verification = computed(() => job.value?.result?.businessVerification);
const recoveryEvidence = computed(() => job.value?.result?.recoveryEvidence);
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
    }
    if (b.status === "fulfilled" && b.value) {
      backups.value = b.value.items;
    }
    if (a.status === "fulfilled" && a.value) {
      artifacts.value = a.value.items;
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
function resetFilters() {
  filters.value = { action: "", state: "", id: "" };
  taskPage.value = 1;
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
  () => route.fullPath,
  () => {
    backupConfirm.value = false;
    confirmation.value = undefined;
    acknowledged.value = false;
    taskPage.value = 1;
    void load();
  },
);
watch(filters, () => (taskPage.value = 1), { deep: true });
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
  <div class="console-page" :class="`ops-page ops-${page}`">
    <ConsoleConfirm ref="modal" />
    <div v-if="page === 'login'" class="console-login">
      <form class="console-login-card" @submit.prevent="signIn">
        <p class="console-login-crumb">
          <span aria-hidden="true">城院帮</span>
          <span aria-hidden="true">/</span>
          <span aria-hidden="true">独立运维</span>
          <span aria-hidden="true">/</span>
          <span aria-current="page">登录</span>
        </p>
        <h1>独立运维登录</h1>
        <p class="console-login-desc">
          仅限已授权的运维管理员。独立运维进程与授权不依赖业务数据库，业务维护期间仍可查询持久任务。
        </p>
        <p
          v-if="ready"
          class="console-banner is-success console-login-session"
        >
          运维会话已核验。<RouterLink to="/ops/overview">进入运行总览</RouterLink>
        </p>
        <fieldset :disabled="busy">
          <label
            >运维账号<input
              v-model="login"
              name="ops-login"
              autocomplete="username"
              required
              maxlength="64"
              placeholder="输入运维账号" /></label
          ><label
            >密码<input
              v-model="password"
              name="ops-password"
              type="password"
              autocomplete="current-password"
              required
              placeholder="输入密码" /></label
          ><button class="console-primary console-login-submit" type="submit">
            {{ busy ? "正在核验运维身份…" : "登录独立运维" }}
          </button>
        </fieldset>
        <p
          v-if="error"
          class="console-banner is-error console-login-error"
          role="alert"
        >
          {{ error }}
        </p>
        <p class="console-login-extra">
          普通校园账号与社区管理令牌不能获得运维权限。登录失败过多会触发15分钟限流，请核对本机维护凭据后再试。
        </p>
      </form>
    </div>
    <section v-else-if="!ready" class="console-panel console-gate">
      <h2>
        {{ opsSession.status === "checking" ? "正在核验运维会话" : "请先登录独立运维" }}
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
      <p v-if="error" class="console-banner is-error" role="alert">
        {{ error }}
        <span v-if="!pending" class="console-banner-actions"
          ><button type="button" :disabled="busy || loading" @click="load">
            重新读取</button
          ></span
        >
      </p>
      <section
        v-if="pending"
        class="console-banner is-warning"
        role="status"
        aria-label="原操作结果待确认"
      >
        原操作结果待确认：{{ actionLabels[pending.action] }} · business ·
        {{ pending.referenceId || "当前受控版本" }}。原请求已保存，刷新页面不会生成第二个请求键。
        <span class="console-banner-actions">
          <button type="button" :disabled="busy" @click="submit">
            按原请求核对任务
          </button>
          <button type="button" :disabled="busy" @click="reconcile">
            读取任务并核对后结束
          </button>
          <RouterLink class="button" to="/ops/tasks">查看任务记录</RouterLink>
        </span>
      </section>
      <p
        v-if="status?.recoveryRequired"
        class="console-banner is-error"
        role="alert"
      >
        控制器要求人工核查，已阻止新操作。请按手册核对数据库、图片、保护副本和进程，再由本机维护CLI确认；刷新不会解除维护状态。
        <RouterLink to="/ops/manual">查看维护手册</RouterLink>
      </p>
      <p v-if="active" class="console-banner is-warning" role="status">
        同一目标已有 {{ jobLabels[active.state] }} 任务。<RouterLink
          :to="`/ops/tasks/${active.id}`"
          >查看原任务</RouterLink
        >，当前不能交错发起操作。
      </p>
      <p
        v-if="loading && page !== 'task'"
        class="console-muted"
        role="status"
      >
        正在读取运维状态…
      </p>

      <p v-if="readError" class="console-banner is-warning" role="status">
        {{ job && page === "task" ? "任务状态暂无法确认。当前显示上次结果。" : "部分数据读取失败，已有结果保留。" }}
        {{ readError }}
        <button type="button" :disabled="loading || busy" @click="load">重新读取</button>
      </p>
      <template v-if="page === 'overview'">
        <ConsolePageHeader
          title="运行总览"
          description="核对业务健康、运行版本与最近任务，再进入受控操作。"
        />
        <div class="console-split is-wide">
          <section class="console-panel" aria-labelledby="ops-env-title">
            <div class="console-card-head">
              <div>
                <h2 id="ops-env-title" class="console-card-title">运行环境</h2>
                <p class="console-card-desc">
                  受控目标 business · 独立运维端口 8091
                </p>
              </div>
              <div class="console-card-tools">
                <button type="button" :disabled="busy || loading" @click="load">
                  刷新状态
                </button>
              </div>
            </div>
            <dl class="console-facts">
              <div>
                <dt>业务服务</dt>
                <dd>
                  <span
                    class="console-tag"
                    :class="status?.healthy ? 'is-positive' : 'is-negative'"
                    >{{
                      status ? (status.healthy ? "健康" : "未通过健康检查") : "未读取"
                    }}</span
                  >
                  ·
                  {{
                    status?.managedProcessRunning
                      ? "受控进程运行中"
                      : "未确认受控进程运行"
                  }}
                </dd>
              </div>
              <div>
                <dt>运行版本</dt>
                <dd>{{ status?.release.id || "未登记运行版本" }}</dd>
              </div>
              <div>
                <dt>独立运维</dt>
                <dd><span class="console-tag is-positive">可达</span> · 会话已核验</dd>
              </div>
              <div>
                <dt>最近完整备份</dt>
                <dd>
                  {{
                    lastCompleteBackup
                      ? dateText(lastCompleteBackup.createdAt)
                      : "暂无完整备份"
                  }}
                  <small>完整清单不等于已完成离机备份</small>
                </dd>
              </div>
            </dl>
            <h3 class="console-sub-title">最近任务</h3>
            <div class="console-table-wrap">
              <table class="console-table">
                <thead>
                  <tr>
                    <th>任务编号</th>
                    <th>动作</th>
                    <th>阶段</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in jobs.slice(0, 5)" :key="row.id">
                    <td>{{ row.id }}</td>
                    <td>{{ actionLabels[row.action] }}</td>
                    <td>{{ phaseText(row) }}</td>
                    <td>
                      <span class="console-tag" :class="jobTone(row.state)">{{
                        jobLabels[row.state]
                      }}</span>
                    </td>
                    <td>
                      <RouterLink :to="`/ops/tasks/${row.id}`">查看任务</RouterLink>
                    </td>
                  </tr>
                  <tr v-if="!busy && !jobs.length">
                    <td colspan="5" class="console-table-empty">暂无任务记录。</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p class="console-card-foot">
              <template v-if="status?.dailyBackup">
                <template v-if="status.dailyBackup.enabled"
                  >每日备份 {{ status.dailyBackup.time }}（{{
                    status.dailyBackup.timeZone
                  }}），最近入队
                  {{ status.dailyBackup.lastScheduledFor ? dateText(status.dailyBackup.lastScheduledFor) : "暂无" }}，执行结果见任务。</template
                >
                <template v-else>当前环境未开启每日自动备份。</template>
              </template>
              离机副本由维护人员导出和验收。
              <RouterLink to="/ops/tasks">查看全部任务</RouterLink>
            </p>
          </section>
          <section class="console-panel" aria-labelledby="ops-actions-title">
            <h2 id="ops-actions-title" class="console-card-title">受控操作</h2>
            <p class="console-card-desc">
              每项操作先读取确认回执，再提交任务并按原编号续查。
            </p>
            <ul class="console-entry-list">
              <li v-for="entry in entries" :key="entry.to">
                <RouterLink :to="entry.to">
                  <strong>{{ entry.title }}</strong>
                  <span>{{ entry.desc }}</span>
                </RouterLink>
              </li>
            </ul>
            <p class="console-callout">
              任务返回编号仅代表已受理；结果以任务页回读为准。重复请求保留原操作和请求键。
            </p>
          </section>
        </div>
      </template>

      <template v-if="page === 'tasks' || page === 'exceptions'">
        <ConsolePageHeader
          title="任务记录"
          :description="
            page === 'exceptions'
              ? '当前仅显示失败、中断与进行中的任务。'
              : '按动作、状态或原任务编号续查；最多显示最近 100 条。'
          "
        />
        <section class="console-panel" aria-labelledby="ops-jobs-title">
          <div class="console-card-head">
            <div>
              <h2 id="ops-jobs-title" class="console-card-title">
                {{ page === "exceptions" ? "失败、中断与进行中任务" : "全部任务" }}
              </h2>
              <p class="console-card-desc">
                每条任务保留原编号、阶段与状态；失败任务不会标记成功。
              </p>
            </div>
            <div class="console-card-tools">
              <button type="button" :disabled="busy || loading" @click="load">
                读取任务记录
              </button>
            </div>
          </div>
          <div class="console-inline-filters">
            <label class="console-filter"
              >动作<select v-model="filters.action" aria-label="按动作筛选">
                <option value="">全部</option>
                <option
                  v-for="(label, key) in actionLabels"
                  :key="key"
                  :value="key"
                >
                  {{ label }}
                </option></select></label
            ><label class="console-filter"
              >状态<select v-model="filters.state" aria-label="按状态筛选">
                <option value="">全部</option>
                <option v-for="(label, key) in jobLabels" :key="key" :value="key">
                  {{ label }}
                </option></select></label
            ><label class="console-filter"
              >编号<input
                v-model.trim="filters.id"
                placeholder="原任务编号"
                aria-label="按任务编号筛选" /></label
            >
            <div class="console-filter-actions">
              <button type="button" @click="resetFilters">清空筛选</button>
            </div>
          </div>
          <p v-if="busy" class="console-muted" role="status">
            正在读取任务记录…
          </p>
          <div class="console-table-wrap">
            <table class="console-table">
              <thead>
                <tr>
                  <th>任务编号</th>
                  <th>动作 · 目标</th>
                  <th>阶段</th>
                  <th>状态</th>
                  <th>提交时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedJobs" :key="row.id">
                  <td>{{ row.id }}</td>
                  <td>
                    {{ actionLabels[row.action] }} · {{ row.target }}
                    <small v-if="row.referenceId">{{ row.referenceId }}</small>
                  </td>
                  <td>{{ phaseText(row) }}</td>
                  <td>
                    <span class="console-tag" :class="jobTone(row.state)">{{
                      jobLabels[row.state]
                    }}</span>
                  </td>
                  <td>{{ dateText(row.createdAt) }}</td>
                  <td>
                    <RouterLink :to="`/ops/tasks/${row.id}`">查看任务</RouterLink>
                  </td>
                </tr>
                <tr v-if="!busy && !pagedJobs.length">
                  <td colspan="6" class="console-table-empty">
                    {{ jobs.length ? "没有匹配筛选的任务。" : "暂无任务记录。" }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <ConsolePager
            :page="taskPage"
            :total="filteredJobs.length"
            :size="20"
            :busy="busy"
            @turn="taskPage = $event"
          />
        </section>
      </template>

      <template v-if="page === 'logs'">
        <ConsolePageHeader
          title="受控日志"
          description="业务停止期间仍可读取的有限任务事件与业务安全请求字段。"
        />
        <p
          v-if="status && !status.healthy"
          class="console-banner is-warning"
          role="status"
        >
          业务健康检查未通过。独立运维保持可查询；以下仅为合同提供的有限字段，不读取原始日志。
        </p>
        <section class="console-panel" aria-labelledby="ops-logs-title">
          <div class="console-card-head">
            <div>
              <h2 id="ops-logs-title" class="console-card-title">近期日志</h2>
              <p class="console-card-desc">
                仅展示合同提供的有限字段，不读取原始日志、SQL、凭据或运行目录。
              </p>
            </div>
            <div class="console-card-tools">
              <button type="button" :disabled="busy || loading" @click="load">
                刷新日志
              </button>
            </div>
          </div>
          <div class="console-inline-filters">
            <label class="console-filter"
              >来源<select v-model="logFilters.source" aria-label="按来源筛选">
                <option value="">全部</option>
                <option value="task">任务事件</option>
                <option value="request">业务请求</option></select></label
            ><label class="console-filter"
              >级别 / 状态<input
                v-model.trim="logFilters.level"
                placeholder="如 FAILED、5xx"
                aria-label="按级别筛选" /></label
            ><label class="console-filter"
              >关联编号<input
                v-model.trim="logFilters.ref"
                placeholder="任务或追踪编号"
                aria-label="按关联编号筛选" /></label
            >
          </div>
          <div class="console-inline-filters">
            <label class="console-filter"
              >追踪编号<input v-model.trim="logTrace" aria-label="服务端追踪编号"
            /></label>
            <label class="console-filter"
              >最多记录<select v-model.number="logLimit">
                <option :value="30">30</option>
                <option :value="50">50</option>
                <option :value="100">100</option>
              </select></label
            >
            <label class="console-check"><input v-model="logOnlyErrors" type="checkbox" />只读错误</label>
          </div>
          <p v-if="logError" class="console-banner is-warning" role="status">
            {{ logError }}{{ logs ? "。当前显示上次读取结果。" : "" }}
          </p>
          <p v-else-if="submittedLogQuery && submittedLogQuery !== opsLogQuery(logTrace, logOnlyErrors, logLimit)" class="console-form-note">
            筛选条件已修改，刷新日志后应用。
          </p>
          <p v-if="loading" class="console-muted" role="status">正在读取日志…</p>
          <div class="console-table-wrap">
            <table class="console-table">
              <thead>
                <tr>
                  <th>发生时间</th>
                  <th>来源</th>
                  <th>级别</th>
                  <th>安全摘要</th>
                  <th>关联编号</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in logRows" :key="`${row.source}-${index}`">
                  <td>{{ row.time }}</td>
                  <td>{{ row.source }}</td>
                  <td>
                    <span
                      class="console-tag"
                      :class="
                        /fail|error|5\d\d|中断/i.test(row.level)
                          ? 'is-negative'
                          : 'is-neutral'
                      "
                      >{{ row.level }}</span
                    >
                  </td>
                  <td>{{ row.summary }}</td>
                  <td class="num">{{ row.ref }}</td>
                </tr>
                <tr v-if="!busy && !logRows.length">
                  <td colspan="5" class="console-table-empty">
                    {{ logs && (logs.taskEvents.length || logs.businessRequests.length) ? "没有匹配筛选的日志。" : "暂无日志记录。" }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>

      <template v-if="page === 'deploy' || page === 'restart'">
        <ConsolePageHeader
          :title="page === 'deploy' ? '部署已登记发布包' : '重启业务'"
          :description="
            page === 'deploy'
              ? '选择已登记发布包，读取部署确认后提交任务。'
              : '核对目标与执行条件，读取重启确认后提交任务。'
          "
        />
        <ConsoleSteps
          :steps="['读取预检', '核对与确认', '查询任务']"
          :current="confirmation ? 1 : 0"
        />
        <div class="console-split">
          <section class="console-panel" aria-label="目标与引用">
            <h2 class="console-card-title">
              {{ page === "deploy" ? "发布包与受控目标" : "目标与执行条件" }}
            </h2>
            <p class="console-card-desc">
              固定目标：城院帮业务服务（business）
            </p>
            <dl class="console-facts">
              <div>
                <dt>受控目标</dt>
                <dd>business · {{ status?.release.id || "未登记运行版本" }}</dd>
              </div>
              <div v-if="page === 'deploy'">
                <dt>发布包</dt>
                <dd>
                  <template v-if="reference">{{ reference }}</template>
                  <template v-else>尚未选择，请在下方列表中选择</template>
                </dd>
              </div>
              <div v-else>
                <dt>执行条件</dt>
                <dd>重启当前受控版本，不切换发布包</dd>
              </div>
              <div>
                <dt>任务互斥</dt>
                <dd>
                  <span
                    class="console-tag"
                    :class="active ? 'is-warning' : 'is-positive'"
                    >{{
                      active ? `已有${jobLabels[active.state]}任务` : "无进行中任务"
                    }}</span
                  >
                </dd>
              </div>
              <div>
                <dt>维护状态</dt>
                <dd>
                  <span
                    class="console-tag"
                    :class="status?.recoveryRequired ? 'is-negative' : 'is-positive'"
                    >{{ status?.recoveryRequired ? "要求人工核查" : "未要求" }}</span
                  >
                </dd>
              </div>
            </dl>
            <template v-if="page === 'deploy'">
              <h3 class="console-sub-title">已登记发布包</h3>
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
                    <tr
                      v-for="row in artifacts"
                      :key="row.id"
                      :class="{ 'is-selected': reference === row.id }"
                    >
                      <td>{{ row.id }}</td>
                      <td class="console-fingerprint">{{ row.sha256 }}</td>
                      <td class="console-fingerprint">{{ row.schemaFingerprint }}</td>
                      <td>
                        <button
                          class="console-link"
                          type="button"
                          :disabled="frozen"
                          :aria-pressed="reference === row.id"
                          @click="
                            reference = row.id;
                            changed();
                          "
                        >
                          {{ reference === row.id ? "已选择" : "选择" }}
                        </button>
                      </td>
                    </tr>
                    <tr v-if="!artifacts.length">
                      <td colspan="4" class="console-table-empty">
                        暂无已登记包，请由维护人员在本机CLI登记。
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </section>
          <section class="console-panel" aria-label="影响与保护">
            <h2 class="console-card-title">影响与保护</h2>
            <p class="console-card-desc">
              {{
                page === "deploy"
                  ? "部署会切换业务运行版本，业务可能短暂停止。"
                  : "重启会短暂停止业务进程，不切换版本，也不改动数据。"
              }}
            </p>
            <p class="console-callout is-warning">
              {{
                page === "deploy"
                  ? "独立运维保持可查询。部署前请核对发布包指纹与当前模式指纹一致。"
                  : "独立运维保持可查询。同一目标有进行中任务时不能发起重启。"
              }}
            </p>
            <template v-if="confirmation">
              <h3 class="console-sub-title">确认回执</h3>
              <dl class="console-facts">
                <div>
                  <dt>动作 · 目标</dt>
                  <dd>
                    {{ actionLabels[confirmation.action] }} · {{ confirmation.target }}
                  </dd>
                </div>
                <div>
                  <dt>引用</dt>
                  <dd>{{ confirmation.referenceId || "当前受控版本" }}</dd>
                </div>
                <div>
                  <dt>有效至</dt>
                  <dd>
                    {{ dateText(confirmation.expiresAt)
                    }}<small v-if="expired">已过期，请重新读取确认。</small>
                  </dd>
                </div>
              </dl>
              <p class="console-form-note">{{ confirmation.impact }}</p>
              <label class="console-check">
                <input
                  v-model="acknowledged"
                  type="checkbox"
                  :disabled="frozen || expired"
                />我已核对目标、版本/备份及停机或覆盖影响</label
              >
            </template>
            <p v-else class="console-form-note">
              读取确认后显示实际影响与有效期，再勾选核对并提交任务。
            </p>
          </section>
        </div>
        <footer class="console-footer">
          <div class="console-footer-actions">
            <RouterLink class="button console-secondary" to="/ops/overview">
              返回运行总览
            </RouterLink>
            <template v-if="!confirmation">
              <button
                class="console-primary"
                type="button"
                :disabled="!canAct || frozen || !validReference"
                @click="prepare"
              >
                {{ page === "deploy" ? "读取部署确认" : "读取重启确认" }}
              </button>
            </template>
            <template v-else>
              <button
                class="console-secondary"
                type="button"
                :disabled="frozen"
                @click="changed"
              >
                返回修改
              </button>
              <button
                class="console-primary"
                type="button"
                :disabled="!acknowledged || expired || frozen || !canAct"
                @click="submit"
              >
                确认{{ actionLabels[confirmation.action] }}并创建任务
              </button>
            </template>
          </div>
        </footer>
      </template>

      <template v-if="page === 'backups'">
        <ConsolePageHeader
          title="备份清单"
          description="完整备份包含数据库、图片和版本清单；离机副本由维护人员导出和验收。"
        />
        <section class="console-panel" aria-labelledby="ops-backups-title">
          <div class="console-card-head">
            <div>
              <h2 id="ops-backups-title" class="console-card-title">
                备份记录
              </h2>
              <p class="console-card-desc">
                最多显示 100 份；文件 SHA256 在执行前由服务端核验。
              </p>
            </div>
            <div class="console-card-tools">
              <RouterLink class="button console-secondary" to="/ops/manual">
                查看交接说明
              </RouterLink>
              <button
                class="console-primary"
                type="button"
                :disabled="frozen || !canAct"
                @click="
                  backupConfirm = true;
                  changed();
                "
              >
                创建完整备份
              </button>
            </div>
          </div>
          <p v-if="busy" class="console-muted" role="status">
            正在读取备份记录…
          </p>
          <div class="console-table-wrap">
            <table class="console-table">
              <thead>
                <tr>
                  <th>备份</th>
                  <th>时点</th>
                  <th>完整性清单</th>
                  <th>模式指纹</th>
                  <th>保留参考</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in backups" :key="row.id">
                  <td>{{ row.id }}</td>
                  <td>{{ dateText(row.createdAt) }}</td>
                  <td>
                    <span
                      class="console-tag"
                      :class="row.complete ? 'is-positive' : 'is-negative'"
                      >{{ row.complete ? "清单标记完整" : "不完整" }}</span
                    >
                  </td>
                  <td class="console-fingerprint">{{ row.schemaFingerprint }}</td>
                  <td>{{ backupWindow(row) }}</td>
                  <td>
                    <RouterLink
                      :to="{ path: '/ops/restore', query: { backup: row.id } }"
                      >核对并恢复</RouterLink
                    >
                  </td>
                </tr>
                <tr v-if="!busy && !backups.length">
                  <td colspan="6" class="console-table-empty">暂无备份记录。</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="console-card-foot">
            <template v-if="status?.dailyBackup">
              <template v-if="status.dailyBackup.enabled"
                >每日备份 {{ status.dailyBackup.time }}（{{
                  status.dailyBackup.timeZone
                }}），最近入队
                {{ status.dailyBackup.lastScheduledFor ? dateText(status.dailyBackup.lastScheduledFor) : "暂无" }}，执行结果见任务。</template
              >
              <template v-else>当前环境未开启每日自动备份。</template>
            </template>
            离机副本由维护人员导出和验收，步骤见运维手册。
          </p>
        </section>
        <template v-if="backupConfirm">
          <div class="console-split">
            <section class="console-panel" aria-label="备份范围">
              <h2 class="console-card-title">备份范围</h2>
              <p class="console-card-desc">固定目标：城院帮业务服务（business）</p>
              <dl class="console-facts">
                <div>
                  <dt>运行版本</dt>
                  <dd>{{ status?.release.id || "未登记运行版本" }}</dd>
                </div>
                <div>
                  <dt>备份范围</dt>
                  <dd>数据库、图片和版本清单</dd>
                </div>
                <div>
                  <dt>任务互斥</dt>
                  <dd>
                    <span
                      class="console-tag"
                      :class="active ? 'is-warning' : 'is-positive'"
                      >{{
                        active ? `已有${jobLabels[active.state]}任务` : "无进行中任务"
                      }}</span
                    >
                  </dd>
                </div>
                <div>
                  <dt>维护状态</dt>
                  <dd>
                    <span
                      class="console-tag"
                      :class="status?.recoveryRequired ? 'is-negative' : 'is-positive'"
                      >{{ status?.recoveryRequired ? "要求人工核查" : "未要求" }}</span
                    >
                  </dd>
                </div>
              </dl>
            </section>
            <section class="console-panel" aria-label="备份回执说明">
              <h2 class="console-card-title">备份回执说明</h2>
              <p class="console-card-desc">
                任务返回编号仅代表已受理，结果在任务页回读。
              </p>
              <p class="console-callout is-warning">
                业务可能短暂停止；独立运维保持可查询。完整备份包含数据库、图片和版本清单。
              </p>
              <template v-if="confirmation">
                <h3 class="console-sub-title">确认回执</h3>
                <dl class="console-facts">
                  <div>
                    <dt>动作 · 目标</dt>
                    <dd>
                      {{ actionLabels[confirmation.action] }} ·
                      {{ confirmation.target }}
                    </dd>
                  </div>
                  <div>
                    <dt>有效至</dt>
                    <dd>
                      {{ dateText(confirmation.expiresAt)
                      }}<small v-if="expired">已过期，请重新读取确认。</small>
                    </dd>
                  </div>
                </dl>
                <p class="console-form-note">{{ confirmation.impact }}</p>
                <label class="console-check">
                  <input
                    v-model="acknowledged"
                    type="checkbox"
                    :disabled="frozen || expired"
                  />我已核对目标及停机影响</label
                >
              </template>
              <p v-else class="console-form-note">
                读取确认后显示实际影响与有效期，再勾选核对并提交任务。
              </p>
            </section>
          </div>
          <footer class="console-footer">
            <div class="console-footer-actions">
              <button
                class="console-secondary"
                type="button"
                :disabled="frozen"
                @click="
                  backupConfirm = false;
                  changed();
                "
              >
                取消
              </button>
              <button
                v-if="!confirmation"
                class="console-primary"
                type="button"
                :disabled="!canAct || frozen"
                @click="prepare"
              >
                读取备份确认
              </button>
              <template v-else>
                <button
                  class="console-secondary"
                  type="button"
                  :disabled="frozen"
                  @click="changed"
                >
                  返回修改
                </button>
                <button
                  class="console-primary"
                  type="button"
                  :disabled="!acknowledged || expired || frozen || !canAct"
                  @click="submit"
                >
                  确认创建完整备份
                </button>
              </template>
            </div>
          </footer>
        </template>
      </template>

      <template v-if="page === 'restore'">
        <ConsolePageHeader
          title="恢复备份"
          description="恢复会覆盖当前业务数据库、图片和版本；先读取预检，再确认覆盖。"
        />
        <section class="console-panel" aria-labelledby="ops-restore-list-title">
          <div class="console-card-head">
            <div>
              <h2 id="ops-restore-list-title" class="console-card-title">
                选择完整备份
              </h2>
              <p class="console-card-desc">
                只有清单标记完整的备份可以恢复；服务端在执行前校验清单、模式和
                SHA256，并保存当前保护副本。
              </p>
            </div>
            <div class="console-card-tools">
              <RouterLink class="button console-secondary" to="/ops/backups">
                返回备份清单
              </RouterLink>
            </div>
          </div>
          <p v-if="busy" class="console-muted" role="status">
            正在读取备份记录…
          </p>
          <div class="console-table-wrap">
            <table class="console-table">
              <thead>
                <tr>
                  <th>备份</th>
                  <th>时点</th>
                  <th>完整性清单</th>
                  <th>模式指纹</th>
                  <th>保留参考</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in backups"
                  :key="row.id"
                  :class="{ 'is-selected': reference === row.id }"
                >
                  <td>{{ row.id }}</td>
                  <td>{{ dateText(row.createdAt) }}</td>
                  <td>
                    <span
                      class="console-tag"
                      :class="row.complete ? 'is-positive' : 'is-negative'"
                      >{{ row.complete ? "清单标记完整" : "不完整" }}</span
                    >
                  </td>
                  <td class="console-fingerprint">{{ row.schemaFingerprint }}</td>
                  <td>{{ backupWindow(row) }}</td>
                  <td>
                    <button
                      v-if="row.complete"
                      class="console-link"
                      type="button"
                      :disabled="frozen"
                      :aria-pressed="reference === row.id"
                      @click="
                        reference = row.id;
                        changed();
                      "
                    >
                      {{ reference === row.id ? "已选择" : "选择此备份" }}
                    </button>
                    <span v-else class="console-form-note">不可恢复</span>
                  </td>
                </tr>
                <tr v-if="!busy && !backups.length">
                  <td colspan="6" class="console-table-empty">暂无备份记录。</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <div class="console-split">
          <section class="console-panel" aria-label="已选择备份">
            <h2 class="console-card-title">已选择备份</h2>
            <p class="console-card-desc">
              {{ reference || "尚未选择备份" }}
            </p>
            <dl class="console-facts">
              <div>
                <dt>备份时点</dt>
                <dd>
                  {{ selectedBackup ? dateText(selectedBackup.createdAt) : "—" }}
                </dd>
              </div>
              <div>
                <dt>完整性清单</dt>
                <dd>
                  {{ selectedBackup?.complete ? "清单标记完整" : "—" }}
                </dd>
              </div>
              <div>
                <dt>模式指纹</dt>
                <dd class="console-fingerprint">
                  {{ selectedBackup?.schemaFingerprint || "—" }}
                </dd>
              </div>
              <div>
                <dt>运行版本</dt>
                <dd>{{ status?.release.id || "未登记运行版本" }}</dd>
              </div>
            </dl>
            <h3 class="console-sub-title">预检项目</h3>
            <ol class="console-checklist">
              <li
                v-for="item in [
                  '备份清单与模式指纹',
                  '文件 SHA256 核对',
                  '当前数据保护副本',
                  '业务核验基线',
                ]"
                :key="item"
              >
                <span>{{ item }}</span>
                <span
                  class="console-tag"
                  :class="confirmation ? 'is-positive' : 'is-neutral'"
                  >{{ confirmation ? "已通过" : "待检查" }}</span
                >
              </li>
            </ol>
          </section>
          <section class="console-panel" aria-label="恢复影响">
            <h2 class="console-card-title">恢复影响</h2>
            <p class="console-card-desc">
              恢复会覆盖当前业务数据库、图片和备份版本。
            </p>
            <p class="console-callout is-warning">
              覆盖不可撤销。服务端先校验清单、模式和
              SHA256，并保存当前保护副本；任务失败时保留保护副本与原任务记录。
            </p>
            <template v-if="confirmation">
              <h3 class="console-sub-title">核对快照与覆盖范围</h3>
              <dl class="console-facts">
                <div>
                  <dt>动作 · 目标</dt>
                  <dd>
                    {{ actionLabels[confirmation.action] }} · {{ confirmation.target }}
                  </dd>
                </div>
                <div>
                  <dt>备份快照</dt>
                  <dd>
                    {{
                      confirmation.restorePreview
                        ? dateText(
                            confirmation.restorePreview.backupSnapshotAt ||
                              confirmation.restorePreview.backupCreatedAt,
                          )
                        : "—"
                    }}
                  </dd>
                </div>
                <div>
                  <dt>备份版本</dt>
                  <dd>
                    {{
                      confirmation.restorePreview?.backupReleaseId || "沿用当前版本"
                    }}
                  </dd>
                </div>
                <div>
                  <dt>覆盖范围</dt>
                  <dd>
                    {{
                      confirmation.restorePreview?.affectedComponents
                        .map((item) => componentLabels[item] || item)
                        .join("、") || "—"
                    }}
                  </dd>
                </div>
                <div>
                  <dt>已完成文件核对</dt>
                  <dd>
                    {{ confirmation.restorePreview?.verifiedFileCount ?? "—" }} 个，其中媒体
                    {{ confirmation.restorePreview?.mediaFileCount ?? "—" }} 个
                  </dd>
                </div>
                <div>
                  <dt>业务核验基线</dt>
                  <dd>
                    {{
                      confirmation.restorePreview?.businessSnapshotAvailable
                        ? "已包含"
                        : "暂无，恢复后需抽查"
                    }}
                  </dd>
                </div>
                <div>
                  <dt>有效至</dt>
                  <dd>
                    {{ dateText(confirmation.expiresAt)
                    }}<small v-if="expired">已过期，请重新读取预检。</small>
                  </dd>
                </div>
              </dl>
              <p class="console-form-note">{{ confirmation.impact }}</p>
              <label class="console-check">
                <input
                  v-model="acknowledged"
                  type="checkbox"
                  :disabled="frozen || expired"
                />我已核对目标、备份及覆盖影响</label
              >
            </template>
            <p v-else class="console-form-note">
              选择备份后读取恢复预检，服务端返回快照、覆盖范围与文件核对结果。
            </p>
          </section>
        </div>
        <footer class="console-footer">
          <div class="console-footer-actions">
            <template v-if="!confirmation">
              <button
                class="console-primary"
                type="button"
                :disabled="!canAct || frozen || !validReference"
                @click="prepare"
              >
                读取恢复预检
              </button>
            </template>
            <template v-else>
              <button
                class="console-secondary"
                type="button"
                :disabled="frozen"
                @click="changed"
              >
                返回修改
              </button>
              <button
                class="console-danger"
                type="button"
                :disabled="!acknowledged || expired || frozen || !canAct"
                @click="submit"
              >
                确认覆盖并恢复
              </button>
            </template>
          </div>
        </footer>
      </template>

      <template v-if="page === 'task'">
        <p v-if="loading && !job" class="console-muted" role="status">
          正在读取任务 {{ route.params.id }}…
        </p>
        <template v-if="job">
          <ConsolePageHeader
            :title="`${actionLabels[job.action]}任务 · ${jobLabels[job.state]}`"
            :meta="`任务 ${job.id} · ${job.target}${job.referenceId ? ` · 引用 ${job.referenceId}` : ''}`"
            :back="{ label: '返回任务记录', to: '/ops/tasks' }"
          >
            <template #aside>
              <span class="console-tag" :class="jobTone(job.state)">{{
                jobLabels[job.state]
              }}</span>
            </template>
          </ConsolePageHeader>
          <p
            class="console-banner"
            :class="stateBanner(job.state).tone"
            role="status"
          >
            {{ stateBanner(job.state).text }}
          </p>
          <div class="console-split">
            <section class="console-panel" aria-label="执行阶段">
              <h2 class="console-card-title">执行阶段</h2>
              <p class="console-card-desc">
                提交 {{ dateText(job.createdAt) }} · 开始
                {{ job.startedAt ? dateText(job.startedAt) : "尚未开始" }} · 结束
                {{ job.finishedAt ? dateText(job.finishedAt) : "尚未结束" }}
              </p>
              <ol v-if="job.events.length" class="console-timeline">
                <li
                  v-for="(event, index) in job.events"
                  :key="index"
                  :class="eventTone(index)"
                >
                  <span class="console-timeline-time">{{
                    shortDateText(event.occurredAt)
                  }}</span>
                  <div class="console-timeline-body">
                    <h3>{{ phases[event.phase] || event.phase }}</h3>
                    <p
                      v-if="
                        index === job.events.length - 1 &&
                        job.state === 'RUNNING'
                      "
                    >
                      执行中，每 2 秒查询原任务。
                    </p>
                    <p
                      v-else-if="
                        index === job.events.length - 1 && job.errorCode
                      "
                    >
                      错误码：{{ job.errorCode }}
                    </p>
                  </div>
                </li>
              </ol>
              <p v-else class="console-form-note">任务已登记，尚无执行阶段。</p>
              <p
                v-if="job.state === 'INTERRUPTED'"
                class="console-callout is-warning"
              >
                执行器中断，禁止自动重做。请按手册完成本机核查。
              </p>
              <p
                v-if="['QUEUED', 'RUNNING'].includes(job.state)"
                class="console-form-note"
              >
                每 2 秒查询原任务，业务停止不会改变任务编号。切到后台时暂停轮询。
              </p>
            </section>
            <section class="console-panel" aria-label="任务回执">
              <h2 class="console-card-title">
                {{ job.state === "SUCCEEDED" ? "任务回执" : "任务信息" }}
              </h2>
              <p class="console-card-desc">
                以下字段由服务端任务记录回读，返回编号仅代表已受理。
              </p>
              <dl class="console-facts">
                <div>
                  <dt>任务编号</dt>
                  <dd><CopyValue :value="job.id" label="任务编号" /></dd>
                </div>
                <div>
                  <dt>动作 · 目标</dt>
                  <dd>{{ actionLabels[job.action] }} · {{ job.target }}</dd>
                </div>
                <div>
                  <dt>引用</dt>
                  <dd>{{ job.referenceId || "当前受控版本" }}</dd>
                </div>
                <div>
                  <dt>保护备份</dt>
                  <dd>{{ protectionBackup || "无" }}</dd>
                </div>
                <div v-if="job.result?.healthy !== undefined">
                  <dt>业务健康检查</dt>
                  <dd>
                    <span
                      class="console-tag"
                      :class="job.result.healthy ? 'is-positive' : 'is-negative'"
                      >{{ job.result.healthy ? "通过" : "未通过" }}</span
                    >
                  </dd>
                </div>
                <div v-if="job.result?.backupId">
                  <dt>完整备份</dt>
                  <dd>{{ job.result.backupId }}</dd>
                </div>
                <div v-if="backupReceipt">
                  <dt>备份保留状态</dt>
                  <dd>{{ dateText(backupReceipt.createdAt) }} · {{ backupWindow(backupReceipt) }}</dd>
                </div>
                <div v-if="backupReadError">
                  <dt>备份详情</dt>
                  <dd>{{ backupReadError }}</dd>
                </div>
                <div v-if="lastTrace">
                  <dt>关联追踪号</dt>
                  <dd><CopyValue :value="lastTrace" label="追踪号" /></dd>
                </div>
                <div v-if="job.result?.verifiedMediaReferences !== undefined">
                  <dt>已核对媒体引用</dt>
                  <dd>{{ job.result.verifiedMediaReferences }} 条</dd>
                </div>
                <div v-if="verification">
                  <dt>业务数据核对</dt>
                  <dd>
                    {{ verification.status === "VERIFIED" ? "通过" : "暂无基线" }} ·
                    {{ verification.checkedTables.length }} 张表
                  </dd>
                </div>
                <div v-if="recoveryEvidence">
                  <dt>快照距恢复开始</dt>
                  <dd>
                    {{
                      recoveryEvidence.snapshotAgeMillis === null
                        ? "暂无数据"
                        : `${Math.round(recoveryEvidence.snapshotAgeMillis / 1000)} 秒`
                    }}
                  </dd>
                </div>
                <div v-if="recoveryEvidence">
                  <dt>恢复到服务可用</dt>
                  <dd>
                    {{
                      recoveryEvidence.restoreElapsedMillis === null
                        ? "暂无数据"
                        : `${Math.round(recoveryEvidence.restoreElapsedMillis / 1000)} 秒`
                    }}
                  </dd>
                </div>
                <div v-if="job.result?.referenceId">
                  <dt>回执引用</dt>
                  <dd>{{ job.result.referenceId }}</dd>
                </div>
              </dl>
            </section>
          </div>
          <footer class="console-footer">
            <div class="console-footer-actions">
              <RouterLink class="button console-secondary" to="/ops/overview">
                返回运行总览
              </RouterLink>
              <RouterLink
                v-if="['FAILED', 'INTERRUPTED'].includes(job.state)"
                class="button console-secondary"
                to="/ops/logs"
              >
                查看受控日志
              </RouterLink>
              <RouterLink
                v-if="job.state === 'INTERRUPTED'"
                class="button console-secondary"
                to="/ops/manual"
              >
                查看维护手册
              </RouterLink>
              <button
                class="console-primary"
                type="button"
                :disabled="busy"
                @click="load"
              >
                再次读取任务状态
              </button>
            </div>
          </footer>
        </template>
      </template>

      <template v-if="page === 'manual'">
        <ConsolePageHeader
          title="回退与离机交接"
          description="区分回退与恢复，按清单核对离机副本后再处理。"
        />
        <div class="console-split">
          <section class="console-panel" aria-labelledby="ops-manual-title">
            <h2 id="ops-manual-title" class="console-card-title">
              回退与恢复的区别
            </h2>
            <p class="console-card-desc">先选择正确的恢复目标，再进入对应流程。</p>
            <dl class="console-facts">
              <div>
                <dt>回到同模式旧代码</dt>
                <dd>
                  选择 CLI 已登记发布包<small>核对数据库模式指纹和运行版本</small>
                </dd>
              </div>
              <div>
                <dt>数据回到备份时点</dt>
                <dd>
                  备份校验与数据库、图片、版本恢复<small>覆盖影响、保护副本及任务健康检查</small>
                </dd>
              </div>
            </dl>
            <p class="console-callout">
              每日调度、离机备份、跨主机部署及 RPO/RTO
              计时尚未完成，不能按“健康”状态推定它们已验收。
            </p>
          </section>
          <section class="console-panel" aria-labelledby="ops-manual-list-title">
            <h2 id="ops-manual-list-title" class="console-card-title">
              离机副本检查清单
            </h2>
            <p class="console-card-desc">
              按顺序核对；未完成的项不要标记为已验收。
            </p>
            <ol class="console-checklist">
              <li v-for="(item, index) in manualItems" :key="index">
                <span>{{ item }}</span>
                <span class="console-tag is-neutral">人工核对</span>
              </li>
            </ol>
          </section>
        </div>
        <footer class="console-footer">
          <div class="console-footer-actions">
            <RouterLink class="button console-secondary" to="/ops/backups">
              查看备份清单
            </RouterLink>
            <RouterLink class="button console-secondary" to="/ops/deploy">
              查看已登记发布包
            </RouterLink>
          </div>
        </footer>
      </template>
    </template>
  </div>
</template>
