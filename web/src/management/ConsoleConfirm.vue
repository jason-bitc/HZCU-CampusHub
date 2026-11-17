<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId } from "vue";
import type { ConfirmationCopy } from "./console-ux";

const modal = ref<HTMLDialogElement>();
const cancel = ref<HTMLButtonElement>();
const copy = ref<ConfirmationCopy>();
const id = useId();
let origin: HTMLElement | undefined;
let settle: ((accepted: boolean) => void) | undefined;

function ask(value: ConfirmationCopy): Promise<boolean> {
  if (settle) return Promise.resolve(false);
  copy.value = value;
  origin =
    document.activeElement instanceof HTMLElement
      ? document.activeElement
      : undefined;
  return new Promise((resolve) => {
    settle = resolve;
    void nextTick(() => {
      if (!settle) return;
      modal.value?.showModal();
      cancel.value?.focus();
    });
  });
}
function close(accepted: boolean) {
  const resolve = settle;
  if (!resolve) return;
  settle = undefined;
  modal.value?.close();
  if (origin?.isConnected) origin.focus({ preventScroll: true });
  origin = undefined;
  resolve(accepted);
}
onBeforeUnmount(() => close(false));
defineExpose({ ask });
</script>
<template>
  <dialog
    ref="modal"
    class="console-panel console-dialog"
    :aria-labelledby="id + '-title'"
    :aria-describedby="id + '-detail'"
    @cancel.prevent="close(false)"
  >
    <h2 :id="id + '-title'">{{ copy?.title }}</h2>
    <p :id="id + '-detail'" class="console-preserve">{{ copy?.detail }}</p>
    <div class="card-actions">
      <button ref="cancel" type="button" @click="close(false)">取消</button>
      <button
        type="button"
        :class="copy?.danger ? 'console-danger' : 'console-primary'"
        @click="close(true)"
      >
        {{ copy?.action }}
      </button>
    </div>
  </dialog>
</template>
