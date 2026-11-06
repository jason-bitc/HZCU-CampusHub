<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { get, message, send } from "../lib/api";
import {
  readPrivate,
  removePrivate,
  session,
  writePrivate,
} from "../lib/session";
import { dateText, stateName } from "../lib/content";
import type { Owned, Page } from "../types";
import MemberGate from "../components/MemberGate.vue";
const router = useRouter();
const result = ref<Page<Owned>>();
const page = ref(1);
const busy = ref(false);
const error = ref("");
const filter = ref("ALL");
const pending = ref("");
const states = [
  "ALL",
  "DRAFT",
  "PUBLISHED",
  "AUTHOR_OFFLINE",
  "ADMIN_HIDDEN",
  "DELETED",
];
async function load() {
  if (session.status !== "ready") return;
  busy.value = true;
  error.value = "";
  try {
    pending.value = readPrivate<string>("create") || "";
    result.value = await get(`/me/articles?page=${page.value}&size=20`, true);
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function create() {
  busy.value = true;
  error.value = "";
  try {
    if (!pending.value) {
      const key = crypto.randomUUID();
      writePrivate("create", key);
      pending.value = key;
    }
    const r = await send<{ articleId: number }>("/me/articles", "POST", {
      requestId: pending.value,
    });
    removePrivate("create");
    pending.value = "";
    await router.push(`/editor/${r.articleId}`);
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function transition(item: Owned, action: "unpublish" | "restore") {
  if (
    !confirm(
      action === "restore"
        ? "恢复上次公开版本？当前草稿的修改不会随之发布。"
        : "下架后，其他读者将无法读取这篇文章。确认下架？",
    )
  )
    return;
  busy.value = true;
  error.value = "";
  try {
    await send(`/me/articles/${item.articleId}/${action}`, "POST", {
      expectedRevision: item.revision,
    });
    await load();
  } catch (e) {
    error.value = `${message(e)} 请重新读取文章状态再操作。`;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
watch(() => session.status, load);
</script>
<template>
  <MemberGate title="登录后管理你的文章"
    ><div class="page-heading">
      <div>
        <h1>每一次复盘，都值得留下</h1>
        <p>公开版本与草稿分别保存，编辑草稿不会改变正在展示的文章。</p>
      </div>
      <button class="primary" :disabled="busy" @click="create">
        {{ pending ? "继续上次创建" : "＋ 写一篇经验" }}
      </button>
    </div>
    <p v-if="pending" class="notice">
      上次创建结果尚未确认，再次点击会沿用原请求。
    </p>
    <div class="workspace-tabs" aria-label="文章状态">
      <button
        v-for="state in states"
        :key="state"
        :aria-pressed="filter === state"
        @click="filter = state"
      >
        {{ state === "ALL" ? "全部文章" : stateName[state] }}
      </button>
    </div>
    <p v-if="error" class="error" role="alert">
      {{ error }} <button @click="load">重新读取状态</button>
    </p>
    <p v-if="busy" role="status">正在读取或处理，请稍候…</p>
    <div class="workspace-grid">
      <article
        v-for="item in result?.items.filter(
          (item) => filter === 'ALL' || item.state === filter,
        )"
        :key="item.articleId"
        class="panel workspace-card"
      >
        <p class="state-label" :data-state="item.state">
          {{ stateName[item.state] }}<span>修订 {{ item.revision }}</span>
        </p>
        <h2>{{ item.title || "未命名草稿" }}</h2>
        <p class="meta">{{ dateText(item.updatedAt) }}</p>
        <div class="card-actions">
          <RouterLink
            v-if="item.state !== 'DELETED'"
            class="button"
            :to="`/editor/${item.articleId}`"
            >{{
              item.state === "ADMIN_HIDDEN" ? "查看草稿" : "继续编辑"
            }}</RouterLink
          ><RouterLink
            v-if="item.state === 'PUBLISHED'"
            class="button"
            :to="`/articles/${item.articleId}`"
            >查看公开文章</RouterLink
          >
        </div>
        <button
          v-if="item.state === 'PUBLISHED'"
          class="text-button"
          :disabled="busy"
          @click="transition(item, 'unpublish')"
        >
          下架</button
        ><button
          v-if="item.state === 'AUTHOR_OFFLINE'"
          class="text-button"
          :disabled="busy"
          @click="transition(item, 'restore')"
        >
          恢复上次公开版本
        </button>
        <p v-if="item.state === 'ADMIN_HIDDEN'" class="hint">
          管理员隐藏的文章不能自行修改、发布或恢复。
        </p>
      </article>
    </div>
    <section
      v-if="
        !busy &&
        result &&
        !result.items.some((item) => filter === 'ALL' || item.state === filter)
      "
      class="panel state-panel"
    >
      <h2>这一页还没有此状态的文章</h2>
      <p>可以切换状态、翻页，或写下第一篇经验。</p>
    </section>
    <nav
      v-if="result && result.total > result.size"
      class="pagination"
      aria-label="工作区翻页"
    >
      <button
        :disabled="busy || page <= 1"
        @click="
          page--;
          load();
        "
      >
        上一页</button
      ><span>第 {{ page }} 页（状态筛选作用于当前页）</span
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
