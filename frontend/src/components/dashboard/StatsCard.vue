/*
 * 文件功能:统计卡片组件，用于仪表盘页面展示设备/告警统计指标
 *
 * 【Props 说明】
 * - label: 指标名称（如"设备总数"、"在线设备"）
 * - value: 指标数值（Number 或 String，为 null 时显示 "-"）
 * - color: 顶部边框颜色 + 数值文字颜色 + 图标颜色
 * - icon: Element Plus 图标组件名称（如 "Monitor", "CircleCheck"）
 * - bgColor: 图标区域背景色（可选，默认为 color + 透明度）
 *
 * 【复用范围】
 * 仪表盘设备卡片（3个）+ 告警卡片（2个），后续其他页面的统计卡片也可复用
 */
<template>
  <el-card :style="{ borderTopColor: color, borderTopWidth: '3px' }" shadow="hover">
    <div class="stats-card-content">
      <!-- 左侧：指标名称 + 数值 -->
      <div class="stats-card-left">
        <div class="stats-card-label">{{ label }}</div>
        <div class="stats-card-value" :style="{ color }">{{ value }}</div>
      </div>
      <!-- 右侧：彩色图标 -->
      <div class="stats-card-icon" :style="{ backgroundColor: bgColor || color + '1a' }">
        <el-icon :size="32" :color="color">
          <component :is="icon" />
        </el-icon>
      </div>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], required: true },
  color: { type: String, default: '#409EFF' },
  icon: { type: String, required: true },
  bgColor: { type: String, default: '' },
})
</script>

<style scoped>
.stats-card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-card-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stats-card-value {
  font-size: 28px;
  font-weight: bold;
}

.stats-card-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}
</style>
