<script setup lang="ts">
import ConfirmDialog from "./ConfirmDialog.vue";
import OperationFeedback from "./OperationFeedback.vue";
import { operationFailure, type Feedback } from "../lib/feedback";
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { get, message, request, send } from "../lib/api";
import { readPrivate, removePrivate, writePrivate } from "../lib/session";
import { dateText } from "../lib/content";
import type { ImageAsset, Page } from "../types";
import CommentImage from "./PrivateImage.vue";

interface Commenter {
  nickname: string;
  authorId?: number;
}
interface Comment {
  commentId: number;
  articleId: number;
  rootCommentId: number;
  available: boolean;
  canDelete: boolean;
  replyCount: number;
  commenter?: Commenter | null;
  replyTo?: { available: boolean; commenter?: Commenter | null };
  createdAt?: string;
  body?: string;
  images?: ImageAsset[];
}
interface CommentRequest {
  requestId: string;
  body: string;
  imageIds: string[];
  replyToCommentId: number | null;
}
const props = defineProps<{ articleId: number }>();
const path = `/articles/${props.articleId}/comments`;
const key = `comment:${props.articleId}`;
const roots = ref<Page<Comment>>();
const route = useRoute();
const linkedRoot = ref<Comment>();
const visibleRoots = computed(() => {
  const items = roots.value?.items ?? [];
  return linkedRoot.value &&
    !items.some((row) => row.commentId === linkedRoot.value?.commentId)
    ? [linkedRoot.value, ...items]
    : items;
});
const replies = ref<Record<number, Page<Comment>>>({});
const target = ref<Comment>();
const replyId = ref<number | null>(null);
const body = ref("");
const imageIds = ref<string[]>([]);
const pending = ref<CommentRequest>();
const upload = ref<{ file: File; requestId: string }>();
const busy = ref(false);
const error = ref("");
const notice = ref("");
const confirmation = ref<InstanceType<typeof ConfirmDialog>>();
const composerFeedback = ref<Feedback>();
const composerBusy = ref("");
const deleting = ref<number>();
const deletedMessage = ref("");
const rowFeedback = ref<Record<number, Feedback>>({});
let asking = false;
const composer = ref<HTMLTextAreaElement>();
let mounted = true;
async function perform(action: () => Promise<void>) {
  if (busy.value) return;
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
async function readRoots(page = 1) {
  roots.value = await get<Page<Comment>>(`${path}?page=${page}&size=20`, true);
}
async function readReplies(root: number, page = 1) {
  replies.value[root] = await get<Page<Comment>>(
    `/comments/${root}/replies?page=${page}&size=20`,
    true,
  );
}
async function reopen() {
  await perform(async () => {
    pending.value = readPrivate<CommentRequest>(key) ?? undefined;
    if (pending.value) {
      body.value = pending.value.body;
      imageIds.value = pending.value.imageIds;
      replyId.value = pending.value.replyToCommentId;
    }
    await readRoots();
    const rootId = Number(route.query.thread);
    if (Number.isSafeInteger(rootId) && rootId > 0) {
      const thread = await get<Page<Comment> & { root: Comment }>(
        `/comments/${rootId}/replies?page=1&size=20`,
        true,
      );
      if (thread.root.articleId !== props.articleId)
        throw new Error("相关评论暂不可查看。");
      linkedRoot.value = thread.root;
      replies.value[rootId] = thread;
      await nextTick();
      const comment = Number(route.query.comment);
      const target =
        Number.isSafeInteger(comment) && comment > 0
          ? document.getElementById(`comment-${comment}`)
          : null;
      (
        target ?? document.getElementById(`comment-${rootId}`)
      )?.scrollIntoView();
    }
  });
}
function reply(comment: Comment) {
  target.value = comment;
  replyId.value = comment.commentId;
  composer.value?.focus();
}
function markFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file || upload.value || pending.value) return;
  if (
    !["image/jpeg", "image/png"].includes(file.type) ||
    file.size > 10 * 1024 * 1024
  ) {
    composerFeedback.value = {
      kind: "error",
      text: "请选择不超过 10MiB 的 JPEG 或 PNG 图片。",
    };
    return;
  }
  upload.value = { file, requestId: crypto.randomUUID() };
  void uploadImage();
}
async function uploadImage() {
  const selected = upload.value;
  if (!selected) return;
  if (composerBusy.value) return;
  composerBusy.value = "正在上传评论图片…";
  composerFeedback.value = undefined;
  try {
    const form = new FormData();
    form.set("file", selected.file);
    form.set("requestId", selected.requestId);
    const image = await request<ImageAsset>(
      `/articles/${props.articleId}/comment-images`,
      { method: "POST", auth: true, form },
    );
    imageIds.value.push(image.imageId);
    upload.value = undefined;
    composerFeedback.value = {
      kind: "success",
      text: "图片已添加到本条评论。",
    };
  } catch (e) {
    composerFeedback.value = operationFailure(
      e,
      "图片上传待确认，重试会使用原图和原请求。",
    );
  } finally {
    composerBusy.value = "";
  }
}
async function submit() {
  if (composerBusy.value || busy.value) return;
  composerBusy.value = "正在提交评论…";
  composerFeedback.value = undefined;
  try {
    notice.value = "";
    if (!pending.value) {
      if (upload.value || (!body.value.trim() && !imageIds.value.length))
        return;
      const value = {
        requestId: crypto.randomUUID(),
        body: body.value,
        imageIds: [...imageIds.value],
        replyToCommentId: replyId.value,
      };
      writePrivate(key, value);
      pending.value = value;
    }
    const receipt = await send<{ rootCommentId: number }>(
      path,
      "POST",
      pending.value,
    );
    if (!mounted) return;
    removePrivate(key);
    pending.value = undefined;
    target.value = undefined;
    replyId.value = null;
    imageIds.value = [];
    body.value = "";
    notice.value = "评论已提交。";
    const root = receipt.rootCommentId;
    if (replies.value[root]) await readReplies(root, replies.value[root].page);
    await perform(() => readRoots());
  } catch (e) {
    composerFeedback.value = operationFailure(
      e,
      pending.value ? "评论提交结果待确认，原输入已保留。" : undefined,
    );
  } finally {
    composerBusy.value = "";
  }
}
async function remove(comment: Comment) {
  if (!comment.canDelete || busy.value || deleting.value || asking) return;
  asking = true;
  try {
    if (
      !(await confirmation.value?.ask({
        title: "删除评论 " + comment.commentId + "？",
        detail: "该评论将停止公开显示。请确认删除的是你选择的这条评论。",
        action: "删除评论",
      }))
    )
      return;
  } finally {
    asking = false;
  }
  deleting.value = comment.commentId;
  deletedMessage.value = "";
  delete rowFeedback.value[comment.commentId];
  try {
    await send(`/comments/${comment.commentId}`, "DELETE");
    deletedMessage.value = "已删除评论 " + comment.commentId + "。";
    await perform(async () => {
      const root = comment.rootCommentId;
      if (replies.value[root])
        await readReplies(root, replies.value[root].page);
      await readRoots(roots.value?.page);
    });
  } catch (e) {
    rowFeedback.value[comment.commentId] = operationFailure(
      e,
      "删除结果待确认，请重新读取评论。",
    );
  } finally {
    deleting.value = undefined;
  }
}
async function releasePending() {
  if (
    !(await confirmation.value?.ask({
      title: "结束待确认评论？",
      detail: "请先检查评论列表，避免重复发布。正文和图片会保留在编辑区。",
      action: "结束本机记录",
    }))
  )
    return;
  removePrivate(key);
  pending.value = undefined;
}
onMounted(reopen);
onBeforeUnmount(() => {
  mounted = false;
});
</script>
<template>
  <section class="source-section" aria-label="文章评论">
    <ConfirmDialog ref="confirmation" />
    <h2>评论</h2>
    <p v-if="busy" class="hint" role="status">正在读取评论…</p>
    <p v-if="deletedMessage" class="success" role="status">
      {{ deletedMessage }}
    </p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button
      class="text-button"
      :disabled="busy"
      @click="perform(() => readRoots())"
    >
      刷新评论
    </button>
    <p v-if="roots && !roots.total">还没有评论。</p>
    <section v-for="root in visibleRoots" :key="root.commentId" class="panel">
      <div
        v-for="comment in [root, ...(replies[root.commentId]?.items ?? [])]"
        :key="comment.commentId"
        :id="`comment-${comment.commentId}`"
        class="stack"
      >
        <template v-if="comment.available">
          <p class="meta">
            <RouterLink
              v-if="comment.commenter?.authorId"
              :to="`/authors/${comment.commenter.authorId}`"
              >{{ comment.commenter.nickname }}</RouterLink
            >
            <span v-else>{{ comment.commenter?.nickname || "校园用户" }}</span>
            <span v-if="comment.replyTo"
              >回复
              {{ comment.replyTo.commenter?.nickname || "一条评论" }}</span
            >
            <time v-if="comment.createdAt">{{
              dateText(comment.createdAt)
            }}</time>
          </p>
          <p style="white-space: pre-wrap; overflow-wrap: anywhere">
            {{ comment.body }}
          </p>
          <div v-if="comment.images?.length" class="card-actions">
            <CommentImage
              v-for="image in comment.images"
              :key="image.imageId"
              :image-id="image.imageId"
            />
          </div>
          <div class="card-actions">
            <button
              class="text-button"
              :disabled="busy || !!pending"
              @click="reply(comment)"
            >
              回复
            </button>
            <RouterLink
              :to="{
                path: '/me/cases/new',
                query: { type: 'COMMENT', target: comment.commentId },
              }"
              >举报评论</RouterLink
            >
            <button
              v-if="comment.canDelete"
              class="text-button"
              :disabled="busy || !!deleting"
              @click="remove(comment)"
            >
              删除本人评论</button
            ><OperationFeedback
              :feedback="rowFeedback[comment.commentId]"
              :processing="
                deleting === comment.commentId ? '正在删除评论…' : undefined
              "
            />
          </div>
        </template>
        <p v-else class="hint">这条评论已不可用。</p>
      </div>
      <button
        v-if="root.replyCount && !replies[root.commentId]"
        class="text-button"
        :disabled="busy"
        @click="perform(() => readReplies(root.commentId))"
      >
        查看 {{ root.replyCount }} 条回复
      </button>
      <nav
        v-else-if="replies[root.commentId]?.total > 20"
        class="pagination"
        aria-label="回复翻页"
      >
        <button
          :disabled="busy || replies[root.commentId].page <= 1"
          @click="
            perform(() =>
              readReplies(root.commentId, replies[root.commentId].page - 1),
            )
          "
        >
          上一页回复
        </button>
        <span>第 {{ replies[root.commentId].page }} 页</span>
        <button
          :disabled="
            busy ||
            replies[root.commentId].page * 20 >= replies[root.commentId].total
          "
          @click="
            perform(() =>
              readReplies(root.commentId, replies[root.commentId].page + 1),
            )
          "
        >
          下一页回复
        </button>
      </nav>
    </section>
    <nav
      v-if="roots && roots.total > roots.size"
      class="pagination"
      aria-label="评论翻页"
    >
      <button
        :disabled="busy || roots.page <= 1"
        @click="perform(() => readRoots(roots!.page - 1))"
      >
        上一页评论
      </button>
      <span>第 {{ roots.page }} 页</span>
      <button
        :disabled="busy || roots.page * roots.size >= roots.total"
        @click="perform(() => readRoots(roots!.page + 1))"
      >
        下一页评论
      </button>
    </nav>
    <form class="stack" @submit.prevent="submit">
      <p v-if="notice" class="success" role="status">{{ notice }}</p>
      <OperationFeedback
        :feedback="composerFeedback"
        :processing="composerBusy || undefined"
      />
      <label for="comment-body">{{
        target
          ? `回复 ${target.commenter?.nickname || "校园用户"}`
          : replyId
            ? `回复评论 ${replyId}`
            : "写评论"
      }}</label>
      <button
        v-if="replyId && !pending"
        class="text-button"
        type="button"
        @click="
          target = undefined;
          replyId = null;
        "
      >
        取消回复
      </button>
      <textarea
        id="comment-body"
        ref="composer"
        v-model="body"
        rows="4"
        maxlength="10000"
        :disabled="!!composerBusy || busy || !!pending"
      />
      <div v-if="imageIds.length" class="card-actions">
        <div v-for="imageId in imageIds" :key="imageId">
          <CommentImage :image-id="imageId" />
          <button
            type="button"
            class="text-button"
            :disabled="!!composerBusy || busy || !!pending"
            @click="imageIds = imageIds.filter((id) => id !== imageId)"
          >
            移除图片
          </button>
        </div>
      </div>
      <label v-if="!pending && !upload"
        >添加评论图片
        <input
          type="file"
          accept="image/jpeg,image/png"
          :disabled="!!composerBusy || busy"
          @change="markFile"
        />
      </label>
      <div v-if="upload" class="card-actions">
        <button
          type="button"
          :disabled="!!composerBusy || busy"
          @click="uploadImage"
        >
          重试上传
        </button>
        <button
          type="button"
          :disabled="!!composerBusy || busy"
          @click="upload = undefined"
        >
          移除待上传图片
        </button>
      </div>
      <div class="card-actions">
        <button
          class="primary"
          :disabled="
            !!composerBusy ||
            busy ||
            (!!upload && !pending) ||
            (!pending && !body.trim() && !imageIds.length)
          "
        >
          {{ pending ? "按原请求核对评论" : "提交评论" }}
        </button>
        <button
          v-if="pending"
          type="button"
          :disabled="!!composerBusy || busy"
          @click="releasePending"
        >
          结束待确认记录
        </button>
      </div>
    </form>
  </section>
</template>
