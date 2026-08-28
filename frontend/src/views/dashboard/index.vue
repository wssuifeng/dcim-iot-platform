<template>
  <page-shell :title="'运行驾驶舱'" :subtitle="dashboard.scopeDescription || '机房、告警与数据一屏总览'">
    <template #action>
      <el-button :icon="Refresh" :loading="loading" @click="fetchAll">刷新</el-button>
    </template>

    <!-- 顶层大数字：一屏回答三问 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <span class="kpi-label">设备总数</span>
        <stat-value :value="devices.length" size="lg" />
        <span class="kpi-sub num">在线 {{ onlineCount }} / 离线 {{ offlineCount }}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">活动告警</span>
        <stat-value :value="activeAlarms" size="lg" :danger="activeAlarms > 0" />
        <span class="kpi-sub num">今日新增 {{ todayAlarms }}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">机房总数</span>
        <stat-value :value="rooms.length" size="lg" />
        <span class="kpi-sub num">网关在线 {{ onlineGatewayCount }} / {{ rooms.length }}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">今日上报设备</span>
        <stat-value :value="reportedToday" size="lg" />
        <span class="kpi-sub num">数据流动 {{ reportedToday > 0 ? '正常' : '停滞' }}</span>
      </div>
    </div>

    <!-- 机房总览卡：机房都活着吗 -->
    <section class="cockpit-section">
      <div class="section-heading">
        <h2>机房总览</h2>
        <el-button v-if="isAdmin" link type="primary" @click="router.push('/cloud-rooms')">管理机房</el-button>
      </div>
      <div v-if="rooms.length" class="room-grid">
        <div
          v-for="room in roomCards"
          :key="room.id"
          :class="['room-card', `room-card--${room.health}`]"
          @click="router.push({ path: '/data-monitor', query: { roomId: room.id } })"
        >
          <div class="room-card-head">
            <span class="room-card-name">{{ room.roomName }}</span>
            <span class="room-card-gateway">
              <state-dot :state="room.gatewayState" />
              <span class="room-card-gateway-text">{{ gatewayLabel(room.gatewayState) }}</span>
            </span>
          </div>
          <div class="room-card-body">
            <div class="room-card-metric">
              <span class="kpi-sub">设备在线</span>
              <span class="num room-card-num">{{ room.onlineDevices }}/{{ room.totalDevices }}</span>
            </div>
            <div class="room-card-metric">
              <span class="kpi-sub">触发告警</span>
              <span class="num room-card-num" :class="{ 'is-danger': room.triggeredAlarms > 0 }">{{
                room.triggeredAlarms
              }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="room-empty">
        <el-empty :image-size="72" description="尚无机房——创建机房即得接入凭证">
          <el-button v-if="isAdmin" type="primary" @click="router.push('/cloud-rooms')">新建机房</el-button>
        </el-empty>
      </div>
    </section>

    <!-- 告警态势：有什么告警 -->
    <div class="cockpit-grid">
      <section class="cockpit-section cockpit-section-trend">
        <div class="section-heading"><h2>近七日告警趋势</h2></div>
        <alert-trend-chart :data="trendData" :loading="loading" />
      </section>

      <section class="cockpit-section">
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

      <section class="cockpit-section">
        <div class="section-heading"><h2>设备告警 TOP</h2></div>
        <el-table :data="ranking" size="small" :show-header="false" empty-text="暂无排名数据">
          <el-table-column type="index" width="45" />
          <el-table-column prop="deviceName" min-width="150" show-overflow-tooltip />
          <el-table-column width="130">
            <template #default="{ row }">
              <el-progress :percentage="row.percentage" :show-text="false" :stroke-width="7" />
            </template>
          </el-table-column>
          <el-table-column prop="alarmCount" width="60" align="right" />
        </el-table>
      </section>
    </div>

    <section class="cockpit-section recent-section">
      <div class="section-heading">
        <h2>最近告警</h2>
        <el-button link type="primary" @click="router.push('/alerts')">查看全部</el-button>
      </div>
      <el-table v-loading="recentLoading" :data="recentAlarms" size="small" row-key="id">
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <span :class="['level-tag', `alarm-band-l${row.alarmLevel}`]">{{ row.alarmLevel }}级</span>
          </template>
        </el-table-column>
        <el-table-column label="设备" min-width="140">
          <template #default="{ row }">{{ deviceName(row.deviceId) }}</template>
        </el-table-column>
        <el-table-column prop="alarmMessage" label="告警" min-width="220" show-overflow-tooltip />
        <el-table-column prop="triggeredTime" label="触发时间" min-width="165" class-name="num" />
      </el-table>
    </section>
  </page-shell>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getAlarmDashboardStats } from '@/api/dashboard'
import { getAlarmEventPage } from '@/api/alarmEvent'
import { getDevicePage } from '@/api/device'
import { getCloudRoomPage, getRoomCredential } from '@/api/cloudRoom'
import { onSummary } from '@/api/realtime'
import { useAuthStore } from '@/stores/auth'
import AlertTrendChart from '@/components/dashboard/AlertTrendChart.vue'

defineOptions({ name: 'CockpitDashboard' })

const router = useRouter()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.isAdmin)
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
const rooms = ref([])
const roomCredentials = ref({})
const roomTriggeredAlarms = ref({})
const recentAlarms = ref([])
let removeSummaryListener = null

const totalAlarms = computed(() =>
  Object.values(dashboard.value.stateDistribution || {}).reduce((sum, value) => sum + Number(value || 0), 0),
)
const activeAlarms = computed(
  () => stateCount('TRIGGERED') + stateCount('ACKED') + stateCount('HANDLING'),
)
const todayAlarms = computed(() => {
  const today = new Date().toISOString().slice(0, 10)
  return Number(dashboard.value.dailyTrend?.[today] || 0)
})
const onlineCount = computed(() => devices.value.filter((d) => d.status === 1).length)
const offlineCount = computed(() => devices.value.length - onlineCount.value)
const onlineGatewayCount = computed(() =>
  roomCards.value.filter((r) => r.gatewayState === 'online').length,
)
const reportedToday = computed(() => {
  const dayStart = new Date()
  dayStart.setHours(0, 0, 0, 0)
  return devices.value.filter((d) => d.lastOnlineTime && new Date(d.lastOnlineTime) >= dayStart).length
})
const trendData = computed(() =>
  Object.entries(dashboard.value.dailyTrend || {}).map(([date, count]) => ({ date: date.slice(5), count })),
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
    value: Number(dashboard.value.levelDistribution?.[item.key] || 0),
  }))
  const total = items.reduce((sum, item) => sum + item.value, 0) || 1
  return items.map((item) => ({ ...item, percentage: Math.round((item.value / total) * 100) }))
})
const ranking = computed(() => {
  const source = dashboard.value.deviceRanking || []
  const max = Math.max(1, ...source.map((item) => Number(item.alarmCount) || 0))
  return source.map((item) => ({
    ...item,
    deviceName: deviceName(item.deviceId),
    percentage: Math.round(((Number(item.alarmCount) || 0) / max) * 100),
  }))
})

// 机房卡片：绿=网关在线且无触发告警 / 黄=有告警 / 红=网关离线
const roomCards = computed(() =>
  rooms.value.map((room) => {
    const credential = roomCredentials.value[room.id]
    const gatewayState = resolveGatewayState(credential?.lastSeenAt)
    const roomDevices = devices.value.filter((d) => d.cloudRoomId === room.id)
    const triggered = roomTriggeredAlarms.value[room.id] || 0
    const health =
      gatewayState === 'online' ? (triggered > 0 ? 'warn' : 'ok') : gatewayState === 'stale' ? 'idle' : 'down'
    return {
      id: room.id,
      roomName: room.roomName,
      gatewayState,
      health,
      totalDevices: roomDevices.length,
      onlineDevices: roomDevices.filter((d) => d.status === 1).length,
      triggeredAlarms: triggered,
    }
  }),
)

// 网关状态口径与机房管理页一致：最近活跃 5 分钟内在线；从未上线=stale
function resolveGatewayState(lastSeenAt) {
  if (!lastSeenAt) return 'stale'
  const seen = new Date(lastSeenAt)
  return Date.now() - seen.getTime() < 5 * 60 * 1000 ? 'online' : 'offline'
}

function gatewayLabel(state) {
  return { online: '网关在线', offline: '网关离线', stale: '从未上线' }[state] || state
}

function stateCount(state) {
  return Number(dashboard.value.stateDistribution?.[state] || 0)
}

async function fetchDashboard() {
  loading.value = true
  try {
    const [statsResult, recentResult, deviceResult, roomResult] = await Promise.all([
      getAlarmDashboardStats(),
      getAlarmEventPage({ current: 1, size: 5 }),
      getDevicePage({ current: 1, size: 200 }),
      getCloudRoomPage({ current: 1, size: 100 }),
    ])
    dashboard.value = statsResult.data || dashboard.value
    recentAlarms.value = recentResult.data?.records || []
    devices.value = deviceResult.data?.records || []
    rooms.value = roomResult.data?.records || []
    await Promise.all([
      fetchRoomCredentials(),
      fetchRoomTriggeredAlarms(),
    ])
  } finally {
    loading.value = false
  }
}

async function fetchRoomCredentials() {
  const entries = await Promise.all(
    rooms.value.map(async (room) => {
      const res = await getRoomCredential(room.id).catch(() => null)
      return [room.id, res?.data || null]
    }),
  )
  roomCredentials.value = Object.fromEntries(entries)
}

async function fetchRoomTriggeredAlarms() {
  const entries = await Promise.all(
    rooms.value.map(async (room) => {
      const res = await getAlarmEventPage({ current: 1, size: 1, cloudRoomId: room.id, state: 'TRIGGERED' })
        .catch(() => null)
      return [room.id, Number(res?.data?.total || 0)]
    }),
  )
  roomTriggeredAlarms.value = Object.fromEntries(entries)
}

function fetchAll() {
  void fetchDashboard()
}

function deviceName(id) {
  return devices.value.find((item) => item.id === id)?.deviceName || `设备 #${id}`
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
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 20px;
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

.cockpit-section {
  min-width: 0;
  padding: 20px;
  margin-bottom: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.section-heading h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 16px;
}

.room-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.room-card {
  padding: 14px 16px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.room-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.room-card--ok {
  border-left-color: var(--color-success);
}

.room-card--warn {
  border-left-color: var(--color-warning);
}

.room-card--down {
  border-left-color: var(--color-danger);
}

.room-card--idle {
  border-left-color: var(--color-text-3);
}

.room-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.room-card-name {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-card-gateway {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.room-card-gateway-text {
  color: var(--color-text-3);
  font-size: 12px;
}

.room-card-body {
  display: flex;
  gap: 24px;
}

.room-card-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.room-card-num {
  color: var(--color-text);
  font-size: 18px;
  font-weight: 600;
}

.room-card-num.is-danger {
  color: var(--color-danger);
}

.room-empty {
  padding: 12px 0;
}

.cockpit-grid {
  display: grid;
  grid-template-columns: minmax(420px, 1.6fr) minmax(240px, 0.8fr) minmax(220px, 0.7fr);
  gap: 16px;
}

.cockpit-grid .cockpit-section {
  margin-bottom: 16px;
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

.level-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
  color: var(--alarm-band, var(--color-text-2));
  background: var(--alarm-band-bg, var(--color-surface-2));
}

@media (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cockpit-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .kpi-grid {
    grid-template-columns: 1fr;
  }
}
</style>
