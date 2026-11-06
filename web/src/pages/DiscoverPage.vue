<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { LocationQueryRaw } from "vue-router";
import { get, message } from "../lib/api";
import type { Article, Catalog, Page } from "../types";
import ArticleCard from "../components/ArticleCard.vue";
const route = useRoute();
const router = useRouter();
const catalog = ref<Catalog>();
const result = ref<Page<Article>>();
const busy = ref(false);
const error = ref("");
const keyword = ref("");
let generation = 0;
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
onMounted(load);
watch(() => route.query, load);
onBeforeUnmount(() => (generation += 1));
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
