<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { get, message, send } from "../lib/api";
import { readPrivate, removePrivate, writePrivate } from "../lib/session";
import ConfirmDialog from "./ConfirmDialog.vue";
import type { Wallet } from "../types";

const props = defineProps<{ articleId: number; articleTitle: string }>();
const confirmation = ref<InstanceType<typeof ConfirmDialog>>();
let asking = false;
const emit = defineEmits<{ confirmed: [] }>();
interface CoinState {
  spent: number;
  remaining: number;
  eligible: boolean;
  reason: string;
}
interface CoinRequest {
  amount: number;
  requestId: string;
}
const state = ref<CoinState>();
const wallet = ref<Wallet>();
const pending = ref<CoinRequest>();
const busy = ref(false);
const error = ref("");
const notice = ref("");
const reasonText: Record<string, string> = {
  SELF_ARTICLE: "不能给自己的文章投币。",
  NO_REAL_AUTHOR: "这篇文章没有关联作者，暂不可投币。",
  ARTICLE_COIN_LIMIT: "已达到这篇文章的累计投币额度。",
};
const max = computed(() =>
  Math.min(state.value?.remaining ?? 0, wallet.value?.balance ?? 0),
);
const key = `coin:${props.articleId}`;
let mounted = true;
async function readState() {
  const [c, w] = await Promise.all([
    get<CoinState>(`/articles/${props.articleId}/coin-state`, true),
    get<Wallet>("/me/coins", true),
  ]);
  if (!mounted) return;
  state.value = c;
  wallet.value = w;
}
async function load() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    pending.value = readPrivate<CoinRequest>(key) ?? undefined;
    await readState();
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function submit(amount?: number) {
  if (busy.value || asking) return;
  if (!pending.value) {
    if (!state.value?.eligible || !amount || amount > max.value) return;
    asking = true;
    try {
      if (
        !(await confirmation.value?.ask({
          title: `确认投 ${amount} 硬币？`,
          detail: `硬币将投给《${props.articleTitle}》。投出后无法自行撤回。`,
          action: `投 ${amount} 硬币`,
        }))
      )
        return;
    } finally {
      asking = false;
    }
    if (!mounted || busy.value) return;
  }
  error.value = "";
  notice.value = "";
  busy.value = true;
  try {
    if (!pending.value) {
      if (!amount) return;
      const value = { amount, requestId: crypto.randomUUID() };
      writePrivate(key, value);
      pending.value = value;
    }
    const receipt = await send<{ delta: number }>(
      `/articles/${props.articleId}/coins`,
      "POST",
      pending.value,
    );
    if (!mounted) return;
    removePrivate(key);
    pending.value = undefined;
    notice.value = `已投 ${-receipt.delta} 币。`;
    state.value = undefined;
    wallet.value = undefined;
    emit("confirmed");
    await readState();
  } catch (e) {
    error.value = pending.value
      ? `${message(e)} 可按原金额核对结果。`
      : message(e);
  } finally {
    busy.value = false;
  }
}
async function finishPending() {
  await load();
  if (!pending.value || !state.value) return;
  if (
    !(await confirmation.value?.ask({
      title: "结束待确认投币记录？",
      detail: "已读取当前额度和余额。结束本机记录后，已完成的投币仍然有效。",
      action: "结束本机记录",
    }))
  )
    return;
  removePrivate(key);
  pending.value = undefined;
}
onMounted(load);
onBeforeUnmount(() => {
  mounted = false;
});
</script>
<template>
  <div class="stack">
    <ConfirmDialog ref="confirmation" />
    <p v-if="busy" class="hint" role="status">正在核对投币…</p>
    <p v-if="wallet && state" class="hint">
      余额 {{ wallet.balance }} 币 · 本篇已投 {{ state.spent }} 币 · 剩余额度
      {{ state.remaining }} 币
    </p>
    <p v-if="state && !state.eligible" class="hint">
      {{ reasonText[state.reason] || "当前文章暂不可投币。" }}
    </p>
    <p v-else-if="wallet && wallet.balance < 1" class="hint">
      余额不足。可在
      <RouterLink to="/me/wallet">我的硬币</RouterLink> 查看奖励。
    </p>
    <button v-if="pending" :disabled="busy" @click="submit()">
      核对上次投 {{ pending.amount }} 币
    </button>
    <div v-else-if="state?.eligible && max > 0" class="card-actions">
      <button
        v-for="amount in max"
        :key="amount"
        :disabled="busy"
        @click="submit(amount)"
      >
        投 {{ amount }} 币
      </button>
    </div>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <p v-if="error" class="error" role="alert">
      {{ error }}
      <button
        v-if="pending"
        class="text-button"
        :disabled="busy"
        @click="finishPending"
      >
        读取最新状态并结束待确认记录
      </button>
      <button v-else class="text-button" :disabled="busy" @click="load">
        重新核对投币状态
      </button>
    </p>
  </div>
</template>
