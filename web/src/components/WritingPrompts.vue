<script setup lang="ts">
import { ref } from "vue";
import { get, message } from "../lib/api";

interface Prompt {
  id: string;
  title: string;
  question: string;
}
const prompts = ref<Prompt[]>();
const error = ref(""),
  busy = ref(false);
async function load(event?: Event) {
  if (
    (event && !(event.currentTarget as HTMLDetailsElement).open) ||
    busy.value ||
    prompts.value
  )
    return;
  busy.value = true;
  error.value = "";
  try {
    const data = await get<{ items: Prompt[] }>("/me/writing-prompts", true);
    prompts.value = data.items;
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <details class="panel" @toggle="load">
    <summary>写作提示（可跳过）</summary>
    <p v-if="busy" role="status">正在读取写作提示…</p>
    <p v-if="error" class="error" role="alert">
      {{ error }} <button type="button" @click="load()">重新读取提示</button>
    </p>
    <p v-if="prompts && !prompts.length" class="hint">暂无写作提示。</p>
    <ul v-if="prompts?.length">
      <li v-for="item in prompts" :key="item.id">
        <strong>{{ item.title }}</strong> · {{ item.question }}
      </li>
    </ul>
  </details>
</template>
