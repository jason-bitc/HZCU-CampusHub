<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { get, message, send } from "../lib/api";
import { clearSession, session } from "../lib/session";
import type { Catalog, Contact, Profile } from "../types";
import MemberGate from "../components/MemberGate.vue";
const router = useRouter();
const profile = ref<Profile>();
const contact = ref<Contact>();
const catalog = ref<Catalog>();
const wallet = ref<{ balance: number; receivedContribution: number }>();
const busy = ref(false);
const error = ref("");
const notice = ref("");
async function load() {
  if (session.status !== "ready") return;
  busy.value = true;
  error.value = "";
  try {
    const [p, c, t, w] = await Promise.all([
      get<Profile>("/me/profile", true),
      get<Contact>("/me/contact-settings", true),
      get<Catalog>("/catalog"),
      get<{ balance: number; receivedContribution: number }>("/me/coins", true),
    ]);
    profile.value = p;
    contact.value = c;
    catalog.value = t;
    wallet.value = w;
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function save(kind: "profile" | "contact") {
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    if (kind === "profile" && profile.value) {
      const { revision, ...data } = profile.value;
      const r = await send<Profile>("/me/profile", "PUT", {
        ...data,
        expectedRevision: revision,
      });
      profile.value = { ...data, revision: r.revision };
    } else if (contact.value) {
      const { revision, ...data } = contact.value;
      if (!data.type && !data.value) {
        data.type = null;
        data.value = null;
      }
      const r = await send<Contact>("/me/contact-settings", "PUT", {
        ...data,
        expectedRevision: revision,
      });
      contact.value = { ...data, revision: r.revision };
    }
    notice.value = "已保存。";
  } catch (e) {
    error.value = `${message(e)} 未自动覆盖。请保留当前修改，或重新读取服务器版本后再编辑。`;
  } finally {
    busy.value = false;
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
    ><div class="page-heading">
      <div>
        <h1>{{ profile?.nickname || "个人中心" }}</h1>
        <p>{{ session.me?.email }} · 校园邮箱已验证</p>
      </div>
      <button :disabled="busy" @click="logout">退出当前账号</button>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" class="success" role="status">{{ notice }}</p>
    <div class="profile-layout">
      <form v-if="profile" class="panel" @submit.prevent="save('profile')">
        <h2>个人资料</h2>
        <fieldset :disabled="busy">
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
      </form>
      <div class="stack">
        <form v-if="contact" class="panel" @submit.prevent="save('contact')">
          <h2>联系设置</h2>
          <fieldset :disabled="busy">
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
              关闭后读者无法读取联系方式，已保存的本人设置可以保留。
            </p>
            <button type="submit">保存联系设置</button>
          </fieldset>
        </form>
        <section v-if="wallet" class="panel">
          <h2>我的硬币</h2>
          <p class="balance">{{ wallet.balance }}<span> 硬币</span></p>
          <p>收到的有效贡献 {{ wallet.receivedContribution }} 硬币</p>
          <p class="hint">
            硬币不能购买、提现或兑换现金。网页奖励和账本待接入，暂请在小程序查看。
          </p>
        </section>
        <RouterLink class="button" to="/me/appeals">我的限制与申诉</RouterLink>
        <button :disabled="busy" @click="load">重新读取服务器资料</button>
      </div>
    </div></MemberGate
  >
</template>
