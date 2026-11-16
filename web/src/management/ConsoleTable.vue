<script setup lang="ts">
import { useRoute } from "vue-router";
withDefaults(
  defineProps<{
    columns: string[];
    widths?: string[];
    rows: string[][];
    targets?: string[];
    empty?: string;
    rowHeight?: number;
  }>(),
  { empty: "接口待接入，暂无可核验记录。", rowHeight: 84 },
);
const route = useRoute();
function tone(text: string) {
  return /失败|隐藏|受限|失效|拒绝|未通过|中断/.test(text)
    ? "console-negative"
    : /已完成|查看|通过|启用|有效|待核查/.test(text)
      ? "console-positive"
      : "";
}
</script>
<template>
  <div class="console-table-wrap">
    <table class="console-table">
      <thead>
        <tr>
          <th
            v-for="(column, index) in columns"
            :key="column"
            scope="col"
            :style="{ width: widths?.[index] }"
          >
            {{ column }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, r) in rows"
          :key="r"
          :style="{ height: `${rowHeight}px` }"
        >
          <td v-for="(value, c) in row" :key="c" :class="tone(value)">
            <RouterLink
              v-if="targets?.[r] && c === row.length - 1"
              :to="{
                path: targets[r],
                query: route.query.preview === '1' ? { preview: '1' } : {},
              }"
              >{{ value }}</RouterLink
            ><template v-else>{{ value }}</template>
          </td>
        </tr>
        <tr v-if="!rows.length">
          <td :colspan="columns.length" class="console-table-empty">
            {{ empty }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
