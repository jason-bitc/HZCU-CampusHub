<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, get, message, send } from "../lib/api";
import { session } from "../lib/session";
import { dateText, safeLink, sourceName } from "../lib/content";
import type { AuthorSummary, Detail } from "../types";
import MarkdownView from "../components/MarkdownView.vue";
import ArticleCoins from "../components/ArticleCoins.vue";
import ArticleComments from "../components/ArticleComments.vue";
const route = useRoute();
const router = useRouter();
const actionBar = ref<HTMLElement>();
const actionHeight = ref(0);
watch(actionBar, (element, _, cleanup) => {
  if (!element) return;
  const observer = new ResizeObserver(() => {
    actionHeight.value = Math.ceil(element.getBoundingClientRect().height);
  });
  observer.observe(element);
  cleanup(() => observer.disconnect());
});
const markdown = ref<InstanceType<typeof MarkdownView>>();
const confirmationRead = ref(false);
type Interaction = "like" | "favorite";
const actionSuccess = ref("");
const lastIntent = ref<{ kind: Interaction; active: boolean }>();
const reloadNeeded = ref(false);

const articleId = computed(() => Number(route.params.id));
const discovery = computed(() => {
  const from = route.query.from;
  return typeof from === "string" && /^\/(?:\?[^#]*)?$/.test(from) ? from : "/";
});
const article = ref<Detail>();
const error = ref("");
const actionError = ref("");
const busy = ref(false);
const interaction = ref<{ liked: boolean; favorited: boolean }>();
const author = ref<AuthorSummary>();
const authorError = ref("");
const authorLoading = ref(false);
const countError = ref("");
const acting = ref<Interaction>();
let generation = 0;
let interactionGeneration = 0;
async function load() {
  const current = ++generation;
  const id = articleId.value;
  article.value = undefined;
  interaction.value = undefined;
  actionError.value = "";
  countError.value = "";
  acting.value = undefined;
  actionSuccess.value = "";
  lastIntent.value = undefined;
  reloadNeeded.value = false;
  busy.value = true;
  error.value = "";
  try {
    const value = await get<Detail>(`/articles/${id}`);
    if (current !== generation) return;
    article.value = value;
    const bookmark = route.query.reading;
    if (typeof bookmark === "string") {
      await nextTick();
      if (current === generation)
        requestAnimationFrame(() => {
          if (current !== generation) return;
          if (/^section-\d+$/.test(bookmark)) markdown.value?.jump(bookmark);
          else if (/^\d{1,7}$/.test(bookmark))
            window.scrollTo(0, Number(bookmark));
        });
    }
    void loadInteraction();
    void loadAuthor();
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function loadAuthor() {
  const current = generation;
  const epoch = session.epoch;
  author.value = undefined;
  authorError.value = "";
  authorLoading.value = false;
  if (session.status !== "ready" || !article.value) return;
  authorLoading.value = true;
  try {
    const value = await get<AuthorSummary>(
      `/articles/${articleId.value}/author`,
      true,
    );
    if (current === generation && epoch === session.epoch) author.value = value;
  } catch (e) {
    if (
      current === generation &&
      epoch === session.epoch &&
      !(e instanceof ApiError && e.status === 404)
    )
      authorError.value = message(e);
  } finally {
    if (current === generation && epoch === session.epoch)
      authorLoading.value = false;
  }
}
async function refreshCoinCount() {
  const current = generation;
  countError.value = "";
  try {
    const value = await get<Detail>(`/articles/${articleId.value}`);
    if (current === generation && article.value)
      article.value.counts.effectiveCoins = value.counts.effectiveCoins;
  } catch (e) {
    if (current === generation) countError.value = message(e);
  }
}
async function loadInteraction() {
  const current = generation;
  const read = ++interactionGeneration;
  if (session.status !== "ready" || !article.value) {
    interaction.value = undefined;
    confirmationRead.value = false;
    acting.value = undefined;
    lastIntent.value = undefined;
    return;
  }
  confirmationRead.value = true;
  try {
    const value = await get<NonNullable<typeof interaction.value>>(
      `/articles/${articleId.value}/interaction-state`,
      true,
    );
    if (current !== generation || read !== interactionGeneration) return;
    interaction.value = value;
    reloadNeeded.value = false;
    actionError.value = "";
    const intent = lastIntent.value;
    if (intent) {
      const actual = intent.kind === "like" ? value.liked : value.favorited;
      if (actual === intent.active) {
        actionSuccess.value =
          intent.kind === "like"
            ? actual
              ? "已确认点赞。"
              : "已确认取消点赞。"
            : actual
              ? "已确认收藏。"
              : "已确认取消收藏。";
        lastIntent.value = undefined;
      } else {
        actionError.value = "已重新读取互动状态，本次修改未生效，可以重试。";
      }
    }
  } catch (e) {
    if (current === generation && read === interactionGeneration) {
      reloadNeeded.value = true;
      actionError.value =
        (lastIntent.value ? "结果待确认，请重新读取互动状态。 " : "") +
        message(e);
    }
  } finally {
    if (current === generation && read === interactionGeneration)
      confirmationRead.value = false;
  }
}
async function toggle(kind: Interaction) {
  if (
    !interaction.value ||
    acting.value ||
    confirmationRead.value ||
    reloadNeeded.value
  )
    return;
  const current = generation;
  const epoch = session.epoch;
  const id = articleId.value;
  acting.value = kind;
  actionError.value = "";
  actionSuccess.value = "";
  const active =
    kind === "like" ? !interaction.value.liked : !interaction.value.favorited;
  lastIntent.value = { kind, active };
  try {
    const receipt = await send<{ effectiveLikes?: number }>(
      `/articles/${id}/${kind}`,
      active ? "PUT" : "DELETE",
    );
    if (current !== generation || epoch !== session.epoch) return;
    if (article.value && typeof receipt.effectiveLikes === "number")
      article.value.counts.effectiveLikes = receipt.effectiveLikes;
  } catch (e) {
    if (current !== generation || epoch !== session.epoch) return;
    if (e instanceof ApiError && e.status < 500) {
      lastIntent.value = undefined;
      actionError.value = (e.status === 403 ? "权限不足。 " : "") + message(e);
      acting.value = undefined;
      return;
    }
  }
  try {
    if (current === generation && epoch === session.epoch)
      await loadInteraction();
  } finally {
    if (current === generation) acting.value = undefined;
  }
}
function loginAtPosition() {
  const reading =
    markdown.value?.locateSection() || String(Math.round(window.scrollY));
  const next = router.resolve({
    path: route.path,
    query: { ...route.query, reading },
  }).fullPath;
  void router.push({ path: "/account", query: { next } });
}
function scrollTo(id: string) {
  const target = document.getElementById(id);
  if (!target) return;
  target.scrollIntoView({ block: "start" });
  target.setAttribute("tabindex", "-1");
  target.focus({ preventScroll: true });
}
watch(articleId, load, { immediate: true });
const readingError = ref("");
async function startReading() {
  if (!article.value || session.status !== "ready") return;
  readingError.value = "";
  try {
    await send(`/articles/${articleId.value}/reading-session`, "POST");
  } catch (e) {
    if (session.status === "ready") readingError.value = message(e);
  }
}
watch([article, () => session.status], startReading);
watch([() => session.epoch, () => session.status], () => {
  void loadInteraction();
  void loadAuthor();
});
</script>
<template>
  <div class="reading-page">
    <nav class="breadcrumb" aria-label="位置">
      <RouterLink :to="discovery">‹ 返回发现</RouterLink>
    </nav>
    <section v-if="busy" class="panel state-panel" role="status">
      正在读取全文…
    </section>
    <section v-else-if="error" class="panel state-panel" role="alert">
      <h1>文章暂时无法阅读</h1>
      <p>{{ error }}</p>
      <button @click="load">重新读取</button
      ><RouterLink class="button" :to="discovery">返回发现</RouterLink>
    </section>
    <div v-else-if="article" class="reading-layout">
      <article
        class="panel reading-article"
        :style="{ '--reading-actions-height': actionHeight + 'px' }"
      >
        <header class="article-heading">
          <h1>{{ article.title }}</h1>
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
            ><span v-if="session.status === 'ready'">已认证</span
            ><span>首次发布 {{ dateText(article.publishedAt) }}</span
            ><span>更新 {{ dateText(article.updatedAt) }}</span
            ><span
              >{{ article.counts.effectiveLikes }} 个赞 ·
              {{ article.counts.effectiveCoins }} 硬币认可</span
            >
          </p>
          <p class="summary">{{ article.summary }}</p>
        </header>
        <MarkdownView ref="markdown" :body="article.body" toc />
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
        <div v-if="typeof route.query.from === 'string'" class="reading-return">
          <RouterLink class="button" :to="discovery">返回结果列表</RouterLink>
        </div>
        <ArticleComments
          v-if="session.status === 'ready'"
          :key="`${articleId}:${session.epoch}:${route.query.thread || ''}:${route.query.comment || ''}`"
          :article-id="articleId"
          id="article-comments"
          class="article-comments"
        />
        <section v-else id="article-comments" class="article-comments guest-comments">
          <h2>评论</h2>
          <p class="hint">登录并认证后可查看评论与回复。</p>
          <button class="text-button" @click="loginAtPosition">登录后查看评论</button>
        </section>
      </article>
      <aside class="reading-sidebar">
        <section class="toc-card" aria-label="文章目录">
          <h2>文章目录</h2>
          <div class="toc-list">
            <button
              v-for="h in markdown?.headings ?? []"
              :key="h.id"
              class="toc-item"
              :class="{ 'is-level-3': h.level >= 3 }"
              :aria-current="h.id === markdown?.currentHeading ? 'location' : undefined"
              :title="h.text"
              @click="markdown?.jump(h.id)"
            >
              {{ h.text }}
            </button>
          </div>
        </section>
        <section class="reading-tip">
          <h2>阅读提示</h2>
          <p>对照当时的基础、经历阶段与内容来源，找到适合自己的部分。</p>
        </section>
        <template v-if="session.status === 'ready'">
          <section id="article-coins" class="panel reading-side-card" aria-label="投币">
            <h2>投币</h2>
            <ArticleCoins
              :key="`${articleId}:${session.epoch}`"
              :article-id="articleId"
              :article-title="article.title"
              @confirmed="refreshCoinCount"
            />
            <p v-if="countError" class="error" role="alert">
              公开币数刷新失败。{{ countError }}
              <button class="text-button" @click="refreshCoinCount">重试</button>
            </p>
          </section>
          <div class="reading-side-links">
            <RouterLink
              v-if="author"
              :to="`/authors/${author.authorId}`"
              >{{ author.nickname }} · 作者主页</RouterLink
            >
            <span v-else-if="authorLoading" class="hint" role="status"
              >正在读取作者资料…</span
            >
            <span v-else-if="authorError" class="hint" role="alert"
              >{{ authorError }}
              <button class="text-button" @click="loadAuthor">重新读取作者</button></span
            >
            <span v-else class="hint">当前文章没有可访问的作者主页。</span>
            <RouterLink
              :to="{
                path: '/me/cases/new',
                query: { type: 'ARTICLE', target: articleId },
              }"
              >举报这篇经验</RouterLink
            >
            <p v-if="readingError" class="hint" role="status">
              阅读奖励计时未启动。{{ readingError }}
              <button class="text-button" @click="startReading">重试计时</button>
            </p>
          </div>
        </template>
      </aside>
    </div>
    <div
      v-if="article && !busy"
      ref="actionBar"
      class="reading-bar"
      aria-label="文章互动"
    >
      <template v-if="session.status === 'ready'">
        <p class="sr-only" aria-live="polite">
          {{ article.counts.effectiveLikes }} 个赞，
          {{ article.counts.effectiveCoins }} 硬币认可<span v-if="interaction?.liked"
            >，已点赞</span
          >
        </p>
        <div class="reading-bar-actions">
          <button
            :disabled="!!acting || confirmationRead || reloadNeeded || !interaction"
            :aria-pressed="interaction?.liked || false"
            @click="toggle('like')"
          >
            {{ acting === "like" ? "处理中…" : interaction?.liked ? "已点赞" : "点赞" }}
          </button>
          <button
            :disabled="!!acting || confirmationRead || reloadNeeded || !interaction"
            :aria-pressed="interaction?.favorited || false"
            @click="toggle('favorite')"
          >
            {{ acting === "favorite" ? "处理中…" : interaction?.favorited ? "已收藏" : "收藏" }}
          </button>
          <button @click="scrollTo('article-coins')">投币</button>
          <button @click="scrollTo('article-comments')">查看评论</button>
          <RouterLink
            v-if="author"
            class="reading-bar-link"
            :to="`/authors/${author.authorId}`"
            >作者主页</RouterLink
          >
        </div>
      </template>
      <template v-else>
        <p>游客可读完整正文。登录并认证后，可查看作者、收藏、评论与投币。</p>
        <div class="reading-bar-actions">
          <button class="primary" @click="loginAtPosition">登录后参与互动</button>
        </div>
      </template>
      <div v-if="session.status === 'ready'" class="reading-bar-feedback" aria-live="polite">
        <p v-if="actionError" class="error" role="alert">
          {{ actionError }}
          <button class="text-button" :disabled="confirmationRead" @click="loadInteraction">
            重读互动状态
          </button>
        </p>
        <p v-else-if="actionSuccess" class="success" role="status">{{ actionSuccess }}</p>
      </div>
    </div>
  </div>
</template>
