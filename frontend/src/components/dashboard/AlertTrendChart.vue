/*
 * 文件功能:告警趋势折线图组件，使用 ECharts + vue-echarts 渲染 7 天告警数量趋势
 *
 * 【ECharts 按需引入策略】
 * 本组件使用 ECharts 6.x 的「按需引入」模式（Tree Shaking）：
 *   - 只引入 LineChart（折线图）、Grid（坐标系）、Tooltip（提示框）、Legend（图例）
 *   - 避免全量引入 ECharts，减少打包体积约 60%
 *
 * 【数据格式】
 * props.data: [{ date: "01-25", count: 12 }, ...]
 *   - date: 横轴日期标签（已截取 MM-DD）
 *   - count: 纵轴告警数量
 */
<template>
  <div class="chart-container" ref="containerRef">
    <!-- 加载中显示骨架屏，避免图表渲染空白闪烁 -->
    <v-chart v-if="!loading" :option="chartOption" />
    <el-skeleton v-else :rows="6" animated />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import VChart from 'vue-echarts'
import { use, getInstanceByDom } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useThemeStore, THEME_DARK } from '@/stores/theme'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const containerRef = ref(null)
const themeStore = useThemeStore()
let resizeObserver = null

const isDark = computed(() => themeStore.theme === THEME_DARK)
const lineColor = computed(() => (isDark.value ? '#ff5d82' : '#ef4444'))
const areaColor = computed(() => (isDark.value ? 'rgba(255, 46, 99, 0.2)' : 'rgba(239, 68, 68, 0.1)'))
const textColor = computed(() => (isDark.value ? '#94a3b8' : '#64748b'))
const axisColor = computed(() => (isDark.value ? '#334155' : '#e2e8f0'))

const chartOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    backgroundColor: isDark.value ? 'rgba(21, 29, 46, 0.96)' : 'rgba(255, 255, 255, 0.96)',
    borderColor: isDark.value ? '#334155' : '#e2e8f0',
    textStyle: { color: isDark.value ? '#f1f5f9' : '#1e293b' },
  },
  legend: {
    data: ['告警数量'],
    textStyle: { color: textColor.value },
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: props.data.map((d) => d.date),
    axisLine: { lineStyle: { color: axisColor.value } },
    axisLabel: { color: textColor.value },
    splitLine: { show: false },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLine: { show: false },
    axisLabel: { color: textColor.value },
    splitLine: { lineStyle: { color: isDark.value ? 'rgba(51, 65, 85, 0.6)' : '#f1f5f9' } },
  },
  series: [
    {
      name: '告警数量',
      type: 'line',
      data: props.data.map((d) => d.count),
      smooth: true,
      areaStyle: { color: areaColor.value },
      itemStyle: { color: lineColor.value },
      lineStyle: { color: lineColor.value, width: 3 },
    },
  ],
}))

function resizeChart() {
  const dom = containerRef.value?.querySelector('.v-chart')
  if (dom) {
    const instance = getInstanceByDom(dom)
    instance?.resize()
  }
}

onMounted(() => {
  resizeObserver = new ResizeObserver(resizeChart)
  if (containerRef.value) {
    resizeObserver.observe(containerRef.value)
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})

watch(() => themeStore.theme, resizeChart)
</script>

<style scoped>
.chart-container {
  height: 280px;
}
</style>
