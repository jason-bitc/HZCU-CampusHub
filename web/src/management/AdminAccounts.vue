<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { stateLabels, useMutation } from "./governance";
import type { Appeal, Page, Restriction, User } from "./governance";
import { message } from "../lib/api";
import { dateText } from "../lib/content";
import AdminGate from "./AdminGate.vue";
import ConsolePager from "./ConsolePager.vue";
import MutationNotice from "./MutationNotice.vue";
const route = useRoute(),
  router = useRouter();
const users = ref<Page<User>>(),
  appeals = ref<Page<Appeal>>(),
  user = ref<User>(),
  restrictions = ref<Restriction[]>([]),
  appeal = ref<Appeal>();
const scope = ref("COMMENT"),
  reason = ref(""),
  outcome = ref("REJECTED"),
  reply = ref(""),
  error = ref(""),
  busy = ref(false),
  readingUser = ref(false),
  restrictionsReady = ref(false);
let generation = 0,
  userRead = 0;
const mutation = useMutation("accounts", "USER_RESTRICT", load),
  decision = useMutation("appeals", "APPEAL_HANDLE", load);
const frozen = computed(
  () =>
    busy.value ||
    readingUser.value ||
    mutation.busy.value ||
    decision.busy.value ||
    !!mutation.pending.value ||
    !!decision.pending.value,
);
const restriction = computed(() =>
  restrictions.value.find((row) => row.scope === scope.value),
);
async function load() {
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  users.value = undefined;
  appeals.value = undefined;
  try {
    const [u, a] = await Promise.all([
      adminRequest<Page<User>>(
        `/admin/users?page=${Math.max(1, Number(route.query.users) || 1)}&size=10`,
      ),
      adminRequest<Page<Appeal>>(
        `/admin/appeals?page=${Math.max(1, Number(route.query.appeals) || 1)}&size=10`,
      ),
    ]);
    if (current !== generation) return;
    users.value = u;
    appeals.value = a;
    mutation.restore();
    decision.restore();
    if (appeal.value)
      appeal.value = a.items.find((row) => row.id === appeal.value?.id);
    if (user.value) await readUser(user.value);
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function readUser(row: User) {
  const current = generation,
    read = ++userRead;
  user.value = row;
  restrictions.value = [];
  restrictionsReady.value = false;
  readingUser.value = true;
  error.value = "";
  try {
    const result = await adminRequest<{ items: Restriction[] }>(
      `/admin/users/${row.id}/restrictions`,
    );
    if (current === generation && read === userRead) {
      restrictions.value = result.items;
      restrictionsReady.value = true;
    }
  } catch (e) {
    if (current === generation && read === userRead) error.value = message(e);
  } finally {
    if (read === userRead) readingUser.value = false;
  }
}
async function set(enabled: boolean) {
  if (!user.value || frozen.value || !restrictionsReady.value) return;
  if (!reason.value.trim()) {
    error.value = "请填写限制或解除的事实依据。";
    return;
  }
  await mutation.start(
    `/admin/users/${user.value.id}/restrictions`,
    "POST",
    {
      scope: scope.value,
      enabled,
      expectedRevision: restriction.value?.revision || 0,
      reason: reason.value,
    },
    enabled ? "限制所选行为" : "解除所选限制",
  );
}
async function decide() {
  if (!appeal.value || frozen.value) return;
  if (!reply.value.trim()) {
    error.value = "请填写向本人公开的复核答复。";
    return;
  }
  await decision.start(
    `/admin/appeals/${appeal.value.id}/decisions`,
    "POST",
    {
      outcome: outcome.value,
      expectedRevision: appeal.value.revision,
      reply: reply.value,
    },
    "保存申诉复核决定",
  );
}
function turn(key: string, page: number) {
  void router.push({ query: { ...route.query, [key]: String(page) } });
}
onMounted(load);
watch(() => [route.query, adminSession.status], load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <h1 class="console-title">账号限制与申诉复核</h1>
  <AdminGate>
    <p class="console-note">
      发布与评论分别限制；阅读、草稿保存和本人申诉继续可用。接受申诉只解除申诉关联版本的限制，不自动恢复隐藏内容。
    </p>
    <p v-if="error" class="error" role="alert">
      {{ error }} <button :disabled="busy" @click="load">重新读取</button>
    </p>
    <p v-if="busy" role="status">正在读取账号与申诉…</p>
    <MutationNotice
      :pending="mutation.pending.value"
      :busy="mutation.busy.value"
      :error="mutation.error.value"
      :notice="mutation.notice.value"
      @retry="mutation.submit"
      @reconcile="mutation.reconcile"
    />
    <MutationNotice
      :pending="decision.pending.value"
      :busy="decision.busy.value"
      :error="decision.error.value"
      :notice="decision.notice.value"
      @retry="decision.submit"
      @reconcile="decision.reconcile"
    />
    <div class="console-split console-equal">
      <section class="console-panel console-account-limit">
        <h2>限制范围</h2>
        <div v-if="users" class="console-table-wrap">
          <table class="console-table">
            <thead>
              <tr>
                <th>账号</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in users.items" :key="row.id">
                <td>
                  {{ row.nickname }}<small>用户 {{ row.id }}</small>
                </td>
                <td>{{ row.accountState }}</td>
                <td>
                  <button :disabled="frozen" @click="readUser(row)">
                    查看限制
                  </button>
                </td>
              </tr>
              <tr v-if="!users.items.length">
                <td colspan="3">暂无账号。</td>
              </tr>
            </tbody>
          </table>
        </div>
        <ConsolePager
          v-if="users"
          :page="users.page"
          :size="users.size"
          :total="users.total"
          :busy="frozen"
          @turn="turn('users', $event)"
        />
        <p>关联账号：{{ user ? `${user.nickname} · ${user.id}` : "待选择" }}</p>
        <fieldset
          :disabled="
            frozen || !user || !restrictionsReady || !mutation.allowed.value
          "
        >
          <label
            >限制行为<select v-model="scope">
              <option value="PUBLISH">发布及作者恢复上架</option>
              <option value="COMMENT">新评论及回复</option>
            </select></label
          >
          <p>
            当前：{{
              !restrictionsReady
                ? "尚未核验"
                : restriction?.enabled
                  ? "受限"
                  : "允许"
            }}
            · 修订 {{ restriction?.revision || 0 }}
          </p>
          <p v-if="restriction">
            原理由：{{ restriction.reason }} ·
            {{ dateText(restriction.updatedAt) }}
          </p>
          <label
            >变更理由<textarea
              v-model="reason"
              required
              maxlength="2000"
              rows="3"
            ></textarea>
          </label>
          <div class="console-actions">
            <button
              class="console-danger"
              :disabled="!!restriction?.enabled"
              @click="set(true)"
            >
              确认限制所选行为</button
            ><button :disabled="!restriction?.enabled" @click="set(false)">
              解除所选限制
            </button>
          </div>
        </fieldset>
      </section>
      <section class="console-panel console-account-limit">
        <h2>本人申诉与复核</h2>
        <div v-if="appeals" class="console-table-wrap">
          <table class="console-table">
            <thead>
              <tr>
                <th>申诉 / 限制版本</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in appeals.items" :key="row.id">
                <td>
                  {{ row.id }} / 限制 {{ row.restrictionId }} v{{
                    row.restrictionRevision
                  }}
                </td>
                <td>{{ stateLabels[row.state] || row.state }}</td>
                <td>
                  <button
                    :disabled="frozen"
                    @click="
                      appeal = row;
                      reply = '';
                      outcome = 'REJECTED';
                    "
                  >
                    查看与复核
                  </button>
                </td>
              </tr>
              <tr v-if="!appeals.items.length">
                <td colspan="3">暂无申诉。</td>
              </tr>
            </tbody>
          </table>
        </div>
        <ConsolePager
          v-if="appeals"
          :page="appeals.page"
          :size="appeals.size"
          :total="appeals.total"
          :busy="frozen"
          @turn="turn('appeals', $event)"
        />
        <template v-if="appeal"
          ><h3>
            申诉 {{ appeal.id }} ·
            {{ stateLabels[appeal.state] || appeal.state }}
          </h3>
          <p class="console-preserve">申诉理由：{{ appeal.reason }}</p>
          <p v-if="appeal.reply" class="console-preserve">
            已有答复：{{ appeal.reply }}
          </p>
          <form @submit.prevent="decide">
            <fieldset
              :disabled="
                frozen || appeal.state !== 'OPEN' || !decision.allowed.value
              "
            >
              <label
                >复核结论<select v-model="outcome" aria-label="复核结论">
                  <option value="REJECTED">驳回申诉，维持原限制</option>
                  <option value="ACCEPTED">接受申诉，解除对应版本限制</option>
                </select></label
              ><label
                >公开复核答复<textarea
                  v-model="reply"
                  maxlength="2000"
                  rows="3"
                  required
                ></textarea></label
              ><button class="console-primary">保存复核决定</button>
            </fieldset>
          </form></template
        >
        <p v-else>请选择需要核对的申诉。</p>
      </section>
    </div>
  </AdminGate>
</template>
