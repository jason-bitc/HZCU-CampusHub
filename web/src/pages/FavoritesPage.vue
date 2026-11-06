<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { get, message, send } from "../lib/api";
import { session } from "../lib/session";
import { favoritesPage, lastFavoritesPage } from "../lib/favorites";
import type { Favorite, Page } from "../types";
import MemberGate from "../components/MemberGate.vue";
import { dateText } from "../lib/content";
const route = useRoute();
const router = useRouter();
const result = ref<Page<Favorite>>();
const busy = ref(false);
const changing = ref(false);
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
  result.value = undefined;
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
  changing.value = true;
  error.value = "";
  notice.value = "";
  try {
    await send(`/articles/${id}/favorite`, "DELETE");
    notice.value = "已取消收藏。";
    await load();
  } catch (e) {
    error.value = `${message(e)} 取消结果尚未确认，可刷新收藏或再次取消。`;
  } finally {
    changing.value = false;
  }
}
function turn(page: number) {
  void router.push({
    path: "/favorites",
    query: { ...route.query, page: String(page) },
  });
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
      <button :disabled="busy || changing" @click="load">刷新收藏</button>
    </div>
    <nav class="collection-tabs" aria-label="工作区分类">
      <RouterLink
        v-for="item in [
          { text: '公开经验', state: 'PUBLISHED' },
          { text: '草稿', state: 'DRAFT' },
          { text: '自主下架', state: 'AUTHOR_OFFLINE' },
          { text: '管理员隐藏', state: 'ADMIN_HIDDEN' },
        ]"
        :key="item.state"
        :to="{ path: '/workspace', query: { state: item.state } }"
        >{{ item.text }}</RouterLink
      ><RouterLink class="active" to="/favorites">收藏</RouterLink>
    </nav>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <section v-if="error" class="panel" role="alert">
      <h2>收藏暂时读取或操作失败</h2>
      <p>{{ error }}</p>
      <button :disabled="busy || changing" @click="load">重试当前页</button>
    </section>
    <p v-if="busy" role="status">正在读取第 {{ currentPage() }} 页收藏…</p>
    <section v-if="result" class="favorites-panel">
      <div class="favorites-toolbar">
        <span>共 {{ result.total }} 条收藏 · 按最近收藏时间排序</span
        ><label
          >每页<select
            aria-label="每页收藏条数"
            :value="currentSize()"
            :disabled="busy || changing"
            @change="resize(($event.target as HTMLSelectElement).value)"
          >
            <option :value="5">5 条</option>
            <option :value="10">10 条</option>
            <option :value="20">20 条</option>
          </select></label
        >
      </div>
      <div v-if="result.items.length" class="favorites-table-wrap">
        <table class="favorites-table">
          <thead>
            <tr>
              <th scope="col">内容</th>
              <th scope="col">当前状态</th>
              <th scope="col">最后更新</th>
              <th scope="col">允许的操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in result.items" :key="item.articleId">
              <td>
                <RouterLink
                  v-if="item.available"
                  :to="`/articles/${item.articleId}`"
                  >{{ item.article.title }}</RouterLink
                >
                <h2 v-else class="favorite-unavailable-title">
                  此收藏暂不可用
                </h2>
              </td>
              <td>{{ item.available ? "可阅读" : "内容不可用" }}</td>
              <td>
                {{ item.available ? dateText(item.article.updatedAt) : "—" }}
              </td>
              <td>
                <RouterLink
                  v-if="item.available"
                  :to="`/articles/${item.articleId}`"
                  >继续阅读</RouterLink
                ><button
                  class="text-button"
                  :disabled="busy || changing"
                  @click="remove(item.articleId)"
                >
                  取消收藏
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <section v-else-if="!busy" class="state-panel">
        <h2>还没有收藏的经验</h2>
        <p>在经验列表或正文中点击收藏，之后可在这里继续阅读。</p>
        <RouterLink class="button primary" to="/">去发现经验</RouterLink>
      </section>
      <nav
        v-if="result.total > result.size"
        class="pagination"
        aria-label="收藏翻页"
      >
        <button
          :disabled="busy || changing || currentPage() <= 1"
          @click="turn(currentPage() - 1)"
        >
          上一页</button
        ><span
          >第 {{ currentPage() }} /
          {{ lastFavoritesPage(result.total, result.size) }} 页</span
        ><button
          :disabled="
            busy ||
            changing ||
            currentPage() >= lastFavoritesPage(result.total, result.size)
          "
          @click="turn(currentPage() + 1)"
        >
          下一页
        </button>
      </nav>
    </section>
    <p v-if="result?.items.length" class="hint collection-footnote">
      文章不可用时仅保留收藏位置，不展示历史标题、摘要或正文；恢复公开后读取当前公开版本。
    </p></MemberGate
  >
</template>
