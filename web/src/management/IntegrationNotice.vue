<script setup lang="ts">
defineProps<{ feature: string; preview?: boolean }>();
</script>
<template>
  <p v-if="!preview" class="console-integration" role="status">
    {{
      feature
    }}的后端接口尚未开发，当前数据与执行结果无法核验；此处操作不会发起。<span
      >布局已按原型保留，可在页顶查看原型示例。</span
    >
  </p>
</template>
