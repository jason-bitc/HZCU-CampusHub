<script setup lang="ts">
import ConsoleConfirm from "./ConsoleConfirm.vue";
import { computed, onMounted, ref } from "vue";
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
const stateText = computed(() =>
  current.value
    ? { VALID: "已验证", REVOKED: "已撤销" }[current.value.state] ||
      current.value.state
    : "未读取",
);
const tone = computed(() =>
  current.value?.state === "VALID"
    ? "is-positive"
    : current.value?.state === "REVOKED"
      ? "is-negative"
      : "is-neutral",
);
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
    !(await modal.value?.ask({
      title: `撤销 ${props.nickname} 的校园认证？`,
      detail: `账号 ${props.userId} · 认证修订 ${snapshot.revision}。撤销后将限制校园认证功能。本人可在个人中心的校园认证、通知和申诉入口查看原因、申请复核或重新认证。\n\n撤销理由：${reason.value.trim()}`,
      action: "撤销校园认证",
      danger: true,
    }))
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
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
</script>
<template>
  <section
    class="console-stack"
    :aria-label="`校园认证 · ${nickname}`"
    style="margin-top: 16px"
  >
    <p v-if="loading" class="console-muted" role="status">
      正在读取校园认证…
    </p>
    <p v-if="loadError" class="console-banner is-error" role="alert">
      {{ loadError }}
      <span class="console-banner-actions"
        ><button type="button" @click="load">刷新认证</button></span
      >
    </p>
    <ConsoleConfirm ref="modal" />
    <MutationNotice
      :pending="pending"
      :busy="busy || loading"
      :error="error"
      :notice="notice"
      @retry="mutation.submit"
      @reconcile="mutation.reconcile"
    />
    <div v-if="current" class="console-split">
      <section class="console-panel" aria-label="认证对象">
        <h2 class="console-card-title">认证对象</h2>
        <p class="console-card-desc">
          {{ nickname }} · U-{{ userId }} · 校园邮箱{{ stateText }}
        </p>
        <dl class="console-facts">
          <div>
            <dt>账号</dt>
            <dd>{{ nickname }} · U-{{ userId }}</dd>
          </div>
          <div>
            <dt>当前认证</dt>
            <dd>
              <span class="console-tag" :class="tone">{{ stateText }}</span>
              · 修订 {{ current.revision }}
            </dd>
          </div>
          <div>
            <dt>最近验证</dt>
            <dd>
              {{ current.verifiedAt ? dateText(current.verifiedAt) : "暂无记录" }}
            </dd>
          </div>
          <div>
            <dt>最近撤销</dt>
            <dd>
              <template v-if="current.lastRevocation"
                >管理员 {{ current.lastRevocation.adminId }} ·
                {{ dateText(current.lastRevocation.occurredAt) }}
                <small class="console-preserve">{{
                  current.lastRevocation.reason
                }}</small></template
              >
              <template v-else>无</template>
            </dd>
          </div>
        </dl>
        <p class="console-callout">
          撤销后，认证专属能力受限。账号仍可查看撤销原因及处理答复。若认证申诉接受，用户仍需重新验证校园邮箱。
        </p>
      </section>
      <form
        v-if="current.state === 'VALID' && allowed"
        :id="`certification-form-${userId}`"
        class="console-panel"
        @submit.prevent="revoke"
      >
        <h2 class="console-card-title">确认撤销认证</h2>
        <p class="console-card-desc">
          撤销理由将记录并通过本人通知与申诉路径可见。
        </p>
        <fieldset
          class="console-form"
          :disabled="busy || loading || !!pending"
        >
          <label class="console-field"
            ><span>处理动作</span
            ><input value="撤销校园认证" readonly /></label
          ><label class="console-field"
            ><span>必填理由</span
            ><textarea
              v-model="reason"
              required
              maxlength="2000"
              rows="3"
              placeholder="说明账号归属或认证依据需要重新核对的原因。"
          /></label>
        </fieldset>
        <div class="console-form-actions">
          <button
            class="console-danger"
            type="submit"
            :disabled="
              busy || loading || !!pending || !reason.trim()
            "
          >
            确认撤销
          </button>
        </div>
      </form>
      <section v-else class="console-panel" aria-label="申诉路径">
        <h2 class="console-card-title">申诉路径</h2>
        <p class="console-card-desc">账号可在本人申诉记录查看撤销原因。</p>
        <p class="console-form-note">
          撤销后的认证申诉在“账号与申诉”中复核；接受申诉后仍需本人重新验证校园邮箱。
          <RouterLink to="/admin/accounts">前往账号与申诉</RouterLink>
        </p>
      </section>
    </div>
  </section>
</template>
