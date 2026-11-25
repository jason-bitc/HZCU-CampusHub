<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import type { Article } from "../types";
import { cardDate, sourceName } from "../lib/content";
import FavoriteButton from "./FavoriteButton.vue";
const props = defineProps<{
  article: Article;
  backTo?: string;
  showFavorite?: boolean;
}>();
const emit = defineEmits<{ open: [] }>();
const router = useRouter();
const destination = computed(() => {
  const query: Record<string, string> = {};
  if (props.backTo) query.from = props.backTo;
  return { path: `/articles/${props.article.id}`, query };
});
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
  emit("open");
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
        ><time :datetime="article.publishedAt">{{
          cardDate(article.publishedAt)
        }}</time>
      </p>
      <div class="article-card-actions">
        <FavoriteButton v-if="showFavorite" :article-id="article.id" />
        <span class="card-read" aria-hidden="true">阅读</span>
      </div>
    </div>
  </article>
</template>
