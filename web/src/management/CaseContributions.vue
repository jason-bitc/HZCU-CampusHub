<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { dateText } from "../lib/content";
import { adminRequest, adminSession } from "./client";
import { useMutation } from "./governance";
import type { Page } from "./governance";
import ConsolePager from "./ConsolePager.vue";
import ConsoleConfirm from "./ConsoleConfirm.vue";
import { consoleError } from "./console-ux";
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
const emit = defineEmits<{ changed: [] }>();
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
    if (current === generation) loadError.value = consoleError(e);
  } finally {
    if (current === generation) loading.value = false;
  }
}
const mutation = useMutation(
  `case-contributions:${props.caseId}`,
  "CONTRIBUTION_INVALIDATE",
  async () => {
    await load(records.value?.page, audit.value?.page);
    emit("changed");
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
    class="console-panel console-history"
    aria-labelledby="case-contributions-title"
  >
    <ConsoleConfirm ref="modal" />
    <h2 id="case-contributions-title">关联投币与案件审计</h2>
    <p class="console-muted">
      投币失效会重新计算文章有效币数、排序与作者荣誉。读者原流水和余额保留。
    </p>
    <p v-if="!canHandle" class="hint">请由处于处理阶段的案件认领人提交失效。</p>
    <button
      :disabled="loading || busy"
      @click="
        load(records?.page, audit?.page);
        emit('changed');
      "
    >
      刷新投币与审计
    </button>
    <p v-if="loading" role="status">正在读取投币与案件审计…</p>
    <p v-if="loadError" class="error" role="alert">
      {{ loadError }}
      <span v-if="records || audit">当前显示的是上次读取结果。</span>
    </p>
    <MutationNotice
      :pending="pending"
      :busy="busy || loading"
      :error="error"
      :notice="notice"
      @retry="mutation.submit"
      @reconcile="mutation.reconcile"
    />
    <div v-if="records?.items.length" class="console-table-wrap">
      <table class="console-table console-responsive-table" role="table">
        <thead role="rowgroup">
          <tr role="row">
            <th scope="col" role="columnheader">投币流水</th>
            <th scope="col" role="columnheader">币数与时间</th>
            <th scope="col" role="columnheader">处置结果</th>
            <th scope="col" role="columnheader">操作</th>
          </tr>
        </thead>
        <tbody role="rowgroup">
          <tr role="row" v-for="record in records.items" :key="record.eventId">
            <td data-label="投币流水" role="cell">{{ record.eventId }}</td>
            <td data-label="币数与时间" role="cell">
              {{ record.amount }} 币<small>{{
                dateText(record.occurredAt)
              }}</small>
            </td>
            <td data-label="处置结果" role="cell">
              <span>{{ record.effective ? "有效" : "已失效" }}</span>
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
            <td data-label="操作" role="cell">
              <button
                v-if="record.effective && canHandle && allowed"
                :disabled="!canSelect"
                @click="select(record.eventId)"
              >
                标记失效
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-else-if="records && !loading">这篇文章暂无投币记录。</p>
    <ConsolePager
      v-if="records"
      :page="records.page"
      :size="records.size"
      :total="records.total"
      :busy="loading || busy"
      @turn="load($event, audit?.page)"
    />
    <form
      v-if="selected && !pending && canHandle && allowed"
      class="console-note"
      @submit.prevent="invalidate"
    >
      <h3>标记投币失效</h3>
      <p>流水 {{ selected }} · 案件修订 {{ revision }}</p>
      <label
        >失效原因<textarea
          v-model="reason"
          required
          maxlength="2000"
          rows="3"
          :disabled="busy"
        />
      </label>
      <div class="card-actions">
        <button
          type="submit"
          class="console-danger"
          :disabled="!canSelect || !reason.trim()"
        >
          确认失效
        </button>
        <button type="button" :disabled="busy" @click="select('')">取消</button>
      </div>
    </form>
    <h3>案件审计</h3>
    <ol v-if="audit?.items.length">
      <li
        v-for="entry in audit.items"
        :key="`${entry.source}:${entry.sequence}`"
      >
        <strong>{{
          entry.source === "CONTRIBUTION" ? "投币失效" : entry.action
        }}</strong>
        <time>{{ dateText(entry.occurredAt) }}</time>
        <p>
          操作人 {{ entry.actorId
          }}<span v-if="entry.contributionEventId">
            · 流水 {{ entry.contributionEventId }}</span
          >
        </p>
        <p v-if="entry.reason" class="console-preserve">{{ entry.reason }}</p>
      </li>
    </ol>
    <p v-else-if="audit && !loading">暂无案件审计。</p>
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
