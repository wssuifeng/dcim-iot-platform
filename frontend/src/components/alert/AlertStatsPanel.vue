<template>
  <el-row :gutter="16" class="alert-stats-panel">
    <el-col :span="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-item">
          <div class="stat-label">告警总数</div>
          <div class="stat-value" style="color: var(--color-warning)">{{ stats.total ?? '-' }}</div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover" class="stat-card stat-card-danger">
        <div class="stat-item">
          <div class="stat-label">未处理</div>
          <div class="stat-value" style="color: var(--color-danger)">{{ stats.unhandled ?? '-' }}</div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-item">
          <div class="stat-label">紧急告警</div>
          <div class="stat-value" style="color: var(--color-danger)">{{ stats.emergency ?? 0 }}</div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-item">
          <div class="stat-label">已处理</div>
          <div class="stat-value" style="color: var(--color-success)">{{ stats.handled ?? 0 }}</div>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
defineProps({
  stats: { type: Object, default: () => ({}) },
})
</script>

<style scoped>
.alert-stats-panel {
  margin-bottom: 20px;
}

.stat-card {
  border-top: 3px solid var(--color-warning);
}

.stat-card-danger {
  border-top-color: var(--color-danger);
  background-color: var(--color-danger-light);
}

.stat-label {
  font-size: 14px;
  color: var(--color-text-3);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}
</style>
