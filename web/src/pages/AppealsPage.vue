<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
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
import MemberGate from "../components/MemberGate.vue";
const route = useRoute(),
  router = useRouter();
const restrictions = ref<Restriction[]>([]),
  appeals = ref<Page<Appeal>>(),
  selected = ref<number>(),
  reason = ref(""),
  pending = ref<{ restrictionId: number; reason: string; requestId: string }>(),
  error = ref(""),
  notice = ref(""),
  busy = ref(false);
let generation = 0;
const labels: Record<string, string> = {
  OPEN: "等待复核",
  ACCEPTED: "申诉已接受",
  REJECTED: "申诉已驳回",
};
async function load() {
  if (session.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  restrictions.value = [];
  appeals.value = undefined;
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
  if (busy.value) return;
  error.value = "";
  notice.value = "";
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
    busy.value = true;
    await send("/me/appeals", "POST", pending.value);
    removePrivate("appeal");
    pending.value = undefined;
    reason.value = "";
    notice.value = "申诉已提交，复核状态以服务端记录为准。";
    busy.value = false;
    await load();
  } catch (e) {
    error.value = `${message(e)}${pending.value ? " 原申诉和请求键已保留，可按原请求核对。" : ""}`;
  } finally {
    busy.value = false;
  }
}
async function reconcile() {
  await load();
  if (error.value) return;
  if (
    confirm(
      "请核对申诉记录及最新限制。确认结束本机待确认请求？这不会撤销服务端申诉。",
    )
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
    ><div class="page-heading">
      <div>
        <h1>我的限制与申诉</h1>
        <p>仅显示本人行为限制及复核结果。</p>
      </div>
      <RouterLink to="/me">返回个人中心</RouterLink>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <p v-if="busy" role="status">正在读取或提交…</p>
    <section v-if="pending" class="panel">
      <h2>上次申诉结果待确认</h2>
      <p>原理由与请求键已保存，刷新不会创建另一条申诉。</p>
      <button :disabled="busy" @click="submit">按原请求核对申诉</button
      ><button :disabled="busy" @click="reconcile">读取记录并核对后结束</button>
    </section>
    <div class="profile-layout">
      <section class="panel">
        <h2>当前行为限制</h2>
        <p v-if="!busy && !restrictions.length">暂无行为限制记录。</p>
        <div v-for="row in restrictions" :key="row.id" class="restriction-row">
          <h3>
            {{
              row.scope === "PUBLISH" ? "发布及作者恢复上架" : "新评论及回复"
            }}
            · {{ row.enabled ? "当前受限" : "已解除" }}
          </h3>
          <p class="console-preserve">{{ row.reason }}</p>
          <p class="hint">
            限制 {{ row.id }} · 版本 {{ row.revision }} ·
            {{ dateText(row.updatedAt) }}
          </p>
        </div>
        <form @submit.prevent="submit">
          <fieldset :disabled="busy || !!pending">
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
            ><button
              class="primary"
              :disabled="!restrictions.some((r) => r.enabled)"
            >
              提交本人申诉
            </button>
          </fieldset>
        </form>
        <p class="hint">
          同一限制版本仅允许一条待复核申诉。解除限制不自动恢复被隐藏的文章或评论。
        </p>
      </section>
      <section class="panel">
        <h2>申诉记录</h2>
        <p v-if="appeals && !appeals.items.length">暂无申诉记录。</p>
        <div
          v-for="row in appeals?.items"
          :key="row.id"
          class="restriction-row"
        >
          <h3>申诉 {{ row.id }} · {{ labels[row.state] || row.state }}</h3>
          <p>
            对应限制 {{ row.restrictionId }} 版本 {{ row.restrictionRevision }}
          </p>
          <p class="console-preserve">{{ row.reason }}</p>
          <p v-if="row.reply" class="console-preserve">
            复核答复：{{ row.reply }}
          </p>
          <p class="hint">{{ dateText(row.updatedAt) }}</p>
        </div>
        <div v-if="appeals" class="pager">
          <button
            :disabled="busy || appeals.page <= 1"
            @click="router.push({ query: { page: String(appeals.page - 1) } })"
          >
            上一页</button
          ><span>第 {{ appeals.page }} 页 · 共 {{ appeals.total }} 条</span
          ><button
            :disabled="busy || appeals.page * appeals.size >= appeals.total"
            @click="router.push({ query: { page: String(appeals.page + 1) } })"
          >
            下一页
          </button>
        </div>
        <button :disabled="busy" @click="load">重新读取本人状态</button>
      </section>
    </div></MemberGate
  >
</template>
