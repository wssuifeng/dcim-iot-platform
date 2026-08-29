<template>
  <div class="trend-block">
    <v-chart v-if="option" :option="option" class="trend-chart-canvas" autoresize />
    <el-empty v-else :image-size="72" description="当前范围暂无采样数据" />
  </div>
</template>

<script setup>
/*
 * 文件功能:设备指标趋势图（时间轴 + min/max 波动带）
 *
 * 【24 小时范围 bug 修复】旧版用 category 轴贴合数据实际范围，且数据来自
 *   分页查询（仅最新 50 条），选 24 小时实际只画出最近几十分钟。
 *   现改为 time 轴并显式锁定 min/max 为所选范围，数据源换 /metric-samples/trend
 *   分桶聚合端点——无论原始采样密度如何，轴始终横跨完整所选窗口。
 */
import { computed } from 'vue'
import VChart from 'vue-echarts'
import '@/styles/echarts-theme'
import { useThemeStore, THEME_DARK } from '@/stores/theme'

const props = defineProps({
  // 分桶聚合点：[{ time, avgValue, minValue, maxValue }]（时间升序）
  data: { type: Array, default: () => [] },
  // 当前所选范围（与父级 RANGE_MS 一致）：1h/6h/24h/7d/30d
  range: { type: String, default: '24h' },
  // 指标单位（tooltip 展示）
  unit: { type: String, default: '' },
})

const RANGE_MS = { '1h': 3600000, '6h': 21600000, '24h': 86400000, '7d': 604800000, '30d': 2592000000 }

const themeStore = useThemeStore()
const isDark = computed(() => themeStore.theme === THEME_DARK)

function colors() {
  return isDark.value
    ? { text: '#94a3b8', axis: '#334155', split: 'rgba(51, 65, 85, 0.55)', primary: '#00d4ff', band: 'rgba(0, 212, 255, 0.10)', tipBg: 'rgba(21, 29, 46, 0.96)', tipBorder: '#334155', tipText: '#f1f5f9' }
    : { text: '#64748b', axis: '#e2e8f0', split: 'rgba(148, 163, 184, 0.25)', primary: '#2563eb', band: 'rgba(37, 99, 235, 0.10)', tipBg: 'rgba(255, 255, 255, 0.96)', tipBorder: '#e2e8f0', tipText: '#1e293b' }
}

// 轴始终锁定完整所选窗口：数据稀疏时留白也是诚实呈现
const windowRange = computed(() => {
  const end = Date.now()
  return [end - (RANGE_MS[props.range] || RANGE_MS['24h']), end]
})

function formatTick(value) {
  const d = new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  const hhmm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  if (RANGE_MS[props.range] <= RANGE_MS['24h']) return hhmm
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hhmm}`
}

function formatTipTime(value) {
  const d = new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatValue(v) {
  if (v == null || Number.isNaN(Number(v))) return '-'
  return `${Number(v)}${props.unit ? ` ${props.unit}` : ''}`
}

const option = computed(() => {
  const points = props.data || []
  if (points.length === 0) return null
  const c = colors()
  const times = points.map((p) => new Date(p.time).getTime())
  const avg = points.map((p) => Number(p.avgValue))
  const min = points.map((p) => Number(p.minValue))
  // 波动带用 stack 技巧：下界序列 + （max-min）幅度序列，幅度序列带半透明填充
  const bandSpan = points.map((p) => Number(p.maxValue) - Number(p.minValue))

  return {
    animationDuration: 400,
    grid: { top: 18, right: 16, bottom: 40, left: 46, containLabel: false },
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tipBg,
      borderColor: c.tipBorder,
      textStyle: { color: c.tipText, fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: c.primary, opacity: 0.35 } },
      formatter: (params) => {
        if (!params || !params.length) return ''
        const row = (name, value, strong) =>
          `<div style="display:flex;justify-content:space-between;gap:16px;margin-top:4px;">`
          + `<span>${strong ? '<b>' : ''}${name}${strong ? '</b>' : ''}</span>`
          + `<span style="font-family:ui-monospace,Menlo,Consolas,monospace;">${formatValue(value)}</span></div>`
        const byName = Object.fromEntries(params.map((p) => [p.seriesName, p]))
        const max = byName['最低'] && byName['波动'] ? Number(byName['最低'].value[1]) + Number(byName['波动'].value[1]) : null
        return `<div style="color:${c.tipText}">${formatTipTime(params[0].axisValue)}${props.unit ? `（${props.unit}）` : ''}</div>`
          + row('均值', byName['均值']?.value[1], true)
          + row('最高', max)
          + row('最低', byName['最低']?.value[1])
      },
    },
    xAxis: {
      type: 'time',
      min: windowRange.value[0],
      max: windowRange.value[1],
      axisLine: { lineStyle: { color: c.axis } },
      axisTick: { show: false },
      axisLabel: { color: c.text, fontSize: 10, hideOverlap: true, formatter: formatTick },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      axisLabel: { color: c.text, fontSize: 10 },
      splitLine: { lineStyle: { color: c.split, type: 'dashed' } },
    },
    series: [
      {
        name: '最低',
        type: 'line',
        data: times.map((t, i) => [t, min[i]]),
        stack: 'band',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        silent: true,
        areaStyle: { opacity: 0 },
        emphasis: { disabled: true },
      },
      {
        name: '波动',
        type: 'line',
        data: times.map((t, i) => [t, bandSpan[i]]),
        stack: 'band',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        silent: true,
        areaStyle: { color: c.band },
        emphasis: { disabled: true },
      },
      {
        name: '均值',
        type: 'line',
        data: times.map((t, i) => [t, avg[i]]),
        symbol: 'circle',
        symbolSize: 4,
        showSymbol: false,
        smooth: 0.3,
        lineStyle: { color: c.primary, width: 2 },
        itemStyle: { color: c.primary },
        emphasis: { focus: 'none' },
      },
    ],
  }
})
</script>

<style scoped>
.trend-block {
  margin-bottom: 12px;
}

.trend-chart-canvas {
  height: 300px;
  width: 100%;
}
</style>
