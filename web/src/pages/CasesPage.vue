<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, get, message, request, send } from "../lib/api";
import { dateText } from "../lib/content";
import {
  readPrivate,
  removePrivate,
  session,
  writePrivate,
} from "../lib/session";
import type { Page } from "../types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import MemberGate from "../components/MemberGate.vue";
import PrivateImage from "../components/PrivateImage.vue";

type TargetType = "ARTICLE" | "COMMENT";
interface CaseRow {
  caseId: number;
  kind: string;
  category: string;
  state: string;
  createdAt: string;
  targetType: TargetType | null;
  articleId: number | null;
  commentId: number | null;
}
interface CaseDetail extends CaseRow {
  description: string;
  images: { id: string }[];
  history: {
    revision: number;
    state: string;
    outcome: string | null;
    reply: string | null;
    occurredAt: string;
  }[];
}
interface Submission {
  requestId: string;
  kind: "REPORT" | "FEEDBACK";
  category: string;
  description: string;
  targetType: TargetType | null;
  targetId: number | null;
  imageIds: string[];
}
const route = useRoute(),
  router = useRouter();
const creating = route.path === "/me/cases/new";
const id = Number(route.params.id);
const report =
  route.query.type !== undefined || route.query.target !== undefined;
const targetType =
  route.query.type === "ARTICLE" || route.query.type === "COMMENT"
    ? route.query.type
    : null;
const targetId =
  typeof route.query.target === "string" &&
  /^[1-9]\d*$/.test(route.query.target) &&
  Number.isSafeInteger(Number(route.query.target))
    ? Number(route.query.target)
    : null;
const invalidTarget = report && !(targetType && targetId);
const storageKey = report ? `report:${targetType}:${targetId}` : "feedback";
const categories: Record<string, string> = report
  ? { ADVERTISEMENT: "广告", MISLEADING_CONTENT: "误导性内容", OTHER: "其他" }
  : { BUG: "使用问题", SUGGESTION: "建议", OTHER: "其他" };
const states: Record<string, string> = {
  OPEN: "已提交",
  PROCESSING: "处理中",
  NEEDS_ACTION: "待后续处理",
  CLOSED: "已完成",
};
const outcomes: Record<string, string> = {
  NO_VIOLATION: "未发现违规",
  DUPLICATE: "重复举报",
  REQUIRES_GOVERNANCE: "进入后续治理",
  GOVERNANCE_COMPLETED: "已完成治理",
  ANSWERED: "已答复",
  RECORDED: "已记录",
};
const confirmation = ref<InstanceType<typeof ConfirmDialog>>();
const listing = ref<Page<CaseRow> & { snapshot: number }>();
const detail = ref<CaseDetail>();
const pending = ref<Submission>();
const selection = ref<{ file: File; requestId: string }>();
const imageIds = ref<string[]>([]);
const category = ref(Object.keys(categories)[0]);
const description = ref("");
const busy = ref(false),
  error = ref("");
const title = computed(() =>
  creating
    ? report
      ? "举报内容"
      : "提交反馈"
    : id
      ? "反馈处理进度"
      : "我的反馈与举报",
);
let active = true;
onBeforeUnmount(() => {
  active = false;
});
async function run(action: () => Promise<void>) {
  if (busy.value || session.status !== "ready") return;
  busy.value = true;
  error.value = "";
  try {
    await action();
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
function load(page = 1, reset = true) {
  void run(async () => {
    if (creating) {
      if (invalidTarget) return;
      pending.value = readPrivate<Submission>(storageKey) ?? undefined;
      if (pending.value) {
        category.value = pending.value.category;
        description.value = pending.value.description;
        imageIds.value = pending.value.imageIds.slice();
      }
    } else if (id) {
      detail.value = await get(`/me/cases/${id}`, true);
    } else {
      const previous =
        !reset && listing.value ? `&snapshot=${listing.value.snapshot}` : "";
      listing.value = await get(
        `/me/cases?page=${page}&size=20${previous}`,
        true,
      );
    }
  });
}
function choose(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file || busy.value || selection.value || pending.value) return;
  if (
    !["image/jpeg", "image/png"].includes(file.type) ||
    file.size > 10 * 1024 * 1024
  ) {
    error.value = "请选择 10 MiB 以内的 JPEG 或 PNG 图片。";
    return;
  }
  selection.value = { file, requestId: crypto.randomUUID() };
  upload();
}
function upload() {
  const selected = selection.value;
  if (!selected) return;
  void run(async () => {
    const form = new FormData();
    form.set("file", selected.file);
    form.set("requestId", selected.requestId);
    const image = await request<{ id: string }>("/me/case-images", {
      method: "POST",
      auth: true,
      form,
    });
    imageIds.value.push(image.id);
    selection.value = undefined;
  });
}
function submit() {
  if (invalidTarget || selection.value || !description.value.trim()) return;
  void run(async () => {
    if (!pending.value) {
      const body: Submission = {
        requestId: crypto.randomUUID(),
        kind: report ? "REPORT" : "FEEDBACK",
        category: category.value,
        targetType,
        targetId,
        description: description.value,
        imageIds: imageIds.value.slice(),
      };
      writePrivate(storageKey, body);
      pending.value = body;
    }
    try {
      const receipt = await send<{ caseId: number }>(
        "/me/cases",
        "POST",
        pending.value,
      );
      removePrivate(storageKey);
      pending.value = undefined;
      if (active) await router.replace(`/me/cases/${receipt.caseId}`);
    } catch (e) {
      // Keep the original body and key when acceptance could not be confirmed.
      if (e instanceof ApiError && [400, 413, 415, 422].includes(e.status)) {
        removePrivate(storageKey);
        pending.value = undefined;
      }
      throw e;
    }
  });
}
async function endPending() {
  if (
    !(await confirmation.value?.ask({
      title: "结束待确认提交？",
      detail: "请先在我的反馈与举报中核对提交结果。当前表单输入会保留。",
      action: "结束本机记录",
    }))
  )
    return;
  removePrivate(storageKey);
  pending.value = undefined;
}
onMounted(() => load());
watch(
  () => session.status,
  () => load(),
);
</script>
<template>
  <MemberGate title="登录后使用反馈与举报">
    <ConfirmDialog ref="confirmation" />
    <div class="page-heading service-heading">
      <h1>{{ title }}</h1>
      <div class="card-actions">
        <button
          v-if="!creating"
          :disabled="busy"
          @click="load(listing?.page || 1)"
        >
          重新读取
        </button>
        <RouterLink
          class="button"
          :to="creating || id ? '/me/cases' : '/me/cases/new'"
        >
          {{ creating || id ? "我的反馈与举报" : "提交反馈" }}
        </RouterLink>
      </div>
    </div>
    <p v-if="busy" role="status">正在读取或提交…</p>
    <p v-if="error" class="error" role="alert">
      {{ error }}
      <span v-if="listing || detail">当前显示的是上次读取结果。</span>
      <button v-if="!creating" @click="load(listing?.page || 1)">
        重试读取
      </button>
    </p>
    <template v-if="creating">
      <p v-if="invalidTarget" class="panel state-panel">
        举报对象无效，请从文章或评论入口重新打开。
      </p>
      <form v-else class="panel stack" @submit.prevent="submit">
        <p v-if="report">
          举报对象 {{ targetType === "ARTICLE" ? "文章" : "评论" }}
          {{ targetId }}
        </p>
        <p v-if="pending" class="hint">
          上次提交结果待核对，继续提交会使用原请求。
        </p>
        <fieldset :disabled="busy || !!pending">
          <label
            >类别<select v-model="category">
              <option
                v-for="(label, value) in categories"
                :key="value"
                :value="value"
              >
                {{ label }}
              </option>
            </select></label
          >
          <label
            >说明<textarea
              v-model="description"
              required
              rows="5"
              maxlength="10000"
            />
          </label>
          <label
            >证据图片<input
              type="file"
              accept="image/jpeg,image/png"
              :disabled="!!selection"
              @change="choose"
          /></label>
        </fieldset>
        <div class="card-actions" v-if="selection">
          <button type="button" :disabled="busy" @click="upload">
            重试图片上传
          </button>
          <button type="button" :disabled="busy" @click="selection = undefined">
            移除待上传图片
          </button>
        </div>
        <div class="card-actions" v-if="imageIds.length">
          <div v-for="imageId in imageIds" :key="imageId">
            <PrivateImage :image-id="imageId" kind="case" />
            <button
              type="button"
              :disabled="busy || !!pending"
              @click="imageIds = imageIds.filter((value) => value !== imageId)"
            >
              移除图片
            </button>
          </div>
        </div>
        <div class="card-actions">
          <button
            class="primary"
            :disabled="busy || !!selection || !description.trim()"
          >
            {{ pending ? "核对上次提交" : report ? "提交举报" : "提交反馈" }}
          </button>
          <button
            v-if="pending"
            type="button"
            :disabled="busy"
            @click="endPending"
          >
            结束待确认记录
          </button>
        </div>
      </form>
    </template>
    <section v-else-if="detail" class="panel stack">
      <h2>
        案件 {{ detail.caseId }} · {{ states[detail.state] || detail.state }}
      </h2>
      <p v-if="detail.targetType">
        提交对象
        {{
          detail.targetType === "ARTICLE"
            ? `文章 ${detail.articleId}`
            : `评论 ${detail.commentId}`
        }}
      </p>
      <p style="white-space: pre-wrap; overflow-wrap: anywhere">
        {{ detail.description }}
      </p>
      <div v-if="detail.images.length" class="card-actions">
        <PrivateImage
          v-for="image in detail.images"
          :key="image.id"
          :image-id="image.id"
          kind="case"
        />
      </div>
      <h3>处理记录</h3>
      <ol class="record-timeline">
        <li
          v-for="row in [...detail.history].sort(
            (a, b) => a.revision - b.revision,
          )"
          :key="row.revision"
        >
          <time class="meta" :datetime="row.occurredAt">{{
            dateText(row.occurredAt)
          }}</time>
          <strong>{{
            (row.outcome && outcomes[row.outcome]) || states[row.state]
          }}</strong>
          <p v-if="row.reply" class="long-content">{{ row.reply }}</p>
        </li>
      </ol>
    </section>
    <template v-else-if="listing">
      <p v-if="!listing.total" class="panel state-panel">
        暂无反馈与举报记录。
      </p>
      <article
        v-for="row in listing.items"
        :key="row.caseId"
        class="panel restriction-row"
      >
        <h2>
          <RouterLink :to="`/me/cases/${row.caseId}`"
            >{{ row.kind === "REPORT" ? "内容举报" : "使用反馈" }}
            {{ row.caseId }}</RouterLink
          >
        </h2>
        <p class="meta">
          {{ states[row.state] || row.state }} · {{ dateText(row.createdAt) }}
        </p>
      </article>
      <nav
        v-if="listing.total > listing.size"
        class="pagination"
        aria-label="反馈记录翻页"
      >
        <button
          :disabled="busy || listing.page <= 1"
          @click="load(listing.page - 1, false)"
        >
          上一页
        </button>
        <span>第 {{ listing.page }} 页</span>
        <button
          :disabled="busy || listing.page * listing.size >= listing.total"
          @click="load(listing.page + 1, false)"
        >
          下一页
        </button>
      </nav>
    </template>
  </MemberGate>
</template>
