/*
 * 文件功能:仪表盘页面，登录后的默认首页，展示设备统计、告警概览、告警趋势图和最近告警列表
 *
 * 【数据加载流程】（参见 UI-02-DashboardPage.md §3 数据流图）
 *   onMounted
 *     → fetchStats()      → GET /api/dashboard/stats        → 设备统计 + 告警概览
 *     → fetchRecentAlerts() → GET /api/alerts?isHandled=0    → 最近未处理告警
 *     → fetchTrendData()  → GET /api/alerts/stats/time-range ×7 → 7天趋势
 *     → setInterval(30s)  → fetchStats() 定时刷新
 *   onUnmounted → clearInterval（防止内存泄漏）
 *
 * 【卡片计算逻辑】
 * - 离线设备数 = totalDevices - onlineDevices（后台未直接返回离线数，前端计算）
 * - 所有卡片通过 computed 从 stats 对象推导，避免手动维护多个 ref
 */
<template>
  <div class="dashboard">
    <!-- 第一行：设备统计卡片（总数/在线/离线） -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="card in deviceCards" :key="card.label">
        <stats-card :label="card.label" :value="card.value" :color="card.color" :icon="card.icon" />
      </el-col>
    </el-row>
    <!-- 第二行：告警统计卡片（总数/未处理） -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="card in alertCards" :key="card.label">
        <stats-card
          :label="card.label"
          :value="card.value"
          :color="card.color"
          :icon="card.icon"
          :bg-color="card.bgColor"
        />
      </el-col>
    </el-row>
    <!-- 第三行：告警趋势图 + 最近告警列表 -->
    <el-row :gutter="20">
      <el-col :span="14">
        <el-card>
          <template #header>最近 7 天告警趋势</template>
          <alert-trend-chart :data="trendData" :loading="trendLoading" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>最近告警</template>
          <recent-alert-list :alerts="recentAlerts" :loading="loading" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getDashboardStats } from '@/api/dashboard'
import { getAlerts, getAlertStatsByTimeRange } from '@/api/alert'
import StatsCard from '@/components/dashboard/StatsCard.vue'
import AlertTrendChart from '@/components/dashboard/AlertTrendChart.vue'
import RecentAlertList from '@/components/dashboard/RecentAlertList.vue'

const loading = ref(false)
const trendLoading = ref(false)
const stats = ref({})
const recentAlerts = ref([])
const trendData = ref([])

// 30 秒定时器句柄，组件卸载时清除
let timer = null

/*
 * 计算属性功能:根据 stats 对象动态生成设备统计卡片数据
 * 返回数据:cardConfig[] - 设备卡片配置数组
 *
 * 【注意】离线设备数通过 totalDevices - onlineDevices 计算
 * 因为后端 DashboardStatsDTO 直接返回 statusDistribution Map，
 * 前端计算比遍历 Map 更简洁
 */
const deviceCards = computed(() => {
  const ds = stats.value.deviceStats || {}
  return [
    { label: '设备总数', value: ds.total ?? '-', color: '#409EFF', icon: 'Monitor' },
    { label: '在线设备', value: ds.online ?? '-', color: '#67C23A', icon: 'CircleCheck' },
    { label: '离线设备', value: ds.offline ?? '-', color: '#909399', icon: 'Remove' },
    { label: '故障设备', value: ds.fault ?? '-', color: '#F56C6C', icon: 'WarningFilled' },
  ]
})

/*
 * 计算属性功能:根据 stats 对象动态生成告警统计卡片数据
 * 返回数据:cardConfig[] - 告警卡片配置数组
 *
 * 【注意】未处理告警卡片使用红色背景（bgColor: '#FEF0F0'），视觉上突出紧急程度
 */
const alertCards = computed(() => {
  const as = stats.value.alertStats || {}
  return [
    { label: '总告警', value: as.total ?? '-', color: '#E6A23C', icon: 'Warning' },
    { label: '未处理告警', value: as.unhandled ?? '-', color: '#F56C6C', icon: 'Bell', bgColor: '#FEF0F0' },
  ]
})

/*
 * 方法功能:获取仪表盘核心统计数据（设备 + 告警概览）
 * 参数:无
 * 返回数据:无（直接更新 stats.value）
 *
 * 【loading 管理】
 * fetchStats 是 P0 核心请求，控制全局 loading。
 * fetchRecentAlerts 和 fetchTrendData 是 P1 非核心请求，
 * 各自独立管理 loading，不阻塞核心统计卡片展示
 */
async function fetchStats() {
  loading.value = true
  try {
    const res = await getDashboardStats()
    stats.value = res.data
  } finally {
    loading.value = false
  }
}

/*
 * 方法功能:获取最近 10 条未处理告警
 * 参数:无
 * 返回数据:无（直接更新 recentAlerts.value）
 *
 * 【异常处理】非核心功能，静默失败
 */
async function fetchRecentAlerts() {
  try {
    const res = await getAlerts({ isHandled: 0, size: 10, current: 1 })
    recentAlerts.value = res.data.records || []
  } catch {
    // 非核心功能，静默失败
  }
}

/*
 * 方法功能:按天获取最近 7 天的告警统计（用于趋势图）
 * 参数:无
 * 返回数据:无（直接更新 trendData.value）
 *
 * 【实现策略】后端 /alerts/stats/time-range 每次只支持一个时间段查询，
 * 因此 7 天需要循环调用 7 次，使用顺序 await 而非 Promise.all：
 *   - 优先保证每一天的顺序（横轴日期必须是时间递增的）
 *   - 7 次异步请求逐天执行，总耗时 ≈ 7 × 单次请求延迟
 *
 * 【日期计算】从今天往前推 6 天（共 7 天），每天的时间窗口为 00:00:00 ~ 次日 00:00:00
 */
async function fetchTrendData() {
  trendLoading.value = true
  try {
    const days = []
    const now = new Date()
    for (let i = 6; i >= 0; i--) {
      // 计算当前天的起止时间
      const d = new Date(now)
      d.setDate(d.getDate() - i)
      const dateStr = d.toISOString().slice(0, 10)               // YYYY-MM-DD
      const nextDay = new Date(d)
      nextDay.setDate(nextDay.getDate() + 1)
      const nextDayStr = nextDay.toISOString().slice(0, 10)

      const res = await getAlertStatsByTimeRange({
        startTime: `${dateStr} 00:00:00`,
        endTime: `${nextDayStr} 00:00:00`,
      })
      days.push({
        date: dateStr.slice(5),   // 横轴只显示 MM-DD
        count: res.data?.total ?? 0,
      })
    }
    trendData.value = days
  } catch {
    trendData.value = []
  } finally {
    trendLoading.value = false
  }
}

/*
 * 【生命周期管理】
 * onMounted: 首次进入页面时并行加载所有数据
 * onUnmounted: 清除定时器，防止切换页面后仍在请求（内存泄漏）
 */
onMounted(() => {
  fetchStats()
  fetchRecentAlerts()
  fetchTrendData()
  timer = setInterval(fetchStats, 30000)  // 每 30 秒刷新核心统计
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.stats-row {
  margin-bottom: 20px;
}
</style>
