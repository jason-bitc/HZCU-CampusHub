<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { kinds, useMutation } from "./governance";
import type { Category, Page } from "./governance";
import AdminGate from "./AdminGate.vue";
import ConsolePager from "./ConsolePager.vue";
import ConsoleConfirm from "./ConsoleConfirm.vue";
import { consoleError } from "./console-ux";
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
const merging = ref<Category>();
const targets = ref<Category[]>([]);
const canonical = ref<number>();
const mergeReason = ref("");
const mutation = useMutation("categories", "CATEGORY_MANAGE", load);
const frozen = computed(
  () => busy.value || mutation.busy.value || !!mutation.pending.value,
);
async function load() {
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
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
      if (fresh) editing.value = fresh;
      else {
        editing.value = undefined;
        showForm.value = false;
      }
    }
  } catch (e) {
    if (current === generation) error.value = consoleError(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
function edit(row?: Category) {
  if (mutation.pending.value) return;
  merging.value = undefined;
  editing.value = row;
  name.value = row?.name || "";
  kind.value = row?.kind || String(route.query.kind || "JOB");
  enabled.value = row?.enabled ?? true;
  order.value = row?.displayOrder ?? 10;
  reason.value = "";
  showForm.value = true;
}
async function chooseMerge(row: Category, preserve = false) {
  if (frozen.value || row.canonicalId || !mutation.allowed.value) return;
  const current = generation;
  showForm.value = false;
  merging.value = row;
  if (!preserve) {
    targets.value = [];
    canonical.value = undefined;
    mergeReason.value = "";
  }
  busy.value = true;
  error.value = "";
  try {
    let page = 1;
    let completed = false;
    const options = new Map<number, Category>();
    while (!completed && current === generation) {
      const batch = await adminRequest<Page<Category>>(
        `/admin/categories?kind=${row.kind}&page=${page}&size=100`,
      );
      for (const item of batch.items)
        if (item.id !== row.id && item.enabled && !item.canonicalId)
          options.set(item.id, item);
      completed = !batch.items.length || page * batch.size >= batch.total;
      page++;
    }
    if (current === generation) targets.value = [...options.values()];
  } catch (e) {
    if (current === generation) error.value = consoleError(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function refreshMerge() {
  const id = merging.value?.id;
  await load();
  if (error.value || !id) return;
  const row = data.value?.items.find((item) => item.id === id);
  if (!row || row.canonicalId) {
    error.value = "源分类已经变更或离开当前列表，请核对列表中的状态。";
    return;
  }
  await chooseMerge(row, true);
}
async function merge() {
  const from = merging.value;
  const to = targets.value.find((row) => row.id === canonical.value);
  if (!from || !to || frozen.value || !mergeReason.value.trim()) return;
  if (
    !(await modal.value?.ask({
      title: "确认归并同义分类？",
      detail: `源分类 ${from.name}（${from.id}，修订 ${from.revision}）将归入 ${to.name}（${to.id}，修订 ${to.revision}）。归并作用于当前分类及公开目录，原文章引用仍由服务端保留。\n\n归并理由：${mergeReason.value.trim()}`,
      action: "归并到 " + to.name,
      danger: true,
    }))
  )
    return;
  await mutation.start(
    `/admin/categories/${from.id}/merge`,
    "POST",
    {
      canonicalId: to.id,
      expectedRevision: from.revision,
      expectedCanonicalRevision: to.revision,
      reason: mergeReason.value.trim(),
    },
    "归并同义分类",
  );
  if (!mutation.pending.value && mutation.notice.value)
    merging.value = undefined;
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
  if (!mutation.pending.value && mutation.notice.value) showForm.value = false;
}
function turn(page: number) {
  void router.push({ query: { ...route.query, page: String(page) } });
}
onMounted(load);
watch(() => [route.query, adminSession.status], load);
onBeforeUnmount(() => generation++);
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
</script>
<template>
  <h1 class="console-title">岗位、专业、公司与主题</h1>
  <AdminGate>
    <ConsoleConfirm ref="modal" />
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
        <table class="console-table console-responsive-table" role="table">
          <thead role="rowgroup">
            <tr role="row">
              <th scope="col" role="columnheader">标准名称</th>
              <th scope="col" role="columnheader">类型</th>
              <th scope="col" role="columnheader">状态</th>
              <th scope="col" role="columnheader">排序 / 修订</th>
              <th scope="col" role="columnheader">操作</th>
            </tr>
          </thead>
          <tbody role="rowgroup">
            <tr role="row" v-for="row in data.items" :key="row.id">
              <td data-label="标准名称" role="cell">
                {{ row.name }}<small>分类 {{ row.id }}</small>
              </td>
              <td data-label="类型" role="cell">{{ kinds[row.kind] }}</td>
              <td data-label="状态" role="cell">
                {{
                  row.canonicalId
                    ? `已归并至分类 ${row.canonicalId}`
                    : row.enabled
                      ? "启用"
                      : "已停用"
                }}
              </td>
              <td data-label="排序 / 修订" role="cell">
                {{ row.displayOrder }} / {{ row.revision }}
              </td>
              <td data-label="操作" role="cell">
                <button
                  v-if="!row.canonicalId"
                  :disabled="frozen || !mutation.allowed.value"
                  @click="edit(row)"
                >
                  编辑 / 启停
                </button>
                <button
                  v-if="!row.canonicalId"
                  :disabled="frozen || !mutation.allowed.value"
                  @click="chooseMerge(row)"
                >
                  归并同义标签
                </button>
              </td>
            </tr>
            <tr role="row" v-if="!data.items.length">
              <td role="cell" colspan="5">当前分类暂无标签。</td>
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
      <form v-if="merging" class="console-panel" @submit.prevent="merge">
        <h2>归并同义分类</h2>
        <p>来源 {{ merging.name }} · 修订 {{ merging.revision }}</p>
        <p class="hint">
          归并影响公开目录的同义分类，历史文章关联由服务端保留。
        </p>
        <button
          v-if="mutation.error.value && !mutation.pending.value"
          :disabled="busy"
          type="button"
          @click="refreshMerge"
        >
          重新读取归并对象
        </button>
        <fieldset :disabled="frozen">
          <label
            >保留的同类标签<select v-model="canonical" required>
              <option :value="undefined" disabled>请选择启用标签</option>
              <option v-for="row in targets" :key="row.id" :value="row.id">
                {{ row.name }} · {{ row.id }}
              </option>
            </select></label
          >
          <p v-if="!busy && !targets.length" class="hint">
            暂无可归入的启用标签。
          </p>
          <label
            >归并理由<textarea
              v-model="mergeReason"
              required
              maxlength="2000"
              rows="2"
            />
          </label>
          <button
            class="console-primary"
            :disabled="!canonical || !mergeReason.trim()"
          >
            确认归并
          </button>
          <button type="button" @click="merging = undefined">取消</button>
        </fieldset>
      </form>
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
        停用和归并均保留历史文章关联。归并后，公开目录使用保留的启用标签。
      </p>
    </div>
  </AdminGate>
</template>
