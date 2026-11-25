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
  <div class="console-login">
    <form class="console-login-card" @submit.prevent="submit">
      <p class="console-login-crumb">
        <span aria-hidden="true">城院帮</span>
        <span aria-hidden="true">/</span>
        <span aria-hidden="true">社区管理</span>
        <span aria-hidden="true">/</span>
        <span aria-current="page">登录</span>
      </p>
      <h1>社区管理登录</h1>
      <p class="console-login-desc">
        使用社区管理账号进入案件和治理工作台。
      </p>
      <p
        v-if="adminSession.status === 'ready' && !preview"
        class="console-banner is-success console-login-session"
      >
        管理会话已核验。<RouterLink to="/admin/cases">进入案件队列</RouterLink>
      </p>
      <fieldset :disabled="busy || preview">
        <label
          >账号<input
            v-model="login"
            name="admin-login"
            autocomplete="username"
            required
            placeholder="输入管理员账号" /></label
        ><label
          >密码<input
            v-model="password"
            name="admin-password"
            type="password"
            autocomplete="current-password"
            required
            placeholder="输入密码" /></label
        ><button
          class="console-primary console-login-submit"
          type="submit"
        >
          {{ busy ? "正在核验管理身份…" : "登录社区管理" }}
        </button>
      </fieldset>
      <p v-if="error" class="console-banner is-error console-login-error" role="alert">
        {{ error }}
      </p>
      <p class="console-login-extra">
        仅限已授权的社区管理账号。普通校园账号不能获得管理权限；社区身份不能执行部署或恢复。
      </p>
    </form>
  </div>
</template>
