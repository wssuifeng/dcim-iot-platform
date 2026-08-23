<template>
  <el-card class="trend-chart" shadow="never">
    <template #header>
      <span>趋势图</span>
      <el-radio-group v-model="selectedRange" size="small" style="margin-left: 16px" @change="val => $emit('range-change', val)">
        <el-radio-button value="1h">1小时</el-radio-button>
        <el-radio-button value="6h">6小时</el-radio-button>
        <el-radio-button value="24h">24小时</el-radio-button>
        <el-radio-button value="7d">7天</el-radio-button>
        <el-radio-button value="30d">30天</el-radio-button>
      </el-radio-group>
    </template>
    <v-chart v-if="option" :option="option" style="height: 340px" autoresize />
    <el-empty v-else description="请选择设备和时间范围" />
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useThemeStore, THEME_DARK } from '@/stores/theme'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  data: { type: Array, default: () => [] },
})
defineEmits(['range-change'])

const themeStore = useThemeStore()
const selectedRange = ref('24h')

const isDark = computed(() => themeStore.theme === THEME_DARK)
const textColor = computed(() => (isDark.value ? '#94a3b8' : '#64748b'))
const axisColor = computed(() => (isDark.value ? '#334155' : '#e2e8f0'))
const lineColor = computed(() => (isDark.value ? '#00d4ff' : '#2563eb'))
const areaColor = computed(() => (isDark.value ? 'rgba(0, 212, 255, 0.2)' : 'rgba(37, 99, 235, 0.1)'))

const option = computed(() => {
  if (!props.data || props.data.length === 0) return null
  const times = props.data.map((d) => d.createTime || '').reverse()
  const values = props.data.map((d) => d.value).reverse()
  return {
    grid: { top: 20, right: 20, bottom: 30, left: 50 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark.value ? 'rgba(21, 29, 46, 0.96)' : 'rgba(255, 255, 255, 0.96)',
      borderColor: isDark.value ? '#334155' : '#e2e8f0',
      textStyle: { color: isDark.value ? '#f1f5f9' : '#1e293b' },
    },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { rotate: 30, fontSize: 10, color: textColor.value },
      axisLine: { lineStyle: { color: axisColor.value } },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: textColor.value },
      splitLine: { lineStyle: { color: isDark.value ? 'rgba(51, 65, 85, 0.6)' : '#f1f5f9' } },
    },
    series: [
      {
        data: values,
        type: 'line',
        smooth: true,
        areaStyle: { color: areaColor.value },
        itemStyle: { color: lineColor.value },
        lineStyle: { color: lineColor.value, width: 2 },
      },
    ],
  }
})

watch(() => themeStore.theme, () => {
  // option 是 computed，依赖 isDark 自动重新计算，vue-echarts 会响应
})
</script>

<style scoped>
.trend-chart { margin-bottom: 16px; }
</style>
