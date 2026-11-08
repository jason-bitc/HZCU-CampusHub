<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { LocationQueryRaw } from "vue-router";
import { get, message } from "../lib/api";
import type { Article, Catalog, Page, Profile } from "../types";
import ArticleCard from "../components/ArticleCard.vue";
import { session } from "../lib/session";
const route = useRoute();
const router = useRouter();
const catalog = ref<Catalog>();
const result = ref<Page<Article>>();
const profile = ref<Profile>();
const busy = ref(false);
const error = ref("");
const profileBusy = ref(false);
const profileError = ref("");
const keyword = ref("");
let generation = 0;
let profileGeneration = 0;
const groups = [
  { key: "jobs", param: "jobId", label: "目标岗位" },
  { key: "majors", param: "majorId", label: "文章适用专业" },
  { key: "companies", param: "companyId", label: "公司" },
  { key: "topics", param: "topicId", label: "主题" },
] as const;
function selected(key: string) {
  const value = route.query[key];
  return typeof value === "string" ? value : "";
}
function change(key: string, value: string) {
  const query: LocationQueryRaw = { ...route.query, page: "1" };
  if (value) query[key] = value;
  else delete query[key];
  void router.push({ path: "/", query });
}
function search() {
  change("keyword", keyword.value.trim());
}
async function load() {
  const current = ++generation;
  busy.value = true;
  error.value = "";
  keyword.value = selected("keyword");
  const params = new URLSearchParams({
    size: "10",
    page: selected("page") || "1",
    sort: selected("sort") || "recognized",
  });
  for (const key of ["keyword", "jobId", "majorId", "companyId", "topicId"]) {
    const value = selected(key);
    if (value) params.set(key, value);
  }
  try {
    const [c, r] = await Promise.all([
      catalog.value || get<Catalog>("/catalog"),
      get<Page<Article>>(`/articles?${params}`),
    ]);
    if (current === generation) {
      catalog.value = c;
      result.value = r;
    }
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function loadOwnMajor() {
  const current = ++profileGeneration;
  profileError.value = "";
  if (session.status !== "ready") {
    profile.value = undefined;
    profileBusy.value = false;
    return;
  }
  profileBusy.value = true;
  try {
    const value = await get<Profile>("/me/profile", true);
    if (current === profileGeneration) profile.value = value;
  } catch (e) {
    if (current === profileGeneration) profileError.value = message(e);
  } finally {
    if (current === profileGeneration) profileBusy.value = false;
  }
}
function useSameMajor() {
  if (session.status !== "ready") {
    void router.push({ path: "/account", query: { next: route.fullPath } });
    return;
  }
  const majorId = profile.value?.majorId;
  if (!majorId) {
    void router.push("/me");
    return;
  }
  if (!catalog.value?.majors.some((major) => major.id === majorId)) {
    profileError.value = "本人专业当前不在可筛选分类中，请手动选择。";
    return;
  }
  change("majorId", String(majorId));
}
onMounted(load);
onMounted(loadOwnMajor);
watch(() => route.query, load);
watch(() => session.status, loadOwnMajor);
onBeforeUnmount(() => {
  generation += 1;
  profileGeneration += 1;
});
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>按目标岗位，找到适合自己的经验</h1>
      <p>从相似的起点出发，看看准备、投递与复盘。</p>
    </div>
  </div>
  <div class="discovery-layout discovery-single">
    <div>
      <section class="panel filters" aria-label="筛选经验">
        <form class="search-form" role="search" @submit.prevent="search">
          <label class="sr-only" for="search">搜索文章标题或摘要</label
          ><input
            id="search"
            v-model="keyword"
            type="search"
            placeholder="搜索文章标题或摘要"
          /><button class="primary" type="submit">搜索</button>
        </form>
        <div v-if="catalog" class="filter-grid">
          <label v-for="group in groups" :key="group.key"
            >{{ group.label
            }}<select
              :value="selected(group.param)"
              @change="
                change(group.param, ($event.target as HTMLSelectElement).value)
              "
            >
              <option value="">全部</option>
              <option
                v-for="item in catalog[group.key]"
                :key="item.id"
                :value="String(item.id)"
              >
                {{ item.name }}
              </option>
            </select></label
          >
        </div>
        <div class="same-major">
          <p v-if="session.status !== 'ready'">
            游客可手动选择专业；登录并填写本人专业后可用同专业快捷筛选。
          </p>
          <p v-else-if="profileBusy">正在读取本人专业，仍可手动筛选。</p>
          <p v-else-if="profileError">
            本人专业读取失败或不可用，仍可手动筛选。{{ profileError }}
          </p>
          <p v-else-if="!profile?.majorId">
            尚未填写本人专业，可继续手动选择。
          </p>
          <p v-else>按本人资料中的专业筛选文章适用背景，仍可手动调整。</p>
          <button
            v-if="session.status !== 'ready'"
            :disabled="session.status === 'checking'"
            @click="useSameMajor"
          >
            登录后使用同专业
          </button>
          <button v-else-if="profileBusy" disabled>正在读取本人专业</button>
          <button v-else-if="profileError" @click="loadOwnMajor">
            重试读取本人专业
          </button>
          <RouterLink v-else-if="!profile?.majorId" class="button" to="/me">
            完善本人专业
          </RouterLink>
          <button
            v-else
            :aria-pressed="selected('majorId') === String(profile.majorId)"
            @click="useSameMajor"
          >
            只看同专业
          </button>
        </div>
        <div class="filter-bottom">
          <div class="segmented" aria-label="排序">
            <button
              :aria-pressed="selected('sort') !== 'latest'"
              @click="change('sort', 'recognized')"
            >
              认可优先</button
            ><button
              :aria-pressed="selected('sort') === 'latest'"
              @click="change('sort', 'latest')"
            >
              最新发布
            </button>
          </div>
          <button
            class="text-button"
            :disabled="!Object.keys(route.query).length"
            @click="router.push('/')"
          >
            清除所有条件
          </button>
        </div>
      </section>
      <div class="result-heading" aria-live="polite">
        <span>{{
          busy
            ? "正在查找经验…"
            : error
              ? "当前条件读取失败"
              : result
                ? `找到 ${result.total} 篇经验`
                : "经验列表"
        }}</span
        ><span>全文免费阅读</span>
      </div>
      <section v-if="error" class="panel state-panel" role="alert">
        <h2>暂时无法读取经验</h2>
        <p>{{ error }}</p>
        <button @click="load">重试当前条件</button>
      </section>
      <template v-else-if="!busy"
        ><ArticleCard
          v-for="article in result?.items"
          :key="article.id"
          :article="article"
          show-favorite
        />
        <section
          v-if="result && !result.items.length"
          class="panel state-panel"
        >
          <h2>当前条件下还没有经验</h2>
          <p>可以减少筛选条件，或清除条件重新查找。</p>
          <button @click="router.push('/')">清除条件</button>
        </section></template
      >
      <div v-else class="panel state-panel" role="status">
        <p>正在读取公开文章，请稍候。</p>
      </div>
      <nav
        v-if="result && result.total > result.size && !busy && !error"
        class="pagination"
        aria-label="经验翻页"
      >
        <button
          :disabled="result.page <= 1"
          @click="change('page', String(result.page - 1))"
        >
          上一页</button
        ><span
          >第 {{ result.page }} /
          {{ Math.ceil(result.total / result.size) }} 页</span
        ><button
          :disabled="result.page * result.size >= result.total"
          @click="change('page', String(result.page + 1))"
        >
          下一页
        </button>
      </nav>
    </div>
  </div>
</template>
