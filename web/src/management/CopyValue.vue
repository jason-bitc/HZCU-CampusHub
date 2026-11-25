<script setup lang="ts">
import { ref } from "vue";
const props = defineProps<{ value: string; label: string }>();
const note = ref("");
async function copy() {
  try {
    await navigator.clipboard.writeText(props.value);
    note.value = "已复制" + props.label;
  } catch {
    note.value = "请选中并手动复制";
  }
}
</script>
<template>
  <span class="console-copy">
    <code>{{ value }}</code>
    <button type="button" :aria-label="'复制' + label" @click="copy">
      复制
    </button>
    <small v-if="note" role="status">{{ note }}</small>
  </span>
</template>
