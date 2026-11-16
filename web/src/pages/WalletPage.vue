<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import MemberGate from "../components/MemberGate.vue";
import AuthorHonor from "../components/AuthorHonor.vue";
import { ApiError, get, message, send } from "../lib/api";
import { dateText } from "../lib/content";
import {
  readPrivate,
  removePrivate,
  session,
  writePrivate,
} from "../lib/session";
import type { Page, Wallet } from "../types";

type RewardKind = "login" | "reading";
interface RewardRequest {
  requestId: string;
  sessionId?: string;
}
interface Reading {
  sessionId: string;
  articleId: number;
  claimableAt: string;
}
interface Rewards {
  businessDate: string;
  serverTime: string;
  rewardedToday: number;
  remainingDailyReward: number;
  rules: {
    loginReward: number;
    readingReward: number;
    readingSeconds: number;
    dailyMaximum: number;
  };
  login: { eligible: boolean; claimed: boolean };
  reading: { eligible: boolean; claimed: boolean };
  readingSessions: Reading[];
}
interface Event {
  eventId: string;
  action: string;
  articleId: number | null;
  delta: number;
  balanceAfter: number;
  occurredAt: string;
}
type Ledger = Page<Event> & { snapshot: number };
const wallet = ref<Wallet>();
const rewards = ref<Rewards>();
const ledger = ref<Ledger>();
const pending = ref<Partial<Record<RewardKind, RewardRequest>>>({});
const busy = ref(false),
  error = ref(""),
  notice = ref("");
const time = ref(Date.now());
let offset = 0;
const timer = window.setInterval(() => {
  time.value = Date.now();
}, 1000);
onBeforeUnmount(() => clearInterval(timer));
const next = computed(
  () =>
    rewards.value?.readingSessions
      .slice()
      .sort((a, b) => Date.parse(a.claimableAt) - Date.parse(b.claimableAt))[0],
);
const seconds = computed(() =>
  next.value
    ? Math.max(
        0,
        Math.ceil(
          (Date.parse(next.value.claimableAt) - time.value - offset) / 1000,
        ),
      )
    : null,
);
const kinds = { login: "登录", reading: "阅读" };
const actions: Record<string, string> = {
  LOGIN_REWARD: "登录奖励",
  READING_REWARD: "阅读奖励",
  ARTICLE_COIN: "文章投币",
};
async function snapshot() {
  const [balance, tasks, page] = await Promise.all([
    get<Wallet>("/me/coins", true),
    get<Rewards>("/me/rewards", true),
    get<Ledger>("/me/coin-ledger?page=1&size=20", true),
  ]);
  wallet.value = balance;
  rewards.value = tasks;
  ledger.value = page;
  offset = Date.parse(tasks.serverTime) - Date.now();
  time.value = Date.now();
}
async function run(action: () => Promise<void>) {
  if (busy.value || session.status !== "ready") return;
  busy.value = true;
  error.value = "";
  try {
    await action();
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
function load() {
  void run(async () => {
    for (const kind of ["login", "reading"] as const)
      pending.value[kind] =
        readPrivate<RewardRequest>(`reward:${kind}`) ?? undefined;
    await snapshot();
  });
}
function claim(kind: RewardKind) {
  if (
    !pending.value[kind] &&
    (!rewards.value ||
      rewards.value[kind].claimed ||
      (kind === "login"
        ? !rewards.value.login.eligible
        : seconds.value !== 0 || !next.value))
  )
    return;
  void run(async () => {
    notice.value = "";
    const key = `reward:${kind}`;
    if (!pending.value[kind]) {
      const payload: RewardRequest = { requestId: crypto.randomUUID() };
      if (kind === "reading") payload.sessionId = next.value!.sessionId;
      writePrivate(key, payload);
      pending.value[kind] = payload;
    }
    try {
      const receipt = await send<Event>(
        `/me/rewards/${kind}`,
        "POST",
        pending.value[kind],
      );
      removePrivate(key);
      pending.value[kind] = undefined;
      notice.value = `已领取${kinds[kind]}奖励 · +${receipt.delta} 币`;
    } catch (e) {
      if (
        e instanceof ApiError &&
        [
          "REWARD_ALREADY_CLAIMED",
          "READING_DAY_EXPIRED",
          "LOGIN_REQUIRED_TODAY",
          "NOT_FOUND",
        ].includes(e.code)
      ) {
        removePrivate(key);
        pending.value[kind] = undefined;
        await snapshot();
      }
      throw e;
    }
    await snapshot();
  });
}
function turn(page: number) {
  const saved = ledger.value;
  if (!saved) return;
  void run(async () => {
    ledger.value = await get(
      `/me/coin-ledger?page=${page}&size=${saved.size}&snapshot=${saved.snapshot}`,
      true,
    );
  });
}
onMounted(load);
watch(() => session.status, load);
</script>
<template>
  <MemberGate title="登录后查看硬币与账目">
    <div class="page-heading">
      <h1>我的硬币</h1>
      <button :disabled="busy" @click="load">刷新钱包</button>
    </div>
    <p v-if="busy" role="status">正在读取或提交…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <section v-if="wallet" class="panel">
      <h2>可用余额 {{ wallet.balance }} 币</h2>
      <p>收到的有效贡献 {{ wallet.receivedContribution }} 币</p>
      <AuthorHonor v-if="wallet.honor" :honor="wallet.honor" />
      <p v-else class="hint">当前暂无作者荣誉。</p>
    </section>
    <section v-if="rewards" class="panel stack">
      <h2>每日奖励 · {{ rewards.businessDate }}</h2>
      <p>
        当天已领 {{ rewards.rewardedToday }} 币 · 剩余额度
        {{ rewards.remainingDailyReward }} 币
      </p>
      <p>
        登录奖励 {{ rewards.rules.loginReward }} 币，阅读满
        {{ rewards.rules.readingSeconds }} 秒可领取
        {{ rewards.rules.readingReward }} 币。
      </p>
      <p v-if="!rewards.reading.claimed" class="hint">
        {{
          seconds === null
            ? "打开一篇公开经验开始计时。"
            : seconds > 0
              ? `阅读任务还需约 ${seconds} 秒。`
              : "阅读任务已达到计时门槛。"
        }}
      </p>
      <div class="card-actions">
        <button
          :disabled="
            busy ||
            (!pending.login &&
              (!rewards.login.eligible || rewards.login.claimed))
          "
          @click="claim('login')"
        >
          {{
            pending.login
              ? "核对上次登录奖励"
              : rewards.login.claimed
                ? "今日登录奖励已领"
                : "领取登录奖励"
          }}
        </button>
        <button
          :disabled="
            busy ||
            (!pending.reading && (rewards.reading.claimed || seconds !== 0))
          "
          @click="claim('reading')"
        >
          {{
            pending.reading
              ? "核对上次阅读奖励"
              : rewards.reading.claimed
                ? "今日阅读奖励已领"
                : "领取阅读奖励"
          }}
        </button>
      </div>
    </section>
    <section v-if="ledger" class="panel">
      <h2>本人账目</h2>
      <p v-if="!ledger.total">暂无账目。</p>
      <div
        v-for="row in ledger.items"
        :key="row.eventId"
        class="restriction-row"
      >
        <strong
          >{{ actions[row.action] || row.action }} ·
          {{ row.delta > 0 ? "+" : "" }}{{ row.delta }} 币</strong
        >
        <p class="meta">
          {{ dateText(row.occurredAt) }} · 当时余额
          {{ row.balanceAfter }} 币<span v-if="row.articleId">
            · 文章 {{ row.articleId }}</span
          >
        </p>
      </div>
      <nav
        v-if="ledger.total > ledger.size"
        class="pagination"
        aria-label="本人账目翻页"
      >
        <button
          :disabled="busy || ledger.page <= 1"
          @click="turn(ledger.page - 1)"
        >
          上一页</button
        ><span>第 {{ ledger.page }} 页</span>
        <button
          :disabled="busy || ledger.page * ledger.size >= ledger.total"
          @click="turn(ledger.page + 1)"
        >
          下一页
        </button>
      </nav>
    </section>
  </MemberGate>
</template>
