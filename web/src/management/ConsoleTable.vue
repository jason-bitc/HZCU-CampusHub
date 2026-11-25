<script setup lang="ts">
import CopyValue from "./CopyValue.vue";
import { useRoute } from "vue-router";
withDefaults(
  defineProps<{
    columns: string[];
    widths?: string[];
    rows: string[][];
    targets?: string[];
    empty?: string;
    copyColumns?: number[];
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
    <table class="console-table console-responsive-table" role="table">
      <thead role="rowgroup">
        <tr role="row">
          <th
            v-for="(column, index) in columns"
            :key="column"
            scope="col"
            role="columnheader"
            :style="{ width: widths?.[index] }"
          >
            {{ column }}
          </th>
        </tr>
      </thead>
      <tbody role="rowgroup">
        <tr
          v-for="(row, r) in rows"
          role="row"
          :key="r"
          :style="{ height: `${rowHeight}px` }"
        >
          <td
            v-for="(value, c) in row"
            :key="c"
            :class="tone(value)"
            :data-label="columns[c]"
            role="cell"
          >
            <RouterLink
              v-if="targets?.[r] && c === row.length - 1"
              :to="{
                path: targets[r],
                query: route.query.preview === '1' ? { preview: '1' } : {},
              }"
              >{{ value }}</RouterLink
            ><CopyValue
              v-else-if="copyColumns?.includes(c) && value !== '—'"
              :value="value"
              :label="columns[c] || '编号'"
            />
            <template v-else>{{ value }}</template>
          </td>
        </tr>
        <tr v-if="!rows.length" role="row">
          <td :colspan="columns.length" class="console-table-empty" role="cell">
            {{ empty }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
