<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { get, message, send } from "../lib/api";
import { session } from "../lib/session";
import type { Favorite, Page } from "../types";
import MemberGate from "../components/MemberGate.vue";
import ArticleCard from "../components/ArticleCard.vue";
const result = ref<Page<Favorite>>();
const busy = ref(false);
const error = ref("");
const page = ref(1);
async function load() {
  if (session.status !== "ready") return;
  busy.value = true;
  error.value = "";
  try {
    result.value = await get(`/me/favorites?page=${page.value}&size=10`, true);
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function remove(id: number) {
  busy.value = true;
  error.value = "";
  try {
    await send(`/articles/${id}/favorite`, "DELETE");
    await load();
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
watch(() => session.status, load);
</script>
<template>
  <MemberGate title="登录后查看收藏"
    ><div class="page-heading">
      <div>
        <h1>我的收藏</h1>
        <p>文章下架或不可用时，仅保留收藏位置，不能读取原内容。</p>
      </div>
      <button :disabled="busy" @click="load">刷新收藏</button>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="busy" role="status">正在读取收藏…</p>
    <div
      v-for="item in result?.items"
      :key="item.articleId"
      class="favorite-row"
    >
      <ArticleCard v-if="item.available" :article="item.article" />
      <section v-else class="panel unavailable">
        <h2>此收藏暂不可用</h2>
        <p>文章已下架、隐藏或删除。</p>
      </section>
      <button
        class="text-button"
        :disabled="busy"
        @click="remove(item.articleId)"
      >
        取消收藏
      </button>
    </div>
    <section
      v-if="result && !result.items.length && !busy"
      class="panel state-panel"
    >
      <h2>还没有收藏的经验</h2>
      <p>看到有帮助的文章，可以收藏下来慢慢读。</p>
      <RouterLink class="button primary" to="/">去发现经验</RouterLink>
    </section>
    <nav
      v-if="result && result.total > result.size"
      class="pagination"
      aria-label="收藏翻页"
    >
      <button
        :disabled="busy || page <= 1"
        @click="
          page--;
          load();
        "
      >
        上一页</button
      ><span>第 {{ page }} 页</span
      ><button
        :disabled="busy || page * result.size >= result.total"
        @click="
          page++;
          load();
        "
      >
        下一页
      </button>
    </nav></MemberGate
  >
</template>
