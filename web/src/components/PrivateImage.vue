<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import { message, request } from "../lib/api";

const props = withDefaults(
  defineProps<{ imageId: string; kind?: "comment" | "case" }>(),
  { kind: "comment" },
);
const url = ref("");
const error = ref("");
const busy = ref(false);
let generation = 0;
function release() {
  generation++;
  if (url.value) URL.revokeObjectURL(url.value);
  url.value = "";
}
async function load() {
  release();
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(props.imageId))
    return;
  const current = generation;
  busy.value = true;
  error.value = "";
  try {
    const bytes = await request<Blob>(
      `/media/${props.kind}-images/${props.imageId}`,
      { auth: true, blob: true },
    );
    if (current === generation) url.value = URL.createObjectURL(bytes);
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
watch(() => [props.kind, props.imageId], load, { immediate: true });
onBeforeUnmount(release);
</script>
<template>
  <img
    v-if="url"
    :src="url"
    :alt="kind === 'case' ? '案件证据图片' : '评论附图'"
    width="240"
    style="max-width: 100%; height: auto"
  />
  <span v-else-if="busy" class="hint" role="status">正在读取图片…</span>
  <p v-else-if="error" class="hint">
    {{ error }} <button class="text-button" @click="load">重试图片</button>
  </p>
</template>
