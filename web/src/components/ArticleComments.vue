<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { get, message, request, send } from "../lib/api";
import { readPrivate, removePrivate, writePrivate } from "../lib/session";
import { dateText } from "../lib/content";
import type { ImageAsset, Page } from "../types";
import CommentImage from "./CommentImage.vue";

interface Commenter {
  nickname: string;
  authorId?: number;
}
interface Comment {
  commentId: number;
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
    error.value = "请选择不超过 10MiB 的 JPEG 或 PNG 图片。";
    return;
  }
  upload.value = { file, requestId: crypto.randomUUID() };
  void uploadImage();
}
async function uploadImage() {
  const selected = upload.value;
  if (!selected) return;
  await perform(async () => {
    const form = new FormData();
    form.set("file", selected.file);
    form.set("requestId", selected.requestId);
    const image = await request<ImageAsset>(
      `/articles/${props.articleId}/comment-images`,
      { method: "POST", auth: true, form },
    );
    imageIds.value.push(image.imageId);
    upload.value = undefined;
  });
}
async function submit() {
  await perform(async () => {
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
    await readRoots();
  });
}
async function remove(comment: Comment) {
  if (!comment.canDelete || !window.confirm("确认删除这条本人评论？")) return;
  await perform(async () => {
    await send(`/comments/${comment.commentId}`, "DELETE");
    const root = comment.rootCommentId;
    if (replies.value[root]) await readReplies(root, replies.value[root].page);
    await readRoots(roots.value?.page);
  });
}
function releasePending() {
  if (
    !window.confirm(
      "确定结束上次待确认请求？请先检查评论列表，避免重复发布。正文和图片保留在编辑区。",
    )
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
    <h2>评论</h2>
    <p v-if="busy" class="hint" role="status">正在读取或提交评论…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <button
      class="text-button"
      :disabled="busy"
      @click="perform(() => readRoots())"
    >
      刷新评论
    </button>
    <p v-if="roots && !roots.total">还没有评论。</p>
    <section v-for="root in roots?.items" :key="root.commentId" class="panel">
      <div
        v-for="comment in [root, ...(replies[root.commentId]?.items ?? [])]"
        :key="comment.commentId"
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
            <button
              v-if="comment.canDelete"
              class="text-button"
              :disabled="busy"
              @click="remove(comment)"
            >
              删除本人评论
            </button>
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
        :disabled="busy || !!pending"
      />
      <div v-if="imageIds.length" class="card-actions">
        <div v-for="imageId in imageIds" :key="imageId">
          <CommentImage :image-id="imageId" />
          <button
            type="button"
            class="text-button"
            :disabled="busy || !!pending"
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
          :disabled="busy"
          @change="markFile"
        />
      </label>
      <div v-if="upload" class="card-actions">
        <button type="button" :disabled="busy" @click="uploadImage">
          重试上传
        </button>
        <button type="button" :disabled="busy" @click="upload = undefined">
          移除待上传图片
        </button>
      </div>
      <div class="card-actions">
        <button
          class="primary"
          :disabled="
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
          :disabled="busy"
          @click="releasePending"
        >
          结束待确认记录
        </button>
      </div>
    </form>
  </section>
</template>
