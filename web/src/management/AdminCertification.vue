<script setup lang="ts">
import { onMounted, ref } from "vue";
import { adminRequest } from "./client";
import { useMutation } from "./governance";
import { message } from "../lib/api";
import { dateText } from "../lib/content";
import MutationNotice from "./MutationNotice.vue";

const props = defineProps<{ userId: number; nickname: string }>();
interface Certification {
  userId: number;
  state: string;
  revision: number;
  verifiedAt: string | null;
  lastRevocation: {
    revision: number;
    adminId: number;
    reason: string;
    occurredAt: string;
  } | null;
}
const endpoint = `/admin/users/${props.userId}/certification`;
const current = ref<Certification>();
const loading = ref(false),
  loadError = ref(""),
  reason = ref("");
async function load() {
  if (loading.value) return;
  loading.value = true;
  loadError.value = "";
  try {
    current.value = await adminRequest(endpoint);
  } catch (e) {
    current.value = undefined;
    loadError.value = message(e);
  } finally {
    loading.value = false;
  }
}
const mutation = useMutation(
  `certification:${props.userId}`,
  "CERTIFICATION_REVOKE",
  load,
);
const { pending, busy, error, notice, allowed } = mutation;
onMounted(() => {
  mutation.restore();
  if (pending.value) reason.value = String(pending.value.body.reason ?? "");
  void load();
});
async function revoke() {
  const snapshot = current.value;
  if (
    !snapshot ||
    snapshot.state !== "VALID" ||
    !reason.value.trim() ||
    loading.value ||
    busy.value ||
    !allowed.value ||
    pending.value
  )
    return;
  if (
    !window.confirm(
      `确认撤销“${props.nickname}”的校园认证？对方可查看原因、申请复核或使用原邮箱重新认证。`,
    )
  )
    return;
  await mutation.start(
    `${endpoint}/revocation`,
    "POST",
    { expectedRevision: snapshot.revision, reason: reason.value.trim() },
    "撤销校园认证",
  );
  if (!pending.value) reason.value = "";
}
</script>
<template>
  <section class="console-followup">
    <h3>校园认证 · {{ nickname }}（{{ userId }}）</h3>
    <p v-if="loading" role="status">正在读取校园认证…</p>
    <p v-if="loadError" class="error" role="alert">
      {{ loadError }} <button @click="load">刷新认证</button>
    </p>
    <MutationNotice
      :pending="pending"
      :busy="busy || loading"
      :error="error"
      :notice="notice"
      @retry="mutation.submit"
      @reconcile="mutation.reconcile"
    />
    <template v-if="current">
      <p>
        {{
          current.state === "VALID"
            ? "已验证"
            : current.state === "REVOKED"
              ? "已撤销"
              : current.state
        }}
        · 修订 {{ current.revision }}
      </p>
      <p v-if="current.verifiedAt">
        最近验证 {{ dateText(current.verifiedAt) }}
      </p>
      <div v-if="current.lastRevocation" class="console-note">
        <p>
          最近撤销 · 管理员 {{ current.lastRevocation.adminId }} ·
          {{ dateText(current.lastRevocation.occurredAt) }}
        </p>
        <p class="console-preserve">{{ current.lastRevocation.reason }}</p>
      </div>
      <form
        v-if="current.state === 'VALID' && allowed"
        @submit.prevent="revoke"
      >
        <label
          >撤销理由<textarea
            v-model="reason"
            required
            maxlength="2000"
            rows="3"
            :disabled="busy || loading || !!pending"
          />
        </label>
        <button
          class="console-danger"
          :disabled="busy || loading || !!pending || !reason.trim()"
        >
          确认撤销认证
        </button>
      </form>
    </template>
  </section>
</template>
