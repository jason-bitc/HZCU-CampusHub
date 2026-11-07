<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { kinds, useMutation } from "./governance";
import type { Category, Page } from "./governance";
import { message } from "../lib/api";
import AdminGate from "./AdminGate.vue";
import ConsolePager from "./ConsolePager.vue";
import MutationNotice from "./MutationNotice.vue";
const route = useRoute(),
  router = useRouter();
const data = ref<Page<Category>>(),
  editing = ref<Category>(),
  name = ref(""),
  kind = ref("JOB"),
  enabled = ref(true),
  order = ref(10),
  reason = ref(""),
  error = ref(""),
  busy = ref(false),
  showForm = ref(false);
let generation = 0;
const mutation = useMutation("categories", "CATEGORY_MANAGE", load);
const frozen = computed(
  () => busy.value || mutation.busy.value || !!mutation.pending.value,
);
async function load() {
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  data.value = undefined;
  try {
    const p = new URLSearchParams({
      page: String(Math.max(1, Number(route.query.page) || 1)),
      size: "20",
      kind: String(route.query.kind || "JOB"),
    });
    const result = await adminRequest<Page<Category>>(`/admin/categories?${p}`);
    if (current !== generation) return;
    data.value = result;
    mutation.restore();
    if (editing.value) {
      const fresh = result.items.find((row) => row.id === editing.value?.id);
      if (fresh) edit(fresh);
      else {
        editing.value = undefined;
        showForm.value = false;
      }
    }
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
function edit(row?: Category) {
  if (mutation.pending.value) return;
  editing.value = row;
  name.value = row?.name || "";
  kind.value = row?.kind || String(route.query.kind || "JOB");
  enabled.value = row?.enabled ?? true;
  order.value = row?.displayOrder ?? 10;
  reason.value = "";
  showForm.value = true;
}
async function save() {
  if (frozen.value) return;
  if (
    !name.value.trim() ||
    !reason.value.trim() ||
    !Number.isInteger(order.value)
  ) {
    error.value = "请填写名称、整数排序值和变更理由。";
    return;
  }
  await mutation.start(
    `/admin/categories${editing.value ? `/${editing.value.id}` : ""}`,
    editing.value ? "PUT" : "POST",
    {
      kind: kind.value,
      name: name.value.trim(),
      enabled: enabled.value,
      displayOrder: order.value,
      expectedRevision: editing.value?.revision || 0,
      reason: reason.value,
    },
    editing.value ? "更新分类" : "创建分类",
  );
  if (!mutation.pending.value) showForm.value = false;
}
function turn(page: number) {
  void router.push({ query: { ...route.query, page: String(page) } });
}
onMounted(load);
watch(() => [route.query, adminSession.status], load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <h1 class="console-title">岗位、专业、公司与主题</h1>
  <AdminGate>
    <p v-if="error" class="error" role="alert">
      {{ error }} <button :disabled="busy" @click="load">重新读取</button>
    </p>
    <MutationNotice
      :pending="mutation.pending.value"
      :busy="mutation.busy.value"
      :error="mutation.error.value"
      :notice="mutation.notice.value"
      @retry="mutation.submit"
      @reconcile="mutation.reconcile"
    />
    <section class="console-panel console-category-list">
      <div class="console-filterbar">
        <div class="console-category-tabs">
          <button
            v-for="(label, key) in kinds"
            :key="key"
            :aria-pressed="(route.query.kind || 'JOB') === key"
            :disabled="frozen"
            @click="router.push({ query: { kind: key, page: '1' } })"
          >
            {{ label }}
          </button>
        </div>
        <button
          class="console-primary console-push-right"
          :disabled="frozen || !mutation.allowed.value"
          @click="edit()"
        >
          新增标签
        </button>
      </div>
      <p v-if="busy" role="status">正在读取分类…</p>
      <div v-if="data" class="console-table-wrap">
        <table class="console-table">
          <thead>
            <tr>
              <th>标准名称</th>
              <th>类型</th>
              <th>状态</th>
              <th>排序 / 修订</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in data.items" :key="row.id">
              <td>
                {{ row.name }}<small>分类 {{ row.id }}</small>
              </td>
              <td>{{ kinds[row.kind] }}</td>
              <td>{{ row.enabled ? "启用" : "已停用" }}</td>
              <td>{{ row.displayOrder }} / {{ row.revision }}</td>
              <td>
                <button
                  :disabled="frozen || !mutation.allowed.value"
                  @click="edit(row)"
                >
                  编辑 / 启停
                </button>
              </td>
            </tr>
            <tr v-if="!data.items.length">
              <td colspan="5">当前分类暂无标签。</td>
            </tr>
          </tbody>
        </table>
      </div>
      <ConsolePager
        v-if="data"
        :page="data.page"
        :size="data.size"
        :total="data.total"
        :busy="frozen"
        @turn="turn"
      />
    </section>
    <div class="console-split console-followup">
      <form v-if="showForm" class="console-panel" @submit.prevent="save">
        <h2>{{ editing ? "编辑分类" : "新增分类" }}</h2>
        <fieldset :disabled="frozen || !mutation.allowed.value">
          <label
            >类型<select v-model="kind" :disabled="!!editing">
              <option v-for="(label, key) in kinds" :key="key" :value="key">
                {{ label }}
              </option>
            </select></label
          ><label
            >标准名称<input v-model="name" required maxlength="100" /></label
          ><label
            >展示顺序<input
              v-model.number="order"
              type="number"
              step="1"
              min="-2147483648"
              max="2147483647"
              required /></label
          ><label class="console-check"
            ><input v-model="enabled" type="checkbox" />启用此分类</label
          ><label
            >变更理由<textarea
              v-model="reason"
              required
              maxlength="2000"
              rows="2"
            ></textarea>
          </label>
          <p>当前修订 {{ editing?.revision || 0 }}</p>
          <div class="console-actions">
            <button class="console-primary">确认保存分类</button
            ><button type="button" @click="showForm = false">取消</button>
          </div>
        </fieldset>
      </form>
      <p class="console-note">
        停用保留历史文章关联。当前合同支持创建、改名、排序及启停；同义词合并、历史批量映射和删除尚未提供接口。
      </p>
    </div>
  </AdminGate>
</template>
