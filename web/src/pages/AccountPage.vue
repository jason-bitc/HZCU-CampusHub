<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { acceptSession, session } from "../lib/session";
import { get, message, request } from "../lib/api";
import type { Challenge, Login, Me } from "../types";
const router = useRouter();
const route = useRoute();
const mode = ref<"login" | "register" | "reset">("login");
const email = ref("");
const password = ref("");
const code = ref("");
const challenge = ref<Challenge>();
const busy = ref(false);
const error = ref("");
const notice = ref("");
const now = ref(Date.now());
const timer = setInterval(() => (now.value = Date.now()), 1000);
onBeforeUnmount(() => clearInterval(timer));
const remaining = computed(() =>
  challenge.value
    ? Math.max(
        0,
        Math.ceil((Date.parse(challenge.value.resendAt) - now.value) / 1000),
      )
    : 0,
);
function normalizeEmail() {
  const value = email.value.trim().toLowerCase();
  if (
    !/^[a-z0-9_%+.-]{1,64}@stu\.hzcu\.edu\.cn$/.test(value) ||
    value.split("@")[0]?.includes("..") ||
    value.startsWith(".") ||
    value.includes(".@")
  )
    throw new Error("请输入有效的 stu.hzcu.edu.cn 校园邮箱");
  return value;
}
function validatePassword() {
  if (
    [...password.value].length < 8 ||
    [...password.value].length > 64 ||
    new TextEncoder().encode(password.value).length > 72
  )
    throw new Error("密码需为 8–64 个字符，UTF-8 不超过 72 字节");
}
function switchMode(value: typeof mode.value) {
  mode.value = value;
  password.value = "";
  code.value = "";
  challenge.value = undefined;
  error.value = "";
  notice.value = "";
}
async function sendCode() {
  busy.value = true;
  error.value = "";
  try {
    const value = normalizeEmail();
    challenge.value = await request<Challenge>(
      `/auth/${mode.value === "register" ? "registration-codes" : "password-reset-codes"}`,
      { method: "POST", data: { email: value } },
    );
    notice.value = `${challenge.value.notice} 请检查收件箱，服务受理不等于邮件已送达。`;
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function submit() {
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const value = normalizeEmail();
    validatePassword();
    if (mode.value === "login") {
      const login = await request<Login>("/auth/login", {
        method: "POST",
        data: { email: value, password: password.value },
      });
      password.value = "";
      acceptSession(login);
      try {
        session.me = await get<Me>("/me", true);
        session.status = "ready";
      } catch (e) {
        if (session.token) {
          session.status = "error";
          session.message = message(e);
        }
        throw e;
      }
      const next =
        typeof route.query.next === "string" &&
        /^\/(?!\/)/.test(route.query.next)
          ? route.query.next
          : "/workspace";
      await router.replace(next);
    } else {
      if (
        !challenge.value ||
        Date.parse(challenge.value.expiresAt) <= Date.now()
      )
        throw new Error("请先获取当前邮箱的有效验证码");
      if (!/^\d{6}$/.test(code.value)) throw new Error("请输入 6 位数字验证码");
      const registered = mode.value === "register";
      await request(registered ? "/auth/register" : "/auth/password-resets", {
        method: "POST",
        data: {
          email: value,
          password: password.value,
          challengeId: challenge.value.challengeId,
          code: code.value,
        },
      });
      switchMode("login");
      notice.value = registered
        ? "注册成功，请使用校园账号登录。"
        : "密码已重置，旧会话已失效，请重新登录。";
    }
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="account-layout">
    <section class="account-intro">
      <h1>把经验留给<br />下一个出发的人</h1>
      <p>登录后收藏有用的经验，也可以把自己的准备、投递与复盘整理成文。</p>
      <ul class="plain-list">
        <li>所有公开正文始终免费阅读</li>
        <li>网页和小程序共享文章与收藏</li>
        <li>联系方式由本人选择是否开放</li>
      </ul>
      <RouterLink class="inline-link" to="/">先看看公开经验 →</RouterLink>
    </section>
    <section class="panel account-panel">
      <div class="segmented account-tabs">
        <button
          :aria-pressed="mode === 'login'"
          :disabled="busy"
          @click="switchMode('login')"
        >
          登录</button
        ><button
          :aria-pressed="mode === 'register'"
          :disabled="busy"
          @click="switchMode('register')"
        >
          校园邮箱注册</button
        ><button
          :aria-pressed="mode === 'reset'"
          :disabled="busy"
          @click="switchMode('reset')"
        >
          重置密码
        </button>
      </div>
      <h2>
        {{
          mode === "login"
            ? "欢迎回到城院帮"
            : mode === "register"
              ? "验证你的校园邮箱"
              : "重置应用登录密码"
        }}
      </h2>
      <p class="muted">仅支持 stu.hzcu.edu.cn 校园邮箱</p>
      <form @submit.prevent="submit">
        <fieldset :disabled="busy">
          <label
            >校园邮箱<input
              v-model="email"
              name="email"
              type="email"
              required
              autocomplete="username"
              placeholder="你的校园邮箱"
              @input="
                challenge = undefined;
                code = '';
              " /></label
          ><template v-if="mode !== 'login'"
            ><div class="code-row">
              <label
                >验证码<input
                  v-model="code"
                  name="code"
                  required
                  inputmode="numeric"
                  pattern="[0-9]{6}"
                  maxlength="6"
                  autocomplete="one-time-code" /></label
              ><button
                type="button"
                :disabled="remaining > 0"
                @click="sendCode"
              >
                {{ remaining ? `${remaining} 秒后重发` : "发送验证码" }}
              </button>
            </div>
            <p v-if="challenge" class="hint">
              有效期至
              {{ new Date(challenge.expiresAt).toLocaleTimeString("zh-CN") }}
            </p></template
          ><label
            >{{ mode === "login" ? "应用登录密码" : "设置应用登录密码"
            }}<input
              v-model="password"
              name="password"
              type="password"
              required
              :autocomplete="
                mode === 'login' ? 'current-password' : 'new-password'
              "
          /></label>
          <p class="hint">
            密码 8–64 个字符，UTF-8 不超过 72 字节。请使用应用密码。
          </p>
          <button class="primary full" type="submit">
            {{
              busy
                ? "正在处理…"
                : mode === "login"
                  ? "登录校园账号"
                  : mode === "register"
                    ? "完成注册"
                    : "重置密码"
            }}
          </button>
        </fieldset>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="notice" class="success" role="status">{{ notice }}</p>
      </form>
    </section>
  </div>
</template>
