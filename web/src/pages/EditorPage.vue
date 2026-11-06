<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { onBeforeRouteLeave, useRoute } from "vue-router";
import { ApiError, get, message, request, send } from "../lib/api";
import {
  readPrivate,
  removePrivate,
  session,
  writePrivate,
} from "../lib/session";
import {
  fromDraft,
  publishErrors,
  sourceName,
  stateName,
} from "../lib/content";
import type { Catalog, Draft, Form, ImageAsset } from "../types";
import MemberGate from "../components/MemberGate.vue";
import MarkdownView from "../components/MarkdownView.vue";

interface PublishRequest {
  requestId: string;
  expectedRevision: number;
}
interface UploadRequest {
  requestId: string;
  name: string;
  hash: string;
}
interface Recovery {
  form: Form;
  baseRevision: number;
  pendingPublish: PublishRequest | null;
  pendingUpload: UploadRequest | null;
}
const route = useRoute();
const id = String(route.params.id);
const form = ref<Form>();
const draft = ref<Draft>();
const catalog = ref<Catalog>();
const revision = ref(0);
const saved = ref("");
const busy = ref(false);
const error = ref("");
const notice = ref("");
const preview = ref(false);
const recovery = ref<Recovery | null>(null);
const conflict = ref<Draft>();
const pendingPublish = ref<PublishRequest | null>(null);
const pendingUpload = ref<UploadRequest | null>(null);
const savedAt = ref("");
const bodyInput = ref<HTMLTextAreaElement>();
const uploadInput = ref<HTMLInputElement>();
const publishDialog = ref<HTMLDialogElement>();
let uploadFile: File | undefined;
const dirty = computed(
  () => !!form.value && JSON.stringify(form.value) !== saved.value,
);
const readOnly = computed(
  () => !draft.value || ["ADMIN_HIDDEN", "DELETED"].includes(draft.value.state),
);
const locked = computed(
  () =>
    busy.value ||
    readOnly.value ||
    !!conflict.value ||
    !!pendingPublish.value ||
    !!recovery.value,
);
const tagGroups = [
  { group: "jobs", field: "jobIds", label: "目标岗位" },
  { group: "majors", field: "majorIds", label: "文章适用专业" },
  { group: "companies", field: "companyIds", label: "公司（可选）" },
  { group: "topics", field: "topicIds", label: "主题（可选）" },
] as const;
const comparison = computed(() => {
  if (!form.value || !conflict.value) return [];
  const fields = (value: Form) => {
    const tags = tagGroups.map((group) => ({
      label: group.label,
      text:
        value[group.field]
          .map(
            (id) =>
              catalog.value?.[group.group].find((tag) => tag.id === id)?.name ||
              `当前目录未找到（${id}）`,
          )
          .join("、") || "未选择",
    }));
    return [
      { label: "标题", text: value.title || "未填写" },
      { label: "摘要", text: value.summary || "未填写" },
      ...tags,
      { label: "经历发生阶段", text: value.experienceStage || "未填写" },
      {
        label: "内容来源",
        text: sourceName[value.sourceType || ""] || "未选择",
      },
      { label: "来源说明", text: value.sourceNote || "未填写" },
      {
        label: "资料链接",
        text:
          value.sourceLinks
            .map((link) => `${link.label}\n${link.url}`)
            .join("\n\n") || "未添加",
      },
      { label: "完整正文（Markdown）", text: value.body || "未填写" },
    ];
  };
  const local = fields(form.value);
  const server = fields(fromDraft(conflict.value));
  return local.map((field, index) => ({
    label: field.label,
    local: field.text,
    server: server[index]?.text || "",
    changed: field.text !== server[index]?.text,
  }));
});
function checkpoint() {
  if (!form.value || session.status !== "ready") return;
  try {
    writePrivate(`draft:${id}`, {
      form: form.value,
      baseRevision: revision.value,
      pendingPublish: pendingPublish.value,
      pendingUpload: pendingUpload.value,
    });
  } catch {
    error.value = "本机副本保存失败，请先下载副本并保持此页面。";
  }
}
function apply(value: Draft) {
  draft.value = value;
  revision.value = value.revision;
  form.value = fromDraft(value);
  saved.value = JSON.stringify(form.value);
}
async function load() {
  if (session.status !== "ready") return;
  busy.value = true;
  error.value = "";
  try {
    const [value, categories] = await Promise.all([
      get<Draft>(`/me/articles/${id}/draft`, true),
      get<Catalog>("/catalog"),
    ]);
    catalog.value = categories;
    apply(value);
    const backup = readPrivate<Recovery>(`draft:${id}`);
    if (
      backup &&
      (JSON.stringify(backup.form) !== saved.value ||
        backup.pendingPublish ||
        backup.pendingUpload)
    )
      recovery.value = backup;
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
function restoreBackup() {
  const value = recovery.value;
  if (!value) return;
  form.value = JSON.parse(JSON.stringify(value.form)) as Form;
  pendingPublish.value = value.pendingPublish;
  pendingUpload.value = value.pendingUpload;
  if (value.baseRevision !== revision.value && !value.pendingPublish)
    conflict.value = draft.value;
  revision.value = value.baseRevision;
  recovery.value = null;
  notice.value = "已恢复本机副本，尚未覆盖服务器草稿。";
}
function discardBackup() {
  if (
    !confirm(
      "以服务器草稿继续？当前标签页保存的本机修改与待确认请求将被移除。请先下载副本。",
    )
  )
    return;
  recovery.value = null;
  removePrivate(`draft:${id}`);
}
function downloadBackup() {
  downloadJson(
    recovery.value || {
      form: form.value,
      baseRevision: revision.value,
      pendingPublish: pendingPublish.value,
      pendingUpload: pendingUpload.value,
    },
    `城院帮-草稿-${id}.json`,
  );
}
function downloadServerBackup() {
  if (conflict.value)
    downloadJson(
      {
        form: fromDraft(conflict.value),
        baseRevision: conflict.value.revision,
      },
      `城院帮-服务器草稿-${id}.json`,
    );
}
function downloadJson(value: unknown, filename: string) {
  const content = JSON.stringify(value, null, 2);
  const url = URL.createObjectURL(
    new Blob([content], { type: "application/json" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
async function save(): Promise<boolean> {
  if (locked.value || !form.value) return false;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    if (new TextEncoder().encode(form.value.body).length > 1048576)
      throw new Error("正文超过 1MiB，请保留副本并缩短内容");
    checkpoint();
    const result = await send<Draft>(`/me/articles/${id}/draft`, "PUT", {
      ...form.value,
      expectedRevision: revision.value,
    });
    apply(result);
    savedAt.value = new Date().toLocaleTimeString("zh-CN");
    notice.value = "草稿已保存到服务器。";
    checkpoint();
    return true;
  } catch (e) {
    error.value = message(e);
    if (e instanceof ApiError && e.status === 409) {
      try {
        conflict.value = await get<Draft>(`/me/articles/${id}/draft`, true);
      } catch (r) {
        error.value += ` ${message(r)}`;
      }
    }
    return false;
  } finally {
    busy.value = false;
  }
}
function prepareMerge() {
  if (!conflict.value) return;
  revision.value = conflict.value.revision;
  draft.value = conflict.value;
  saved.value = JSON.stringify(fromDraft(conflict.value));
  conflict.value = undefined;
  pendingPublish.value = null;
  checkpoint();
  notice.value = "本机编辑已保留，请对照下载的服务器版本手动合并，再保存。";
}
function useServer() {
  if (!conflict.value || !confirm("替换编辑区为服务器版本？请先下载本机副本。"))
    return;
  apply(conflict.value);
  conflict.value = undefined;
  pendingPublish.value = null;
  checkpoint();
}
async function publish() {
  if (pendingPublish.value) {
    await submitPublish();
    return;
  }
  if (!form.value || locked.value) return;
  if (pendingUpload.value) {
    error.value = "请先核对待确认的图片上传，再发布文章。";
    return;
  }
  const missing = publishErrors(form.value);
  if (missing.length) {
    error.value = `发布前请${missing.join("、")}。`;
    return;
  }
  if (dirty.value && !(await save())) return;
  preview.value = true;
}
async function confirmPublish() {
  if (!form.value || locked.value) return;
  try {
    const value = {
      expectedRevision: revision.value,
      requestId: crypto.randomUUID(),
    };
    writePrivate(`draft:${id}`, {
      form: form.value,
      baseRevision: revision.value,
      pendingPublish: value,
      pendingUpload: pendingUpload.value,
    });
    pendingPublish.value = value;
    await submitPublish();
  } catch (e) {
    error.value = message(e);
  }
}
async function submitPublish() {
  if (!pendingPublish.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await send(`/me/articles/${id}/publish`, "POST", pendingPublish.value);
    const result = await get<Draft>(`/me/articles/${id}/draft`, true);
    apply(result);
    pendingPublish.value = null;
    preview.value = false;
    notice.value = `发布回执已确认；当前状态：${stateName[result.state]}。`;
    checkpoint();
  } catch (e) {
    error.value = `${message(e)} 尚未确认发布结果，重试会使用原请求。`;
    if (e instanceof ApiError && e.status === 409) {
      try {
        conflict.value = await get<Draft>(`/me/articles/${id}/draft`, true);
      } catch {
        /* Keep original request for retry. */
      }
    }
  } finally {
    busy.value = false;
  }
}
async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file || locked.value) return;
  busy.value = true;
  error.value = "";
  try {
    if (
      !["image/jpeg", "image/png"].includes(file.type) ||
      file.size > 10 * 1024 * 1024
    )
      throw new Error("请选择不超过 10MiB 的 JPEG 或 PNG 图片");
    const bitmap = await createImageBitmap(file);
    const pixels = bitmap.width * bitmap.height;
    bitmap.close();
    if (pixels > 25000000)
      throw new Error("图片像素超过 2500 万，请缩小后上传");
    const hash = [
      ...new Uint8Array(
        await crypto.subtle.digest("SHA-256", await file.arrayBuffer()),
      ),
    ]
      .map((n) => n.toString(16).padStart(2, "0"))
      .join("");
    if (pendingUpload.value && pendingUpload.value.hash !== hash)
      throw new Error(
        "上次上传结果未确认，请重新选择同一张原图，保持原请求键重试。",
      );
    if (!pendingUpload.value)
      pendingUpload.value = {
        requestId: crypto.randomUUID(),
        name: file.name,
        hash,
      };
    uploadFile = file;
    checkpoint();
    const data = new FormData();
    data.set("requestId", pendingUpload.value.requestId);
    data.set("file", uploadFile);
    const image = await request<ImageAsset>(`/me/articles/${id}/images`, {
      method: "POST",
      auth: true,
      form: data,
    });
    if (form.value)
      form.value.body += `\n\n![${file.name.replace(/[\[\]\\\r\n]/g, "")}](${image.url})\n`;
    pendingUpload.value = null;
    uploadFile = undefined;
    notice.value = "图片已上传并插入正文，保存草稿后保留引用。";
    checkpoint();
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
    if (uploadInput.value) uploadInput.value.value = "";
  }
}
function insert(before: string, after = "") {
  if (!form.value || locked.value) return;
  const input = bodyInput.value;
  const start = input?.selectionStart ?? form.value.body.length;
  const end = input?.selectionEnd ?? start;
  form.value.body =
    form.value.body.slice(0, start) +
    before +
    form.value.body.slice(start, end) +
    after +
    form.value.body.slice(end);
}
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value || pendingPublish.value || pendingUpload.value) {
    checkpoint();
    e.preventDefault();
    e.returnValue = "";
  }
}
watch(
  form,
  () => {
    if (dirty.value) checkpoint();
  },
  { deep: true },
);
watch(preview, async (value) => {
  await nextTick();
  if (value) publishDialog.value?.showModal();
  else publishDialog.value?.close();
});
watch(
  () => session.status,
  (status) => {
    if (status === "ready" && !form.value) void load();
  },
);
onMounted(() => {
  void load();
  window.addEventListener("beforeunload", beforeUnload);
});
onBeforeUnmount(() => {
  window.removeEventListener("beforeunload", beforeUnload);
  uploadFile = undefined;
});
onBeforeRouteLeave(() => {
  if (dirty.value || pendingPublish.value || pendingUpload.value) {
    checkpoint();
    return confirm(
      "有未保存或待确认的操作，已尽力保留本标签页副本。确认离开编辑器？",
    );
  }
  return true;
});
</script>
<template>
  <MemberGate title="登录后继续创作"
    ><div class="breadcrumb">
      <RouterLink to="/workspace">我的工作区</RouterLink><span>/</span
      ><span>创作经验</span>
    </div>
    <div class="page-heading editor-heading">
      <div>
        <h1>创作经验</h1>
        <p>
          {{ draft ? stateName[draft.state] : "正在读取草稿" }} · 修订
          {{ revision }}<span v-if="dirty"> · 有未保存修改</span
          ><span v-if="savedAt"> · 本次保存 {{ savedAt }}</span>
        </p>
      </div>
      <div class="card-actions">
        <button :disabled="locked || !dirty" @click="save">保存草稿</button
        ><button
          class="primary"
          :disabled="
            busy || (readOnly && !pendingPublish) || !!conflict || !!recovery
          "
          @click="publish"
        >
          {{ pendingPublish ? "核对上次发布" : "预览并发布" }}
        </button>
      </div>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <p v-if="busy" role="status">正在处理，请保持当前页面…</p>
    <section v-if="recovery" class="panel recovery-panel">
      <h2>发现本标签页保存的副本</h2>
      <p>
        本机副本基于修订 {{ recovery.baseRevision }}，服务器为修订
        {{ revision }}。请先核对，恢复不会自动覆盖云端。
      </p>
      <div class="card-actions">
        <button @click="downloadBackup">下载本机副本</button
        ><button class="primary" @click="restoreBackup">
          恢复本机编辑与待确认请求</button
        ><button @click="discardBackup">以服务器草稿继续</button>
      </div>
    </section>
    <section v-if="conflict && form" class="panel conflict-panel" role="alert">
      <h2>草稿版本有冲突，请手动合并</h2>
      <p>
        本机修改基于修订 {{ revision }}，服务器已是修订
        {{ conflict.revision }}。两份内容均未自动覆盖。
      </p>
      <div class="compare-grid">
        <div>
          <h3>本机编辑 · 基于修订 {{ revision }}</h3>
          <dl class="compare-fields">
            <template v-for="field in comparison" :key="field.label"
              ><dt>
                {{ field.label }}<span v-if="field.changed">有差异</span>
              </dt>
              <dd :class="{ changed: field.changed }">
                {{ field.local }}
              </dd></template
            >
          </dl>
        </div>
        <div>
          <h3>服务器版本 · 修订 {{ conflict.revision }}</h3>
          <dl class="compare-fields">
            <template v-for="field in comparison" :key="field.label"
              ><dt>
                {{ field.label }}<span v-if="field.changed">有差异</span>
              </dt>
              <dd :class="{ changed: field.changed }">
                {{ field.server }}
              </dd></template
            >
          </dl>
        </div>
      </div>
      <div class="card-actions">
        <button @click="downloadBackup">下载本机副本</button
        ><button @click="downloadServerBackup">下载服务器副本</button
        ><button @click="useServer">使用服务器版本</button
        ><button class="primary" @click="prepareMerge">
          保留本机内容，按新修订手动合并
        </button>
      </div>
    </section>
    <p v-if="readOnly && draft" class="notice">
      当前文章为{{ stateName[draft.state] }}，不能编辑或发布。
    </p>
    <div v-if="form" class="editor-layout">
      <section class="panel editor-main">
        <fieldset :disabled="locked">
          <label
            >标题<input
              v-model="form.title"
              maxlength="200"
              placeholder="为这段经历起一个具体的标题" /></label
          ><label
            >摘要<textarea
              v-model="form.summary"
              rows="2"
              maxlength="1000"
              placeholder="可选：让读者了解这篇经验适合什么起点"
            ></textarea>
          </label>
          <div class="editor-tools" aria-label="Markdown 工具">
            <button type="button" @click="insert('## ')">标题</button
            ><button type="button" @click="insert('**', '**')">加粗</button
            ><button type="button" @click="insert('\n> ')">引用</button
            ><button type="button" @click="insert('\n- ')">列表</button
            ><button type="button" @click="insert('\n```\n', '\n```\n')">
              代码</button
            ><button type="button" @click="uploadInput?.click()">
              {{ pendingUpload ? "选择原图重试" : "上传图片" }}</button
            ><button
              type="button"
              @click="insert('[资料名称](', 'https://example.com)')"
            >
              资料链接</button
            ><input
              ref="uploadInput"
              class="sr-only"
              type="file"
              accept="image/png,image/jpeg"
              aria-label="上传正文图片"
              @change="upload"
            />
          </div>
        </fieldset>
        <p v-if="pendingUpload" class="notice">
          图片
          {{ pendingUpload.name }}
          上传结果待确认。请重新选择相同原图，沿用原请求重试。
        </p>
        <div class="writing-grid">
          <label class="source-editor"
            >Markdown 编辑<textarea
              ref="bodyInput"
              v-model="form.body"
              :disabled="locked"
              spellcheck="false"
              placeholder="从当时的起点写起，记录准备、投递、面试和复盘…"
            ></textarea>
          </label>
          <section class="preview-column" aria-label="正文安全预览">
            <h2>安全预览</h2>
            <MarkdownView :body="form.body" private-images />
          </section>
        </div>
        <button class="text-button" @click="downloadBackup">
          下载完整草稿副本
        </button>
      </section>
      <aside class="editor-sidebar">
        <section class="panel">
          <h2>发布信息</h2>
          <fieldset :disabled="locked">
            <fieldset
              v-for="group in tagGroups"
              :key="group.field"
              class="tag-fieldset"
            >
              <legend>{{ group.label }}</legend>
              <label
                v-for="tag in catalog?.[group.group]"
                :key="tag.id"
                class="tag-choice"
                ><input
                  v-model="form[group.field]"
                  type="checkbox"
                  :value="tag.id"
                />{{ tag.name }}</label
              >
            </fieldset>
            <label
              >经历发生阶段<input
                v-model="form.experienceStage"
                maxlength="64"
                placeholder="例如：大三暑假" /></label
            ><label
              >内容来源<select v-model="form.sourceType" aria-label="内容来源">
                <option :value="null">请选择来源</option>
                <option value="FIRST_HAND">亲历分享</option>
                <option value="CURATED">资料整理</option>
                <option value="DEMO">演示内容</option>
              </select></label
            ><label
              >来源说明<textarea
                v-model="form.sourceNote"
                rows="3"
                maxlength="2000"
              ></textarea>
            </label>
          </fieldset>
        </section>
        <section class="panel">
          <h2>资料链接</h2>
          <fieldset :disabled="locked">
            <div
              v-for="(link, index) in form.sourceLinks"
              :key="index"
              class="source-link-fields"
            >
              <label
                >资料名称<input v-model="link.label" maxlength="200" /></label
              ><label
                >HTTP / HTTPS 地址<input
                  v-model="link.url"
                  type="url"
                  maxlength="2000" /></label
              ><button
                type="button"
                class="text-button"
                @click="form.sourceLinks.splice(index, 1)"
              >
                移除此资料链接
              </button>
            </div>
            <button
              type="button"
              @click="form.sourceLinks.push({ label: '', url: '' })"
            >
              添加资料链接
            </button>
          </fieldset>
        </section>
        <section class="panel">
          <h2>写作提示</h2>
          <p>当时起点 / 准备 / 投递面试 / 复盘，按需展开。</p>
          <p class="hint">
            支持完整 Markdown。HTML 按文字显示。图片为 JPEG / PNG，单张不超过
            10MiB。
          </p>
        </section>
      </aside>
    </div>
    <dialog
      ref="publishDialog"
      class="publish-dialog panel"
      aria-labelledby="publish-title"
      @close="preview = false"
      @cancel="preview = false"
    >
      <h2 id="publish-title">确认公开这篇经验</h2>
      <p>{{ form?.title }} · 修订 {{ revision }}</p>
      <p>
        正文、分类、来源说明和资料链接将一并公开，图片将按当前正文引用开放读取。
      </p>
      <p class="hint">
        请在编辑区核对完整安全预览。发布不会公开你的邮箱或关闭的联系方式。
      </p>
      <div class="card-actions">
        <button :disabled="busy" @click="preview = false">返回编辑</button
        ><button
          class="primary"
          :disabled="busy || !!pendingPublish"
          @click="confirmPublish"
        >
          确认发布
        </button>
      </div>
    </dialog>
  </MemberGate>
</template>
