<script setup lang="ts">
import type { Honor } from "../types";
defineProps<{ honor: Honor }>();
const icons: Record<string, string> = {
  sprout: "🌱",
  "open-book": "📖",
  "star-book": "📖 ✦",
  lighthouse: "🗼",
};
</script>
<template>
  <span class="hint">
    <span v-if="honor.badge && icons[honor.badge]" aria-hidden="true">{{
      icons[honor.badge]
    }}</span>
    <span>{{ honor.title }}</span>
  </span>
</template>
