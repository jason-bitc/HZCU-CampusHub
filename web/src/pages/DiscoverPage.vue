<script lang="ts">
let readingOrigin: { path: string; id: string; top: number } | undefined;
</script>
<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import type { LocationQueryRaw } from "vue-router";
import { get, message } from "../lib/api";
import type { Article, Catalog, Page, Profile } from "../types";
import ArticleCard from "../components/ArticleCard.vue";
import FilterSelect from "../components/FilterSelect.vue";
import { session } from "../lib/session";
const route = useRoute();
const router = useRouter();
const catalog = ref<Catalog>();
const result = ref<Page<Article>>();
const resultPath = ref("/");
const profile = ref<Profile>();
const busy = ref(false);
const error = ref("");
const profileBusy = ref(false);
const profileError = ref("");
const keyword = ref(selected("keyword"));
const catalogError = ref("");
const resultHeading = ref<HTMLElement>();
let generation = 0;
let profileGeneration = 0;
let revealPage = false;
const groups = [
  { key: "jobs", param: "jobId", label: "目标岗位" },
  { key: "majors", param: "majorId", label: "文章适用专业" },
  { key: "companies", param: "companyId", label: "公司" },
  { key: "topics", param: "topicId", label: "主题" },
] as const;
const applied = computed(() => {
  const search = selected("keyword");
  const filters = search ? [{ param: "keyword", text: `搜索 ${search}` }] : [];
  for (const { key, param, label } of groups) {
    const id = selected(param);
    if (id) {
      const match = catalog.value?.[key].find((item) => String(item.id) === id);
      filters.push({ param, text: `${label} ${match?.name ?? "已选"}` });
    }
  }
  return filters;
});
// 筛选卡里的下拉先记在 draft，点“应用筛选”再一次性写入地址栏。
const draft = ref<Record<string, string>>(
  Object.fromEntries(groups.map(({ param }) => [param, selected(param)])),
);
function syncDraft() {
  for (const { param } of groups) draft.value[param] = selected(param);
}
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
function applyFilters() {
  const query: LocationQueryRaw = { ...route.query, page: "1" };
  for (const { param } of groups) {
    if (draft.value[param]) query[param] = draft.value[param];
    else delete query[param];
  }
  void router.push({ path: "/", query });
}
function search() {
  keyword.value = keyword.value.trim();
  change("keyword", keyword.value);
}
function clearFilters() {
  keyword.value = "";
  const sort = selected("sort");
  void router.push({ path: "/", query: sort ? { sort } : {} });
}
async function loadCatalog() {
  catalogError.value = "";
  try {
    catalog.value = await get<Catalog>("/catalog");
  } catch {
    catalogError.value = "筛选选项暂时无法加载。";
  }
}
function changePage(page: number) {
  revealPage = true;
  change("page", String(page));
}
async function load() {
  const current = ++generation;
  busy.value = true;
  error.value = "";
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
    const r = await get<Page<Article>>(`/articles?${params}`);
    if (current === generation) {
      result.value = r;
      resultPath.value = route.fullPath;
    }
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) {
      busy.value = false;
      await nextTick();
      if (current !== generation) return;
      if (revealPage) {
        revealPage = false;
        resultHeading.value?.focus();
        resultHeading.value?.scrollIntoView({ block: "start" });
      } else if (!error.value && readingOrigin?.path === route.fullPath) {
        const origin = readingOrigin;
        readingOrigin = undefined;
        const card = document.getElementById(`experience-${origin.id}`);
        if (card) {
          window.scrollBy(0, card.getBoundingClientRect().top - origin.top);
          card.querySelector("a")?.focus({ preventScroll: true });
        }
      }
    }
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
  change(
    "majorId",
    selected("majorId") === String(majorId) ? "" : String(majorId),
  );
}
onBeforeRouteLeave((to, from) => {
  const id = /^\/articles\/(\d+)$/.exec(to.path)?.[1];
  const card =
    id && to.query.from === from.fullPath
      ? document.getElementById(`experience-${id}`)
      : undefined;
  readingOrigin =
    id && card
      ? { id, path: from.fullPath, top: card.getBoundingClientRect().top }
      : undefined;
});
onMounted(load);
onMounted(loadCatalog);
onMounted(loadOwnMajor);
watch(
  () => route.path === "/" && route.query.keyword,
  () => {
    if (route.path === "/") keyword.value = selected("keyword");
  },
);
watch(
  () => route.query,
  () => {
    if (route.path === "/") {
      syncDraft();
      void load();
    }
  },
);
watch(() => session.status, loadOwnMajor);
onBeforeUnmount(() => {
  generation += 1;
  profileGeneration += 1;
});
</script>
<template>
  <div class="page-heading discovery-heading">
    <h1>发现经验</h1>
  </div>
  <div class="discovery-layout discovery-single">
    <div>
      <form class="search-form discovery-search" role="search" @submit.prevent="search">
        <label class="sr-only" for="search">搜索经验</label>
        <div class="search-control">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="m15.5 15.5 5 5" />
          </svg>
          <input
            id="search"
            v-model="keyword"
            type="search"
            maxlength="100"
            placeholder="搜索经验标题或摘要"
          />
          <button class="primary" type="submit">搜索</button>
        </div>
      </form>
      <section class="filters discovery-controls" aria-label="筛选经验">
        <div class="filter-toolbar">
          <FilterSelect
            v-for="group in groups"
            :key="group.key"
            :label="group.label === '文章适用专业' ? '专业' : group.label"
            :model-value="draft[group.param]"
            :options="catalog?.[group.key] ?? []"
            @update:model-value="draft[group.param] = $event"
          />
          <button class="primary filter-apply" type="button" @click="applyFilters">
            应用筛选
          </button>
        </div>
        <div class="filter-footer">
          <div class="same-major">
            <p>登录并认证后可使用同专业快捷筛选</p>
            <button
              v-if="session.status !== 'ready'"
              :disabled="session.status === 'checking'"
              @click="useSameMajor"
            >
              登录后筛同专业
            </button>
            <button v-else-if="profileBusy" disabled>正在读取专业</button>
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
          <button
            v-if="applied.length"
            class="text-button clear-filters"
            @click="clearFilters"
          >
            清除全部
          </button>
        </div>
        <p v-if="catalogError" class="hint" role="status">
          {{ catalogError }}
          <button class="text-button" @click="loadCatalog">重试分类</button>
        </p>
        <ul
          v-if="applied.length"
          class="applied-filters"
          aria-label="已应用的条件"
        >
          <li v-for="filter in applied" :key="filter.param">
            <button
              type="button"
              :aria-label="`移除 ${filter.text}`"
              @click="change(filter.param, '')"
            >
              {{ filter.text }}
              <svg viewBox="0 0 16 16" aria-hidden="true">
                <path d="m4 4 8 8m0-8-8 8" />
              </svg>
            </button>
          </li>
        </ul>
      </section>
      <div ref="resultHeading" class="result-heading" tabindex="-1">
        <h2>
          {{
            busy
              ? "正在查找经验…"
              : error
                ? "当前条件读取失败"
                : applied.length
                  ? "筛选结果"
                  : "公开经验"
          }}
        </h2>
        <div class="result-tools">
          <span class="free-reading">游客可读完整正文</span>
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
        </div>
      </div>
      <section v-if="error" class="panel state-panel" role="alert">
        <span class="state-icon" aria-hidden="true">!</span>
        <h2>暂时无法读取经验</h2>
        <p>{{ error }}</p>
        <p v-if="result?.items.length">
          下方显示的是上次读取成功的结果，当前条件尚未读到结果。
        </p>
        <button @click="load">重试当前条件</button>
      </section>
      <template v-if="!busy && (!error || result?.items.length)">
        <div v-if="result?.items.length" class="discovery-results">
          <ArticleCard
            v-for="article in result.items"
            :key="article.id"
            :back-to="resultPath"
            :article="article"
            show-favorite
          />
        </div>
        <section
          v-if="result && !result.items.length"
          class="panel state-panel"
        >
          <svg
            class="empty-icon"
            viewBox="0 0 48 48"
            fill="none"
            aria-hidden="true"
          >
            <circle cx="21" cy="21" r="12" />
            <path d="m30 30 9 9M15 21h12" />
          </svg>
          <h2>
            {{ applied.length ? "当前条件下还没有经验" : "还没有公开经验" }}
          </h2>
          <p>
            {{
              applied.length
                ? "可以减少筛选条件，或清除条件重新查找。"
                : "有新经验公开后会在这里显示。"
            }}
          </p>
          <button v-if="applied.length" @click="clearFilters">清除条件</button>
        </section>
      </template>
      <div v-if="busy" class="loading-list" role="status">
        <span class="sr-only">正在读取公开文章，请稍候。</span>
        <div
          v-for="index in 4"
          :key="index"
          class="panel article-skeleton"
          aria-hidden="true"
        >
          <span class="skeleton-line skeleton-title"></span>
          <span class="skeleton-line"></span>
          <span class="skeleton-line skeleton-short"></span>
        </div>
      </div>
      <div
        v-if="result && !busy"
        class="discovery-foot"
        aria-live="polite"
      >
        <span class="result-count">{{
          error
            ? "当前条件读取失败"
            : applied.length
              ? `当前结果 ${result.total} 篇`
              : `共 ${result.total} 篇公开经验`
        }}</span>
        <nav
          v-if="result.total > result.size"
          class="pagination"
          aria-label="经验翻页"
        >
          <button
            :aria-label="'上一页'"
            :disabled="result.page <= 1"
            @click="changePage(result.page - 1)"
          >
            ‹</button
          ><span aria-current="page">{{ result.page }}</span
          ><button
            :aria-label="'下一页'"
            :disabled="result.page * result.size >= result.total"
            @click="changePage(result.page + 1)"
          >
            ›
          </button>
        </nav>
      </div>
    </div>
  </div>
</template>
