<script setup lang="ts">
import ConsoleConfirm from "./ConsoleConfirm.vue";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { kinds, useMutation } from "./governance";
import type { Category, Page } from "./governance";
import { message } from "../lib/api";
import AdminGate from "./AdminGate.vue";
import ConsolePageHeader from "./ConsolePageHeader.vue";
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
    if (!mutation.pending.value) merging.value = undefined;
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
  merging.value = undefined;
  editing.value = row;
  name.value = row?.name || "";
  kind.value = row?.kind || String(route.query.kind || "JOB");
  enabled.value = row?.enabled ?? true;
  order.value = row?.displayOrder ?? 10;
  reason.value = "";
  showForm.value = true;
}
async function chooseMerge(row: Category) {
  if (frozen.value || row.canonicalId || !mutation.allowed.value) return;
  const current = generation;
  showForm.value = false;
  merging.value = row;
  targets.value = [];
  canonical.value = undefined;
  mergeReason.value = "";
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
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
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
  if (!mutation.pending.value) merging.value = undefined;
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
const mergeTarget = computed(() =>
  targets.value.find((row) => row.id === canonical.value),
);
function closePanels() {
  if (frozen.value) return;
  showForm.value = false;
  merging.value = undefined;
}
onMounted(load);
watch(
  () => route.query,
  () => void load(),
);
watch(() => adminSession.status, load);
onBeforeUnmount(() => generation++);
const modal = ref<InstanceType<typeof ConsoleConfirm>>();
</script>
<template>
  <AdminGate>
    <div class="console-page">
      <ConsoleConfirm ref="modal" />
      <ConsolePageHeader
        title="分类同义归并"
        description="核对源分类、目标分类、归并方向与影响，再执行。"
      />
      <p v-if="error" class="console-banner is-error" role="alert">
        {{ error }}
        <span class="console-banner-actions"
          ><button type="button" :disabled="busy" @click="load">
            重新读取</button
          ></span
        >
      </p>
      <MutationNotice
        :pending="mutation.pending.value"
        :busy="mutation.busy.value"
        :error="mutation.error.value"
        :notice="mutation.notice.value"
        @retry="mutation.submit"
        @reconcile="mutation.reconcile"
      />
      <section class="console-panel" aria-labelledby="category-list-title">
        <div class="console-card-head">
          <div>
            <h2 id="category-list-title" class="console-card-title">
              分类标签
            </h2>
            <p class="console-card-desc">
              停用和归并均保留历史文章关联。归并后，公开目录使用保留的启用标签。
            </p>
          </div>
          <div class="console-card-tools">
            <div class="console-tabs" role="group" aria-label="分类类型">
              <button
                v-for="(label, key) in kinds"
                :key="key"
                type="button"
                :aria-pressed="(route.query.kind || 'JOB') === key"
                :disabled="frozen"
                @click="router.push({ query: { kind: key, page: '1' } })"
              >
                {{ label }}
              </button>
            </div>
            <button
              class="console-primary"
              type="button"
              :disabled="frozen || !mutation.allowed.value"
              @click="edit()"
            >
              新增标签
            </button>
          </div>
        </div>
        <p v-if="busy" class="console-muted" role="status" style="margin-top: 12px">
          正在读取分类…
        </p>
        <div v-if="data" class="console-table-wrap">
          <table class="console-table">
            <thead>
              <tr>
                <th>标准名称</th>
                <th>类型</th>
                <th>状态</th>
                <th>排序 · 修订</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in data.items" :key="row.id">
                <td>
                  {{ row.name
                  }}<small class="console-inline-code">CAT-{{ row.id }}</small>
                </td>
                <td>{{ kinds[row.kind] }}</td>
                <td>
                  <span
                    class="console-tag"
                    :class="
                      row.canonicalId
                        ? 'is-accent'
                        : row.enabled
                          ? 'is-positive'
                          : 'is-neutral'
                    "
                    >{{
                      row.canonicalId
                        ? `已归并至 CAT-${row.canonicalId}`
                        : row.enabled
                          ? "启用"
                          : "已停用"
                    }}</span
                  >
                </td>
                <td class="num">{{ row.displayOrder }} · {{ row.revision }}</td>
                <td>
                  <span v-if="!row.canonicalId" class="console-cell-actions">
                    <button
                      class="console-link"
                      type="button"
                      :disabled="frozen || !mutation.allowed.value"
                      @click="edit(row)"
                    >
                      编辑 / 启停
                    </button>
                    <button
                      class="console-link"
                      type="button"
                      :disabled="frozen || !mutation.allowed.value"
                      @click="chooseMerge(row)"
                    >
                      归并同义标签
                    </button>
                  </span>
                </td>
              </tr>
              <tr v-if="!busy && !data.items.length">
                <td colspan="5" class="console-table-empty">
                  当前分类暂无标签。
                </td>
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
      <form
        v-if="merging"
        id="category-merge-form"
        class="console-split"
        @submit.prevent="merge"
      >
        <section class="console-panel" aria-label="归并对象">
          <h2 class="console-card-title">归并对象</h2>
          <p class="console-card-desc">
            分类治理 · {{ kinds[merging.kind] || merging.kind }}
          </p>
          <dl class="console-facts">
            <div>
              <dt>源分类</dt>
              <dd>{{ merging.name }} · CAT-{{ merging.id }} · 修订 {{ merging.revision }}</dd>
            </div>
            <div>
              <dt>目标分类</dt>
              <dd>
                {{ mergeTarget ? `${mergeTarget.name} · CAT-${mergeTarget.id}` : "请在右侧选择启用的同类标签" }}
              </dd>
            </div>
            <div>
              <dt>归并方向</dt>
              <dd>
                {{ merging.name }} → {{ mergeTarget?.name || "待选择" }}
              </dd>
            </div>
            <div>
              <dt>回读核对</dt>
              <dd>检查源分类与目标分类状态</dd>
            </div>
          </dl>
          <p class="console-callout">
            归并将按目标分类解释原有引用。已有分类内容和别名如何展示，以服务端归并结果为准。
          </p>
        </section>
        <section class="console-panel" aria-label="复核归并">
          <h2 class="console-card-title">复核归并</h2>
          <p class="console-card-desc">失败时保留源分类、目标分类和输入理由。</p>
          <fieldset class="console-form" :disabled="frozen">
            <label class="console-field"
              ><span>目标分类</span
              ><select v-model="canonical" required aria-label="保留的同类标签">
                <option :value="undefined" disabled>请选择启用标签</option>
                <option v-for="row in targets" :key="row.id" :value="row.id">
                  {{ row.name }} · CAT-{{ row.id }}
                </option>
              </select></label
            >
            <p v-if="!busy && !targets.length" class="console-form-note">
              暂无可归入的启用标签。
            </p>
            <label class="console-field"
              ><span>必填理由</span
              ><textarea
                v-model="mergeReason"
                required
                maxlength="2000"
                rows="3"
                placeholder="说明两个分类为何指向同一类别。"
            /></label>
          </fieldset>
        </section>
      </form>
      <form
        v-if="showForm"
        id="category-form"
        class="console-split"
        @submit.prevent="save"
      >
        <section class="console-panel" aria-label="分类信息">
          <h2 class="console-card-title">
            {{ editing ? "编辑分类" : "新增分类" }}
          </h2>
          <p class="console-card-desc">
            当前修订 {{ editing?.revision || 0 }}<template v-if="editing">
              · 类型不可更改</template
            >
          </p>
          <fieldset
            class="console-form"
            :disabled="frozen || !mutation.allowed.value"
          >
            <label class="console-field"
              ><span>类型</span
              ><select v-model="kind" :disabled="!!editing" aria-label="类型">
                <option v-for="(label, key) in kinds" :key="key" :value="key">
                  {{ label }}
                </option>
              </select></label
            ><label class="console-field"
              ><span>标准名称</span
              ><input
                v-model="name"
                required
                maxlength="100"
                aria-label="标准名称" /></label
            ><label class="console-field"
              ><span>展示顺序</span
              ><input
                v-model.number="order"
                type="number"
                step="1"
                min="-2147483648"
                max="2147483647"
                required
                aria-label="展示顺序" /></label
            >
            <label class="console-check"
              ><input v-model="enabled" type="checkbox" />启用此分类</label
            >
          </fieldset>
        </section>
        <section class="console-panel" aria-label="变更理由">
          <h2 class="console-card-title">变更说明</h2>
          <p class="console-card-desc">变更理由随分类修订一并记录。</p>
          <fieldset
            class="console-form"
            :disabled="frozen || !mutation.allowed.value"
          >
            <label class="console-field"
              ><span>变更理由</span
              ><textarea
                v-model="reason"
                required
                maxlength="2000"
                rows="3"
                aria-label="变更理由"
                placeholder="写明新增、改名或启停的依据。"
            /></label>
          </fieldset>
        </section>
      </form>
      <footer
        v-if="(merging || showForm) && !mutation.pending.value"
        class="console-footer"
      >
        <div class="console-footer-actions">
          <button
            class="console-secondary"
            type="button"
            :disabled="frozen"
            @click="closePanels"
          >
            取消
          </button>
          <button
            v-if="merging"
            class="console-primary"
            type="submit"
            form="category-merge-form"
            :disabled="frozen || !canonical || !mergeReason.trim()"
          >
            确认归并
          </button>
          <button
            v-else
            class="console-primary"
            type="submit"
            form="category-form"
            :disabled="frozen || !mutation.allowed.value"
          >
            确认保存分类
          </button>
        </div>
      </footer>
    </div>
  </AdminGate>
</template>
