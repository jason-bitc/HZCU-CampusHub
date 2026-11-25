<script setup lang="ts">
// 页头：默认为白底页头带；传入 back（返回链接）或 meta 时切换为明细型页头。
defineProps<{
  title: string;
  description?: string;
  back?: { label: string; to: string };
  meta?: string;
  variant?: "band" | "plain";
}>();
</script>
<template>
  <header
    class="console-page-head"
    :class="{
      'is-plain':
        variant === 'plain' || !!back || (!!meta && variant !== 'band'),
    }"
  >
    <RouterLink v-if="back" :to="back.to" class="console-back"
      >‹ {{ back.label }}</RouterLink
    >
    <h1>{{ title }}</h1>
    <span v-if="meta" class="console-page-meta">{{ meta }}</span>
    <p v-if="description && !back" class="console-page-desc">
      {{ description }}
    </p>
    <div v-if="$slots.aside" class="console-page-aside">
      <slot name="aside" />
    </div>
  </header>
</template>
