<script setup lang="ts">
// 控制台线性图标：统一 20 网格、描边 1.33、圆头，随文字颜色着色。
const props = withDefaults(
  defineProps<{
    name:
      | "queue"
      | "content"
      | "accounts"
      | "category"
      | "cert"
      | "audit"
      | "overview"
      | "tasks"
      | "logs"
      | "deploy"
      | "backup"
      | "rollback"
      | "check"
      | "chevron-left"
      | "chevron-right"
      | "menu"
      | "close";
    size?: number;
  }>(),
  { size: 20 },
);
</script>
<template>
  <svg
    class="console-icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 20 20"
    fill="none"
    stroke="currentColor"
    stroke-width="1.33"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <template v-if="props.name === 'queue'">
      <rect x="3.5" y="3.5" width="13" height="14" rx="2" />
      <path d="M7 1.75h6M6.75 7.75h6.5M6.75 11.25h6.5M6.75 14.75h3.5" />
    </template>
    <template v-else-if="props.name === 'content'">
      <path
        d="M11.5 17H5.5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v4.5"
      />
      <path d="M6.5 7.5h7M6.5 11h3" />
      <path
        d="M12.5 11.5h3.5A1.5 1.5 0 0 1 17.5 13v2.5a1.5 1.5 0 0 1-1.5 1.5h-1l-1.75 1.75V17h-.75A1.5 1.5 0 0 1 11 15.5V13a1.5 1.5 0 0 1 1.5-1.5z"
      />
    </template>
    <template v-else-if="props.name === 'accounts'">
      <circle cx="7.25" cy="6.75" r="2.75" />
      <path d="M2.5 16.75c0-2.9 2.05-4.75 4.75-4.75s4.75 1.85 4.75 4.75" />
      <path d="M12.75 4.5a2.5 2.5 0 0 1 0 4.5M14.25 12.3c1.85.55 3.25 2.1 3.25 4.45" />
    </template>
    <template v-else-if="props.name === 'category'">
      <rect x="2.75" y="2.75" width="6" height="6" rx="1.3" />
      <rect x="11.25" y="2.75" width="6" height="6" rx="1.3" />
      <rect x="2.75" y="11.25" width="6" height="6" rx="1.3" />
      <path d="M14.25 11v6.5M11 14.25H17.5" />
    </template>
    <template v-else-if="props.name === 'cert'">
      <rect x="2.25" y="4.25" width="15.5" height="11.5" rx="2" />
      <circle cx="7" cy="9" r="1.6" />
      <path d="M4.75 13.25c.4-1.35 1.25-2.1 2.25-2.1s1.85.75 2.25 2.1M12.25 8h3.25M12.25 11.75h2.25" />
    </template>
    <template v-else-if="props.name === 'audit'">
      <circle cx="10" cy="10" r="7.25" />
      <path d="M10 5.75v4.4l2.9 1.7" />
    </template>
    <template v-else-if="props.name === 'overview'">
      <rect x="2.25" y="3.25" width="15.5" height="10.5" rx="2" />
      <path d="M7 17h6M10 13.75V17" />
      <path d="M5.25 10.75l2.25-2.75 2 1.5 2.5-3 2.75 4.25" />
    </template>
    <template v-else-if="props.name === 'tasks'">
      <rect x="4" y="2.75" width="12" height="14.5" rx="2" />
      <path d="M6.75 7.5l1 1 1.75-2M6.75 12.5l1 1 1.75-2M12 8h2M12 13h2" />
    </template>
    <template v-else-if="props.name === 'logs'">
      <path d="M4.75 2.5h6.75L15.25 6v11.5h-10.5z" />
      <path d="M11.5 2.5V6h3.75M7.25 9.5h5.5M7.25 12.5h5.5M7.25 15.5h3" />
    </template>
    <template v-else-if="props.name === 'deploy'">
      <path d="M10 12.5V3.75M6.5 7.25L10 3.75l3.5 3.5" />
      <path d="M3.75 12.5v3a1.5 1.5 0 0 0 1.5 1.5h9.5a1.5 1.5 0 0 0 1.5-1.5v-3" />
    </template>
    <template v-else-if="props.name === 'backup'">
      <ellipse cx="10" cy="5" rx="6.25" ry="2.4" />
      <path
        d="M3.75 5v10c0 1.33 2.8 2.4 6.25 2.4s6.25-1.07 6.25-2.4V5M3.75 10c0 1.33 2.8 2.4 6.25 2.4s6.25-1.07 6.25-2.4"
      />
    </template>
    <template v-else-if="props.name === 'rollback'">
      <path d="M3.75 10a6.25 6.25 0 1 1 1.9 4.5" />
      <path d="M3.75 18v-3.5H7.25" />
    </template>
    <template v-else-if="props.name === 'check'">
      <path d="M4 10.5l4 4 8-9" />
    </template>
    <template v-else-if="props.name === 'chevron-left'">
      <path d="M12 4l-6 6 6 6" />
    </template>
    <template v-else-if="props.name === 'chevron-right'">
      <path d="M8 4l6 6-6 6" />
    </template>
    <template v-else-if="props.name === 'menu'">
      <path d="M3.5 5.5h13M3.5 10h13M3.5 14.5h13" />
    </template>
    <template v-else-if="props.name === 'close'">
      <path d="M5 5l10 10M15 5L5 15" />
    </template>
  </svg>
</template>
<style scoped>
.console-icon {
  flex: none;
  display: inline-block;
  vertical-align: -0.2em;
}
</style>
