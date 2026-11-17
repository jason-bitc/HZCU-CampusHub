<script setup lang="ts">
import type { Feedback } from "../lib/feedback";
defineProps<{ feedback?: Feedback; processing?: string }>();
</script>

<template>
  <p v-if="processing" class="hint operation-feedback" role="status">
    {{ processing }}
  </p>
  <p
    v-else-if="feedback?.kind === 'success'"
    class="success operation-feedback"
    role="status"
  >
    {{ feedback.text }}
  </p>
  <p
    v-else-if="feedback"
    :class="feedback.kind === 'pending' ? 'notice' : 'error'"
    class="operation-feedback"
    role="alert"
  >
    {{ feedback.text }}
  </p>
</template>
