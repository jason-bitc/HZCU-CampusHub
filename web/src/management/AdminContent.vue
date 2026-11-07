<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { stateLabels, useMutation } from "./governance";
import type { Article, Comment, Page } from "./governance";
import { dateText } from "../lib/content";
import { message } from "../lib/api";
import MarkdownView from "../components/MarkdownView.vue";
import AdminGate from "./AdminGate.vue";
import ConsolePager from "./ConsolePager.vue";
import MutationNotice from "./MutationNotice.vue";
const route = useRoute(),
  router = useRouter();
const data = ref<Page<Article>>(),
  article = ref<Article>(),
  comments = ref<Page<Comment>>();
const selected = ref<{
  type: "articles" | "comments";
  id: number;
  revision: number;
  state: string;
}>();
const reason = ref(""),
  action = ref("HIDE"),
  error = ref(""),
  busy = ref(false),
  commentPage = ref(1);
let generation = 0;
const page = computed(() => Math.max(1, Number(route.query.page) || 1));
const mutation = useMutation("content", "CONTENT_MODERATE", load);
const frozen = computed(
  () => busy.value || mutation.busy.value || !!mutation.pending.value,
);
async function load() {
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  data.value = undefined;
  article.value = undefined;
  comments.value = undefined;
  selected.value = undefined;
  try {
    const query = new URLSearchParams({ page: String(page.value), size: "20" });
    if (typeof route.query.state === "string" && route.query.state)
      query.set("state", route.query.state);
    const result = await adminRequest<Page<Article>>(
      `/admin/articles?${query}`,
    );
    if (current !== generation) return;
    data.value = result;
    mutation.restore();
    if (/^[1-9]\d*$/.test(String(route.query.article || "")))
      await open(Number(route.query.article));
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function open(id: number) {
  const current = generation;
  error.value = "";
  article.value = undefined;
  comments.value = undefined;
  selected.value = undefined;
  try {
    const [a, c] = await Promise.all([
      adminRequest<Article>(`/admin/articles/${id}`),
      adminRequest<Page<Comment>>(
        `/admin/articles/${id}/comments?page=${commentPage.value}&size=10`,
      ),
    ]);
    if (current !== generation) return;
    article.value = a;
    comments.value = c;
    const commentId = Number(route.query.comment);
    const comment = c.items.find((row) => row.id === commentId);
    if (comment) select("comments", comment);
    else if (!commentId) select("articles", a);
    else
      error.value = `案件关联评论 ${commentId} 不在当前页，请翻页后核对并选择目标。`;
  } catch (e) {
    if (current === generation) error.value = message(e);
  }
}
function select(type: "articles" | "comments", row: Article | Comment) {
  if (mutation.pending.value) return;
  selected.value = {
    type,
    id: row.id,
    revision: row.revision,
    state: row.state,
  };
  action.value = row.state === "ADMIN_HIDDEN" ? "RESTORE" : "HIDE";
  reason.value = "";
}
async function moderate() {
  const target = selected.value;
  if (!target || frozen.value) return;
  if (!reason.value.trim()) {
    error.value = "请填写处置依据。";
    return;
  }
  await mutation.start(
    `/admin/${target.type}/${target.id}/moderation`,
    "POST",
    {
      action: action.value,
      expectedRevision: target.revision,
      reason: reason.value,
    },
    action.value === "HIDE" ? "隐藏内容" : "恢复内容",
  );
}
function turn(p: number) {
  void router.push({ query: { ...route.query, page: String(p) } });
}
async function turnComments(p: number) {
  commentPage.value = p;
  if (article.value) await open(article.value.id);
}
onMounted(load);
watch(() => [route.query, adminSession.status], load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <h1 class="console-title">内容巡查</h1>
  <AdminGate>
    <p class="console-note">
      读取已有公开版本，作者草稿不进入巡查。隐藏与恢复均保留治理审计；恢复保留作者原下架状态。
    </p>
    <p v-if="error" class="error" role="alert">
      {{ error }} <button :disabled="busy" @click="load">重新读取</button>
    </p>
    <MutationNotice
      :pending="mutation.pending.value"
      :busy="mutation.busy.value"
      :error="mutation.error.value"
      :notice="mutation.notice.value"
      @retry="mutation.submit"
      @reconcile="mutation.reconcile"
    />
    <section class="console-panel console-patrol">
      <div class="console-filterbar">
        <label class="console-inline-label"
          >文章状态<select
            aria-label="巡查状态"
            :value="route.query.state || ''"
            :disabled="frozen"
            @change="
              router.push({
                query: {
                  ...route.query,
                  page: '1',
                  state: ($event.target as HTMLSelectElement).value,
                },
              })
            "
          >
            <option value="">全部</option>
            <option
              v-for="state in [
                'PUBLISHED',
                'AUTHOR_OFFLINE',
                'ADMIN_HIDDEN',
                'DELETED',
              ]"
              :key="state"
              :value="state"
            >
              {{ stateLabels[state] }}
            </option>
          </select></label
        ><button :disabled="frozen" @click="load">刷新当前状态</button>
      </div>
      <p v-if="busy" role="status">正在读取巡查内容…</p>
      <div v-if="data" class="console-table-wrap">
        <table class="console-table">
          <thead>
            <tr>
              <th>内容 / 对象</th>
              <th>状态</th>
              <th>治理修订</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data.items" :key="row.id">
              <td>
                {{ row.title }}<small>文章 {{ row.id }}</small>
              </td>
              <td>{{ stateLabels[row.state] || row.state }}</td>
              <td>{{ row.revision }}</td>
              <td>{{ dateText(row.updatedAt || "") }}</td>
              <td>
                <button
                  :disabled="frozen"
                  @click="
                    router.push({
                      query: {
                        ...route.query,
                        article: String(row.id),
                        comment: undefined,
                      },
                    })
                  "
                >
                  查看与处置
                </button>
              </td>
            </tr>
            <tr v-if="!data.items.length">
              <td colspan="5">没有符合条件的公开版本。</td>
            </tr>
          </tbody>
        </table>
      </div>
      <ConsolePager
        v-if="data"
        :page="data.page"
        :total="data.total"
        :size="data.size"
        :busy="frozen"
        @turn="turn"
      />
    </section>
    <section
      v-if="article"
      class="console-panel console-followup console-patrol-context"
    >
      <h2>{{ article.title }} · 公开版本 {{ article.versionId }}</h2>
      <p>
        文章 {{ article.id }} ·
        {{ stateLabels[article.state] || article.state }} · 治理修订
        {{ article.revision }}
      </p>
      <MarkdownView
        v-if="article.bodyFormat === 'MARKDOWN'"
        :body="article.body || ''"
        block-images
      />
      <p v-else class="console-preserve">{{ article.body }}</p>
      <p class="hint">
        巡查合同未提供独立鉴权图片接口，此处不加载图片。案件证据图片在对应案件中读取。
      </p>
      <button :disabled="frozen" @click="select('articles', article)">
        选择此文章处置
      </button>
      <h3>文章评论</h3>
      <div class="console-table-wrap">
        <table class="console-table">
          <thead>
            <tr>
              <th>评论 / 内容</th>
              <th>状态</th>
              <th>修订</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in comments?.items" :key="row.id">
              <td class="console-preserve">{{ row.id }} · {{ row.body }}</td>
              <td>{{ stateLabels[row.state] || row.state }}</td>
              <td>{{ row.revision }}</td>
              <td>
                <button :disabled="frozen" @click="select('comments', row)">
                  选择此评论处置
                </button>
              </td>
            </tr>
            <tr v-if="!comments?.items.length">
              <td colspan="4">这篇文章暂无评论。</td>
            </tr>
          </tbody>
        </table>
      </div>
      <ConsolePager
        v-if="comments"
        :page="comments.page"
        :total="comments.total"
        :size="comments.size"
        :busy="frozen"
        @turn="turnComments"
      />
    </section>
    <div class="console-split console-followup">
      <form class="console-panel" @submit.prevent="moderate">
        <h2>处置确认</h2>
        <p>
          对象：{{
            selected
              ? `${selected.type === "articles" ? "文章" : "评论"} ${selected.id} · 修订 ${selected.revision}`
              : "待选择"
          }}
        </p>
        <fieldset :disabled="frozen || !selected || !mutation.allowed.value">
          <label
            >动作<select v-model="action" aria-label="治理动作">
              <option v-if="selected?.state === 'ADMIN_HIDDEN'" value="RESTORE">
                恢复
              </option>
              <option v-else value="HIDE">隐藏</option>
            </select></label
          ><label
            >必填理由<textarea
              v-model="reason"
              required
              maxlength="2000"
              rows="3"
              placeholder="写明已核对的事实及关联案件。"
            ></textarea></label
          ><button
            class="console-danger"
            :disabled="
              ![
                'PUBLISHED',
                'AUTHOR_OFFLINE',
                'VISIBLE',
                'ADMIN_HIDDEN',
              ].includes(selected?.state || '')
            "
          >
            {{ action === "HIDE" ? "确认隐藏" : "确认恢复" }}
          </button>
        </fieldset>
      </form>
      <p class="console-note">
        409冲突后读取当前修订，由操作者重新确认。隐藏文章不会自动限制账号，解除账号限制也不会自动恢复内容。
      </p>
    </div>
  </AdminGate>
</template>
