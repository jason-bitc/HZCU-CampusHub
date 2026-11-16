<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { get, message, send } from "../lib/api";
import { dateText } from "../lib/content";
import { session } from "../lib/session";
import type { Page } from "../types";
import MemberGate from "../components/MemberGate.vue";

interface Target {
  type: string;
  id: number | null;
  articleId: number | null;
  rootCommentId: number | null;
}
interface Notice {
  id: number;
  message: string;
  outcome: string | null;
  createdAt: string;
  readAt: string | null;
  target: Target | null;
}
const result = ref<Page<Notice> & { unreadCount: number }>();
const error = ref("");
const busy = ref(false);
const outcomes: Record<string, string> = {
  HIDDEN: "已隐藏",
  RESTORED: "已恢复",
  ANSWERED: "已答复",
  RECORDED: "已记录",
  NO_VIOLATION: "未发现违规",
  DUPLICATE: "重复案件",
  REQUIRES_GOVERNANCE: "待治理",
  GOVERNANCE_COMPLETED: "已完成治理",
  ENABLED: "限制已启用",
  DISABLED: "限制已解除",
  ACCEPTED: "申诉已接受",
  REJECTED: "申诉已驳回",
  INVALIDATED: "已标记失效",
};
async function load(page = 1) {
  if (busy.value || !["ready", "limited"].includes(session.status)) return;
  busy.value = true;
  error.value = "";
  try {
    result.value = await get(`/me/notifications?page=${page}&size=20`, true);
  } catch (e) {
    result.value = undefined;
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function mark(row: Notice) {
  if (busy.value || row.readAt) return;
  busy.value = true;
  error.value = "";
  try {
    const receipt = await send<{ readAt: string }>(
      `/me/notifications/${row.id}/read`,
      "PUT",
    );
    row.readAt = receipt.readAt;
    if (result.value)
      result.value.unreadCount = Math.max(0, result.value.unreadCount - 1);
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
function destination(target: Target | null) {
  if (!target) return undefined;
  switch (target.type) {
    case "CERTIFICATION":
      return { path: "/me/certification" };
    case "RESTRICTION":
      return { path: "/me/appeals", hash: "#restrictions" };
    case "APPEAL":
      return target.id ? { path: `/me/appeals/${target.id}` } : undefined;
    case "CASE":
      return target.id ? { path: `/me/cases/${target.id}` } : undefined;
    case "ARTICLE":
      return target.id ? { path: `/articles/${target.id}` } : undefined;
    case "COMMENT":
      return target.articleId && target.rootCommentId
        ? {
            path: `/articles/${target.articleId}`,
            query: {
              thread: String(target.rootCommentId),
              comment: String(target.id),
            },
          }
        : undefined;
    default:
      return undefined;
  }
}
onMounted(() => load());
watch(
  () => session.status,
  () => load(),
);
</script>
<template>
  <MemberGate allow-limited title="登录后查看站内通知">
    <div class="page-heading">
      <h1>站内通知</h1>
      <p v-if="result">{{ result.unreadCount }} 条未读</p>
    </div>
    <p v-if="busy" role="status">正在读取或更新通知…</p>
    <p v-if="error" class="error" role="alert">
      {{ error }} <button @click="load()">刷新通知</button>
    </p>
    <p v-if="result && !result.total" class="panel state-panel">暂无通知。</p>
    <article
      v-for="row in result?.items"
      :key="row.id"
      class="panel restriction-row"
    >
      <h2>{{ row.message }}</h2>
      <p class="meta">
        {{ dateText(row.createdAt) }} · {{ row.readAt ? "已读" : "未读"
        }}<span v-if="row.outcome && outcomes[row.outcome]">
          · {{ outcomes[row.outcome] }}</span
        >
      </p>
      <div class="card-actions">
        <RouterLink
          v-if="destination(row.target)"
          class="button"
          :to="destination(row.target)!"
          >查看相关内容</RouterLink
        >
        <span v-else class="hint">相关内容暂不可查看。</span>
        <button v-if="!row.readAt" :disabled="busy" @click="mark(row)">
          标记已读
        </button>
      </div>
    </article>
    <nav
      v-if="result && result.total > result.size"
      class="pagination"
      aria-label="通知翻页"
    >
      <button
        :disabled="busy || result.page <= 1"
        @click="load(result.page - 1)"
      >
        上一页
      </button>
      <span>第 {{ result.page }} 页</span>
      <button
        :disabled="busy || result.page * result.size >= result.total"
        @click="load(result.page + 1)"
      >
        下一页
      </button>
    </nav>
  </MemberGate>
</template>
