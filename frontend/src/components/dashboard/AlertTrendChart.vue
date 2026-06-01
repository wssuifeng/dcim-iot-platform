/*
 * 文件功能:告警趋势折线图组件，使用 ECharts + vue-echarts 渲染 7 天告警数量趋势
 *
 * 【ECharts 按需引入策略】
 * 本组件使用 ECharts 5.x 的「按需引入」模式（Tree Shaking）：
 *   - 只引入 LineChart（折线图）、Grid（坐标系）、Tooltip（提示框）、Legend（图例）
 *   - 避免全量引入 ECharts，减少打包体积约 60%
 *
 * 【数据格式】
 * props.data: [{ date: "01-25", count: 12 }, ...]
 *   - date: 横轴日期标签（已截取 MM-DD）
 *   - count: 纵轴告警数量
 */
<template>
  <div class="chart-container">
    <!-- 加载中显示骨架屏，避免图表渲染空白闪烁 -->
    <v-chart v-if="!loading" :option="chartOption" autoresize />
    <el-skeleton v-else :rows="6" animated />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import VChart from 'vue-echarts'
// 按需引入 ECharts 模块（Tree Shaking）
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

/*
 * 方法功能:根据 props.data 动态计算 ECharts 配置
 * 返回数据:echartsOption - ECharts 配置对象
 *
 * 【配置要点】
 * - tooltip.trigger = 'axis': 鼠标悬停显示该轴所有数据
 * - smooth = true: 平滑曲线（非折线）
 * - areaStyle: 半透明填充区域（面积图效果）
 * - yAxis.minInterval = 1: 纵轴最小间隔为 1（告警数量为整数）
 */
const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['告警数量'] },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: { type: 'category', data: props.data.map((d) => d.date) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      name: '告警数量',
      type: 'line',
      data: props.data.map((d) => d.count),
      smooth: true,
      areaStyle: { opacity: 0.1 },
      itemStyle: { color: '#F56C6C' },
    },
  ],
}))
</script>

<style scoped>
.chart-container {
  height: 280px;
}
</style>
