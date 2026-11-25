<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { get, message, send } from "../lib/api";
import { session } from "../lib/session";
import { favoritesPage, lastFavoritesPage } from "../lib/favorites";
import type { Favorite, Page } from "../types";
import OperationFeedback from "../components/OperationFeedback.vue";
import { operationFailure, type Feedback } from "../lib/feedback";
import MemberGate from "../components/MemberGate.vue";
import ArticleCard from "../components/ArticleCard.vue";
const route = useRoute();
const router = useRouter();
const result = ref<Page<Favorite>>();
const busy = ref(false);
const changing = ref<number>();
const rowFeedback = ref<Record<number, Feedback>>({});
const error = ref("");
const notice = ref("");
let generation = 0;
function currentPage() {
  const p = Number(route.query.page || 1);
  return Number.isSafeInteger(p) && p > 0 && p <= 1000000 ? p : 1;
}
function currentSize() {
  const size = Number(route.query.size || 10);
  return [5, 10, 20].includes(size) ? size : 10;
}
function resize(size: string) {
  void router.push({ path: "/favorites", query: { size, page: "1" } });
}
async function load() {
  if (session.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  try {
    const value = favoritesPage(
      await get<Page<Favorite>>(
        `/me/favorites?page=${currentPage()}&size=${currentSize()}`,
        true,
      ),
    );
    if (current !== generation) return;
    const last = lastFavoritesPage(value.total, value.size);
    if (currentPage() > last) {
      await router.replace({
        path: "/favorites",
        query: { ...route.query, page: String(last) },
      });
      return;
    }
    result.value = value;
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function remove(id: number) {
  if (busy.value || changing.value) return;
  changing.value = id;
  delete rowFeedback.value[id];
  notice.value = "";
  try {
    await send(`/articles/${id}/favorite`, "DELETE");
    notice.value = "已取消收藏。";
    if (result.value?.items.some((item) => item.articleId === id)) {
      result.value.items = result.value.items.filter(
        (item) => item.articleId !== id,
      );
      result.value.total = Math.max(0, result.value.total - 1);
    }
    await load();
  } catch (e) {
    rowFeedback.value[id] = operationFailure(
      e,
      "取消结果待确认，可刷新收藏或再次取消。",
    );
  } finally {
    changing.value = undefined;
  }
}
function turn(page: number) {
  void router.push({
    path: "/favorites",
    query: { ...route.query, page: String(page) },
  });
}
const unavailableCount = computed(
  () => result.value?.items.filter((item) => !item.available).length ?? 0,
);
async function removeUnavailable() {
  const ids =
    result.value?.items
      .filter((item) => !item.available)
      .map((item) => item.articleId) ?? [];
  if (!ids.length || busy.value || changing.value) return;
  changing.value = ids[0];
  error.value = "";
  try {
    for (const id of ids) await send(`/articles/${id}/favorite`, "DELETE");
    notice.value = `已移除 ${ids.length} 条不可读收藏。`;
    await load();
  } catch (e) {
    error.value = `${message(e)} 可逐条移除，或刷新后重试。`;
  } finally {
    changing.value = undefined;
  }
}
function visibility() {
  if (document.visibilityState === "visible" && !busy.value && !changing.value)
    void load();
}
watch(() => [route.query.page, route.query.size], load);
watch(() => session.status, load);
onMounted(() => {
  void load();
  document.addEventListener("visibilitychange", visibility);
});
onBeforeUnmount(() => {
  generation++;
  document.removeEventListener("visibilitychange", visibility);
});
</script>
<template>
  <MemberGate title="登录后查看收藏"
    ><div class="page-heading">
      <div>
        <h1>我的收藏</h1>
        <p>收藏有帮助的经验，留给下一次阅读。</p>
      </div>
      <button
        class="text-button"
        :disabled="busy || !!changing"
        @click="load"
      >
        刷新收藏
      </button>
    </div>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <section v-if="error" class="panel" role="alert">
      <h2>收藏暂时读取或操作失败</h2>
      <p>{{ error }} <span v-if="result">当前显示的是上次读取结果。</span></p>
      <button :disabled="busy || !!changing" @click="load">重试当前页</button>
    </section>
    <p v-if="busy" role="status">正在读取第 {{ currentPage() }} 页收藏…</p>
    <div v-if="result" class="favorites-layout">
      <section class="favorites-panel favorites-stream" aria-label="收藏列表">
        <div v-if="result.items.length" class="favorites-list">
          <div
            v-for="item in result.items"
            :key="item.articleId"
            class="favorite-row"
          >
            <ArticleCard
              v-if="item.available"
              :article="item.article"
              show-favorite
            />
            <article v-else class="panel article-card favorite-unavailable">
              <div class="article-card-heading">
                <h2 class="favorite-unavailable-title">此收藏暂不可用</h2>
              </div>
              <p class="summary">内容不可读时仅保留收藏位置，不展示历史标题、摘要或正文。</p>
              <div class="article-card-footer">
                <p class="meta"><span>内容不可用</span></p>
                <div class="article-card-actions">
                  <button
                    class="text-button"
                    :disabled="busy || !!changing"
                    @click="remove(item.articleId)"
                  >
                    {{
                      changing === item.articleId ? "正在移除…" : "移除收藏"
                    }}
                  </button>
                </div>
              </div>
              <OperationFeedback :feedback="rowFeedback[item.articleId]" />
            </article>
          </div>
        </div>
        <section v-else-if="!busy" class="state-panel">
          <h2>还没有收藏的经验</h2>
          <p>在经验列表或正文中点击收藏，之后可在这里继续阅读。</p>
          <RouterLink class="button primary" to="/">去发现经验</RouterLink>
        </section>
        <div class="favorites-toolbar">
          <span aria-live="polite"
            >共 {{ result.total }} 条收藏 · 按最近收藏时间排序</span
          >
          <div class="favorites-tools">
            <label
              >每页<select
                aria-label="每页收藏条数"
                :value="currentSize()"
                :disabled="busy || !!changing"
                @change="resize(($event.target as HTMLSelectElement).value)"
              >
                <option :value="5">5 条</option>
                <option :value="10">10 条</option>
                <option :value="20">20 条</option>
              </select></label
            >
            <nav
              v-if="result.total > result.size"
              class="pagination"
              aria-label="收藏翻页"
            >
              <button
                aria-label="上一页"
                :disabled="busy || !!changing || currentPage() <= 1"
                @click="turn(currentPage() - 1)"
              >
                ‹</button
              ><span aria-current="page">{{ currentPage() }}</span
              ><button
                aria-label="下一页"
                :disabled="
                  busy ||
                  !!changing ||
                  currentPage() >= lastFavoritesPage(result.total, result.size)
                "
                @click="turn(currentPage() + 1)"
              >
                ›
              </button>
            </nav>
          </div>
        </div>
      </section>
      <aside class="favorites-aside">
        <p v-if="unavailableCount" class="notice" role="status">
          {{ unavailableCount }} 条收藏当前不可读，可从列表移除。
        </p>
        <button
          v-if="unavailableCount"
          :disabled="busy || !!changing"
          @click="removeUnavailable"
        >
          移除失效项
        </button>
        <p class="hint collection-footnote">
          文章不可用时仅保留收藏位置，不展示历史标题、摘要或正文；恢复公开后读取当前公开版本。
        </p>
      </aside>
    </div></MemberGate
  >
</template>
