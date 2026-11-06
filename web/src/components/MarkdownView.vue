<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";
import { message, request } from "../lib/api";
import { imagePath, safeLink } from "../lib/content";
import { session } from "../lib/session";

const props = withDefaults(
  defineProps<{ body: string; privateImages?: boolean; toc?: boolean }>(),
  { privateImages: false, toc: false },
);
const urls = ref<Record<string, string>>({});
const error = ref("");
const tocOpen = ref(false);
let generation = 0;
const md = new MarkdownIt({ html: false, linkify: false, breaks: false });
md.renderer.rules.link_open = (tokens, index, options, _env, renderer) => {
  const token = tokens[index];
  const href = safeLink(String(token.attrGet("href") || ""));
  token.attrSet("href", href || "#");
  token.attrSet("target", "_blank");
  token.attrSet("rel", "noopener noreferrer");
  return renderer.renderToken(tokens, index, options);
};
const headings = computed(() => {
  const tokens = md.parse(props.body, {});
  return tokens.flatMap((token, index) =>
    token.type === "heading_open"
      ? [
          {
            id: `section-${index}`,
            text: tokens[index + 1]?.content || "",
            level: Number(token.tag.slice(1)),
          },
        ]
      : [],
  );
});
const html = computed(() => {
  const tokens = md.parse(props.body, {});
  tokens.forEach((token, index) => {
    if (token.type === "heading_open") token.attrSet("id", `section-${index}`);
    for (const child of token.children || []) {
      if (child.type !== "image") continue;
      const src = String(child.attrGet("src") || "");
      const internal = imagePath(src);
      if (internal)
        child.attrSet(
          "src",
          props.privateImages ? urls.value[internal] || "" : internal,
        );
      else child.attrSet("src", safeLink(src));
      child.attrSet("loading", "lazy");
      child.attrSet("referrerpolicy", "no-referrer");
    }
  });
  return DOMPurify.sanitize(md.renderer.render(tokens, md.options, {}), {
    ADD_ATTR: ["target", "loading", "referrerpolicy"],
    ALLOW_DATA_ATTR: false,
    ALLOWED_URI_REGEXP:
      /^(?:(?:https?|blob):|\/api\/v1\/media\/images\/[a-f0-9-]{36}$|#)/i,
  });
});
function release() {
  Object.values(urls.value).forEach(URL.revokeObjectURL);
  urls.value = {};
}
async function loadImages() {
  const current = ++generation;
  release();
  error.value = "";
  if (!props.privateImages || session.status !== "ready") return;
  const paths = new Set<string>();
  md.parse(props.body, {}).forEach((token) =>
    (token.children || []).forEach((child) => {
      const path =
        child.type === "image"
          ? imagePath(String(child.attrGet("src") || ""))
          : "";
      if (path) paths.add(path);
    }),
  );
  await Promise.all(
    [...paths].map(async (path) => {
      try {
        const blob = await request<Blob>(path.slice("/api/v1".length), {
          auth: true,
          blob: true,
        });
        const url = URL.createObjectURL(blob);
        if (current === generation) urls.value = { ...urls.value, [path]: url };
        else URL.revokeObjectURL(url);
      } catch (e) {
        if (current === generation)
          error.value = `部分私有图片读取失败：${message(e)}`;
      }
    }),
  );
}
watch(() => [props.body, props.privateImages, session.epoch], loadImages, {
  immediate: true,
});
onBeforeUnmount(() => {
  generation += 1;
  release();
});
function jump(id: string) {
  document
    .getElementById(id)
    ?.scrollIntoView({ behavior: "smooth", block: "start" });
  tocOpen.value = false;
}
</script>
<template>
  <div class="markdown-shell">
    <button
      v-if="toc && headings.length"
      class="toc-trigger"
      :aria-expanded="tocOpen"
      aria-controls="reading-toc"
      @click="tocOpen = !tocOpen"
    >
      目录
    </button>
    <aside v-if="tocOpen" id="reading-toc" class="toc-panel">
      <h2>文章目录</h2>
      <button
        v-for="h in headings"
        :key="h.id"
        class="toc-item"
        :style="{ paddingLeft: `${(h.level - 1) * 10 + 12}px` }"
        @click="jump(h.id)"
      >
        {{ h.text }}</button
      ><button class="text-button" @click="tocOpen = false">收起目录</button>
    </aside>
    <div class="markdown" v-html="html"></div>
    <p v-if="error" class="error" role="alert">
      {{ error }} <button @click="loadImages">重新读取图片</button>
    </p>
  </div>
</template>
