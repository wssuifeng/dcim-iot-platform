<template>
  <el-card class="stats-panel" shadow="never">
    <template #header>统计概览</template>
    <el-row :gutter="16">
      <el-col :span="6">
        <div class="stat-item"><div class="stat-value">{{ stats.minVal ?? '-' }}</div><div class="stat-label">最小值</div></div>
      </el-col>
      <el-col :span="6">
        <div class="stat-item"><div class="stat-value">{{ stats.maxVal ?? '-' }}</div><div class="stat-label">最大值</div></div>
      </el-col>
      <el-col :span="6">
        <div class="stat-item"><div class="stat-value">{{ formatAvg(stats.avgVal) }}</div><div class="stat-label">平均值</div></div>
      </el-col>
      <el-col :span="6">
        <div class="stat-item"><div class="stat-value">{{ stats.count ?? 0 }}</div><div class="stat-label">数据条数</div></div>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
defineProps({
  stats: { type: Object, default: () => ({}) },
})

function formatAvg(val) {
  return val != null ? Number(val).toFixed(2) : '-'
}
</script>

<style scoped>
.stats-panel { margin-bottom: 16px; }
.stat-item { text-align: center; }
.stat-value { font-size: 24px; font-weight: bold; color: #303133; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
