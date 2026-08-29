/*
 * 文件功能:近七日告警趋势图组件，ECharts + vue-echarts 渲染
 *
 * 【ECharts 按需引入策略】
 * 只引入 LineChart（折线图）、Grid（坐标系）、Tooltip（提示框），减少打包体积。
 *
 * 【视觉语言】
 * 线色取五级色带 L1（告警语义红，与 theme.css --alarm-l1 同源），
 * 面积用同色纵向渐变淡出；空数据显示空态而非空白坐标系。
 *
 * 【数据格式】
 * props.data: [{ date: "08-23", count: 12 }, ...]
 */
<template>
  <div class="chart-container" ref="containerRef">
    <v-chart v-if="showChart" :option="chartOption" autoresize />
    <el-skeleton v-else-if="loading" :rows="6" animated />
    <el-empty v-else :image-size="72" description="近七日暂无告警" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import VChart from 'vue-echarts'
import { use, getInstanceByDom, graphic } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useThemeStore, THEME_DARK } from '@/stores/theme'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const containerRef = ref(null)
const themeStore = useThemeStore()
let resizeObserver = null

const isDark = computed(() => themeStore.theme === THEME_DARK)
const showChart = computed(() => !props.loading && props.data.length > 0)

const chartOption = computed(() => {
  const c = isDark.value
    ? { text: '#94a3b8', axis: '#334155', split: 'rgba(51, 65, 85, 0.55)', tipBg: 'rgba(21, 29, 46, 0.96)', tipBorder: '#334155', tipText: '#f1f5f9' }
    : { text: '#64748b', axis: '#e2e8f0', split: 'rgba(148, 163, 184, 0.25)', tipBg: 'rgba(255, 255, 255, 0.96)', tipBorder: '#e2e8f0', tipText: '#1e293b' }
  // 五级色带 L1 红：告警趋势的语义色，不使用主色蓝
  const line = isDark.value ? '#f87171' : '#dc2626'
  const area = new graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: isDark.value ? 'rgba(248, 113, 113, 0.26)' : 'rgba(220, 38, 38, 0.18)' },
    { offset: 1, color: isDark.value ? 'rgba(248, 113, 113, 0.02)' : 'rgba(220, 38, 38, 0.01)' },
  ])
  return {
    animationDuration: 400,
    grid: { top: 24, right: 16, bottom: 8, left: 16, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tipBg,
      borderColor: c.tipBorder,
      textStyle: { color: c.tipText, fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: line, opacity: 0.35 } },
      valueFormatter: (value) => `${value} 条`,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.data.map((d) => d.date),
      axisLine: { lineStyle: { color: c.axis } },
      axisTick: { show: false },
      axisLabel: { color: c.text, fontSize: 10, margin: 12 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: c.text, fontSize: 10 },
      splitLine: { lineStyle: { color: c.split, type: 'dashed' } },
    },
    series: [
      {
        name: '告警数量',
        type: 'line',
        data: props.data.map((d) => d.count),
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 5,
        showSymbol: props.data.length <= 8,
        areaStyle: { color: area },
        itemStyle: { color: line, borderColor: isDark.value ? 'rgba(21, 29, 46, 0.9)' : '#fff', borderWidth: 1 },
        lineStyle: { color: line, width: 2 },
      },
    ],
  }
})

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
