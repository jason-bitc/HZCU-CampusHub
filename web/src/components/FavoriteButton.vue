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
  if (saved.value === undefined) {
    await load();
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const r = await send<{ favorited: boolean }>(
      `/articles/${props.articleId}/favorite`,
      saved.value ? "DELETE" : "PUT",
    );
    saved.value = r.favorited;
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
watch(() => session.status, load);
</script>
<template>
  <div class="card-favorite">
    <button :disabled="busy" :aria-pressed="saved === true" @click="act">
      {{
        busy
          ? "读取收藏…"
          : session.status !== "ready"
            ? "登录后收藏"
            : saved === undefined
              ? "重读收藏状态"
              : saved
                ? "已收藏 · 取消"
                : "收藏经验"
      }}
    </button>
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
  </div>
</template>
