<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, get, message, send } from "../lib/api";
import { session } from "../lib/session";
import { dateText, safeLink, sourceName } from "../lib/content";
import type { AuthorSummary, Detail } from "../types";
import MarkdownView from "../components/MarkdownView.vue";
import AuthorHonor from "../components/AuthorHonor.vue";
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
  <div class="breadcrumb">
    <RouterLink :to="discovery">发现经验</RouterLink><span>/</span
    ><span>阅读正文</span>
  </div>
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
        <div v-if="session.status === 'ready'" class="meta">
          <RouterLink v-if="author" :to="`/authors/${author.authorId}`"
            >{{ author.nickname }} · 作者主页</RouterLink
          >
          <AuthorHonor v-if="author?.honor" :honor="author.honor" />
          <span v-if="authorLoading" role="status">正在读取作者资料…</span>
          <span v-else-if="authorError" role="alert"
            >{{ authorError }}
            <button class="text-button" @click="loadAuthor">
              重新读取作者
            </button></span
          >
          <span v-else-if="!author" class="hint"
            >当前文章没有可访问的作者主页。</span
          >
        </div>
      </header>
      <section ref="actionBar" class="reading-actions" aria-label="文章互动">
        <h2>这篇经验有帮助吗</h2>
        <p class="muted">
          {{ article.counts.effectiveLikes }} 个赞 ·
          {{ article.counts.effectiveCoins }} 硬币认可
        </p>
        <template v-if="session.status === 'ready'"
          ><div class="reading-action-buttons">
            <button
              :disabled="
                !!acting || confirmationRead || reloadNeeded || !interaction
              "
              :aria-pressed="interaction?.liked || false"
              @click="toggle('like')"
            >
              {{
                acting === "like"
                  ? "正在处理点赞…"
                  : interaction?.liked
                    ? "已点赞 · 取消"
                    : "赞同这篇经验"
              }}</button
            ><button
              :disabled="
                !!acting || confirmationRead || reloadNeeded || !interaction
              "
              :aria-pressed="interaction?.favorited || false"
              @click="toggle('favorite')"
            >
              {{
                acting === "favorite"
                  ? "正在处理收藏…"
                  : interaction?.favorited
                    ? "已收藏 · 取消"
                    : "收藏，稍后阅读"
              }}
            </button>
          </div>
          <div class="reading-action-feedback">
            <p v-if="actionError" class="error" role="alert">
              {{ actionError }}
              <button
                class="text-button"
                :disabled="confirmationRead"
                @click="loadInteraction"
              >
                重读互动状态
              </button>
            </p>
            <p v-if="confirmationRead && !acting" class="hint" role="status">
              正在读取互动状态…
            </p>
            <p v-if="actionSuccess" class="success" role="status">
              {{ actionSuccess }}
            </p>
          </div>
        </template>
        <button v-else class="primary" @click="loginAtPosition">
          登录校园账号参与互动
        </button>
      </section>
      <MarkdownView ref="markdown" :body="article.body" toc>
        <template #toc-action>
          <button
            v-if="session.status !== 'ready'"
            class="text-button toc-login"
            @click="loginAtPosition"
          >
            登录后继续阅读
          </button>
        </template>
      </MarkdownView>
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
      />
    </article>
    <aside class="reading-sidebar">
      <section v-if="session.status === 'ready'" class="panel">
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
      <section class="panel">
        <h2>阅读提示</h2>
        <p>对照当时的基础、经历阶段与内容来源，找到适合自己的部分。</p>
      </section>
      <div v-if="session.status === 'ready'" class="panel stack">
        <RouterLink
          :to="{
            path: '/me/cases/new',
            query: { type: 'ARTICLE', target: articleId },
          }"
          >举报这篇经验</RouterLink
        >
        <p v-if="readingError" class="hint" role="status">
          阅读奖励计时未启动。{{ readingError }}
          <button @click="startReading">重试计时</button>
        </p>
      </div>
    </aside>
  </div>
</template>
