<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import ConsoleTable from "./ConsoleTable.vue";
import IntegrationNotice from "./IntegrationNotice.vue";
import { adminRequest, adminSession } from "./client";
import { caseStates, outcomes } from "./types";
import type { CaseDetail } from "./types";
import type { Catalog } from "../types";
import { get, message } from "../lib/api";
import { dateText } from "../lib/content";
const route = useRoute();
const preview = computed(() => route.query.preview === "1");
const section = computed(() => route.path.split("/").pop() || "content");
const group = ref<keyof Catalog>("jobs");
const catalog = ref<Catalog>();
const caseId = ref("");
const audit = ref<string[][]>([]);
const error = ref("");
const busy = ref(false);
const title = computed(
  () =>
    ({
      content: "内容巡查",
      accounts: "账号限制与申诉复核",
      categories: "岗位、专业、公司与主题",
      contributions: "贡献治理与荣誉规则",
      audit: "治理审计与操作结果",
    })[section.value as "content"] || "社区管理",
);
const categoryRows = computed(() =>
  preview.value
    ? [
        [
          "C++后端",
          "C++开发 / C++服务器",
          "岗位",
          "启用",
          "38篇经验",
          "编辑 / 停用",
        ],
        ["软件工程", "软工", "专业", "启用", "56篇经验", "编辑 / 合并映射"],
        ["旧公司示例", "历史名称", "公司", "已停用", "2篇历史经验", "查看映射"],
      ]
    : catalog.value?.[group.value].map((row) => [
        row.name,
        "未提供",
        { jobs: "岗位", majors: "专业", companies: "公司", topics: "主题" }[
          group.value
        ],
        "启用",
        "未提供",
        "写接口待接入",
      ]) || [],
);
async function loadCatalog() {
  if (preview.value) return;
  try {
    catalog.value = await get<Catalog>("/catalog");
  } catch (e) {
    error.value = message(e);
  }
}
async function readAudit() {
  if (preview.value || adminSession.status !== "ready") return;
  busy.value = true;
  error.value = "";
  audit.value = [];
  try {
    if (
      !/^[1-9]\d*$/.test(caseId.value) ||
      !Number.isSafeInteger(Number(caseId.value))
    )
      throw new Error("请输入有效案件编号");
    const d = await adminRequest<CaseDetail>(`/admin/cases/${caseId.value}`);
    audit.value = d.history.map((row, index) => [
      `${dateText(row.occurredAt)} / 修订${row.revision}`,
      `案件 ${d.caseId} · 操作人 ${row.actorId}`,
      `${index ? caseStates[d.history[index - 1]?.state || ""] : ""} → ${caseStates[row.state]}`,
      row.reason ||
        row.internalNote ||
        row.reply ||
        outcomes[row.outcome || ""] ||
        row.action,
      "已记录",
    ]);
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
onMounted(() => {
  if (section.value === "categories") void loadCatalog();
});
</script>
<template>
  <h1 class="console-title">{{ title }}</h1>
  <IntegrationNotice
    :preview="preview"
    :feature="section === 'audit' ? '全局治理审计' : title"
  />
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <template v-if="section === 'content'"
    ><section class="console-panel console-patrol">
      <div class="console-filterbar">
        <span>文章 / 评论</span
        ><label class="console-inline-label"
          >状态<select disabled aria-label="巡查状态">
            <option>全部</option>
          </select></label
        ><label class="console-inline-label"
          >岗位<select disabled aria-label="巡查岗位">
            <option>全部</option>
          </select></label
        ><input disabled placeholder="关键词检索" aria-label="巡查关键词" />
      </div>
      <ConsoleTable
        :columns="['内容 / 对象', '作者', '状态', '更新时间', '可执行操作']"
        :rows="
          preview
            ? [
                [
                  '8周实习准备复盘 / 文章',
                  '用户 DEMO-U01',
                  '公开',
                  '11月4日 09:20',
                  '查看 / 隐藏',
                ],
                [
                  '项目选择的经验 / 文章',
                  '用户 DEMO-U02',
                  '管理员隐藏',
                  '11月3日 16:00',
                  '查看理由 / 授权恢复',
                ],
                [
                  '某条回复 / 评论',
                  '用户 DEMO-U03',
                  '公开',
                  '11月3日 15:20',
                  '查看上下文 / 隐藏',
                ],
              ]
            : []
        "
      />
    </section>
    <div class="console-split console-followup">
      <section class="console-panel">
        <h2>处置确认</h2>
        <h3>对象：{{ preview ? "文章 DEMO-001" : "待选择" }} 动作：隐藏</h3>
        <label
          >必填理由<textarea
            disabled
            rows="2"
            :placeholder="
              preview ? '内容含未经说明的推广。' : '填写处理依据，关联案件。'
            "
          ></textarea>
        </label>
        <div class="console-actions">
          <button class="console-danger" disabled>确认隐藏</button
          ><button disabled>取消</button>
        </div>
      </section>
      <p class="console-note">
        先发布后治理，不增加逐篇人工预审。恢复内容需社区权限；作者自主下架与管理员隐藏不同。
      </p>
    </div></template
  >
  <div v-else-if="section === 'accounts'" class="console-split console-equal">
    <section class="console-panel console-account-limit">
      <h2>限制范围</h2>
      <fieldset disabled>
        <label
          >关联账号 *<input
            :placeholder="preview ? '用户 DEMO-U01' : '选择关联账号'" /></label
        ><label
          >限制行为 *<select>
            <option>发布 / 评论（明确所选范围）</option>
          </select></label
        ><label
          >理由 *<textarea
            rows="3"
            placeholder="填写处理依据并关联案件。"
          ></textarea>
        </label>
        <p class="console-muted">开始与解除记录均保留；决定通知本人。</p>
        <button class="console-danger">确认限制所选行为</button
        ><button>解除已有账号限制</button>
      </fieldset>
    </section>
    <section class="console-panel console-account-limit">
      <h2>本人申诉与复核</h2>
      <h3 class="console-big">
        {{ preview ? "申诉 DEMO-AP08 · 复核中" : "待选择申诉" }}
      </h3>
      <p class="console-muted">
        原决定：{{ preview ? "限制发布与评论" : "未读取" }}
      </p>
      <p class="console-muted">
        申诉理由：{{ preview ? "补充事实及来源说明。" : "未读取" }}
      </p>
      <p class="console-muted">申请者可以访问本人申诉，即使认证已失效。</p>
      <fieldset disabled>
        <label
          >复核结论 *<select>
            <option>维持原决定 / 撤销所选限制</option>
          </select></label
        ><label
          >复核理由 *<textarea
            rows="3"
            placeholder="写明核对结果与实际影响。"
          ></textarea></label
        ><button class="console-primary">保存复核决定并通知本人</button>
      </fieldset>
      <p class="console-muted">解除限制不自动恢复文章；需要另行审查内容。</p>
    </section>
  </div>
  <template v-else-if="section === 'categories'"
    ><section class="console-panel console-category-list">
      <div class="console-filterbar">
        <div class="console-category-tabs">
          <button
            v-for="(label, key) in {
              jobs: '岗位',
              majors: '专业',
              companies: '公司',
              topics: '主题',
            }"
            :key="key"
            :aria-pressed="group === key"
            @click="group = key"
          >
            {{ label }}
          </button>
        </div>
        <button class="console-primary console-push-right" disabled>
          新增标签
        </button>
      </div>
      <ConsoleTable
        :columns="['标准名称', '同义名称', '类型', '状态', '历史关联', '操作']"
        :rows="categoryRows"
      />
      <p v-if="!preview" class="hint">
        当前仅只读公开 catalog
        的启用标签；不含停用记录、同义映射和历史关联数量。
      </p>
    </section>
    <div class="console-split console-followup">
      <section class="console-panel">
        <h2>合并或停用前确认</h2>
        <h3>源标签 → 目标标签：{{ preview ? "软工 → 软件工程" : "待选择" }}</h3>
        <p class="console-muted">
          历史文章保留关联，旧筛选名称解释到标准名称。
        </p>
        <button disabled>确认映射关系</button>
      </section>
      <p class="console-note">
        分类支持全校结构，首批可放计算机示例。停用不会删除历史文章，分类不变成招聘投递板块。
      </p>
    </div></template
  >
  <div
    v-else-if="section === 'contributions'"
    class="console-split console-contribution-split"
  >
    <section class="console-panel console-contribution">
      <h2>贡献核查</h2>
      <ConsoleTable
        :columns="['记录', '文章', '有效状态', '操作']"
        :widths="['37.5758%', '21.5152%', '18.7879%', '22.1211%']"
        :row-height="83"
        :rows="
          preview
            ? [
                ['DEMO-G021 · 1币', 'DEMO-001', '有效', '查看关联案件'],
                ['DEMO-G018 · 2币', 'DEMO-003', '待核查', '查看历史记录'],
              ]
            : []
        "
      /><label
        >失效原因与关联案件 *<textarea
          disabled
          rows="3"
          placeholder="填写事实，关联已确认的治理结果。"
        ></textarea>
      </label>
      <p class="console-muted">失效后更新有效贡献、公开排序与已启用荣誉。</p>
      <p class="console-warning-text">
        钱包返还细则待确认；此原型不提供任意改余额。
      </p>
      <button class="console-danger" disabled>确认贡献失效并保留审计</button>
    </section>
    <section class="console-panel console-contribution">
      <h2>荣誉规则（设计待确认）</h2>
      <ConsoleTable
        :columns="['称号', '门槛', '状态']"
        :widths="['41.6667%', '27.451%', '30.8823%']"
        :row-height="77"
        :rows="
          preview
            ? [
                ['城院之星', '待评审', '停用'],
                ['测试称号A', '测试10', '仅测试'],
                ['测试称号B', '测试20', '仅测试'],
              ]
            : []
        "
      />
      <p class="console-muted">正式规则未确认，不启用公开称号。</p>
      <p class="console-muted">规则含称号、门槛、图案及启用状态。</p>
      <p class="console-muted">测试集仅用于S04，不代表正式门槛。</p>
      <button disabled>查看测试状态预览</button
      ><button disabled>正式启用：待设计评审</button>
    </section>
  </div>
  <template v-else
    ><section class="console-panel console-audit">
      <form
        v-if="!preview"
        class="console-filterbar"
        @submit.prevent="readAudit"
      >
        <label class="console-inline-label"
          >案件编号<input
            v-model="caseId"
            inputmode="numeric"
            placeholder="读取本人获授权的案件历史"
            :disabled="busy" /></label
        ><button :disabled="busy || adminSession.status !== 'ready'">
          读取案件处理历史</button
        ><RouterLink v-if="adminSession.status !== 'ready'" to="/admin/login"
          >先登录社区管理</RouterLink
        >
      </form>
      <ConsoleTable
        :columns="['时间 / 记录', '对象', '前后状态', '理由 / 关联', '结果']"
        :rows="
          preview
            ? [
                [
                  '11月4日 10:10 / AUD-08',
                  '文章 DEMO-001',
                  '公开 → 隐藏',
                  '广告推广 / DEMO-C102',
                  '已完成',
                ],
                [
                  '11月4日 09:58 / AUD-07',
                  '用户 DEMO-U01',
                  '发布允许 → 受限',
                  '治理结论 / DEMO-C08',
                  '已完成',
                ],
                [
                  '11月4日 09:51 / AUD-06',
                  '贡献 DEMO-G018',
                  '有效 → 失效',
                  '核查结论 / DEMO-C03',
                  '已完成',
                ],
                [
                  '11月4日 09:45 / AUD-05',
                  '评论 DEMO-092',
                  '公开 → 不变',
                  '写入失败 / DEMO-C02',
                  '失败 · 可核查',
                ],
              ]
            : audit
        "
        empty="全局审计接口未提供；可登录后按案件读取现有处理历史。"
      />
    </section>
    <p class="console-note console-followup">
      记录操作人、理由、时间、结果与关联对象。用户仅查看本人案件摘要；隐藏内容不通过通知重新公开，删除普通内容不抹除审计。
    </p></template
  >
</template>
