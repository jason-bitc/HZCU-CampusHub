<script setup lang="ts">
import type { Article } from "../types";
import FavoriteButton from "./FavoriteButton.vue";
import { dateText, sourceName } from "../lib/content";
import { computed } from "vue";
import { useRouter } from "vue-router";
const props = defineProps<{
  article: Article;
  showFavorite?: boolean;
  backTo?: string;
}>();
const router = useRouter();
const destination = computed(() => ({
  path: `/articles/${props.article.id}`,
  query: props.backTo ? { from: props.backTo } : {},
}));
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
  void router.push(destination.value);
}
</script>
<template>
  <article
    :id="backTo ? `experience-${article.id}` : undefined"
    class="article-card panel"
    @click="open"
  >
    <div class="article-card-heading">
      <h2>
        <RouterLink :to="destination">{{ article.title }}</RouterLink>
      </h2>
      <span class="card-open" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M5 12h14M13 6l6 6-6 6" />
        </svg>
      </span>
    </div>
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
    <div class="article-card-footer">
      <p class="meta">
        <span>{{ sourceName[article.sourceType] || "来源待确认" }}</span
        ><time>{{ dateText(article.publishedAt) }}</time
        ><span>投币 {{ article.counts.effectiveCoins }}</span
        ><span>点赞 {{ article.counts.effectiveLikes }}</span>
      </p>
      <FavoriteButton v-if="showFavorite" :article-id="article.id" />
    </div>
  </article>
</template>
