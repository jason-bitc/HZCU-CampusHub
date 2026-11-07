<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import {
  adminRequest,
  adminSession,
  storeCommand,
  pendingCommand,
  forgetCommand,
} from "./client";
import { caseStates, outcomes } from "./types";
import type { CaseDetail, CaseContext, CaseCommand } from "./types";
import { exampleCase, exampleContext } from "./examples";
import AdminGate from "./AdminGate.vue";
import MarkdownView from "../components/MarkdownView.vue";
import { dateText } from "../lib/content";
import { message } from "../lib/api";
const route = useRoute();
const id = Number(route.params.id);
const preview = computed(() => route.query.preview === "1");
const detail = ref<CaseDetail>();
const context = ref<CaseContext>();
const busy = ref(false);
const error = ref("");
const notice = ref("");
const reply = ref("");
const internalNote = ref("");
const reason = ref("");
const outcome = ref("");
const pending = ref<CaseCommand | null>(null);
const images = ref<Record<string, string>>({});
const imageError = ref("");
const loadingImages = ref(false);
let generation = 0;
const owned = computed(
  () => !!detail.value && detail.value.assigneeId === adminSession.me?.adminId,
);
const canHandle = computed(
  () =>
    owned.value &&
    ["PROCESSING", "NEEDS_ACTION"].includes(detail.value?.state || "") &&
    !!adminSession.me?.capabilities.includes("CASE_HANDLE"),
);
const choices = computed(() =>
  detail.value?.kind === "FEEDBACK"
    ? ["ANSWERED", "RECORDED"]
    : [
        "NO_VIOLATION",
        "DUPLICATE",
        "REQUIRES_GOVERNANCE",
        "GOVERNANCE_COMPLETED",
      ],
);
const frozen = computed(() => preview.value || busy.value || !!pending.value);
async function load() {
  if (preview.value) {
    detail.value = exampleCase;
    context.value = exampleContext;
    return;
  }
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  releaseImages();
  detail.value = undefined;
  context.value = undefined;
  try {
    const [d, c] = await Promise.all([
      adminRequest<CaseDetail>(`/admin/cases/${id}`),
      adminRequest<CaseContext>(`/admin/cases/${id}/context`),
    ]);
    if (current !== generation) return;
    detail.value = d;
    context.value = c;
    pending.value = pendingCommand(id);
    if (pending.value) {
      reply.value = pending.value.body.reply || "";
      internalNote.value = pending.value.body.internalNote || "";
      reason.value = pending.value.body.reason || "";
      outcome.value = pending.value.body.outcome || "";
    }
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function act(action: CaseCommand["action"]) {
  if (preview.value || busy.value || !detail.value) return;
  if (pending.value) {
    await submit();
    return;
  }
  error.value = "";
  try {
    const body: CaseCommand["body"] = {
      expectedRevision: detail.value.revision,
      requestId: crypto.randomUUID(),
    };
    if (action === "decisions") {
      if (!choices.value.includes(outcome.value) || !reply.value.trim())
        throw new Error("请选择处理结论并填写向本人公开的答复");
      body.outcome = outcome.value;
      body.reply = reply.value;
      body.internalNote = internalNote.value;
    }
    if (action === "release") {
      if (!reason.value.trim()) throw new Error("请填写释放原因");
      body.reason = reason.value;
    }
    const command = { action, caseId: id, body };
    storeCommand(command);
    pending.value = command;
    await submit();
  } catch (e) {
    error.value = message(e);
  }
}
async function submit() {
  if (!pending.value || preview.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    const command = pending.value;
    await adminRequest(
      `/admin/cases/${id}/${command.action}`,
      "POST",
      command.body,
    );
    forgetCommand(id);
    pending.value = null;
    notice.value = "操作回执已确认，当前状态以最新读取为准。";
    await load();
  } catch (e) {
    error.value = `${message(e)} 原请求与输入已保留，可按原请求核对。`;
  } finally {
    busy.value = false;
  }
}
async function reconcile() {
  if (preview.value || busy.value) return;
  await load();
  if (
    detail.value &&
    confirm(
      "已读取最新状态和历史。请核对原操作是否执行；确认结束本机待确认请求？这不会撤销服务端操作。",
    )
  ) {
    forgetCommand(id);
    pending.value = null;
    notice.value = "已结束原请求，请依据当前修订和处理历史继续。";
  }
}
function releaseImages() {
  Object.values(images.value).forEach(URL.revokeObjectURL);
  images.value = {};
  imageError.value = "";
}
async function loadImages() {
  if (preview.value || !detail.value || busy.value || loadingImages.value)
    return;
  loadingImages.value = true;
  releaseImages();
  const current = generation;
  try {
    const ids = new Set([
      ...detail.value.images.map((i) => i.id),
      ...(context.value?.media.map((i) => i.id) || []),
    ]);
    for (const image of ids) {
      const blob = await adminRequest<Blob>(
        `/admin/cases/${id}/media/${image}`,
        "GET",
        undefined,
        false,
        true,
      );
      const url = URL.createObjectURL(blob);
      if (current !== generation) {
        URL.revokeObjectURL(url);
        return;
      }
      images.value = { ...images.value, [image]: url };
    }
  } catch (e) {
    imageError.value = message(e);
  } finally {
    loadingImages.value = false;
  }
}
const markdownImages = computed(() =>
  Object.fromEntries(
    Object.entries(images.value).flatMap(([image, url]) => [
      [`/api/v1/media/images/${image}`, url],
      [`/api/v1/media/comment-images/${image}`, url],
    ]),
  ),
);
onMounted(load);
watch(() => adminSession.status, load);
onBeforeUnmount(() => {
  generation++;
  releaseImages();
});
</script>
<template>
  <h1 class="console-title">
    案件 {{ preview ? "DEMO-C102" : id }} · 上下文与处理
  </h1>
  <AdminGate :preview="preview"
    ><p v-if="error" class="error" role="alert">
      {{ error }} <button v-if="!pending" @click="load">重新读取案件</button>
    </p>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <p v-if="busy" role="status">正在读取或处理案件…</p>
    <p v-if="detail?.articleId && !preview" class="console-note">
      需要先执行内容治理时，<RouterLink
        :to="{
          path: '/admin/content',
          query: {
            article: String(detail.articleId),
            comment: detail.commentId ? String(detail.commentId) : undefined,
          },
        }"
        >前往对应文章与评论巡查</RouterLink
      >。完成后返回并重新读取案件；后端核对真实治理事件后才允许选择“已执行治理并闭案”。
    </p>
    <section v-if="pending" class="console-note">
      <h2>上次操作结果待确认</h2>
      <p>
        原修订
        {{ pending.body.expectedRevision }}
        与请求键已保留，重复请求不创建第二次处理。
      </p>
      <div class="card-actions">
        <button :disabled="busy" @click="submit">按原请求核对</button
        ><button :disabled="busy" @click="reconcile">
          核对最新状态并结束本次请求
        </button>
      </div>
    </section>
    <div v-if="detail && context" class="console-split console-case-split">
      <section class="console-panel console-context">
        <h2>关联文章与必要上下文</h2>
        <span class="console-badge">{{
          preview ? "公开 · 待核查" : caseStates[detail.state]
        }}</span
        ><template v-if="context.articleAvailable && context.article"
          ><h3 class="console-article-title">{{ context.article.title }}</h3>
          <p class="console-muted">{{ context.article.summary }}</p>
          <div class="console-context-preview">
            <h3>受控上下文预览</h3>
            <MarkdownView
              :body="context.article.body"
              block-images
              :image-sources="markdownImages"
            />
          </div>
          <p v-if="!preview" class="hint">
            本案固定公开版本 {{ context.article.versionId }} · 当前文章状态
            {{ context.article.currentState }}
          </p></template
        >
        <p v-else class="console-muted">
          本案未关联可读取的文章版本，未提供的历史内容不会补造。
        </p>
        <div
          v-for="comment in context.comments"
          :key="`${comment.role}:${comment.commentId}`"
          class="console-comment"
        >
          <h3>
            {{
              comment.role === "TARGET"
                ? "目标评论"
                : comment.role === "ROOT"
                  ? "主评论"
                  : "回复上下文"
            }}
            · {{ comment.commentId }}
          </h3>
          <p>
            {{
              comment.available
                ? comment.body
                : "此上下文不可读取，仅保留必要标识。"
            }}
          </p>
        </div>
        <h2>提交者说明与私人证据</h2>
        <p class="console-muted">说明：{{ detail.description }}</p>
        <p class="console-muted">
          证据图片：{{ detail.images.length }} 张，仅案件处理范围可见。
        </p>
        <button
          v-if="!preview && (detail.images.length || context.media.length)"
          :disabled="loadingImages"
          @click="loadImages"
        >
          读取本案证据与关联图片
        </button>
        <p v-if="imageError" class="error" role="alert">{{ imageError }}</p>
        <div class="console-evidence">
          <figure v-for="(url, image) in images" :key="image">
            <img :src="url" alt="本案授权图片" />
          </figure>
        </div>
        <RouterLink
          v-if="context.article?.currentState === 'PUBLISHED' && !preview"
          class="button console-object-link"
          :to="`/articles/${context.article.articleId}`"
          >查看当前公开对象</RouterLink
        ><button v-else class="console-object-link" disabled>查看原对象</button>
      </section>
      <form
        class="console-panel console-decision"
        @submit.prevent="act('decisions')"
      >
        <h2>处理决定</h2>
        <p v-if="!preview" class="console-muted">
          {{ caseStates[detail.state] }} · 修订 {{ detail.revision }} ·
          {{
            detail.assigneeId === null
              ? "未分配"
              : owned
                ? "本人受理"
                : `管理员 ${detail.assigneeId} 受理`
          }}
        </p>
        <button
          v-if="detail.state === 'OPEN'"
          class="console-primary"
          :disabled="
            frozen || !adminSession.me?.capabilities.includes('CASE_HANDLE')
          "
          type="button"
          @click="act('claim')"
        >
          受理此案件
        </button>
        <fieldset :disabled="frozen || !canHandle">
          <label
            >处理动作 *<input
              v-if="preview"
              value="隐藏内容 / 无违规 / 补充说明"
              disabled
            /><select v-else v-model="outcome" aria-label="处理结论">
              <option value="">请选择处理结论</option>
              <option v-for="choice in choices" :key="choice" :value="choice">
                {{ outcomes[choice] }}
              </option>
            </select></label
          ><label
            >{{ preview ? "处理理由 *" : "公开答复 / 处理理由 *"
            }}<textarea
              v-model="reply"
              rows="4"
              maxlength="10000"
              required
              placeholder="写明事实、依据与影响范围。"
            ></textarea></label
          ><label v-if="preview"
            >负责人<input value="管理员甲" readonly /></label
          ><label v-else
            >内部备注<textarea
              v-model="internalNote"
              rows="2"
              maxlength="10000"
              placeholder="仅管理处理范围可见"
            ></textarea>
          </label>
          <p class="console-positive">
            {{
              preview ? "向本人发送处理结果通知" : "处理答复与内部备注分别保留"
            }}
          </p>
          <button class="console-primary" type="submit">
            确认处理并留存记录
          </button>
        </fieldset>
        <p class="console-muted">执行失败时保留当前输入，案件不标成已完成。</p>
        <div v-if="canHandle && !preview" class="console-release">
          <label
            >释放原因<textarea
              v-model="reason"
              :disabled="frozen"
              rows="2"
              maxlength="10000"
            ></textarea></label
          ><button type="button" :disabled="frozen" @click="act('release')">
            释放回待受理
          </button>
        </div>
        <p v-if="!preview" class="hint">
          “交接后续治理”只记为待后续治理，不会隐藏内容、限制账号或改动贡献。
        </p>
      </form>
    </div>
    <p v-if="preview" class="console-note console-followup">
      处置后公开列表、正文、收藏与通知按目标状态一致展示。治理记录保留，不因作者删除内容而抹除。
    </p>
    <section v-if="detail && !preview" class="console-panel console-history">
      <h2>处理时间线</h2>
      <ol>
        <li v-for="entry in detail.history" :key="entry.revision">
          <strong
            >修订 {{ entry.revision }} · {{ caseStates[entry.state] }} ·
            {{ outcomes[entry.outcome || ""] || entry.action }}</strong
          ><time>{{ dateText(entry.occurredAt) }}</time>
          <p v-if="entry.reply">公开答复：{{ entry.reply }}</p>
          <p v-if="entry.internalNote">内部备注：{{ entry.internalNote }}</p>
          <p v-if="entry.reason">释放原因：{{ entry.reason }}</p>
          <small>{{ entry.actorDomain }} · 操作人 {{ entry.actorId }}</small>
        </li>
      </ol>
    </section></AdminGate
  >
</template>
