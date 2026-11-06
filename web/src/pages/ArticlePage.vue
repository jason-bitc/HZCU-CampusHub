<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { get, message, send } from "../lib/api";
import { session } from "../lib/session";
import { dateText, safeLink, sourceName } from "../lib/content";
import type { Detail } from "../types";
import MarkdownView from "../components/MarkdownView.vue";
const route = useRoute();
const article = ref<Detail>();
const error = ref("");
const actionError = ref("");
const busy = ref(false);
const interaction = ref<{ liked: boolean; favorited: boolean }>();
const acting = ref(false);
async function load() {
  busy.value = true;
  error.value = "";
  try {
    article.value = await get<Detail>(`/articles/${route.params.id}`);
    await loadInteraction();
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function loadInteraction() {
  if (session.status !== "ready") {
    interaction.value = undefined;
    return;
  }
  try {
    interaction.value = await get(
      `/articles/${route.params.id}/interaction-state`,
      true,
    );
  } catch (e) {
    actionError.value = message(e);
  }
}
async function toggle(kind: "like" | "favorite") {
  if (!interaction.value) return;
  acting.value = true;
  actionError.value = "";
  try {
    const active =
      kind === "like" ? !interaction.value.liked : !interaction.value.favorited;
    await send(
      `/articles/${route.params.id}/${kind}`,
      active ? "PUT" : "DELETE",
    );
    await loadInteraction();
    article.value = await get<Detail>(`/articles/${route.params.id}`);
  } catch (e) {
    actionError.value = message(e);
  } finally {
    acting.value = false;
  }
}
onMounted(load);
watch(() => session.status, loadInteraction);
</script>
<template>
  <div class="breadcrumb">
    <RouterLink to="/">发现经验</RouterLink><span>/</span><span>阅读正文</span>
  </div>
  <section v-if="busy" class="panel state-panel" role="status">
    正在读取全文…
  </section>
  <section v-else-if="error" class="panel state-panel" role="alert">
    <h1>文章暂时无法阅读</h1>
    <p>{{ error }}</p>
    <button @click="load">重新读取</button
    ><RouterLink class="button" to="/">返回发现</RouterLink>
  </section>
  <div v-else-if="article" class="reading-layout">
    <article class="panel reading-article">
      <header class="article-heading">
        <h1>{{ article.title }}</h1>
        <p class="summary">{{ article.summary }}</p>
        <p class="article-tags">
          <span
            v-for="c in [
              ...article.categories.jobs,
              ...article.categories.majors,
              ...article.categories.companies,
            ]"
            :key="c.id"
            >{{ c.name }}</span
          ><span>{{ article.experienceStage }}</span>
        </p>
        <p class="meta">
          <span>{{ sourceName[article.sourceType] }}</span
          ><span>首次发布 {{ dateText(article.publishedAt) }}</span
          ><span>更新 {{ dateText(article.updatedAt) }}</span>
        </p>
      </header>
      <MarkdownView :body="article.body" toc />
      <section
        v-if="article.sourceNote || article.sourceLinks.length"
        class="source-section"
      >
        <h2>内容来源与资料</h2>
        <p>{{ article.sourceNote }}</p>
        <template v-for="(link, i) in article.sourceLinks" :key="i"
          ><a
            v-if="safeLink(link.url)"
            :href="safeLink(link.url)"
            target="_blank"
            rel="noopener noreferrer"
            >{{ link.label }} ↗</a
          ></template
        >
      </section>
    </article>
    <aside class="reading-sidebar">
      <section class="panel">
        <h2>这篇经验有帮助吗</h2>
        <p class="muted">
          {{ article.counts.effectiveLikes }} 个赞 ·
          {{ article.counts.effectiveCoins }} 硬币认可
        </p>
        <template v-if="session.status === 'ready'"
          ><div class="stack">
            <button
              :disabled="acting || !interaction"
              :aria-pressed="interaction?.liked || false"
              @click="toggle('like')"
            >
              {{
                interaction?.liked ? "已点赞 · 取消" : "赞同这篇经验"
              }}</button
            ><button
              :disabled="acting || !interaction"
              :aria-pressed="interaction?.favorited || false"
              @click="toggle('favorite')"
            >
              {{ interaction?.favorited ? "已收藏 · 取消" : "收藏，稍后阅读" }}
            </button>
          </div>
          <p v-if="actionError" class="error" role="alert">
            {{ actionError }}
            <button class="text-button" @click="loadInteraction">
              重读互动状态
            </button>
          </p></template
        ><template v-else
          ><p>登录后点赞和收藏，全文可直接阅读。</p>
          <RouterLink
            class="button primary full"
            :to="{ path: '/account', query: { next: route.fullPath } }"
            >登录校园账号</RouterLink
          ></template
        >
      </section>
      <section class="panel">
        <h2>阅读提示</h2>
        <p>对照当时的基础、经历阶段与内容来源，找到适合自己的部分。</p>
        <p class="hint">
          作者联系、评论与投币暂请在小程序中使用，网页正在分批接入。
        </p>
      </section>
    </aside>
  </div>
</template>
