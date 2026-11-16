<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import ArticleCard from "../components/ArticleCard.vue";
import AuthorHonor from "../components/AuthorHonor.vue";
import MemberGate from "../components/MemberGate.vue";
import { get, message } from "../lib/api";
import { safeLink } from "../lib/content";
import { session } from "../lib/session";
import type { Article, Author, AuthorContact, Page } from "../types";

const route = useRoute();
const id = computed(() => Number(route.params.id));
const author = ref<Author>();
const articles = ref<Page<Article>>();
const contact = ref<AuthorContact>();
const busy = ref(false);
const contactBusy = ref(false);
const error = ref("");
const contactError = ref("");
const copied = ref(false);
let generation = 0;
let contactRound = 0;
async function load(page = 1) {
  const current = ++generation;
  contact.value = undefined;
  contactBusy.value = false;
  contactError.value = "";
  copied.value = false;
  author.value = undefined;
  articles.value = undefined;
  error.value = "";
  busy.value = false;
  if (session.status !== "ready") return;
  busy.value = true;
  try {
    const [profile, list] = await Promise.all([
      get<Author>(`/authors/${id.value}`, true),
      get<Page<Article>>(
        `/authors/${id.value}/articles?page=${page}&size=20`,
        true,
      ),
    ]);
    if (current !== generation) return;
    author.value = profile;
    articles.value = list;
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function readContact(copy = false) {
  if (!author.value || contactBusy.value) return;
  const current = generation;
  const round = ++contactRound;
  contact.value = undefined;
  contactError.value = "";
  copied.value = false;
  contactBusy.value = true;
  try {
    const value = await get<AuthorContact>(
      `/authors/${id.value}/contact`,
      true,
    );
    if (current !== generation || round !== contactRound || document.hidden)
      return;
    contact.value = value.enabled
      ? { enabled: true, type: value.type, value: value.value }
      : { enabled: false };
    if (copy && value.enabled && value.type !== "WEB") {
      await navigator.clipboard.writeText(value.value);
      if (current === generation) copied.value = true;
    }
  } catch (e) {
    if (current === generation) contactError.value = message(e);
  } finally {
    if (current === generation) contactBusy.value = false;
  }
}
function clearContact() {
  if (!document.hidden) return;
  contactRound++;
  contact.value = undefined;
  copied.value = false;
}
document.addEventListener("visibilitychange", clearContact);
watch([id, () => session.status, () => session.epoch], () => void load(), {
  immediate: true,
});
onBeforeUnmount(() => {
  generation++;
  document.removeEventListener("visibilitychange", clearContact);
});
</script>
<template>
  <MemberGate title="登录后查看作者资料">
    <p v-if="busy" class="panel state-panel" role="status">正在读取作者资料…</p>
    <section v-else-if="error" class="panel state-panel" role="alert">
      <h1>作者主页暂时无法访问</h1>
      <p>{{ error }}</p>
      <button @click="load()">重新读取</button>
    </section>
    <template v-else-if="author && articles">
      <section class="panel state-panel">
        <h1>{{ author.nickname }}</h1>
        <p class="meta">
          <span>校园邮箱已验证</span>
          <span v-if="author.major">{{ author.major.name }}</span>
          <span v-if="author.currentStage === 'ENROLLED'">在校</span>
          <span v-if="author.currentStage === 'GRADUATED'">已毕业</span>
          <AuthorHonor v-if="author.honor" :honor="author.honor" />
        </p>
        <p v-if="author.bio">{{ author.bio }}</p>
        <button :disabled="contactBusy" @click="readContact()">
          查看作者联系方式
        </button>
        <p v-if="contactBusy" role="status">正在核对授权…</p>
        <p v-if="contactError" class="error" role="alert">{{ contactError }}</p>
        <p v-if="contact && !contact.enabled" class="hint" role="status">
          作者当前未公开联系方式。
        </p>
        <div v-else-if="contact?.enabled">
          <p class="hint">联系方式由作者自愿提供，请自行核验站外信息。</p>
          <template v-if="contact.type === 'WEB'">
            <a
              v-if="safeLink(contact.value)"
              :href="safeLink(contact.value)"
              target="_blank"
              rel="noopener noreferrer"
              >打开作者提供的站外链接</a
            >
            <p v-else class="error">链接格式不支持。</p>
          </template>
          <p v-else>
            {{ contact.type === "WECHAT" ? "微信" : "QQ" }} ·
            {{ contact.value }}
            <button
              class="text-button"
              :disabled="contactBusy"
              @click="readContact(true)"
            >
              重新核对并复制
            </button>
          </p>
          <p v-if="copied" role="status">已复制联系方式。</p>
        </div>
      </section>
      <h2>公开经验 · {{ author.publicArticleCount }} 篇</h2>
      <ArticleCard
        v-for="article in articles.items"
        :key="article.id"
        :article="article"
      />
      <nav
        v-if="articles.total > articles.size"
        class="pagination"
        aria-label="作者文章翻页"
      >
        <button
          :disabled="articles.page === 1"
          @click="load(articles.page - 1)"
        >
          上一页
        </button>
        <span>第 {{ articles.page }} 页</span>
        <button
          :disabled="articles.page * articles.size >= articles.total"
          @click="load(articles.page + 1)"
        >
          下一页
        </button>
      </nav>
    </template>
  </MemberGate>
</template>
