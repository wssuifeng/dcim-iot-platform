<template>
  <el-row :gutter="16" class="alert-stats-panel">
    <el-col :span="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-item">
          <div class="stat-label">告警总数</div>
          <div class="stat-value" style="color: #E6A23C">{{ stats.total ?? '-' }}</div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover" class="stat-card stat-card-danger">
        <div class="stat-item">
          <div class="stat-label">未处理</div>
          <div class="stat-value" style="color: #F56C6C">{{ stats.unhandled ?? '-' }}</div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-item">
          <div class="stat-label">紧急告警</div>
          <div class="stat-value" style="color: #F56C6C">{{ stats.emergency ?? 0 }}</div>
        </div>
      </el-card>
    </el-col>
    <el-col :span="6">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-item">
          <div class="stat-label">已处理</div>
          <div class="stat-value" style="color: #67C23A">{{ stats.handled ?? 0 }}</div>
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
  border-top: 3px solid #E6A23C;
}

.stat-card-danger {
  border-top-color: #F56C6C;
  background-color: #FEF0F0;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}
</style>
