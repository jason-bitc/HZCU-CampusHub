<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminRequest, adminSession } from "./client";
import type { Page, User } from "./governance";
import { message } from "../lib/api";
import AdminGate from "./AdminGate.vue";
import AdminCertification from "./AdminCertification.vue";
import ConsolePageHeader from "./ConsolePageHeader.vue";
import ConsolePager from "./ConsolePager.vue";
const route = useRoute(),
  router = useRouter();
const users = ref<Page<User>>(),
  user = ref<User>(),
  keyword = ref(""),
  error = ref(""),
  busy = ref(false);
let generation = 0;
const accountStateText = (state: string) =>
  ({ ACTIVE: "正常", RESTRICTED: "受限", DISABLED: "停用" })[state] || state;
const page = computed(() => Math.max(1, Number(route.query.page) || 1));
const rows = computed(() =>
  (users.value?.items || []).filter((row) => {
    const text = keyword.value.trim().toLowerCase();
    return (
      !text ||
      row.nickname.toLowerCase().includes(text) ||
      `u-${row.id}`.includes(text)
    );
  }),
);
async function load() {
  if (adminSession.status !== "ready") return;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  users.value = undefined;
  try {
    const result = await adminRequest<Page<User>>(
      `/admin/users?page=${page.value}&size=10`,
    );
    if (current !== generation) return;
    users.value = result;
    if (user.value)
      user.value = result.items.find((row) => row.id === user.value?.id);
  } catch (e) {
    if (current === generation) error.value = message(e);
  } finally {
    if (current === generation) busy.value = false;
  }
}
function turn(next: number) {
  void router.push({ query: { ...route.query, page: String(next) } });
}
onMounted(load);
watch(() => route.query, load);
watch(() => adminSession.status, load);
onBeforeUnmount(() => generation++);
</script>
<template>
  <AdminGate>
    <div class="console-page">
      <ConsolePageHeader
        title="校园认证"
        description="查看会员校园邮箱认证状态；撤销须记录理由并保留申诉路径。"
      />
      <p v-if="error" class="console-banner is-error" role="alert">
        {{ error }}
        <span class="console-banner-actions"
          ><button type="button" :disabled="busy" @click="load">
            重新读取</button
          ></span
        >
      </p>
      <p v-if="busy" class="console-muted" role="status">正在读取账号…</p>
      <section class="console-panel" aria-labelledby="certification-list-title">
        <div class="console-card-head">
          <div>
            <h2 id="certification-list-title" class="console-card-title">
              认证对象
            </h2>
            <p class="console-card-desc">
              选择账号后核对认证状态；撤销决定需填写理由。
            </p>
          </div>
          <div class="console-card-tools">
            <label class="console-inline-label"
              >关键词<input
                v-model="keyword"
                placeholder="昵称或编号"
                aria-label="关键词"
            /></label>
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
              <tr v-for="row in rows" :key="row.id">
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
                    :aria-pressed="user?.id === row.id"
                    @click="user = row"
                  >
                    {{ user?.id === row.id ? "已选中" : "查看认证" }}
                  </button>
                </td>
              </tr>
              <tr v-if="!busy && !rows.length">
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
          :busy="busy"
          @turn="turn"
        />
      </section>
      <AdminCertification
        v-if="user"
        :key="user.id"
        :user-id="user.id"
        :nickname="user.nickname"
      />
    </div>
  </AdminGate>
</template>
