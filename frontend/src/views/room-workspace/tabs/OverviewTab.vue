<template>
  <div v-loading="loading" class="overview-tab">
    <!-- KPI 四卡 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <span class="kpi-label">设备总数</span>
        <stat-value :value="devices.length" size="lg" />
        <span class="kpi-sub num">在线 {{ onlineCount }} / 离线 {{ devices.length - onlineCount }}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">活动告警</span>
        <stat-value :value="activeAlarms" size="lg" :danger="activeAlarms > 0" />
        <span class="kpi-sub num">今日新增 {{ todayAlarms }}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">今日上报设备</span>
        <stat-value :value="reportedToday" size="lg" />
        <span class="kpi-sub num">数据流动 {{ reportedToday > 0 ? '正常' : '停滞' }}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">近七日告警</span>
        <stat-value :value="weekAlarms" size="lg" :warning="weekAlarms > 0" />
        <span class="kpi-sub num">七日累计</span>
      </div>
    </div>

    <div class="overview-grid">
      <!-- 趋势 -->
      <section class="overview-section overview-trend">
        <div class="section-heading"><h2>近七日告警趋势</h2></div>
        <alert-trend-chart :data="trendData" :loading="loading" />
      </section>

      <!-- 级别分布 -->
      <section class="overview-section">
        <div class="section-heading"><h2>级别分布</h2></div>
        <div class="level-band-list">
          <div v-for="item in levelDistribution" :key="item.key" :class="['level-band-row', `alarm-band-l${item.key}`]">
            <span class="level-band-label">{{ item.label }}</span>
            <div class="level-band-bar">
              <div class="level-band-fill" :style="{ width: `${item.percentage}%` }" />
            </div>
            <span class="level-band-value num">{{ item.value }}</span>
          </div>
        </div>
      </section>

      <!-- 设备告警 TOP -->
      <section class="overview-section">
        <div class="section-heading">
          <h2>设备告警 TOP</h2>
          <el-button link type="primary" @click="goAlarms">全部告警</el-button>
        </div>
        <el-table :data="ranking" size="small" :show-header="false" empty-text="暂无告警">
          <el-table-column type="index" width="45" />
          <el-table-column prop="deviceName" min-width="140" show-overflow-tooltip />
          <el-table-column width="110">
            <template #default="{ row }">
              <el-progress :percentage="row.percentage" :show-text="false" :stroke-width="7" />
            </template>
          </el-table-column>
          <el-table-column prop="alarmCount" width="55" align="right" />
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAlarmDashboardStats } from '@/api/dashboard'
import { getDevicePage } from '@/api/device'
import { onSummary } from '@/api/realtime'
import AlertTrendChart from '@/components/dashboard/AlertTrendChart.vue'

defineOptions({ name: 'RoomOverviewTab' })

const props = defineProps({
  roomId: { type: Number, required: true },
})
const emit = defineEmits(['room-alarms-loaded'])

const router = useRouter()
const loading = ref(false)
const stats = ref({ stateDistribution: {}, levelDistribution: {}, dailyTrend: {}, deviceRanking: [] })
const devices = ref([])
let removeSummaryListener = null

const onlineCount = computed(() => devices.value.filter((d) => d.status === 1).length)
const activeAlarms = computed(() => {
  const dist = stats.value.stateDistribution || {}
  return Number(dist.TRIGGERED || 0) + Number(dist.ACKED || 0) + Number(dist.HANDLING || 0)
})
const todayAlarms = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return Number(stats.value.dailyTrend?.[today] || 0)
})
const weekAlarms = computed(() =>
  Object.values(stats.value.dailyTrend || {}).reduce((sum, v) => sum + Number(v || 0), 0),
)
const reportedToday = computed(() => {
  const dayStart = new Date()
  dayStart.setHours(0, 0, 0, 0)
  return devices.value.filter((d) => d.lastOnlineTime && new Date(d.lastOnlineTime) >= dayStart).length
})
const trendData = computed(() =>
  Object.entries(stats.value.dailyTrend || {}).map(([date, count]) => ({ date: date.slice(5), count })),
)
const levelMeta = Object.freeze([
  { key: '1', label: '紧急' },
  { key: '2', label: '严重' },
  { key: '3', label: '一般' },
  { key: '4', label: '警告' },
  { key: '5', label: '提示' },
])
const levelDistribution = computed(() => {
  const items = levelMeta.map((item) => ({
    ...item,
    value: Number(stats.value.levelDistribution?.[`L${item.key}`] || 0),
  }))
  const total = items.reduce((sum, item) => sum + item.value, 0) || 1
  return items.map((item) => ({ ...item, percentage: Math.round((item.value / total) * 100) }))
})
const ranking = computed(() => {
  const source = stats.value.deviceRanking || []
  const max = Math.max(1, ...source.map((item) => Number(item.alarmCount) || 0))
  return source.map((item) => ({
    ...item,
    deviceName: devices.value.find((d) => d.id === item.deviceId)?.deviceName || `设备 #${item.deviceId}`,
    percentage: Math.round(((Number(item.alarmCount) || 0) / max) * 100),
  }))
})

async function fetchOverview() {
  loading.value = true
  try {
    const [statsRes, deviceRes] = await Promise.all([
      getAlarmDashboardStats({ cloudRoomId: props.roomId }),
      getDevicePage({ current: 1, size: 200, cloudRoomId: props.roomId }),
    ])
    stats.value = statsRes.data || stats.value
    devices.value = deviceRes.data?.records || []
    emit('room-alarms-loaded', activeAlarms.value)
  } finally {
    loading.value = false
  }
}

function goAlarms() {
  router.push({ name: 'room-alarms', params: { roomId: props.roomId } })
}

onMounted(() => {
  void fetchOverview()
  removeSummaryListener = onSummary((summary) => {
    if (summary.resourceType === 'ALARM_EVENT') void fetchOverview()
  })
})

onBeforeUnmount(() => removeSummaryListener?.())
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.kpi-label {
  color: var(--color-text-2);
  font-size: 13px;
}

.kpi-sub {
  color: var(--color-text-3);
  font-size: 12px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(360px, 1.5fr) minmax(220px, 0.8fr) minmax(220px, 0.8fr);
  gap: 16px;
}

.overview-section {
  min-width: 0;
  padding: 18px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.section-heading h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 16px;
}

.level-band-list {
  display: grid;
  gap: 12px;
}

.level-band-row {
  display: grid;
  grid-template-columns: 42px minmax(60px, 1fr) 36px;
  align-items: center;
  gap: 10px;
}

.level-band-label {
  color: var(--color-text-2);
  font-size: 13px;
}

.level-band-bar {
  height: 8px;
  border-radius: 4px;
  background: var(--color-surface-2);
  overflow: hidden;
}

.level-band-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--alarm-band, var(--color-primary));
  transition: width 0.3s ease;
}

.level-band-value {
  color: var(--color-text);
  font-weight: 600;
  text-align: right;
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
