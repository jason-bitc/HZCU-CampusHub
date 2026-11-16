<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
} from "vue";
import type { Category } from "../types";

const props = defineProps<{
  label: string;
  modelValue: string;
  options: Category[];
}>();
const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const id = useId();
const root = ref<HTMLElement>();
const trigger = ref<HTMLButtonElement>();
const list = ref<HTMLElement>();
const open = ref(false);
const activeIndex = ref(0);
const labelId = `filter-label-${id}`;
const valueId = `filter-value-${id}`;
const listId = `filter-list-${id}`;
const items = computed(() => [
  { value: "", name: "全部" },
  ...props.options.map((option) => ({
    value: String(option.id),
    name: option.name,
  })),
]);
const selectedLabel = computed(
  () =>
    items.value.find((item) => item.value === props.modelValue)?.name ?? "全部",
);

function optionId(index: number) {
  return `filter-option-${id}-${index}`;
}
function close(restoreFocus = false) {
  if (!open.value) return;
  open.value = false;
  if (restoreFocus) trigger.value?.focus();
}
async function openList() {
  if (open.value) return;
  activeIndex.value = Math.max(
    0,
    items.value.findIndex((item) => item.value === props.modelValue),
  );
  open.value = true;
  await nextTick();
  scrollActiveIntoView();
}
function scrollActiveIntoView() {
  const option = list.value?.children[activeIndex.value];
  if (option instanceof HTMLElement)
    option.scrollIntoView({ block: "nearest" });
}
function moveActive(offset: number) {
  activeIndex.value =
    (activeIndex.value + offset + items.value.length) % items.value.length;
  void nextTick(scrollActiveIntoView);
}
function select(value: string) {
  emit("update:modelValue", value);
  close(true);
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape" && open.value) {
    event.preventDefault();
    event.stopPropagation();
    close(true);
    return;
  }
  if (event.key === "Tab") {
    close();
    return;
  }
  if (event.key === "ArrowDown" || event.key === "ArrowUp") {
    event.preventDefault();
    if (!open.value) void openList();
    else moveActive(event.key === "ArrowDown" ? 1 : -1);
    return;
  }
  if (open.value && (event.key === "Home" || event.key === "End")) {
    event.preventDefault();
    activeIndex.value = event.key === "Home" ? 0 : items.value.length - 1;
    void nextTick(scrollActiveIntoView);
    return;
  }
  if (open.value && (event.key === "Enter" || event.key === " ")) {
    event.preventDefault();
    select(items.value[activeIndex.value].value);
  }
}
function onPointerDown(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) close();
}

onMounted(() => document.addEventListener("pointerdown", onPointerDown));
onBeforeUnmount(() =>
  document.removeEventListener("pointerdown", onPointerDown),
);
</script>

<template>
  <div ref="root" class="filter-select" @keydown="onKeydown">
    <span :id="labelId" class="filter-select-label">{{ label }}</span>
    <div class="filter-select-control">
      <button
        :id="valueId"
        ref="trigger"
        class="filter-select-trigger"
        :class="{ 'is-open': open, 'has-value': modelValue }"
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-autocomplete="none"
        :aria-expanded="open"
        :aria-controls="listId"
        :aria-labelledby="`${labelId} ${valueId}`"
        :aria-activedescendant="open ? optionId(activeIndex) : undefined"
        @click="open ? close() : openList()"
      >
        <span class="filter-select-value">{{ selectedLabel }}</span>
        <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
          <path d="m6 8 4 4 4-4" />
        </svg>
      </button>
      <Transition name="filter-popover">
        <div
          v-if="open"
          :id="listId"
          ref="list"
          class="filter-select-options"
          role="listbox"
          :aria-labelledby="labelId"
        >
          <div
            v-for="(item, index) in items"
            :id="optionId(index)"
            :key="item.value"
            class="filter-select-option"
            :class="{
              'is-active': activeIndex === index,
              'is-selected': modelValue === item.value,
            }"
            role="option"
            :aria-selected="modelValue === item.value"
            @mousedown.prevent
            @mousemove="activeIndex = index"
            @click="select(item.value)"
          >
            <span>{{ item.name }}</span>
            <svg
              v-if="modelValue === item.value"
              viewBox="0 0 20 20"
              fill="none"
              aria-hidden="true"
            >
              <path d="m5 10 3 3 7-7" />
            </svg>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>
