<script setup lang="ts">
import { useRoute } from "vue-router";
import OpsPrototype from "./OpsPrototype.vue";
import OpsRuntime from "./OpsRuntime.vue";
const route = useRoute();
</script>
<template>
  <OpsPrototype v-if="route.query.preview === '1'" /><OpsRuntime v-else />
</template>
