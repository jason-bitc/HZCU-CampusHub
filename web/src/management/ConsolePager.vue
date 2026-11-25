<script setup lang="ts">
defineProps<{ page: number; total: number; size: number; busy?: boolean }>();
defineEmits<{ turn: [page: number] }>();
</script>
<template>
  <nav class="console-pagination" aria-label="分页">
    <span>共 {{ total }} 条 · 第 {{ page }} 页</span>
    <div class="console-pagination-pages">
      <button
        class="console-icon-btn"
        type="button"
        aria-label="上一页"
        :disabled="busy || page <= 1"
        @click="$emit('turn', page - 1)"
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 20 20"
          fill="none"
          stroke="currentColor"
          stroke-width="1.33"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M12 4l-6 6 6 6" />
        </svg>
      </button>
      <span class="console-icon-btn is-current" aria-current="page">{{
        page
      }}</span>
      <button
        class="console-icon-btn"
        type="button"
        aria-label="下一页"
        :disabled="busy || page * size >= total"
        @click="$emit('turn', page + 1)"
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 20 20"
          fill="none"
          stroke="currentColor"
          stroke-width="1.33"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M8 4l6 6-6 6" />
        </svg>
      </button>
      <span class="console-pagination-size">每页 {{ size }} 条</span>
    </div>
  </nav>
</template>
