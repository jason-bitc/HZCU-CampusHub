<script setup lang="ts">
import type { Article } from "../types";
import { dateText, sourceName } from "../lib/content";
defineProps<{ article: Article }>();
</script>
<template>
  <article class="article-card panel">
    <h2>
      <RouterLink :to="`/articles/${article.id}`">{{
        article.title
      }}</RouterLink>
    </h2>
    <p class="summary">{{ article.summary }}</p>
    <p class="article-tags">
      <span
        v-for="category in [
          ...article.categories.jobs,
          ...article.categories.majors,
          ...article.categories.companies,
          ...article.categories.topics,
        ]"
        :key="category.id"
        >{{ category.name }}</span
      ><span>{{ article.experienceStage }}</span>
    </p>
    <p class="meta">
      <span>{{ sourceName[article.sourceType] || "来源待确认" }}</span
      ><time>{{ dateText(article.publishedAt) }}</time
      ><span>投币 {{ article.counts.effectiveCoins }}</span
      ><span>点赞 {{ article.counts.effectiveLikes }}</span>
    </p>
  </article>
</template>
