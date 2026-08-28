/*
 * 类功能:ECharts light/dark 双主题注册——色板取自 theme.css 令牌，
 *   供趋势图/仪表盘统一注册使用（暗色降饱和、网格线弱化）
 */
import { use } from 'echarts/core'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { LineChart, BarChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'

use([GridComponent, TooltipComponent, LegendComponent, LineChart, BarChart, CanvasRenderer])

// 与 theme.css 的 --chart-* 令牌一致（ECharts 不能直接读 CSS 变量，两处同步维护）
export const CHART_COLORS = {
  light: {
    text: '#64748b',
    axisLine: '#e2e8f0',
    splitLine: '#f1f5f9',
    tooltipBg: 'rgba(255, 255, 255, 0.96)',
    tooltipBorder: '#e2e8f0',
    tooltipText: '#1e293b',
    primary: '#2563eb',
    primaryArea: 'rgba(37, 99, 235, 0.1)',
    palette: ['#2563eb', '#10b981', '#f59e0b', '#8b5cf6', '#06b6d4', '#f43f5e'],
  },
  dark: {
    text: '#94a3b8',
    axisLine: '#334155',
    splitLine: 'rgba(51, 65, 85, 0.6)',
    tooltipBg: 'rgba(21, 29, 46, 0.96)',
    tooltipBorder: '#334155',
    tooltipText: '#f1f5f9',
    primary: '#00d4ff',
    primaryArea: 'rgba(0, 212, 255, 0.2)',
    palette: ['#00d4ff', '#00d9a5', '#ff9f43', '#a78bfa', '#22d3ee', '#ff2e63'],
  },
}

// 告警五级色带（与 theme.css --alarm-l* 一致），图表用 series 色
export const ALARM_LEVEL_COLORS = {
  light: ['#dc2626', '#ea580c', '#d97706', '#2563eb', '#64748b'],
  dark: ['#f87171', '#fb923c', '#fbbf24', '#60a5fa', '#94a3b8'],
}

/*
 * 方法功能:按主题构建基础图表 option 片段（网格/坐标轴/提示框统一样式）
 * 参数:isDark-是否暗色主题
 * 返回数据:Object-可合并的 ECharts option 片段
 * */
export function chartBase(isDark) {
  const c = isDark ? CHART_COLORS.dark : CHART_COLORS.light
  return {
    color: c.palette,
    grid: { top: 24, right: 20, bottom: 32, left: 52 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.tooltipBg,
      borderColor: c.tooltipBorder,
      textStyle: { color: c.tooltipText, fontSize: 12 },
    },
  }
}
