<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import AdminPrototype from "./AdminPrototype.vue";
import AdminContent from "./AdminContent.vue";
import AdminCategories from "./AdminCategories.vue";
import AdminAccounts from "./AdminAccounts.vue";
import AdminAudit from "./AdminAudit.vue";
const route = useRoute();
const component = computed(() =>
  route.query.preview === "1" || route.path.endsWith("contributions")
    ? AdminPrototype
    : {
        content: AdminContent,
        categories: AdminCategories,
        accounts: AdminAccounts,
        audit: AdminAudit,
      }[route.path.split("/").pop() as "content"],
);
</script>
<template><component :is="component" /></template>
