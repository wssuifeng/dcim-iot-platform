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
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'
import '@/styles/echarts-theme'
import { chartBase } from '@/styles/echarts-theme'
import { useThemeStore, THEME_DARK } from '@/stores/theme'

const props = defineProps({
  data: { type: Array, default: () => [] },
})
defineEmits(['range-change'])

const themeStore = useThemeStore()
const selectedRange = ref('24h')

const isDark = computed(() => themeStore.theme === THEME_DARK)

const option = computed(() => {
  if (!props.data || props.data.length === 0) return null
  const base = chartBase(isDark.value)
  const times = props.data.map((d) => d.createTime || '').reverse()
  const values = props.data.map((d) => d.value).reverse()
  return {
    ...base,
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { rotate: 30, fontSize: 10, color: isDark.value ? '#94a3b8' : '#64748b' },
      axisLine: { lineStyle: { color: isDark.value ? '#334155' : '#e2e8f0' } },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b' },
      splitLine: { lineStyle: { color: isDark.value ? 'rgba(51, 65, 85, 0.6)' : '#f1f5f9' } },
    },
    series: [
      {
        data: values,
        type: 'line',
        smooth: true,
        areaStyle: { color: isDark.value ? 'rgba(0, 212, 255, 0.2)' : 'rgba(37, 99, 235, 0.1)' },
        itemStyle: { color: isDark.value ? '#00d4ff' : '#2563eb' },
        lineStyle: { color: isDark.value ? '#00d4ff' : '#2563eb', width: 2 },
      },
    ],
  }
})
</script>

<style scoped>
.trend-chart { margin-bottom: 16px; }
</style>
