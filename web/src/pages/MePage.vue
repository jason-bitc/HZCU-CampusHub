<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
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
    <div class="page-heading">
      <div>
        <h1>{{ profile?.nickname || "个人中心" }}</h1>
        <p>{{ session.me?.email }} · 校园邮箱已验证</p>
      </div>
      <button :disabled="busy" @click="logout">退出当前账号</button>
    </div>
    <nav class="card-actions" aria-label="个人服务">
      <RouterLink class="button" to="/me/notifications">站内通知</RouterLink>
      <RouterLink class="button" to="/me/wallet">奖励与账目</RouterLink>
      <RouterLink class="button" to="/me/cases">我的反馈与举报</RouterLink>
      <RouterLink class="button" to="/me/certification"
        >校园认证状态</RouterLink
      >
    </nav>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="profile-vertical">
      <form class="panel" @submit.prevent="save('profile')">
        <h2>个人资料</h2>
        <fieldset v-if="profile" :disabled="saving.profile || loading.profile">
          <label
            >昵称<input
              v-model="profile.nickname"
              required
              maxlength="40" /></label
          ><label
            >本人专业<select v-model="profile.majorId">
              <option :value="null">暂不设置</option>
              <option v-for="m in catalog?.majors" :key="m.id" :value="m.id">
                {{ m.name }}
              </option>
            </select></label
          ><label
            >当前阶段<select v-model="profile.currentStage">
              <option value="UNSET">暂不设置</option>
              <option value="ENROLLED">在读</option>
              <option value="GRADUATED">已毕业</option>
            </select></label
          ><label
            >个人简介<textarea
              v-model="profile.bio"
              rows="4"
              maxlength="1000"
            ></textarea></label
          ><button class="primary" type="submit">保存资料</button>
        </fieldset>
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
        <button
          type="button"
          class="text-button"
          :disabled="saving.profile || loading.profile"
          @click="readSection('profile', true)"
        >
          重新读取个人资料
        </button>
      </form>
      <form class="panel" @submit.prevent="save('contact')">
        <h2>联系设置</h2>
        <fieldset v-if="contact" :disabled="saving.contact || loading.contact">
          <label class="checkbox"
            ><input
              v-model="contact.enabled"
              type="checkbox"
            />向已认证用户开放联系入口</label
          ><label
            >联系方式类型<select v-model="contact.type">
              <option :value="null">不设置</option>
              <option value="WECHAT">微信</option>
              <option value="QQ">QQ</option>
              <option value="WEB">网页</option>
            </select></label
          ><label
            >联系信息<input v-model="contact.value" maxlength="2000"
          /></label>
          <p class="hint">
            {{
              contact.enabled
                ? "开启后，已认证读者可通过联系入口读取已保存的联系方式。"
                : "关闭后读者无法查看联系方式。下次打开开关时，可以继续使用已保存的信息。"
            }}
          </p>
          <button type="submit">保存联系设置</button>
        </fieldset>
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
        <button
          type="button"
          class="text-button"
          :disabled="saving.contact || loading.contact"
          @click="readSection('contact', true)"
        >
          重新读取联系设置
        </button>
      </form>
      <section class="panel">
        <h2>我的硬币</h2>
        <template v-if="wallet">
          <p class="balance">{{ wallet.balance }}<span> 硬币</span></p>
          <p>收到的有效贡献 {{ wallet.receivedContribution }} 硬币</p>
          <p v-if="wallet.honor">
            作者荣誉 <AuthorHonor :honor="wallet.honor" />
          </p>
          <p v-else class="hint">当前暂无作者荣誉。</p>
          <p class="hint">硬币不能购买、提现或兑换现金。</p>
        </template>
        <OperationFeedback
          :feedback="feedback.wallet"
          :processing="loading.wallet ? '正在读取硬币…' : undefined"
        />
        <button
          v-if="feedback.wallet?.kind === 'error'"
          :disabled="loading.wallet"
          @click="readSection('wallet', true)"
        >
          重读硬币
        </button>
      </section>
    </div></MemberGate
  >
</template>
