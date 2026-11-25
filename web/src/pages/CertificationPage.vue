<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, get, message, send } from "../lib/api";
import { clearSession, session } from "../lib/session";
import { dateText } from "../lib/content";
import MemberGate from "../components/MemberGate.vue";
import type { Challenge } from "../types";
import type { Appeal } from "../management/governance";

interface Certification {
  state: string;
  verifiedAt: string | null;
  canReverify: boolean;
  revocationReason: string | null;
  revokedAt: string | null;
  revision: number;
  canAppeal: boolean;
  appealId: number | null;
}
const route = useRoute(),
  router = useRouter();
const appealView = computed(() => !!route.params.id);
const certification = ref<Certification>();
const appeal = ref<Appeal>();
const challenge = ref<Challenge>();
const code = ref("");
const reason = ref("");
const pending = ref<{
  requestId: string;
  certificationRevision: number;
  reason: string;
}>();
const busy = ref(false),
  error = ref(""),
  notice = ref("");
const now = ref(Date.now());
const timer = window.setInterval(() => {
  now.value = Date.now();
}, 1000);
onBeforeUnmount(() => clearInterval(timer));
const wait = computed(() =>
  challenge.value
    ? Math.max(
        0,
        Math.ceil((Date.parse(challenge.value.resendAt) - now.value) / 1000),
      )
    : 0,
);
const expired = computed(
  () => !challenge.value || Date.parse(challenge.value.expiresAt) <= now.value,
);
async function run(action: () => Promise<void>) {
  if (busy.value || !session.token) return;
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
  if (!["ready", "limited"].includes(session.status)) return;
  void run(async () => {
    if (appealView.value)
      appeal.value = await get(`/me/appeals/${route.params.id}`, true);
    else certification.value = await get("/me/certification", true);
  });
}
function requestCode() {
  if (!certification.value?.canReverify || wait.value) return;
  void run(async () => {
    const received = await send<Challenge>(
      "/me/certification/reverification-codes",
      "POST",
    );
    challenge.value = received;
    code.value = "";
    notice.value = received.notice;
  });
}
function verify() {
  if (!challenge.value || expired.value || !/^\d{6}$/.test(code.value)) return;
  const payload = {
    challengeId: challenge.value.challengeId,
    code: code.value,
  };
  void run(async () => {
    await send("/me/certification/reverification", "POST", payload);
    clearSession("校园认证已恢复，请重新登录。");
    await router.replace("/account");
  });
}
function submitAppeal() {
  if (
    !pending.value &&
    (!certification.value?.canAppeal || !reason.value.trim())
  )
    return;
  void run(async () => {
    pending.value ??= {
      requestId: crypto.randomUUID(),
      certificationRevision: certification.value!.revision,
      reason: reason.value.trim(),
    };
    try {
      const receipt = await send<{ id: number }>(
        "/me/appeals",
        "POST",
        pending.value,
      );
      pending.value = undefined;
      await router.push(`/me/appeals/${receipt.id}`);
    } catch (e) {
      if (
        e instanceof ApiError &&
        [
          "REVISION_CONFLICT",
          "CERTIFICATION_APPEAL_EXISTS",
          "CERTIFICATION_NOT_REVOKED",
        ].includes(e.code)
      ) {
        certification.value = await get("/me/certification", true);
        pending.value = undefined;
      }
      throw e;
    }
  });
}
function logout() {
  void run(async () => {
    await send("/auth/logout", "POST");
    clearSession("已退出校园账号。");
    await router.replace("/account");
  });
}
onMounted(load);
watch(() => session.status, load);
</script>
<template>
  <MemberGate allow-limited title="登录后查看本人认证与申诉">
    <div class="page-heading service-heading">
      <h1>{{ appealView ? "本人申诉详情" : "本人校园认证" }}</h1>
    </div>
    <p v-if="busy" role="status">正在读取或提交…</p>
    <p v-if="error" class="error" role="alert">
      {{ error }}
      <span v-if="certification || appeal">当前显示的是上次读取结果。</span>
      <button @click="load">刷新记录</button>
    </p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <section v-if="appeal" class="panel">
      <h2>
        {{
          appeal.subject === "CAMPUS_CERTIFICATION"
            ? "校园认证申诉"
            : "行为限制申诉"
        }}
      </h2>
      <ol class="record-timeline">
        <li>
          <time
            v-if="appeal.createdAt"
            :datetime="appeal.createdAt"
            class="meta"
            >{{ dateText(appeal.createdAt) }}</time
          >
          <strong>提交申诉</strong>
          <p class="long-content">{{ appeal.reason }}</p>
        </li>
        <li>
          <time :datetime="appeal.updatedAt" class="meta">{{
            dateText(appeal.updatedAt)
          }}</time>
          <strong>{{
            appeal.state === "OPEN"
              ? "等待复核"
              : appeal.state === "ACCEPTED"
                ? "申诉已接受"
                : "申诉已驳回"
          }}</strong>
          <p v-if="appeal.reply" class="long-content">
            复核答复 {{ appeal.reply }}
          </p>
        </li>
      </ol>
      <RouterLink
        class="button"
        :to="
          appeal.subject === 'CAMPUS_CERTIFICATION'
            ? '/me/certification'
            : '/me/appeals'
        "
        >返回</RouterLink
      >
    </section>
    <template v-if="certification">
      <section class="panel stack">
        <h2>
          {{
            certification.state === "VALID"
              ? "校园邮箱已验证"
              : certification.state === "REVOKED"
                ? "校园认证已撤销"
                : certification.state
          }}
        </h2>
        <p v-if="certification.verifiedAt">
          最近验证 {{ dateText(certification.verifiedAt) }}
        </p>
        <p v-if="certification.revocationReason" class="long-content">
          撤销理由 {{ certification.revocationReason }}
        </p>
        <p v-if="certification.revokedAt">
          撤销时间 {{ dateText(certification.revokedAt) }}
        </p>
        <form
          v-if="certification.canReverify"
          class="stack"
          @submit.prevent="verify"
        >
          <h3>使用原校园邮箱重新认证</h3>
          <button
            type="button"
            :disabled="busy || wait > 0"
            @click="requestCode"
          >
            {{ wait ? `${wait} 秒后可重发` : "发送重新认证验证码" }}
          </button>
          <template v-if="challenge">
            <label
              >邮箱验证码<input
                v-model.trim="code"
                inputmode="numeric"
                autocomplete="one-time-code"
                maxlength="6"
            /></label>
            <p v-if="expired" class="hint">验证码已过期，请重新发送。</p>
            <button
              class="primary"
              :disabled="busy || expired || !/^\d{6}$/.test(code)"
            >
              提交重新认证
            </button>
          </template>
        </form>
        <form
          v-if="certification.canAppeal || pending"
          class="stack"
          @submit.prevent="submitAppeal"
        >
          <h3>申请复核本次撤销</h3>
          <label
            >申诉理由<textarea
              v-model="reason"
              required
              maxlength="2000"
              rows="3"
              :disabled="busy || !!pending"
            />
          </label>
          <button :disabled="busy">
            {{ pending ? "按原请求核对申诉" : "提交认证申诉" }}
          </button>
        </form>
        <RouterLink
          v-if="certification.appealId"
          :to="`/me/appeals/${certification.appealId}`"
          >查看本次认证申诉</RouterLink
        >
      </section>
      <div class="card-actions">
        <RouterLink to="/me/notifications">站内通知</RouterLink>
        <button
          v-if="session.status === 'limited'"
          :disabled="busy"
          @click="logout"
        >
          退出校园账号
        </button>
      </div>
    </template>
  </MemberGate>
</template>
