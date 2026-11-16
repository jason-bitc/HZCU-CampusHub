<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import AdminPrototype from "./AdminPrototype.vue";
import AdminContent from "./AdminContent.vue";
import AdminCategories from "./AdminCategories.vue";
import AdminAccounts from "./AdminAccounts.vue";
import AdminAudit from "./AdminAudit.vue";
import AdminContributions from "./AdminContributions.vue";
const route = useRoute();
const component = computed(() =>
  route.query.preview === "1"
    ? AdminPrototype
    : {
        content: AdminContent,
        categories: AdminCategories,
        accounts: AdminAccounts,
        audit: AdminAudit,
        contributions: AdminContributions,
      }[route.path.split("/").pop() as "content"],
);
</script>
<template><component :is="component" /></template>
