<script setup lang="ts">
import ConsoleConfirm from "./ConsoleConfirm.vue";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import { stateLabels, useMutation } from "./governance";
import type { Appeal, Page, Restriction, User } from "./governance";
import { message } from "../lib/api";
import { dateText } from "../lib/content";
import AdminGate from "./AdminGate.vue";
import AdminCertification from "./AdminCertification.vue";
import ConsolePageHeader from "./ConsolePageHeader.vue";
import ConsolePager from "./ConsolePager.vue";
import ConsoleSteps from "./ConsoleSteps.vue";
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
const scopeLabels: Record<string, string> = {
  PUBLISH: "发布及作者恢复上架",
  COMMENT: "新评论及回复",
};
const appealTone = (state: string) =>
  ({ OPEN: "is-warning", ACCEPTED: "is-positive", REJECTED: "is-neutral" })[
    state
  ] || "is-neutral";
const accountStateText = (state: string) =>
  ({ ACTIVE: "正常", RESTRICTED: "受限", DISABLED: "停用" })[state] || state;
const appealStep = (state: string) =>
  ({ OPEN: 1, ACCEPTED: 3, REJECTED: 3 })[state] ?? 1;
const appealSubject = (row: Appeal) =>
  row.subject === "CAMPUS_CERTIFICATION"
    ? `校园认证 v${row.certificationRevision}`
    : `行为限制 v${row.restrictionRevision}`;
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
function closeUser() {
  if (frozen.value) return;
  user.value = undefined;
  restrictions.value = [];
  reason.value = "";
}
function openAppeal(row: Appeal) {
  appeal.value = row;
  reply.value = "";
  outcome.value = "REJECTED";
}
function closeAppeal() {
  if (frozen.value) return;
  appeal.value = undefined;
}
async function set(enabled: boolean) {
  if (!user.value || frozen.value || !restrictionsReady.value) return;
  if (!reason.value.trim()) {
    error.value = "请填写限制或解除的事实依据。";
    return;
  }
  if (
    !(await modal.value?.ask({
      title: (enabled ? "限制" : "解除限制") + "账号 " + user.value.id + "？",
      detail: `${scope.value === "PUBLISH" ? "发布及恢复上架" : "新评论及回复"} · ${enabled ? "该账号将失去对应操作权限。" : "该账号可再次使用对应操作。"}决定写入处理历史。\n\n理由：${reason.value.trim()}`,
      action: "提交限制变更",
      danger: true,
    }))
  )
    return;
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
        title="行为限制与申诉复核"
        description="按限定范围处理申诉，保留历史决定和用户原始陈述。"
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
      <MutationNotice
        :pending="decision.pending.value"
        :busy="decision.busy.value"
        :error="decision.error.value"
        :notice="decision.notice.value"
        @retry="decision.submit"
        @reconcile="decision.reconcile"
      />
      <p v-if="busy" class="console-muted" role="status">
        正在读取账号与申诉…
      </p>
      <section class="console-panel" aria-labelledby="account-limit-title">
        <div class="console-card-head">
          <div>
            <h2 id="account-limit-title" class="console-card-title">账号限制</h2>
            <p class="console-card-desc">
              发布与评论分别限制；阅读、草稿保存和本人申诉继续可用。
            </p>
          </div>
        </div>
        <div v-if="users" class="console-table-wrap">
          <table class="console-table">
            <thead>
              <tr>
                <th>账号</th>
                <th>账号状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in users.items" :key="row.id">
                <td>
                  {{ row.nickname
                  }}<small class="console-inline-code">U-{{ row.id }}</small>
                </td>
                <td>
                  <span class="console-tag">{{
                    accountStateText(row.accountState)
                  }}</span>
                </td>
                <td>
                  <button
                    class="console-link"
                    type="button"
                    :disabled="frozen"
                    :aria-pressed="user?.id === row.id"
                    @click="readUser(row)"
                  >
                    {{ user?.id === row.id ? "已选中" : "查看限制" }}
                  </button>
                </td>
              </tr>
              <tr v-if="!users.items.length">
                <td colspan="3" class="console-table-empty">暂无账号。</td>
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
      </section>
      <template v-if="user">
        <div class="console-split">
          <section class="console-panel" aria-label="账号与当前限制">
            <h2 class="console-card-title">账号与当前限制</h2>
            <p class="console-card-desc">
              {{ user.nickname }} · U-{{ user.id }} · 限制按行为分别记录。
            </p>
            <dl class="console-facts">
              <div>
                <dt>账号</dt>
                <dd>
                  {{ user.nickname }} · U-{{ user.id }} ·
                  {{ accountStateText(user.accountState) }}
                </dd>
              </div>
              <div v-if="!restrictionsReady">
                <dt>限制状态</dt>
                <dd>{{ readingUser ? "正在核验…" : "尚未核验" }}</dd>
              </div>
              <div v-for="row in restrictions" :key="row.id">
                <dt>{{ scopeLabels[row.scope] || row.scope }}</dt>
                <dd>
                  <span
                    class="console-tag"
                    :class="row.enabled ? 'is-negative' : 'is-positive'"
                    >{{ row.enabled ? "受限" : "允许" }}</span
                  >
                  · 修订 {{ row.revision }}
                  <small v-if="row.reason"
                    >理由：{{ row.reason }} · {{ dateText(row.updatedAt) }}</small
                  >
                </dd>
              </div>
            </dl>
            <p class="console-callout">
              接受申诉只解除申诉关联版本的限制，不自动恢复隐藏内容。
            </p>
          </section>
          <form
            id="restriction-form"
            class="console-panel"
            @submit.prevent="set(true)"
          >
            <h2 class="console-card-title">限制决定</h2>
            <p class="console-card-desc">确认后记录理由、操作者和限制修订。</p>
            <fieldset
              class="console-form"
              :disabled="
                frozen || !restrictionsReady || !mutation.allowed.value
              "
            >
              <label class="console-field"
                ><span>限制行为</span
                ><select v-model="scope" aria-label="限制行为">
                  <option value="PUBLISH">发布及作者恢复上架</option>
                  <option value="COMMENT">新评论及回复</option>
                </select></label
              >
              <p class="console-form-note">
                当前：{{
                  !restrictionsReady
                    ? "尚未核验"
                    : restriction?.enabled
                      ? "受限"
                      : "允许"
                }}
                · 修订 {{ restriction?.revision || 0 }}
                <template v-if="restriction?.reason">
                  · 原理由：{{ restriction.reason }}</template
                >
              </p>
              <label class="console-field"
                ><span>变更理由</span
                ><textarea
                  v-model="reason"
                  required
                  maxlength="2000"
                  rows="3"
                  placeholder="写明已核对的事实与依据。"
                ></textarea></label
              >
            </fieldset>
          </form>
        </div>
        <AdminCertification
          :key="user.id"
          class="console-stack"
          :user-id="user.id"
          :nickname="user.nickname"
        />
        <footer
          v-if="!mutation.pending.value"
          class="console-footer"
          :class="{ 'is-inline': !!appeal }"
        >
          <div class="console-footer-actions">
            <button
              class="console-secondary"
              type="button"
              :disabled="frozen"
              @click="closeUser"
            >
              关闭
            </button>
            <button
              class="console-secondary"
              type="button"
              :disabled="
                frozen || !restrictionsReady || !mutation.allowed.value || !restriction?.enabled
              "
              @click="set(false)"
            >
              解除所选限制
            </button>
            <button
              class="console-danger"
              type="submit"
              form="restriction-form"
              :disabled="
                frozen || !restrictionsReady || !mutation.allowed.value
              "
            >
              确认限制所选行为
            </button>
          </div>
        </footer>
      </template>
      <section class="console-panel" aria-labelledby="appeal-list-title">
        <div class="console-card-head">
          <div>
            <h2 id="appeal-list-title" class="console-card-title">申诉复核</h2>
            <p class="console-card-desc">
              接受申诉只解除申诉关联版本的限制；认证申诉由本人重新验证邮箱。
            </p>
          </div>
        </div>
        <div v-if="appeals" class="console-table-wrap">
          <table class="console-table">
            <thead>
              <tr>
                <th>申诉编号</th>
                <th>申诉对象</th>
                <th>提交时间</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in appeals.items" :key="row.id">
                <td class="num">APPEAL-{{ row.id }}</td>
                <td>{{ appealSubject(row) }}</td>
                <td>{{ dateText(row.createdAt) }}</td>
                <td>
                  <span class="console-tag" :class="appealTone(row.state)">{{
                    stateLabels[row.state] || row.state
                  }}</span>
                </td>
                <td>
                  <button
                    class="console-link"
                    type="button"
                    :disabled="frozen"
                    :aria-pressed="appeal?.id === row.id"
                    @click="openAppeal(row)"
                  >
                    {{ appeal?.id === row.id ? "正在复核" : "查看与复核" }}
                  </button>
                </td>
              </tr>
              <tr v-if="!appeals.items.length">
                <td colspan="5" class="console-table-empty">暂无申诉。</td>
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
      </section>
      <template v-if="appeal">
        <ConsoleSteps
          :steps="['事实与证据', '处理决定', '复核提交']"
          :current="appealStep(appeal.state)"
        />
        <div class="console-split">
          <section class="console-panel" aria-label="申诉来源">
            <h2 class="console-card-title">申诉来源</h2>
            <p class="console-card-desc">
              APPEAL-{{ appeal.id }} · {{ appealSubject(appeal) }}
            </p>
            <dl class="console-facts">
              <div>
                <dt>申诉状态</dt>
                <dd>
                  <span class="console-tag" :class="appealTone(appeal.state)">{{
                    stateLabels[appeal.state] || appeal.state
                  }}</span>
                  · 修订 {{ appeal.revision }}
                </dd>
              </div>
              <div>
                <dt>申诉对象</dt>
                <dd>
                  {{
                    appeal.subject === "CAMPUS_CERTIFICATION"
                      ? "校园邮箱认证"
                      : `限制记录 ${appeal.restrictionId} · 版本 ${appeal.restrictionRevision}`
                  }}
                </dd>
              </div>
              <div>
                <dt>原措施</dt>
                <dd>
                  <template
                    v-if="appeal.certificationRevocationReason"
                    >撤销认证 · {{ appeal.certificationRevocationReason
                    }}<template v-if="appeal.certificationRevokedAt">
                      · {{ dateText(appeal.certificationRevokedAt) }}</template
                    ></template
                  >
                  <template v-else>行为限制（见限制记录版本）</template>
                </dd>
              </div>
              <div>
                <dt>申诉理由</dt>
                <dd class="console-preserve">{{ appeal.reason }}</dd>
              </div>
              <div>
                <dt>提交时间</dt>
                <dd>{{ dateText(appeal.createdAt) }}</dd>
              </div>
              <div v-if="appeal.reply">
                <dt>已有答复</dt>
                <dd class="console-preserve">{{ appeal.reply }}</dd>
              </div>
            </dl>
            <p class="console-callout">
              申诉受理后维持当前措施，直至作出复核决定。内容的公开状态需要在内容治理中单独复核。
            </p>
          </section>
          <form
            id="appeal-form"
            class="console-panel"
            @submit.prevent="decide"
          >
            <h2 class="console-card-title">复核决定</h2>
            <p class="console-card-desc">
              确认后，用户可在本人申诉记录查看决定与答复。
            </p>
            <fieldset
              class="console-form"
              :disabled="
                frozen || appeal.state !== 'OPEN' || !decision.allowed.value
              "
            >
              <label class="console-field"
                ><span>复核决定</span
                ><select v-model="outcome" aria-label="复核结论">
                  <option value="REJECTED">驳回申诉</option>
                  <option value="ACCEPTED">
                    {{
                      appeal.subject === "CAMPUS_CERTIFICATION"
                        ? "接受认证申诉，由本人重新验证邮箱"
                        : "接受申诉，解除对应版本限制"
                    }}
                  </option>
                </select></label
              ><label class="console-field"
                ><span>处理答复</span
                ><textarea
                  v-model="reply"
                  maxlength="2000"
                  rows="3"
                  required
                  placeholder="向本人公开的复核答复。"
                ></textarea></label
              >
            </fieldset>
          </form>
        </div>
        <footer
          v-if="!decision.pending.value"
          class="console-footer"
        >
          <div class="console-footer-actions">
            <button
              class="console-secondary"
              type="button"
              :disabled="frozen"
              @click="closeAppeal"
            >
              关闭
            </button>
            <button
              class="console-primary"
              type="submit"
              form="appeal-form"
              :disabled="
                frozen || appeal.state !== 'OPEN' || !decision.allowed.value
              "
            >
              复核并提交
            </button>
          </div>
        </footer>
      </template>
    </div>
  </AdminGate>
</template>
