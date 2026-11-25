<script setup lang="ts">
defineProps<{
  loading?: boolean;
  error?: string;
  stale?: boolean;
  label?: string;
}>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <p v-if="error" class="error console-feedback" role="alert">
    {{ error }} <span v-if="stale">当前显示的是上次读取结果。</span>
    <button type="button" :disabled="loading" @click="$emit('retry')">
      重试当前查询
    </button>
  </p>
  <p v-else-if="loading" class="console-muted" role="status">
    {{ label || "正在读取…" }}
  </p>
</template>
