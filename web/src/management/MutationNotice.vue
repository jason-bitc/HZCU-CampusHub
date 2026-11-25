<script setup lang="ts">
import { ref } from "vue";
import ConsoleConfirm from "./ConsoleConfirm.vue";
import type { ConfirmationCopy } from "./console-ux";
import type { Mutation } from "./governance";
defineProps<{
  pending: Mutation | null;
  busy: boolean;
  error: string;
  notice: string;
}>();
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
defineEmits<{
  retry: [];
  reconcile: [ask: (copy: ConfirmationCopy) => Promise<boolean>];
}>();
function ask(copy: ConfirmationCopy) {
  return modal.value?.ask(copy) ?? Promise.resolve(false);
}
</script>
<template>
  <div v-if="error || notice || pending">
    <ConsoleConfirm ref="modal" />
  <p v-if="error" class="console-banner is-error" role="alert">{{ error }}</p>
  <p v-if="notice" class="console-banner is-success" role="status">
    {{ notice }}
  </p>
  <section
    v-if="pending"
    class="console-banner is-warning"
    role="status"
    aria-label="待确认操作"
  >
    <span
      >{{ pending.label }} · 结果待确认。原对象、修订、理由和请求键已保存，当前表单不会覆盖原请求。</span
    >
    <span class="console-banner-actions"
      ><button type="button" :disabled="busy" @click="$emit('retry')">
        按原请求核对</button
      ><button type="button" :disabled="busy" @click="$emit('reconcile', ask)">
        读取最新状态并核对
      </button></span
    >
  </section>
  </div>
</template>
