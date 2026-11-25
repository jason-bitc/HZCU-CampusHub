<script setup lang="ts">
import ConsoleConfirm from "./ConsoleConfirm.vue";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { message } from "../lib/api";
import { dateText } from "../lib/content";
import { adminRequest, adminSession } from "./client";
import { useMutation } from "./governance";
import type { Page } from "./governance";
import ConsolePager from "./ConsolePager.vue";
import MutationNotice from "./MutationNotice.vue";

interface Contribution {
  eventId: string;
  amount: number;
  occurredAt: string;
  effective: boolean;
  invalidation?: {
    caseId: number;
    adminId: number;
    reason: string;
    occurredAt: string;
  };
}
interface AuditEntry {
  source: string;
  sequence: number;
  action: string;
  actorId: number;
  occurredAt: string;
  reason?: string;
  contributionEventId?: string;
}
const props = defineProps<{
  caseId: number;
  revision: number;
  canHandle: boolean;
}>();
const base = `/admin/cases/${props.caseId}`;
const records = ref<Page<Contribution>>();
const audit = ref<Page<AuditEntry>>();
const loading = ref(false);
const loadError = ref("");
const selected = ref("");
const reason = ref("");
let generation = 0;
async function load(contributionPage = 1, auditPage = 1) {
  const current = ++generation;
  loading.value = true;
  loadError.value = "";
  try {
    const [contributions, history] = await Promise.all([
      adminRequest<Page<Contribution>>(
        `${base}/contributions?page=${contributionPage}&size=20`,
      ),
      adminRequest<Page<AuditEntry>>(`${base}/audit?page=${auditPage}&size=20`),
    ]);
    if (current !== generation) return;
    records.value = contributions;
    audit.value = history;
  } catch (e) {
    if (current === generation) loadError.value = message(e);
  } finally {
    if (current === generation) loading.value = false;
  }
}
const mutation = useMutation(
  `case-contributions:${props.caseId}`,
  "CONTRIBUTION_INVALIDATE",
  async () => {
    await load(records.value?.page, audit.value?.page);
  },
);
const { pending, busy, error, notice, allowed } = mutation;
const canSelect = computed(
  () =>
    props.canHandle &&
    allowed.value &&
    !pending.value &&
    !loading.value &&
    !busy.value,
);
function select(eventId: string) {
  selected.value = eventId;
  reason.value = "";
}
const selectedRecord = computed(() =>
  records.value?.items.find((item) => item.eventId === selected.value),
);
async function invalidate() {
  if (!canSelect.value || !selected.value || !reason.value.trim()) return;
  if (
    !(await modal.value?.ask({
      title: "确认标记这笔投币失效？",
      detail: `案件 ${props.caseId} · 原投币事件 ${selected.value}。有效贡献和作者荣誉将重新计算，读者原流水和余额保留，并记录案件审计。\n\n原因：${reason.value.trim()}`,
      action: "标记投币失效",
      danger: true,
    }))
  )
    return;
  await mutation.start(
    `${base}/contributions/${selected.value}/invalidation`,
    "POST",
    {
      expectedRevision: props.revision,
      reason: reason.value.trim(),
    },
    "投币失效",
  );
  if (!pending.value && notice.value) select("");
}
watch(
  () => [props.revision, adminSession.epoch],
  () => {
    mutation.restore();
    void load();
  },
  { immediate: true },
);
watch(pending, (value, previous) => {
  if (value) {
    selected.value = value.path.split("/").at(-2) || "";
    reason.value = String(value.body.reason ?? "");
  } else if (previous && !error.value && notice.value.startsWith("投币失效")) {
    select("");
  }
});
onBeforeUnmount(() => generation++);
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
</script>
<template>
  <section
    id="case-contributions"
    class="console-panel"
    aria-labelledby="case-contributions-title"
  >
    <ConsoleConfirm ref="modal" />
    <div class="console-card-head">
      <div>
        <h2 id="case-contributions-title" class="console-card-title">
          关联投币
        </h2>
        <p class="console-card-desc">
          投币失效会重新计算文章有效币数、排序与作者荣誉。读者原流水和余额保留。
        </p>
      </div>
      <div class="console-card-tools">
        <button type="button" :disabled="loading || busy" @click="load()">
          刷新投币与审计
        </button>
      </div>
    </div>
    <p v-if="!canHandle" class="console-footnote" style="margin-top: 12px">
      请由处于处理阶段的案件认领人提交失效。
    </p>
    <p v-if="loading" class="console-muted" role="status" style="margin-top: 12px">
      正在读取投币与案件审计…
    </p>
    <p v-if="loadError" class="console-banner is-error" role="alert" style="margin-top: 12px">
      {{ loadError }}
    </p>
    <MutationNotice
      class="console-stack"
      :pending="pending"
      :busy="busy || loading"
      :error="error"
      :notice="notice"
      @retry="mutation.submit"
      @reconcile="mutation.reconcile"
    />
    <div v-if="records?.items.length" class="console-table-wrap">
      <table class="console-table">
        <thead>
          <tr>
            <th>投币流水</th>
            <th>数量</th>
            <th>时间</th>
            <th>处置结果</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records.items" :key="record.eventId">
            <td class="num">{{ record.eventId }}</td>
            <td class="num">{{ record.amount }} 枚硬币</td>
            <td>{{ dateText(record.occurredAt) }}</td>
            <td>
              <span
                class="console-tag"
                :class="record.effective ? 'is-positive' : 'is-negative'"
                >{{ record.effective ? "有效" : "已失效" }}</span
              >
              <template v-if="record.invalidation">
                <small
                  >案件 {{ record.invalidation.caseId }} · 管理员
                  {{ record.invalidation.adminId }} ·
                  {{ dateText(record.invalidation.occurredAt) }}</small
                >
                <small class="console-preserve">{{
                  record.invalidation.reason
                }}</small>
              </template>
            </td>
            <td>
              <button
                v-if="record.effective && canHandle && allowed"
                class="console-link"
                type="button"
                :disabled="!canSelect"
                :aria-pressed="selected === record.eventId"
                @click="select(record.eventId)"
              >
                {{ selected === record.eventId ? "正在核对" : "标记失效" }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else-if="records && !loading" class="console-muted" style="margin-top: 12px">
      这篇文章暂无投币记录。
    </p>
    <ConsolePager
      v-if="records"
      :page="records.page"
      :size="records.size"
      :total="records.total"
      :busy="loading || busy"
      @turn="load($event, audit?.page)"
    />
  </section>
  <div
    v-if="selected && !pending && canHandle && allowed"
    class="console-split"
    style="margin-top: 16px"
  >
    <section class="console-panel" aria-label="投币记录">
      <h2 class="console-card-title">投币记录</h2>
      <p class="console-card-desc">
        投币 {{ selected }} ·
        {{ selectedRecord?.effective ? "当前有效" : "已失效" }}
      </p>
      <dl class="console-facts">
        <div>
          <dt>记录编号</dt>
          <dd>{{ selected }}</dd>
        </div>
        <div>
          <dt>数量</dt>
          <dd>{{ selectedRecord?.amount ?? "—" }} 枚硬币</dd>
        </div>
        <div>
          <dt>案件修订</dt>
          <dd>{{ revision }}</dd>
        </div>
        <div>
          <dt>影响范围</dt>
          <dd>有效贡献、排序、作者荣誉</dd>
        </div>
      </dl>
      <p class="console-callout">
        失效仅影响有效贡献及后续计算。读者的原流水和余额保留。结果与原因写入案件审计。
      </p>
    </section>
    <form class="console-panel" @submit.prevent="invalidate">
      <h2 class="console-card-title">确认投币失效</h2>
      <p class="console-card-desc">请核对记录和本人处置权限。</p>
      <div class="console-form">
        <label class="console-field"
          ><span>记录状态</span
          ><input value="有效 → 失效" readonly /></label
        ><label class="console-field"
          ><span>必填原因</span
          ><textarea
            v-model="reason"
            required
            maxlength="2000"
            rows="3"
            placeholder="核对案件证据后说明该笔贡献为何无效。"
            :disabled="busy"
        /></label>
      </div>
      <div class="console-form-actions">
        <button type="button" :disabled="busy" @click="select('')">取消</button>
        <button
          type="submit"
          class="console-danger"
          :disabled="!canSelect || !reason.trim()"
        >
          确认失效
        </button>
      </div>
    </form>
  </div>
  <section class="console-panel" aria-labelledby="case-audit-title">
    <h2 id="case-audit-title" class="console-card-title">案件审计</h2>
    <p class="console-card-desc">最近发生在前。审计编号和关联对象可用于核对。</p>
    <div v-if="audit?.items.length" class="console-table-wrap">
      <table class="console-table">
        <thead>
          <tr>
            <th>审计编号</th>
            <th>动作</th>
            <th>对象</th>
            <th>操作者</th>
            <th>发生时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in audit.items"
            :key="`${entry.source}:${entry.sequence}`"
          >
            <td class="num">{{ entry.source }}-{{ entry.sequence }}</td>
            <td>
              {{ entry.source === "CONTRIBUTION" ? "投币失效" : entry.action }}
            </td>
            <td>
              <template v-if="entry.contributionEventId"
                >流水 {{ entry.contributionEventId }}</template
              >
              <small v-if="entry.reason" class="console-preserve">{{
                entry.reason
              }}</small>
            </td>
            <td>管理员 {{ entry.actorId }}</td>
            <td>{{ dateText(entry.occurredAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else-if="audit && !loading" class="console-muted" style="margin-top: 12px">
      暂无案件审计。
    </p>
    <ConsolePager
      v-if="audit"
      :page="audit.page"
      :size="audit.size"
      :total="audit.total"
      :busy="loading || busy"
      @turn="load(records?.page, $event)"
    />
  </section>
</template>
