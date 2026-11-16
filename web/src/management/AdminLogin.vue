<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminSession, loginAdmin } from "./client";
import { message } from "../lib/api";
const route = useRoute();
const router = useRouter();
const preview = computed(() => route.query.preview === "1");
const login = ref("");
const password = ref("");
const error = ref("");
const busy = ref(false);
async function submit() {
  if (preview.value) return;
  busy.value = true;
  error.value = "";
  try {
    if (!/^[a-z0-9][a-z0-9._-]{2,63}$/.test(login.value))
      throw new Error("请输入3–64位小写字母、数字、点、短横线或下划线管理账号");
    if (
      [...password.value].length < 8 ||
      [...password.value].length > 64 ||
      new TextEncoder().encode(password.value).length > 72
    )
      throw new Error("密码需8–64个字符，UTF-8不超过72字节");
    await loginAdmin(login.value, password.value);
    password.value = "";
    await router.replace("/admin/cases");
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <h1 class="console-title">社区管理登录</h1>
  <div class="console-login-grid">
    <form class="console-panel console-login-panel" @submit.prevent="submit">
      <h2>仅限已授权的社区管理账号</h2>
      <p
        v-if="adminSession.status === 'ready' && !preview"
        class="console-note"
      >
        管理会话已核验。<RouterLink to="/admin/cases">进入待办案件</RouterLink>
      </p>
      <fieldset :disabled="busy || preview">
        <label
          >管理账号 *<input
            v-model="login"
            name="admin-login"
            autocomplete="username"
            required
            placeholder="请输入账号" /></label
        ><label
          >密码 *<input
            v-model="password"
            name="admin-password"
            type="password"
            autocomplete="current-password"
            required
            placeholder="请输入密码" /></label
        ><button class="console-primary" type="submit">
          {{ busy ? "正在核验管理身份…" : "登录社区管理" }}
        </button>
      </fieldset>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </form>
    <div class="console-login-aside">
      <p class="console-note">
        社区管理只处理内容、案件和分类。普通校园账号不能获得管理权限；社区身份不能执行部署或恢复。
      </p>
      <section class="console-panel">
        <h2>无权限状态</h2>
        <p v-if="error || preview" class="console-negative console-emphasis">
          此账号没有社区管理权限。
        </p>
        <p v-else>需使用已授权管理账号，普通校园认证不能提升权限。</p>
        <p>返回普通阅读，或使用已授权管理账号。</p>
        <RouterLink class="button" to="/">返回普通阅读</RouterLink>
      </section>
    </div>
  </div>
</template>
