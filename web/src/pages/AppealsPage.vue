<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { get, send, message } from "../lib/api";
import {
  session,
  readPrivate,
  writePrivate,
  removePrivate,
} from "../lib/session";
import { dateText } from "../lib/content";
import type { Appeal, Restriction, Page } from "../management/governance";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import OperationFeedback from "../components/OperationFeedback.vue";
import { operationFailure, type Feedback } from "../lib/feedback";
import MemberGate from "../components/MemberGate.vue";
const route = useRoute(),
  router = useRouter();
const confirmation = ref<InstanceType<typeof ConfirmDialog>>();
const submitting = ref(false);
const feedback = ref<Feedback>();
const restrictions = ref<Restriction[]>([]),
  appeals = ref<Page<Appeal>>(),
  selected = ref<number>(),
  reason = ref(""),
  pending = ref<{ restrictionId: number; reason: string; requestId: string }>(),
  error = ref(""),
  busy = ref(false);
let generation = 0;
const labels: Record<string, string> = {
  OPEN: "等待复核",
  ACCEPTED: "申诉已接受",
  REJECTED: "申诉已驳回",
};
const openCount = computed(
  () => appeals.value?.items.filter((row) => row.state === "OPEN").length ?? 0,
);
async function load() {
  if (session.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  try {
    const [r, a] = await Promise.all([
      get<{ items: Restriction[] }>("/me/restrictions", true),
      get<Page<Appeal>>(
        `/me/appeals?page=${Math.max(1, Number(route.query.page) || 1)}&size=10`,
        true,
      ),
    ]);
    if (current !== generation) return;
    restrictions.value = r.items;
    appeals.value = a;
    pending.value =
      readPrivate<NonNullable<typeof pending.value>>("appeal") || undefined;
    if (pending.value) {
      selected.value = pending.value.restrictionId;
      reason.value = pending.value.reason;
    }
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function submit() {
  if (busy.value || submitting.value) return;
  feedback.value = undefined;
  submitting.value = true;
  try {
    if (!pending.value) {
      if (!selected.value || !reason.value.trim())
        throw new Error("请选择当前限制并填写申诉理由。");
      const v = {
        restrictionId: selected.value,
        reason: reason.value,
        requestId: crypto.randomUUID(),
      };
      writePrivate("appeal", v);
      pending.value = v;
    }
    await send("/me/appeals", "POST", pending.value);
    removePrivate("appeal");
    pending.value = undefined;
    reason.value = "";
    feedback.value = { kind: "success", text: "申诉已提交，当前等待复核。" };
    await load();
  } catch (e) {
    feedback.value = operationFailure(
      e,
      pending.value ? "申诉结果待确认。原理由和请求键已保存。" : undefined,
    );
  } finally {
    submitting.value = false;
  }
}
async function reconcile() {
  await load();
  if (error.value) return;
  if (
    await confirmation.value?.ask({
      title: "结束待确认申诉？",
      detail: "请先核对申诉记录。结束本机记录后，服务端已收到的申诉仍然有效。",
      action: "结束本机记录",
    })
  ) {
    removePrivate("appeal");
    pending.value = undefined;
  }
}
onMounted(load);
watch(() => [session.status, route.query], load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <MemberGate title="登录后查看本人限制与申诉"
    ><ConfirmDialog ref="confirmation" />
    <div class="page-heading service-page-heading">
      <div>
        <h1>我的限制与申诉</h1>
        <p>仅显示本人行为限制及复核结果。</p>
      </div>
      <button :disabled="busy" @click="load">重新读取本人状态</button>
    </div>
    <div class="service-page service-stack">
      <p v-if="error" class="error" role="alert">
        {{ error }} <span v-if="appeals">当前显示的是上次读取结果。</span>
      </p>
      <p v-if="busy" class="service-inline-note" role="status">
        正在读取限制与申诉…
      </p>
      <section v-if="pending" class="panel service-card">
        <h2>上次申诉结果待确认</h2>
        <p>原理由与请求键已保存，刷新不会创建另一条申诉。</p>
        <div class="card-actions">
          <button :disabled="busy || submitting" @click="submit">
            按原请求核对申诉</button
          ><button :disabled="busy" @click="reconcile">
            读取记录并核对后结束
          </button>
        </div>
      </section>
      <section class="panel service-card" aria-label="概览">
        <dl class="kv">
          <div>
            <dt>当前行为限制</dt>
            <dd>
              <span v-if="!busy && appeals && !restrictions.length">无</span>
              <span v-else-if="restrictions.length"
                >{{ restrictions.length }} 项</span
              >
              <small
                v-for="row in restrictions"
                :key="row.id"
                >{{
                  row.scope === "PUBLISH" ? "发布及作者恢复上架" : "新评论及回复"
                }}
                · {{ row.enabled ? "当前受限" : "已解除" }} · {{ row.reason }}</small
              >
            </dd>
          </div>
          <div>
            <dt>待复核申诉</dt>
            <dd>
              <span v-if="!appeals">正在读取…</span>
              <span v-else-if="openCount">{{ openCount }} 条</span>
              <span v-else>无</span>
            </dd>
          </div>
          <div>
            <dt>本人反馈记录</dt>
            <dd><RouterLink to="/me/cases">查看本人记录</RouterLink></dd>
          </div>
        </dl>
        <div class="service-foot">
          <RouterLink class="button" :to="{ path: '/me/cases/new' }"
            >提交反馈</RouterLink
          >
        </div>
      </section>
      <section id="restrictions" class="panel service-card" aria-label="提交申诉">
        <h2>对当前限制提交申诉</h2>
        <p v-if="!busy && appeals && !restrictions.length" class="service-empty">
          暂无行为限制记录，无需申诉。
        </p>
        <form v-else @submit.prevent="submit">
          <fieldset :disabled="busy || submitting || !!pending">
            <label
              >申诉对应限制<select
                v-model="selected"
                aria-label="申诉对应限制"
                required
              >
                <option :value="undefined" disabled>请选择当前限制</option>
                <option
                  v-for="row in restrictions.filter((r) => r.enabled)"
                  :key="row.id"
                  :value="row.id"
                >
                  {{ row.scope === "PUBLISH" ? "发布" : "评论" }} · 限制
                  {{ row.id }} 版本 {{ row.revision }}
                </option>
              </select></label
            ><label
              >申诉理由<textarea
                v-model="reason"
                rows="4"
                required
                maxlength="2000"
                placeholder="说明需复核的事实和依据。"
              ></textarea></label
            >
            <div class="card-actions">
              <button
                class="primary"
                :disabled="!restrictions.some((r) => r.enabled)"
              >
                提交本人申诉
              </button>
            </div>
          </fieldset>
          <OperationFeedback
            :feedback="feedback"
            :processing="submitting ? '正在提交申诉…' : undefined"
          />
          <p class="hint">
            同一限制版本仅允许一条待复核申诉。解除限制不自动恢复被隐藏的文章或评论。
          </p>
        </form>
      </section>
      <section id="appeal-records" class="panel service-card" aria-label="申诉记录">
        <h2>申诉记录</h2>
        <p v-if="appeals && !appeals.items.length" class="service-empty">
          暂无申诉记录。
        </p>
        <ul v-else class="service-list">
          <li v-for="row in appeals?.items" :key="row.id" class="service-list-block">
            <div class="service-list-copy">
              <strong
                >申诉 {{ row.id }} · {{ labels[row.state] || row.state }}</strong
              >
              <span
                >对应限制 {{ row.restrictionId }} 版本
                {{ row.restrictionRevision }}</span
              >
              <ol class="record-timeline">
                <li>
                  <time v-if="row.createdAt" class="meta" :datetime="row.createdAt"
                    >{{ dateText(row.createdAt) }}</time
                  >
                  <strong>提交申诉</strong>
                  <p class="long-content">{{ row.reason }}</p>
                </li>
                <li>
                  <time class="meta" :datetime="row.updatedAt">{{
                    dateText(row.updatedAt)
                  }}</time>
                  <strong>{{ labels[row.state] || row.state }}</strong>
                  <p v-if="row.reply" class="long-content">{{ row.reply }}</p>
                </li>
              </ol>
            </div>
          </li>
        </ul>
        <div v-if="appeals" class="service-foot">
          <nav
            v-if="appeals.total > appeals.size"
            class="pagination"
            aria-label="申诉记录翻页"
          >
            <button
              aria-label="上一页"
              :disabled="busy || appeals.page <= 1"
              @click="router.push({ query: { page: String(appeals.page - 1) } })"
            >
              ‹</button
            ><span aria-current="page">{{ appeals.page }}</span
            ><button
              aria-label="下一页"
              :disabled="busy || appeals.page * appeals.size >= appeals.total"
              @click="router.push({ query: { page: String(appeals.page + 1) } })"
            >
              ›
            </button>
          </nav>
          <span>共 {{ appeals.total }} 条</span>
        </div>
      </section>
    </div></MemberGate
  >
</template>
