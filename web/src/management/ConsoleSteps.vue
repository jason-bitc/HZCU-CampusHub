<script setup lang="ts">
// 横向步骤条：current 为当前步骤下标；大于等于步骤数时表示全部完成。
import ConsoleIcon from "./ConsoleIcon.vue";
const props = defineProps<{ steps: string[]; current: number }>();
function state(index: number) {
  if (props.current >= props.steps.length || index < props.current)
    return "done";
  return index === props.current ? "active" : "todo";
}
</script>
<template>
  <section class="console-panel console-steps" aria-label="处理进度">
    <ol class="console-steps-list">
      <li
        v-for="(step, index) in props.steps"
        :key="step"
        :class="`is-${state(index)}`"
        :aria-current="state(index) === 'active' ? 'step' : undefined"
      >
        <span class="console-step-index" aria-hidden="true">
          <ConsoleIcon
            v-if="state(index) === 'done'"
            name="check"
            :size="14"
          />
          <template v-else>{{ index + 1 }}</template>
        </span>
        <span class="console-step-label">{{ step }}</span>
        <span
          v-if="index < props.steps.length - 1"
          class="console-step-line"
          aria-hidden="true"
        />
      </li>
    </ol>
  </section>
</template>
