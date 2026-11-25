<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { get, message, send } from "../lib/api";
import { dateText } from "../lib/content";
import { session } from "../lib/session";
import type { Page } from "../types";
import OperationFeedback from "../components/OperationFeedback.vue";
import { operationFailure, type Feedback } from "../lib/feedback";
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
const marking = ref<number>();
const rowFeedback = ref<Record<number, Feedback>>({});
const retryPage = ref(1);
const countStale = ref(false);
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
async function load(page = result.value?.page ?? 1, syncing = false) {
  if (
    busy.value ||
    (!syncing && marking.value) ||
    !["ready", "limited"].includes(session.status)
  )
    return;
  busy.value = true;
  retryPage.value = page;
  error.value = "";
  try {
    result.value = await get(`/me/notifications?page=${page}&size=20`, true);
    countStale.value = false;
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function mark(row: Notice) {
  if (busy.value || marking.value || row.readAt) return;
  marking.value = row.id;
  delete rowFeedback.value[row.id];
  try {
    const receipt = await send<{ readAt: string; unreadCount?: number }>(
      `/me/notifications/${row.id}/read`,
      "PUT",
    );
    row.readAt = receipt.readAt;
    rowFeedback.value[row.id] = { kind: "success", text: "已标记为已读。" };
    if (result.value && typeof receipt.unreadCount === "number") {
      result.value.unreadCount = receipt.unreadCount;
      countStale.value = false;
    } else {
      countStale.value = true;
      await load(result.value?.page, true);
    }
  } catch (e) {
    rowFeedback.value[row.id] = operationFailure(
      e,
      "标记结果待确认，可刷新通知或重试。",
    );
  } finally {
    marking.value = undefined;
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
    <div class="page-heading service-page-heading">
      <div>
        <h1>站内通知</h1>
        <p v-if="result">
          {{ countStale ? "未读总数待同步" : result.unreadCount + " 条未读" }}
        </p>
      </div>
      <button :disabled="busy || !!marking" @click="load()">刷新通知</button>
    </div>
    <section class="panel service-card" aria-label="通知列表">
      <p v-if="busy" class="service-inline-note" role="status">正在读取通知…</p>
      <p v-if="error" class="notice service-inline-note" role="alert">
        刷新失败，当前显示的是上次读取的通知。{{ error }}
      </p>
      <p v-if="result && !result.total" class="service-empty">暂无通知。</p>
      <ul v-if="result?.items.length" class="service-list">
        <li
          v-for="row in result.items"
          :key="row.id"
          :class="{ 'notification-unread': !row.readAt }"
        >
          <div class="service-list-copy">
            <strong>{{ row.message }}</strong>
            <span
              ><span class="notice-read-state">{{
                row.readAt ? "已读" : "未读"
              }}</span>
              · {{ dateText(row.createdAt) }}
              <template v-if="row.outcome && outcomes[row.outcome]"
                >· {{ outcomes[row.outcome] }}</template
              ></span
            >
            <OperationFeedback
              :feedback="rowFeedback[row.id]"
              :processing="
                marking === row.id && !row.readAt ? '正在标记已读…' : undefined
              "
            />
          </div>
          <div class="service-list-actions">
            <RouterLink
              v-if="destination(row.target)"
              :to="destination(row.target)!"
              >查看相关内容</RouterLink
            >
            <span v-else class="hint">相关内容暂不可查看</span>
            <button
              v-if="!row.readAt"
              :disabled="busy || !!marking"
              @click="mark(row)"
            >
              标记已读
            </button>
          </div>
        </li>
      </ul>
      <div v-if="error || (result && result.total > result.size)" class="service-foot">
        <button v-if="error" :disabled="busy" @click="load(retryPage)">
          重试读取
        </button>
        <nav
          v-if="result && result.total > result.size"
          class="pagination"
          aria-label="通知翻页"
        >
          <button
            aria-label="上一页"
            :disabled="busy || !!marking || result.page <= 1"
            @click="load(result.page - 1)"
          >
            ‹</button
          ><span aria-current="page">{{ result.page }}</span
          ><button
            aria-label="下一页"
            :disabled="
              busy || !!marking || result.page * result.size >= result.total
            "
            @click="load(result.page + 1)"
          >
            ›
          </button>
        </nav>
      </div>
    </section>
  </MemberGate>
</template>
