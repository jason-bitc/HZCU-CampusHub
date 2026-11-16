<script setup lang="ts">
defineProps<{ page: number; total: number; size: number; busy?: boolean }>();
defineEmits<{ turn: [page: number] }>();
</script>
<template>
  <div class="console-pagination">
    <span
      >共 {{ total }} 条 · 第 {{ page }} /
      {{ Math.max(1, Math.ceil(total / size)) }} 页</span
    ><button :disabled="busy || page <= 1" @click="$emit('turn', page - 1)">
      上一页</button
    ><button
      :disabled="busy || page * size >= total"
      @click="$emit('turn', page + 1)"
    >
      下一页
    </button>
  </div>
</template>
