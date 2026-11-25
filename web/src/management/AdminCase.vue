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
import CaseContributions from "./CaseContributions.vue";
import ConsolePageHeader from "./ConsolePageHeader.vue";
import ConsoleSteps from "./ConsoleSteps.vue";
import MarkdownView from "../components/MarkdownView.vue";
import { dateText, shortDateText } from "../lib/content";
import { ApiError } from "../lib/api";
import { caseListPath, consoleError } from "./console-ux";
import ConsoleConfirm from "./ConsoleConfirm.vue";
import ConsoleReadState from "./ConsoleReadState.vue";
const route = useRoute();
const id = Number(route.params.id);
const preview = computed(() => route.query.preview === "1");
const detail = ref<CaseDetail>();
const context = ref<CaseContext>();
const busy = ref(false);
const reading = ref(false);
const readError = ref("");
const reviewRevision = ref<number>();
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
const returnTo = computed(() => caseListPath(route.query.from));
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
const frozen = computed(
  () =>
    preview.value ||
    busy.value ||
    reading.value ||
    !!pending.value ||
    reviewRevision.value !== undefined,
);
async function load() {
  if (preview.value) {
    detail.value = exampleCase;
    context.value = exampleContext;
    return;
  }
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  reading.value = true;
  readError.value = "";
  try {
    const [d, c] = await Promise.allSettled([
      adminRequest<CaseDetail>(`/admin/cases/${id}`),
      adminRequest<CaseContext>(`/admin/cases/${id}/context`),
    ]);
    if (current !== generation) return false;
    if (d.status === "fulfilled") {
      detail.value = d.value;
      const stored = pendingCommand(id);
      if (stored && !pending.value) {
        reply.value = stored.body.reply || "";
        internalNote.value = stored.body.internalNote || "";
        reason.value = stored.body.reason || "";
        outcome.value = stored.body.outcome || "";
      }
      pending.value = stored;
    } else readError.value = consoleError(d.reason);
    if (c.status === "fulfilled") context.value = c.value;
    else
      readError.value += (readError.value ? " " : "") + consoleError(c.reason);
    return d.status === "fulfilled";
  } finally {
    if (current === generation) reading.value = false;
  }
}
async function useLatestRevision() {
  if (busy.value || reading.value || !(await load()) || !detail.value) return;
  if (
    !(await modal.value?.ask({
      title: "按最新案件修订继续？",
      detail: `案件 ${id} 当前修订为 ${detail.value.revision}，状态为${caseStates[detail.value.state]}。请核对处理时间线，保留的选项和理由仍需重新提交。`,
      action: "使用最新修订",
    }))
  )
    return;
  reviewRevision.value = undefined;
  error.value = "";
  notice.value = "已读取最新修订。请核对选项和理由后重新提交。";
}
async function act(action: CaseCommand["action"]) {
  if (frozen.value || !detail.value) return;
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
      const approved = await modal.value?.ask({
        title: `复核案件 ${id} 的处理决定`,
        detail: `${outcomes[outcome.value]} · 修订 ${body.expectedRevision}。公开答复将通知提交人。\n\n${reply.value}`,
        action: "提交处理决定",
        danger: true,
      });
      if (!approved || frozen.value) return;
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
    error.value = consoleError(e);
  }
}
async function submit() {
  if (!pending.value || preview.value || busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const command = pending.value;
    await adminRequest(
      `/admin/cases/${id}/${command.action}`,
      "POST",
      command.body,
    );
    forgetCommand(id);
    pending.value = null;
    notice.value = "操作回执已确认。";
    if ((await load()) && detail.value)
      notice.value += `当前状态为${caseStates[detail.value.state]}，修订 ${detail.value.revision}。`;
    else notice.value += "状态与时间线待重新读取。";
  } catch (e) {
    if (
      e instanceof ApiError &&
      e.status === 409 &&
      e.code === "REVISION_CONFLICT"
    ) {
      reviewRevision.value = pending.value?.body.expectedRevision;
      forgetCommand(id);
      pending.value = null;
      error.value =
        consoleError(e) + " 处理选项和理由已保留，请读取最新修订并核对后继续。";
    } else {
      error.value = consoleError(e) + " 原请求与输入已保留，可按原请求核对。";
    }
  } finally {
    busy.value = false;
  }
}
async function reconcile() {
  if (preview.value || busy.value) return;
  if (!(await load())) return;
  if (
    detail.value &&
    (await modal.value?.ask({
      title: `结束案件 ${id} 的待确认请求？`,
      detail:
        "请核对最新状态和时间线。结束本机待确认记录后，服务端已执行的操作仍然有效。",
      action: "结束本机记录",
    }))
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
    imageError.value = consoleError(e);
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
function scrollToContributions() {
  document.getElementById("case-contributions")?.scrollIntoView({ block: "start" });
}
const stateLabel = computed(() =>
  detail.value ? caseStates[detail.value.state] || detail.value.state : "",
);
const step = computed(
  () =>
    ({ OPEN: 0, PROCESSING: 1, NEEDS_ACTION: 2, CLOSED: 3 })[
      detail.value?.state || ""
    ] ?? 0,
);
const tone = computed(
  () =>
    ({
      OPEN: "is-warning",
      PROCESSING: "is-positive",
      NEEDS_ACTION: "is-neutral",
      CLOSED: "is-positive",
    })[detail.value?.state || ""] || "is-neutral",
);
const targetText = computed(() => {
  const d = detail.value;
  if (!d) return "";
  return d.targetType === "COMMENT"
    ? `评论 CMT-${d.commentId}`
    : d.targetType === "ARTICLE"
      ? `文章 ART-${d.articleId}`
      : "无关联对象";
});
const assigneeText = computed(() => {
  const d = detail.value;
  if (!d) return "";
  return d.assigneeId === null
    ? "待认领"
    : owned.value
      ? "本人"
      : `管理员 ${d.assigneeId}`;
});
const stateBanner = computed(() => {
  if (!detail.value || pending.value) return "";
  switch (detail.value.state) {
    case "PROCESSING":
      return owned.value
        ? "已认领。当前处理人为你，可以进入内容治理。"
        : `已由 ${assigneeText.value} 认领，可查看事实与处理记录。`;
    case "NEEDS_ACTION":
      return "已交接后续治理。后端回读治理事件后，可选择“已执行治理并闭案”。";
    case "CLOSED":
      return "案件已闭案。处理答复与审计记录已留存。";
    default:
      return "";
  }
});
onMounted(load);
watch(() => adminSession.status, load);
onBeforeUnmount(() => {
  generation++;
  releaseImages();
});
</script>
<template>
  <AdminGate :preview="preview">
    <div class="console-page">
      <ConsoleConfirm ref="modal" />
      <ConsoleReadState
        :error="readError"
        :stale="!!detail"
        :loading="reading"
        label="正在读取案件…"
        @retry="load"
      />
      <p v-if="error" class="console-banner is-error" role="alert">
        {{ error }}
        <span class="console-banner-actions"
          ><button v-if="!pending && reviewRevision === undefined" type="button" @click="load">
            重新读取案件</button
          ></span
        >
      </p>
      <button
        v-if="reviewRevision !== undefined"
        type="button"
        :disabled="reading || busy"
        @click="useLatestRevision"
      >
        读取最新修订并核对
      </button>
      <p v-if="notice" class="console-banner is-success" role="status">
        {{ notice }}
      </p>
      <p v-if="pending" class="console-banner is-warning" role="status">
        上次操作结果待确认：原修订 {{ pending.body.expectedRevision }}
        与请求键已保留，重复请求不创建第二次处理。
        <span class="console-banner-actions"
          ><button type="button" :disabled="busy" @click="submit">
            按原请求核对</button
          ><button type="button" :disabled="busy" @click="reconcile">
            核对最新状态并结束本次请求
          </button></span
        >
      </p>
      <p v-if="busy" class="console-muted" role="status">
        正在读取或处理案件…
      </p>
      <template v-if="detail && context">
        <ConsolePageHeader
          variant="plain"
          :back="{ label: '返回案件队列', to: returnTo }"
          :title="`举报详情 · ${stateLabel}`"
          :meta="`CASE-${preview ? 'DEMO-C102' : id}  ·  修订 ${detail.revision}`"
        >
          <template #aside>
            <span class="console-tag" :class="tone">{{ stateLabel }}</span>
          </template>
        </ConsolePageHeader>
        <ConsoleSteps
          :steps="['事实与证据', '处理决定', '复核提交']"
          :current="step"
        />
        <p v-if="stateBanner" class="console-banner is-success" role="status">
          {{ stateBanner }}
        </p>
        <div class="console-split">
          <section class="console-panel" aria-label="事实与证据">
            <h2 class="console-card-title">事实与证据</h2>
            <p class="console-card-desc">
              打开关联内容前先核对固定公开版本。
            </p>
            <dl class="console-facts">
              <div>
                <dt>举报对象</dt>
                <dd>
                  {{ targetText
                  }}<template v-if="context.articleAvailable && context.article">
                    · {{ context.article.title }}</template
                  >
                </dd>
              </div>
              <div>
                <dt>固定公开版本</dt>
                <dd>
                  <template v-if="context.articleAvailable && context.article">
                    v{{ context.article.versionId }} ·
                    {{ dateText(context.article.publishedAt) }}
                    <small
                      >当前文章状态：{{ context.article.currentState }}</small
                    >
                  </template>
                  <template v-else>
                    本案未关联可读取的文章版本，未提供的历史内容不会补造。
                  </template>
                </dd>
              </div>
              <div>
                <dt>举报原因</dt>
                <dd>{{ detail.description }}</dd>
              </div>
              <div>
                <dt>附件与线索</dt>
                <dd>
                  证据图片 {{ detail.images.length }} 张，仅案件处理范围可见。
                  <button
                    v-if="!preview && (detail.images.length || context.media.length)"
                    class="console-link"
                    type="button"
                    :disabled="loadingImages"
                    @click="loadImages"
                  >
                    {{ loadingImages ? "正在读取图片…" : "读取本案证据与关联图片" }}
                  </button>
                </dd>
              </div>
              <div>
                <dt>当前处理人</dt>
                <dd>{{ assigneeText }}</dd>
              </div>
            </dl>
            <p class="console-callout">
              <strong>证据摘要 · </strong>举报属于待核查线索，不会自动认定违规。处理前请在固定公开版本定位相关段落。
            </p>
            <template v-if="context.articleAvailable && context.article">
              <div class="console-case-content">
                <h3 class="console-article-title">{{ context.article.title }}</h3>
                <p class="console-muted">{{ context.article.summary }}</p>
                <MarkdownView
                  :body="context.article.body"
                  block-images
                  :image-sources="markdownImages"
                />
              </div>
            </template>
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
                · CMT-{{ comment.commentId }}
              </h3>
              <p>
                {{
                  comment.available
                    ? comment.body
                    : "此上下文不可读取，仅保留必要标识。"
                }}
              </p>
            </div>
            <p v-if="imageError" class="console-banner is-error" role="alert">
              {{ imageError }}
            </p>
            <div class="console-evidence">
              <figure v-for="(url, image) in images" :key="image">
                <img :src="url" alt="本案授权图片" />
              </figure>
            </div>
          </section>
          <form
            id="case-decision-form"
            class="console-panel"
            @submit.prevent="act('decisions')"
          >
            <h2 class="console-card-title">
              {{ detail.state === "OPEN" ? "案件处理" : "处理决定" }}
            </h2>
            <p class="console-card-desc">
              {{
                detail.state === "CLOSED"
                  ? "核对处理结果、理由和已记录的事件。"
                  : detail.state === "OPEN"
                    ? "认领后可继续处置或提交结案决定。"
                    : "先核对公开版本，再提交处理结论。"
              }}
            </p>
            <div v-if="detail.state === 'OPEN'" class="console-form">
              <label class="console-field"
                ><span>当前处理人</span
                ><input value="待认领" readonly /></label
              >
              <p class="console-form-note">
                认领后可继续处置或提交结案决定。
              </p>
            </div>
            <fieldset
              v-else-if="detail.state !== 'CLOSED'"
              class="console-form"
              :disabled="frozen || !canHandle"
            >
              <label class="console-field"
                ><span>处理动作</span
                ><select v-model="outcome" aria-label="处理结论">
                  <option value="">请选择处理结论</option>
                  <option v-for="choice in choices" :key="choice" :value="choice">
                    {{ outcomes[choice] }}
                  </option></select
                ></label
              ><label class="console-field"
                ><span>处理理由</span
                ><textarea
                  v-model="reply"
                  rows="4"
                  maxlength="10000"
                  required
                  placeholder="写明事实、依据与影响范围，向本人公开。"
                ></textarea></label
              ><label class="console-field"
                ><span>内部备注</span
                ><textarea
                  v-model="internalNote"
                  rows="2"
                  maxlength="10000"
                  placeholder="仅管理处理范围可见"
                ></textarea></label
              >
              <p class="console-form-note">
                处理答复与内部备注分别保留。执行失败时保留当前输入，案件不标成已完成。
              </p>
            </fieldset>
            <dl v-else class="console-kv">
              <div>
                <dt>最后决定</dt>
                <dd>
                  {{
                    outcomes[detail.history.at(-1)?.outcome || ""] ||
                    detail.history.at(-1)?.action ||
                    "已闭案"
                  }}
                </dd>
              </div>
              <div v-if="detail.history.at(-1)?.reply">
                <dt>处理理由</dt>
                <dd>{{ detail.history.at(-1)?.reply }}</dd>
              </div>
            </dl>
            <p
              v-if="detail.articleId && detail.state !== 'CLOSED' && !preview"
              class="console-card-foot"
            >
              需要先执行内容治理时，<RouterLink
                :to="{
                  path: '/admin/content',
                  query: {
                    article: String(detail.articleId),
                    comment: detail.commentId
                      ? String(detail.commentId)
                      : undefined,
                    case: canHandle ? String(id) : undefined,
                  },
                }"
                >打开关联内容</RouterLink
              >。完成后返回并重新读取案件；后端核对真实治理事件后才允许选择“已执行治理并闭案”。
            </p>
            <div v-if="canHandle && !preview" class="console-release">
              <label class="console-field"
                ><span>释放原因</span
                ><textarea
                  v-model="reason"
                  :disabled="frozen"
                  rows="2"
                  maxlength="10000"
                  placeholder="说明为何不再由本人处理"
                ></textarea></label
              >
              <button
                class="console-link"
                type="button"
                :disabled="frozen"
                @click="act('release')"
              >
                释放回待受理
              </button>
            </div>
            <p v-if="preview" class="console-form-note">
              处置后公开列表、正文、收藏与通知按目标状态一致展示。治理记录保留，不因作者删除内容而抹除。
            </p>
          </form>
        </div>
        <CaseContributions
          v-if="
            detail.kind === 'REPORT' && detail.targetType === 'ARTICLE' && !preview
          "
          :key="id"
          :case-id="id"
          :revision="detail.revision"
          :can-handle="canHandle"
        />
        <section
          v-if="detail.history.length && !preview"
          class="console-panel"
          aria-label="处理时间线"
        >
          <h2 class="console-card-title">处理时间线</h2>
          <p class="console-card-desc">
            最近发生在前。修订号与操作人用于核对处置过程。
          </p>
          <ol class="console-timeline">
            <li
              v-for="entry in [...detail.history].reverse()"
              :key="entry.revision"
            >
              <span class="console-timeline-time">{{
                shortDateText(entry.occurredAt)
              }}</span>
              <div class="console-timeline-body">
                <h3>
                  修订 {{ entry.revision }} · {{ caseStates[entry.state] }} ·
                  {{ outcomes[entry.outcome || ""] || entry.action }}
                </h3>
                <p v-if="entry.reply">公开答复：{{ entry.reply }}</p>
                <p v-if="entry.internalNote">
                  内部备注：{{ entry.internalNote }}
                </p>
                <p v-if="entry.reason">释放原因：{{ entry.reason }}</p>
                <p>
                  {{ entry.actorDomain }} · 操作人 {{ entry.actorId
                  }}<template v-if="entry.assigneeId !== null">
                    · 认领人 {{ entry.assigneeId }}</template
                  >
                </p>
              </div>
            </li>
          </ol>
        </section>
        <footer v-if="!pending" class="console-footer">
          <div class="console-footer-actions">
            <RouterLink
              v-if="detail.state !== 'PROCESSING' && detail.state !== 'NEEDS_ACTION'"
              class="button console-secondary"
              :to="returnTo"
              >返回队列</RouterLink
            >
            <template v-if="detail.state === 'OPEN'">
              <button
                class="console-primary"
                type="button"
                :disabled="
                  frozen || !adminSession.me?.capabilities.includes('CASE_HANDLE')
                "
                @click="act('claim')"
              >
                {{ busy ? "正在认领…" : "认领案件" }}
              </button>
            </template>
            <template
              v-else-if="detail.state === 'PROCESSING' || detail.state === 'NEEDS_ACTION'"
            >
              <a
                v-if="detail.kind === 'REPORT' && detail.targetType === 'ARTICLE'"
                class="button console-secondary"
                href="#case-contributions"
                @click.prevent="scrollToContributions"
                >查看关联投币</a
              >
              <RouterLink
                v-if="detail.articleId"
                class="button"
                :class="{ 'console-secondary': !canHandle }"
                :to="{
                  path: '/admin/content',
                  query: {
                    article: String(detail.articleId),
                    comment: detail.commentId
                      ? String(detail.commentId)
                      : undefined,
                    case: canHandle ? String(id) : undefined,
                  },
                }"
                >打开关联内容</RouterLink
              >
              <button
                v-if="canHandle"
                class="console-primary"
                type="submit"
                form="case-decision-form"
                :disabled="frozen"
              >
                {{ busy ? "正在提交…" : "提交处理决定" }}
              </button>
            </template>
            <RouterLink
              v-else-if="detail.state === 'CLOSED'"
              class="button console-primary"
              to="/admin/audit"
              >查看审计记录</RouterLink
            >
          </div>
        </footer>
      </template>
      <section v-else-if="!busy && !reading && !error && !readError" class="console-panel console-gate">
        <h2>案件尚未读取</h2>
        <p>请从案件队列进入，或重新读取案件。</p>
      </section>
    </div>
  </AdminGate>
</template>
