<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
const visible = ref(false);
function update() {
  visible.value = window.scrollY > 320;
}
function top() {
  window.scrollTo({
    top: 0,
    behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "instant"
      : "smooth",
  });
  document.getElementById("main")?.focus({ preventScroll: true });
}
onMounted(() => {
  window.addEventListener("scroll", update, { passive: true });
  update();
});
onBeforeUnmount(() => window.removeEventListener("scroll", update));
</script>
<template>
  <button
    v-if="visible"
    class="back-to-top"
    aria-label="回到顶部"
    title="回到顶部"
    @click="top"
  >
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path d="M6 5h12M6 13l6-6 6 6M12 7v13" />
    </svg>
  </button>
</template>
