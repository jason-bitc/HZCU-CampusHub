<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { get, message, send } from "../lib/api";
import { clearSession, session } from "../lib/session";
import type { Catalog, Contact, Profile, Wallet } from "../types";
import MemberGate from "../components/MemberGate.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import OperationFeedback from "../components/OperationFeedback.vue";
import { operationFailure, type Feedback } from "../lib/feedback";
import AuthorHonor from "../components/AuthorHonor.vue";
const router = useRouter();
const profile = ref<Profile>();
const contact = ref<Contact>();
const catalog = ref<Catalog>();
const wallet = ref<Wallet>();
const stageName: Record<string, string> = {
  UNSET: "暂未设置",
  ENROLLED: "在读",
  GRADUATED: "已毕业",
};
const contactType: Record<string, string> = {
  WECHAT: "微信",
  QQ: "QQ",
  WEB: "网页",
};
const majorName = computed(() =>
  catalog.value?.majors.find((m) => m.id === profile.value?.majorId)?.name,
);
type Section = "profile" | "contact" | "wallet";
const busy = ref(false);
const error = ref("");
const confirmation = ref<InstanceType<typeof ConfirmDialog>>();
const saving = ref<Partial<Record<Section, boolean>>>({});
const loading = ref<Partial<Record<Section, boolean>>>({});
const feedback = ref<Partial<Record<Section, Feedback>>>({});
const snapshots: Partial<Record<Section, string>> = {};
const labels = { profile: "个人资料", contact: "联系设置", wallet: "我的硬币" };
const paths = {
  profile: "/me/profile",
  contact: "/me/contact-settings",
  wallet: "/me/coins",
};
async function readSection(kind: Section, explicit = false) {
  if (session.status !== "ready" || loading.value[kind] || saving.value[kind])
    return;
  const previous =
    kind === "profile"
      ? profile.value
      : kind === "contact"
        ? contact.value
        : undefined;
  if (
    explicit &&
    previous &&
    JSON.stringify(previous) !== snapshots[kind] &&
    !(await confirmation.value?.ask({
      title: "重新读取" + labels[kind] + "？",
      detail: "服务器记录将覆盖这块表单中的未保存输入。",
      action: "读取服务器版本",
    }))
  )
    return;
  loading.value[kind] = true;
  delete feedback.value[kind];
  try {
    if (kind === "profile") {
      const [data, categories] = await Promise.all([
        get<Profile>(paths.profile, true),
        get<Catalog>("/catalog"),
      ]);
      profile.value = data;
      catalog.value = categories;
      snapshots.profile = JSON.stringify(data);
    } else if (kind === "contact") {
      contact.value = await get<Contact>(paths.contact, true);
      snapshots.contact = JSON.stringify(contact.value);
    } else wallet.value = await get<Wallet>(paths.wallet, true);
    if (explicit)
      feedback.value[kind] = {
        kind: "success",
        text: "已重新读取" + labels[kind] + "。",
      };
  } catch (e) {
    feedback.value[kind] = operationFailure(e);
  } finally {
    loading.value[kind] = false;
  }
}
function load() {
  void readSection("profile");
  void readSection("contact");
  void readSection("wallet");
}
async function save(kind: "profile" | "contact") {
  const current = kind === "profile" ? profile.value : contact.value;
  if (!current || saving.value[kind] || loading.value[kind]) return;
  saving.value[kind] = true;
  delete feedback.value[kind];
  try {
    const { revision, ...data } = current;
    if ("type" in data && !data.type && !data.value) {
      data.type = null;
      data.value = null;
    }
    const receipt = await send<{ revision: number }>(paths[kind], "PUT", {
      ...data,
      expectedRevision: revision,
    });
    current.revision = receipt.revision;
    snapshots[kind] = JSON.stringify(current);
    feedback.value[kind] = { kind: "success", text: labels[kind] + "已保存。" };
  } catch (e) {
    feedback.value[kind] = operationFailure(e, "保存结果待确认。");
    feedback.value[kind]!.text += " 当前输入已保留。";
  } finally {
    saving.value[kind] = false;
  }
}
async function logout() {
  busy.value = true;
  error.value = "";
  try {
    await send("/auth/logout", "POST");
    clearSession();
    await router.replace("/");
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
  <MemberGate title="登录后查看个人资料"
    ><ConfirmDialog ref="confirmation" />
    <div class="page-heading service-page-heading">
      <div>
        <h1>个人资料与联系设置</h1>
        <p>{{ session.me?.email }}</p>
      </div>
      <button :disabled="busy" @click="logout">退出当前账号</button>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <section class="panel service-card" aria-label="个人资料与联系设置">
      <dl class="kv">
        <div>
          <dt>本人认证</dt>
          <dd>
            <span>校园邮箱认证</span
            ><RouterLink to="/me/certification">查看认证状态</RouterLink>
          </dd>
        </div>
        <div>
          <dt>个人资料</dt>
          <dd>
            <span v-if="profile"
              >{{ profile.nickname }}<template v-if="majorName">
                · {{ majorName }}</template
              ><template v-if="profile.currentStage !== 'UNSET'">
                · {{ stageName[profile.currentStage] }}</template
              ></span
            >
            <span v-else class="hint">正在读取个人资料…</span>
            <form class="kv-form" @submit.prevent="save('profile')">
              <fieldset v-if="profile" :disabled="saving.profile || loading.profile">
                <div class="form-row">
                  <label
                    >昵称<input
                      v-model="profile.nickname"
                      required
                      maxlength="40" /></label
                  ><label
                    >本人专业<select v-model="profile.majorId">
                      <option :value="null">暂不设置</option>
                      <option
                        v-for="m in catalog?.majors"
                        :key="m.id"
                        :value="m.id"
                      >
                        {{ m.name }}
                      </option>
                    </select></label
                  >
                </div>
                <div class="form-row">
                  <label
                    >当前阶段<select v-model="profile.currentStage">
                      <option value="UNSET">暂不设置</option>
                      <option value="ENROLLED">在读</option>
                      <option value="GRADUATED">已毕业</option>
                    </select></label
                  >
                </div>
                <label
                  >个人简介<textarea
                    v-model="profile.bio"
                    rows="3"
                    maxlength="1000"
                  ></textarea>
                </label>
                <OperationFeedback
                  :feedback="feedback.profile"
                  :processing="
                    saving.profile
                      ? '正在保存个人资料…'
                      : loading.profile
                        ? '正在读取个人资料…'
                        : undefined
                  "
                />
                <div class="form-actions">
                  <button class="primary" type="submit">保存个人资料</button
                  ><button
                    type="button"
                    :disabled="saving.profile || loading.profile"
                    @click="readSection('profile', true)"
                  >
                    重新读取
                  </button>
                </div>
              </fieldset>
            </form>
          </dd>
        </div>
        <div>
          <dt>联系设置</dt>
          <dd>
            <span v-if="contact">{{
              contact.enabled
                ? `公开${contact.type ? ` · ${contactType[contact.type]}` : ""}`
                : "公开已关闭"
            }}</span>
            <span v-else class="hint">正在读取联系设置…</span>
            <form class="kv-form" @submit.prevent="save('contact')">
              <fieldset v-if="contact" :disabled="saving.contact || loading.contact">
                <label class="checkbox"
                  ><input
                    v-model="contact.enabled"
                    type="checkbox"
                  />向已认证用户开放联系入口</label
                >
                <div class="form-row">
                  <label
                    >联系方式类型<select v-model="contact.type">
                      <option :value="null">不设置</option>
                      <option value="WECHAT">微信</option>
                      <option value="QQ">QQ</option>
                      <option value="WEB">网页</option>
                    </select></label
                  ><label
                    >联系信息<input v-model="contact.value" maxlength="2000"
                  /></label>
                </div>
                <p class="hint">
                  {{
                    contact.enabled
                      ? "开启后，已认证读者可通过联系入口读取已保存的联系方式。"
                      : "关闭后读者无法查看联系方式。下次打开开关时，可以继续使用已保存的信息。"
                  }}
                </p>
                <OperationFeedback
                  :feedback="feedback.contact"
                  :processing="
                    saving.contact
                      ? '正在保存联系设置…'
                      : loading.contact
                        ? '正在读取联系设置…'
                        : undefined
                  "
                />
                <div class="form-actions">
                  <button type="submit">保存联系设置</button
                  ><button
                    type="button"
                    :disabled="saving.contact || loading.contact"
                    @click="readSection('contact', true)"
                  >
                    重新读取
                  </button>
                </div>
              </fieldset>
            </form>
          </dd>
        </div>
        <div>
          <dt>我的通知</dt>
          <dd><RouterLink to="/me/notifications">查看通知</RouterLink></dd>
        </div>
        <div>
          <dt>我的硬币与奖励</dt>
          <dd>
            <span v-if="wallet">{{ wallet.balance }} 硬币可用</span>
            <span v-else class="hint">正在读取硬币…</span>
            <RouterLink to="/me/wallet">查看账本</RouterLink>
            <template v-if="wallet?.honor">
              <small
                >作者荣誉 · <AuthorHonor :honor="wallet.honor"
              /></small>
            </template>
            <OperationFeedback
              :feedback="feedback.wallet"
              :processing="loading.wallet ? '正在读取硬币…' : undefined"
            />
          </dd>
        </div>
        <div>
          <dt>反馈与申诉</dt>
          <dd>
            <RouterLink to="/me/cases">查看本人记录</RouterLink
            ><RouterLink to="/me/appeals">限制与申诉</RouterLink>
          </dd>
        </div>
      </dl>
    </section></MemberGate
  >
</template>
