<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { get, message, send } from "../lib/api";
import { session } from "../lib/session";
const props = defineProps<{ articleId: number }>();
const route = useRoute();
const router = useRouter();
const saved = ref<boolean>();
const busy = ref(false);
const error = ref("");
const status = ref("");
const acting = ref(false);
const needsRead = ref(false);
async function load() {
  if (session.status !== "ready") {
    saved.value = undefined;
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const r = await get<{ favorited: boolean }>(
      `/articles/${props.articleId}/interaction-state`,
      true,
    );
    saved.value = r.favorited;
    needsRead.value = false;
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function act() {
  if (session.status !== "ready") {
    await router.push({ path: "/account", query: { next: route.fullPath } });
    return;
  }
  if (busy.value) return;
  if (saved.value === undefined || needsRead.value) {
    await load();
    return;
  }
  busy.value = true;
  acting.value = true;
  error.value = "";
  status.value = "";
  const expected = !saved.value;
  try {
    const r = await send<{ favorited: boolean }>(
      `/articles/${props.articleId}/favorite`,
      saved.value ? "DELETE" : "PUT",
    );
    saved.value = r.favorited;
    status.value = saved.value ? "已收藏。" : "已取消收藏。";
  } catch (e) {
    await load();
    if (error.value) {
      needsRead.value = true;
      error.value = "收藏结果待确认。请重新读取状态。 " + error.value;
    } else if (saved.value === expected)
      status.value = saved.value ? "已确认收藏。" : "已确认取消收藏。";
    else error.value = message(e);
  } finally {
    acting.value = false;
    busy.value = false;
  }
}
onMounted(load);
watch(() => session.status, load);
</script>
<template>
  <div class="card-favorite">
    <button :disabled="busy" :aria-pressed="saved === true" @click="act">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path
          d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9L12 3Z"
        />
      </svg>
      <span>{{
        busy
          ? acting
            ? "处理收藏…"
            : "读取收藏…"
          : session.status !== "ready"
            ? "登录后收藏"
            : saved === undefined || needsRead
              ? "重读收藏状态"
              : saved
                ? "已收藏 · 取消"
                : "收藏经验"
      }}</span>
    </button>
    <p v-if="status" class="sr-only" role="status">{{ status }}</p>
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
  </div>
</template>
