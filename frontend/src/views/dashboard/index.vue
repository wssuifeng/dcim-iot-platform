<template>
  <section class="dashboard-page">
    <header class="page-header">
      <div>
        <h1>告警看板</h1>
        <p>{{ dashboard.scopeDescription || '当前授权范围' }}</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="fetchDashboard">刷新</el-button>
    </header>

    <div class="stats-grid">
      <stats-card label="告警总量" :value="totalAlarms" color="var(--color-text-2)" icon="DataAnalysis" />
      <stats-card label="活动告警" :value="activeAlarms" color="var(--color-danger)" icon="Warning" />
      <stats-card label="已恢复" :value="stateCount('RECOVERED')" color="var(--color-success)" icon="CircleCheck" />
      <stats-card label="已关闭" :value="stateCount('CLOSED')" color="var(--color-info)" icon="CircleClose" />
    </div>

    <div class="dashboard-grid dashboard-grid-primary">
      <section class="dashboard-section trend-section">
        <div class="section-heading">
          <h2>近七日趋势</h2>
        </div>
        <alert-trend-chart :data="trendData" :loading="loading" />
      </section>

      <section class="dashboard-section">
        <div class="section-heading"><h2>状态分布</h2></div>
        <div class="distribution-list">
          <div v-for="item in stateDistribution" :key="item.key" class="distribution-row">
            <span>{{ item.label }}</span>
            <el-progress :percentage="item.percentage" :stroke-width="8" :show-text="false" :color="item.color" />
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <section class="dashboard-section">
        <div class="section-heading"><h2>级别分布</h2></div>
        <div class="level-grid">
          <div v-for="item in levelDistribution" :key="item.key" class="level-row">
            <el-tag :type="item.type" effect="plain">{{ item.label }}</el-tag>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>
    </div>

    <div class="dashboard-grid dashboard-grid-secondary">
      <section class="dashboard-section">
        <div class="section-heading"><h2>设备告警排名</h2></div>
        <el-table :data="ranking" size="small" :show-header="false" empty-text="暂无排名数据">
          <el-table-column type="index" width="45" />
          <el-table-column prop="deviceName" min-width="160" />
          <el-table-column width="150">
            <template #default="{ row }">
              <el-progress :percentage="row.percentage" :show-text="false" :stroke-width="7" />
            </template>
          </el-table-column>
          <el-table-column prop="alarmCount" width="70" align="right" />
        </el-table>
      </section>

      <section class="dashboard-section recent-section">
        <div class="section-heading">
          <h2>最近告警</h2>
          <el-button link type="primary" @click="router.push('/alerts')">查看全部</el-button>
        </div>
        <el-table v-loading="recentLoading" :data="recentAlarms" size="small" row-key="id">
          <el-table-column label="状态" width="95">
            <template #default="{ row }"><el-tag :type="stateType(row.state)" size="small">{{ stateLabel(row.state) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="设备" min-width="140">
            <template #default="{ row }">{{ deviceName(row.deviceId) }}</template>
          </el-table-column>
          <el-table-column prop="alarmMessage" label="告警" min-width="220" show-overflow-tooltip />
          <el-table-column prop="triggeredTime" label="触发时间" min-width="165" />
        </el-table>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getAlarmDashboardStats } from '@/api/dashboard'
import { getAlarmEventPage } from '@/api/alarmEvent'
import { getDevicePage } from '@/api/device'
import { onSummary } from '@/api/realtime'
import AlertTrendChart from '@/components/dashboard/AlertTrendChart.vue'
import StatsCard from '@/components/dashboard/StatsCard.vue'

defineOptions({ name: 'AlarmDashboard' })

const router = useRouter()
const loading = ref(false)
const recentLoading = ref(false)
const dashboard = ref({
  scopeDescription: '',
  stateDistribution: {},
  levelDistribution: {},
  dailyTrend: {},
  deviceRanking: [],
})
const devices = ref([])
const recentAlarms = ref([])
let removeSummaryListener = null
const totalAlarms = computed(() =>
  Object.values(dashboard.value.stateDistribution || {}).reduce((sum, value) => sum + Number(value || 0), 0),
)
const activeAlarms = computed(
  () => stateCount('TRIGGERED') + stateCount('ACKED') + stateCount('HANDLING'),
)
const trendData = computed(() =>
  Object.entries(dashboard.value.dailyTrend || {}).map(([date, count]) => ({ date: date.slice(5), count })),
)
const stateMeta = Object.freeze([
  { key: 'TRIGGERED', label: '已触发', color: 'var(--color-danger)' },
  { key: 'ACKED', label: '已确认', color: 'var(--color-warning)' },
  { key: 'HANDLING', label: '处理中', color: 'var(--color-primary)' },
  { key: 'RECOVERED', label: '已恢复', color: 'var(--color-success)' },
  { key: 'CLOSED', label: '已关闭', color: 'var(--color-text-3)' },
])
const levelMeta = Object.freeze([
  { key: '1', label: '紧急', type: 'danger' },
  { key: '2', label: '严重', type: 'warning' },
  { key: '3', label: '一般', type: 'primary' },
  { key: '4', label: '警告', type: 'info' },
  { key: '5', label: '提示', type: 'info' },
])
const stateDistribution = computed(() =>
  stateMeta.map((item) => {
    const value = stateCount(item.key)
    return {
      ...item,
      value,
      percentage: totalAlarms.value ? Math.round((value / totalAlarms.value) * 100) : 0,
    }
  }),
)
const levelDistribution = computed(() =>
  levelMeta.map((item) => ({
    ...item,
    value: Number(dashboard.value.levelDistribution?.[item.key] || 0),
  })),
)
const ranking = computed(() => {
  const source = dashboard.value.deviceRanking || []
  const max = Math.max(1, ...source.map((item) => Number(item.alarmCount) || 0))
  return source.map((item) => ({
    ...item,
    deviceName: deviceName(item.deviceId),
    percentage: Math.round(((Number(item.alarmCount) || 0) / max) * 100),
  }))
})

function stateCount(state) {
  return Number(dashboard.value.stateDistribution?.[state] || 0)
}

async function fetchDashboard() {
  loading.value = true
  recentLoading.value = true
  try {
    const [statsResult, recentResult, deviceResult] = await Promise.all([
      getAlarmDashboardStats(),
      getAlarmEventPage({ current: 1, size: 5 }),
      getDevicePage({ current: 1, size: 100 }),
    ])
    dashboard.value = statsResult.data || dashboard.value
    recentAlarms.value = recentResult.data?.records || []
    devices.value = deviceResult.data?.records || []
  } finally {
    loading.value = false
    recentLoading.value = false
  }
}

function deviceName(id) {
  return devices.value.find((item) => item.id === id)?.deviceName || `设备 #${id}`
}

function stateLabel(state) {
  return stateMeta.find((item) => item.key === state)?.label || state
}

function stateType(state) {
  return { TRIGGERED: 'danger', ACKED: 'warning', HANDLING: 'primary', RECOVERED: 'success', CLOSED: 'info' }[state]
}

onMounted(() => {
  void fetchDashboard()
  removeSummaryListener = onSummary((summary) => {
    if (summary.resourceType === 'ALARM_EVENT') void fetchDashboard()
  })
})

onBeforeUnmount(() => removeSummaryListener?.())
</script>

<style scoped>
.dashboard-page {
  min-width: 0;
}

.page-header,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.page-header {
  align-items: flex-start;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: var(--color-text-2);
  font-size: 13px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 24px;
}

.dashboard-grid {
  display: grid;
  border-top: 1px solid var(--color-border);
}

.dashboard-grid-primary {
  grid-template-columns: minmax(420px, 1.6fr) minmax(240px, 0.8fr) minmax(200px, 0.65fr);
}

.dashboard-grid-secondary {
  grid-template-columns: minmax(300px, 0.8fr) minmax(540px, 1.5fr);
  border-bottom: 1px solid var(--color-border);
}

.dashboard-section {
  min-width: 0;
  padding: 20px;
  border-right: 1px solid var(--color-border);
  background-color: var(--color-surface);
}

.dashboard-section:last-child {
  border-right: 0;
}

.dashboard-grid-secondary .dashboard-section {
  border-top: 1px solid var(--color-border);
}

.section-heading {
  margin-bottom: 14px;
}

.section-heading h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 16px;
}

.distribution-list,
.level-grid {
  display: grid;
  gap: 15px;
}

.distribution-row {
  display: grid;
  grid-template-columns: 64px minmax(80px, 1fr) 36px;
  align-items: center;
  gap: 10px;
  color: var(--color-text-2);
  font-size: 13px;
}

.distribution-row strong,
.level-row strong {
  color: var(--color-text);
  text-align: right;
  font-family: var(--data-font);
}

.level-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 9px;
  border-bottom: 1px solid var(--color-border);
}

@media (max-width: 1100px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid-primary,
  .dashboard-grid-secondary {
    grid-template-columns: 1fr;
  }

  .dashboard-section {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }
}

@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-section {
    padding: 16px 0;
  }
}
</style>
