<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId } from "vue";

interface Confirmation {
  title: string;
  detail: string;
  action: string;
}
const dialog = ref<HTMLDialogElement>();
const cancel = ref<HTMLButtonElement>();
const options = ref<Confirmation>();
const id = useId();
let trigger: HTMLElement | undefined;
let result: Promise<boolean> | undefined;
let resolve: ((answer: boolean) => void) | undefined;

function ask(value: Confirmation, origin?: HTMLElement | null) {
  if (result)
    return options.value?.title === value.title &&
      options.value.detail === value.detail &&
      options.value.action === value.action
      ? result
      : Promise.resolve(false);
  trigger =
    origin ||
    (document.activeElement instanceof HTMLElement
      ? document.activeElement
      : undefined);
  options.value = value;
  result = new Promise<boolean>((done) => {
    resolve = done;
  });
  void nextTick(() => {
    if (!resolve) return;
    dialog.value?.showModal();
    cancel.value?.focus();
  });
  return result;
}
function finish(answer: boolean) {
  const done = resolve;
  if (!done) return;
  resolve = undefined;
  result = undefined;
  dialog.value?.close();
  trigger?.focus({ preventScroll: true });
  trigger = undefined;
  done(answer);
}
onBeforeUnmount(() => finish(false));
defineExpose({ ask });
</script>

<template>
  <dialog
    ref="dialog"
    class="publish-dialog panel"
    :aria-labelledby="id + '-title'"
    :aria-describedby="id + '-detail'"
    @cancel.prevent="finish(false)"
  >
    <h2 :id="id + '-title'">{{ options?.title }}</h2>
    <p :id="id + '-detail'" class="long-content">{{ options?.detail }}</p>
    <div class="card-actions">
      <button ref="cancel" type="button" @click="finish(false)">取消</button>
      <button class="primary" type="button" @click="finish(true)">
        {{ options?.action }}
      </button>
    </div>
  </dialog>
</template>
