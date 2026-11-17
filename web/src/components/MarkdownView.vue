<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";
import { request } from "../lib/api";
import { imagePath, safeLink } from "../lib/content";
import { session } from "../lib/session";

const props = withDefaults(
  defineProps<{
    body: string;
    privateImages?: boolean;
    toc?: boolean;
    blockImages?: boolean;
    imageSources?: Record<string, string>;
  }>(),
  { privateImages: false, toc: false },
);
const urls = ref<Record<string, string>>({});
const failed = ref<Record<string, boolean>>({});
const currentHeading = ref("");
const loadingPaths = new Set<string>();
let frame = 0;
const tocOpen = ref(false);
const tocButton = ref<HTMLButtonElement>();
const tocPanel = ref<HTMLElement>();
const bodyElement = ref<HTMLElement>();
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
const renderImage = md.renderer.rules.image!;
md.renderer.rules.image = (tokens, index, options, env, renderer) => {
  const image = renderImage(tokens, index, options, env, renderer);
  if (props.blockImages) return image;
  const path = tokens[index]?.attrGet("data-private-image");
  const waiting = !!path && !urls.value[path];
  const downloading = waiting && !failed.value[path];
  return `<span class="markdown-image">${image}<span class="image-fallback" ${waiting ? "" : "hidden"}>${downloading ? "正在读取图片…" : "图片暂时无法显示。"} <button type="button" class="image-retry" ${downloading ? "hidden" : ""}>重试图片</button></span></span>`;
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
      if (props.blockImages) {
        const resolved = props.imageSources?.[src];
        if (resolved) child.attrSet("src", resolved);
        else {
          child.type = "text";
          child.content = `[图片：${child.content || "本案图片"}，需读取本案授权资源]`;
        }
        continue;
      }
      const internal = imagePath(src);
      if (internal && props.privateImages)
        child.attrSet("data-private-image", internal);
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
    ADD_ATTR: ["target", "loading", "referrerpolicy", "data-private-image"],
    ALLOW_DATA_ATTR: false,
    ALLOWED_URI_REGEXP:
      /^(?:(?:https?|blob):|\/api\/v1\/media\/images\/[a-f0-9-]{36}$|#)/i,
  });
});
function release() {
  Object.values(urls.value).forEach(URL.revokeObjectURL);
  urls.value = {};
  failed.value = {};
  loadingPaths.clear();
}
const privatePaths = computed(() => {
  const paths = new Set<string>();
  if (!props.privateImages || props.blockImages || session.status !== "ready")
    return paths;
  for (const token of md.parse(props.body, {}))
    for (const child of token.children ?? []) {
      const path =
        child.type === "image" && imagePath(String(child.attrGet("src") || ""));
      if (path) paths.add(path);
    }
  return paths;
});
async function loadImage(path: string) {
  if (!privatePaths.value.has(path) || loadingPaths.has(path)) return;
  const current = generation;
  loadingPaths.add(path);
  delete failed.value[path];
  try {
    const blob = await request<Blob>(path.slice("/api/v1".length), {
      auth: true,
      blob: true,
    });
    if (current === generation && privatePaths.value.has(path)) {
      if (urls.value[path]) URL.revokeObjectURL(urls.value[path]!);
      urls.value[path] = URL.createObjectURL(blob);
    }
  } catch {
    if (current === generation && privatePaths.value.has(path))
      failed.value[path] = true;
  } finally {
    if (current === generation) loadingPaths.delete(path);
  }
}
function reconcileImages() {
  for (const path of Object.keys(urls.value)) {
    if (privatePaths.value.has(path)) continue;
    URL.revokeObjectURL(urls.value[path]!);
    delete urls.value[path];
  }
  for (const path of Object.keys(failed.value))
    if (!privatePaths.value.has(path)) delete failed.value[path];
  for (const path of privatePaths.value)
    if (!urls.value[path] && !failed.value[path]) void loadImage(path);
}
watch(privatePaths, reconcileImages, { immediate: true });
watch(
  () => session.epoch,
  () => {
    generation++;
    release();
    reconcileImages();
  },
);
function imageFeedback(event: Event) {
  const image = event.target;
  if (!(image instanceof HTMLImageElement) || !image.getAttribute("src"))
    return;
  const fallback =
    image.parentElement?.querySelector<HTMLElement>(".image-fallback");
  if (!fallback) return;
  fallback.hidden = event.type === "load";
  image.classList.toggle("image-broken", event.type === "error");
}
function retryImage(event: MouseEvent) {
  const button = event.target;
  if (!(button instanceof HTMLButtonElement) || !button.matches(".image-retry"))
    return;
  const image = button.closest(".markdown-image")?.querySelector("img");
  const path = image?.getAttribute("data-private-image");
  if (path) {
    if (urls.value[path]) URL.revokeObjectURL(urls.value[path]!);
    delete urls.value[path];
    void loadImage(path);
  } else if (image) {
    const src = image.getAttribute("src");
    if (!src) return;
    image.classList.remove("image-broken");
    image.loading = "eager";
    image.removeAttribute("src");
    requestAnimationFrame(() => image.setAttribute("src", src));
  }
}
function locateSection() {
  const anchor = (tocButton.value?.getBoundingClientRect().bottom || 0) + 22;
  let current = "";
  for (const heading of bodyElement.value?.querySelectorAll<HTMLElement>(
    "[id^=section-]",
  ) ?? []) {
    if (heading.getBoundingClientRect().top > anchor) break;
    current = heading.id;
  }
  return current;
}
function updateSection() {
  if (frame) return;
  frame = requestAnimationFrame(() => {
    frame = 0;
    currentHeading.value = locateSection();
  });
}
watch(
  () => props.toc,
  (enabled, _, cleanup) => {
    if (!enabled) return;
    window.addEventListener("scroll", updateSection, { passive: true });
    window.addEventListener("resize", updateSection);
    cleanup(() => {
      window.removeEventListener("scroll", updateSection);
      window.removeEventListener("resize", updateSection);
      cancelAnimationFrame(frame);
      frame = 0;
    });
  },
  { immediate: true },
);
watch(
  html,
  () => {
    if (props.toc) updateSection();
  },
  { flush: "post" },
);
onBeforeUnmount(() => {
  generation += 1;
  release();
});
function closeToc() {
  tocOpen.value = false;
  tocButton.value?.focus({ preventScroll: true });
}
async function toggleToc() {
  if (tocOpen.value) {
    closeToc();
    return;
  }
  tocOpen.value = true;
  await nextTick();
  (
    tocPanel.value?.querySelector<HTMLButtonElement>(
      '[aria-current="location"]',
    ) ?? tocPanel.value?.querySelector("button")
  )?.focus({ preventScroll: true });
}
function jump(id: string) {
  if (!/^section-\d+$/.test(id)) return;
  const heading = bodyElement.value?.querySelector<HTMLElement>(`#${id}`);
  tocOpen.value = false;
  if (!heading) return;
  currentHeading.value = id;
  heading.tabIndex = -1;
  heading.focus({ preventScroll: true });
  heading.scrollIntoView({ block: "start" });
}
defineExpose({ locateSection, jump });
</script>
<template>
  <div class="markdown-shell">
    <div
      v-if="toc && headings.length"
      class="toc-tools"
      @keydown.esc.stop.prevent="closeToc"
    >
      <button
        ref="tocButton"
        class="toc-trigger"
        :aria-expanded="tocOpen"
        aria-controls="reading-toc"
        @click="toggleToc"
      >
        目录
      </button>
      <nav
        v-show="tocOpen"
        id="reading-toc"
        ref="tocPanel"
        class="toc-panel"
        aria-label="文章目录"
      >
        <h2>文章目录</h2>
        <button
          v-for="h in headings"
          :key="h.id"
          class="toc-item"
          :aria-current="h.id === currentHeading ? 'location' : undefined"
          :style="{ paddingLeft: `${(h.level - 1) * 10 + 12}px` }"
          @click="jump(h.id)"
        >
          {{ h.text }}</button
        ><slot name="toc-action" /><button
          class="text-button"
          @click="closeToc"
        >
          收起目录
        </button>
      </nav>
    </div>
    <div
      ref="bodyElement"
      class="markdown"
      @error.capture="imageFeedback"
      @load.capture="imageFeedback"
      @click="retryImage"
      v-html="html"
    ></div>
  </div>
</template>
