<script setup lang="ts">
import type { Article } from "../types";
import FavoriteButton from "./FavoriteButton.vue";
import { dateText, sourceName } from "../lib/content";
import { useRouter } from "vue-router";
const props = defineProps<{ article: Article; showFavorite?: boolean }>();
const router = useRouter();
function open(event: MouseEvent) {
  if (
    event.defaultPrevented ||
    event.button !== 0 ||
    event.ctrlKey ||
    event.metaKey ||
    event.shiftKey ||
    event.altKey
  )
    return;
  if (
    (event.target as Element).closest("a,button,input,select,textarea,label") ||
    window.getSelection()?.toString()
  )
    return;
  void router.push(`/articles/${props.article.id}`);
}
</script>
<template>
  <article class="article-card panel" @click="open">
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
    <FavoriteButton v-if="showFavorite" :article-id="article.id" />
  </article>
</template>
