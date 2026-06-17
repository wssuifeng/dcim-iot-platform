<template>
  <div class="data-table">
    <el-table :data="list" v-loading="loading" size="small">
      <el-table-column prop="metricKey" label="指标" width="120" />
      <el-table-column prop="value" label="值" width="100" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="createTime" label="采集时间" min-width="160" />
    </el-table>
    <el-pagination
      v-if="total > 0"
      v-model:current-page="currentPage"
      :total="total"
      :page-size="20"
      layout="total, prev, pager, next"
      @change="$emit('page-change', currentPage)"
      small
      style="margin-top: 12px; justify-content: flex-end"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

defineProps({
  list: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
})
defineEmits(['page-change'])

const currentPage = ref(1)
</script>
