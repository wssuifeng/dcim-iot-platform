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
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  data: { type: Array, default: () => [] },
})
defineEmits(['range-change'])

const selectedRange = ref('24h')

const option = computed(() => {
  if (!props.data || props.data.length === 0) return null
  const times = props.data.map(d => d.createTime || '').reverse()
  const values = props.data.map(d => d.value).reverse()
  return {
    grid: { top: 20, right: 20, bottom: 30, left: 50 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: times, axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: { type: 'value' },
    series: [{ data: values, type: 'line', smooth: true, areaStyle: { opacity: 0.1 } }],
  }
})
</script>

<style scoped>
.trend-chart { margin-bottom: 16px; }
</style>
