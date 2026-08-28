<template>
  <page-shell title="跨机房检索对比" subtitle="多机房设备与告警并排分析（2-4 个机房）">
    <!-- 条件区 -->
    <filter-bar>
      <el-select
        v-model="selectedRoomIds"
        multiple
        filterable
        placeholder="选择机房（2-4 个）"
        class="room-multi"
        :multiple-limit="4"
      >
        <el-option v-for="r in rooms" :key="r.id" :label="r.roomName" :value="r.id" />
      </el-select>
      <el-select v-model="filter.typeId" placeholder="全部类型" clearable style="width: 130px">
        <el-option v-for="t in deviceTypes" :key="t.id" :label="t.typeName" :value="t.id" />
      </el-select>
      <el-input v-model="filter.deviceName" placeholder="设备名称模糊" clearable style="width: 160px" />
      <el-select v-model="filter.rangeKey" style="width: 120px">
        <el-option label="近 24 小时" value="24h" />
        <el-option label="近 7 天" value="7d" />
        <el-option label="近 30 天" value="30d" />
      </el-select>
      <el-button type="primary" :icon="Search" :loading="loading" @click="runComparison">对比</el-button>
    </filter-bar>

    <!-- 对比卡 -->
    <div v-if="comparisons.length" class="compare-grid">
      <div v-for="c in comparisons" :key="c.roomId" class="compare-card">
        <div class="compare-card-head">
          <span class="compare-card-name">{{ c.roomName }}</span>
          <el-button link type="primary" size="small" @click="goWorkspace(c.roomId)">进入工作台</el-button>
        </div>
        <div class="compare-kpi-row">
          <div class="compare-kpi">
            <span class="compare-kpi-label">命中设备</span>
            <stat-value :value="c.deviceTotal" />
            <span class="compare-kpi-sub num">在线 {{ c.deviceOnline }}</span>
          </div>
          <div class="compare-kpi">
            <span class="compare-kpi-label">在线率</span>
            <stat-value :value="c.onlineRate" unit="%" :warning="onlineRateWarning(c.onlineRate)" />
            <span class="compare-kpi-sub">{{ onlineRateNote(c.onlineRate) }}</span>
          </div>
          <div class="compare-kpi">
            <span class="compare-kpi-label">活动告警</span>
            <stat-value :value="c.activeAlarms" :danger="c.activeAlarms > 0" />
            <span class="compare-kpi-sub num">范围内 {{ c.alarmCount }}</span>
          </div>
        </div>
        <!-- 级别分布五级色带 -->
        <div class="level-band-list">
          <div v-for="item in levelItems(c)" :key="item.key" :class="['level-band-row', `alarm-band-l${item.key}`]">
            <span class="level-band-label">{{ item.label }}</span>
            <div class="level-band-bar">
              <div class="level-band-fill" :style="{ width: `${item.percentage}%` }" />
            </div>
            <span class="level-band-value num">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else-if="!loading" description="选择至少 2 个机房后点击对比" />

    <!-- 明细区 -->
    <template v-if="comparisons.length">
      <div class="detail-section">
        <el-tabs v-model="detailTab">
          <el-tab-pane label="命中设备" name="devices" />
          <el-tab-pane label="范围内告警" name="alarms" />
        </el-tabs>
        <el-table v-if="detailTab === 'devices'" v-loading="detailLoading" :data="detailDevices" size="small">
          <el-table-column label="机房" min-width="130">
            <template #default="{ row }">{{ roomNameOf(row.cloudRoomId) }}</template>
          </el-table-column>
          <el-table-column prop="deviceName" label="设备名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="deviceCode" label="编码" min-width="120" />
          <el-table-column label="类型" min-width="100">
            <template #default="{ row }">{{ typeLabel(row.typeId) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="{ 0: 'info', 1: 'success', 2: 'danger' }[row.status]" size="small">
                {{ { 0: '离线', 1: '在线', 2: '故障' }[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-table v-else v-loading="detailLoading" :data="detailAlarms" size="small">
          <el-table-column label="机房" min-width="130">
            <template #default="{ row }">{{ roomNameOf(row.cloudRoomId) }}</template>
          </el-table-column>
          <el-table-column label="级别" width="80">
            <template #default="{ row }">
              <span :class="['level-tag', `alarm-band-l${row.alarmLevel}`]">{{ levelLabel(row.alarmLevel) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="alarmMessage" label="告警描述" min-width="220" show-overflow-tooltip />
          <el-table-column prop="triggeredTime" label="触发时间" width="165" />
        </el-table>
      </div>
    </template>
  </page-shell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { compareRooms } from '@/api/dashboard'
import { getCloudRoomPage } from '@/api/cloudRoom'
import { getDevicePage } from '@/api/device'
import { getAlarmEventPage } from '@/api/alarmEvent'
import { getDeviceTypes } from '@/api/deviceType'

defineOptions({ name: 'CrossRoomSearch' })

const router = useRouter()
const rooms = ref([])
const deviceTypes = ref([])
const selectedRoomIds = ref([])
const filter = reactive({ typeId: null, deviceName: '', rangeKey: '24h' })
const loading = ref(false)
const comparisons = ref([])
const detailTab = ref('devices')
const detailLoading = ref(false)
const detailDevices = ref([])
const detailAlarms = ref([])

const RANGE_MS = { '24h': 86400000, '7d': 604800000, '30d': 2592000000 }

const levelMeta = Object.freeze([
  { key: '1', label: '紧急' },
  { key: '2', label: '严重' },
  { key: '3', label: '一般' },
  { key: '4', label: '警告' },
  { key: '5', label: '提示' },
])

function levelItems(comparison) {
  const dist = comparison.levelDistribution || {}
  const items = levelMeta.map((m) => ({ ...m, value: Number(dist[`L${m.key}`] || 0) }))
  const total = items.reduce((sum, item) => sum + item.value, 0) || 1
  return items.map((item) => ({ ...item, percentage: Math.round((item.value / total) * 100) }))
}

function levelLabel(level) {
  return levelMeta.find((m) => m.key === String(level))?.label || level
}

function roomNameOf(roomId) {
  return rooms.value.find((r) => r.id === roomId)?.roomName || `机房 #${roomId}`
}

function typeLabel(typeId) {
  return deviceTypes.value.find((t) => t.id === typeId)?.typeName || `类型 ${typeId}`
}

// 在线率差异高亮：跨机房对比中低于均值的标警示
const avgOnlineRate = computed(() => {
  if (!comparisons.value.length) return 0
  return Math.round(
    comparisons.value.reduce((sum, c) => sum + c.onlineRate, 0) / comparisons.value.length,
  )
})
function onlineRateWarning(rate) {
  return comparisons.value.length >= 2 && rate < avgOnlineRate.value - 20
}
function onlineRateNote(rate) {
  return onlineRateWarning(rate) ? '低于均值 20%+' : `均值 ${avgOnlineRate.value}%`
}

async function runComparison() {
  if (selectedRoomIds.value.length < 2) {
    ElMessage.warning('请选择至少 2 个机房')
    return
  }
  loading.value = true
  try {
    const end = new Date()
    const start = new Date(end.getTime() - RANGE_MS[filter.rangeKey])
    const res = await compareRooms({
      roomIds: selectedRoomIds.value.join(','),
      typeId: filter.typeId ?? undefined,
      deviceName: filter.deviceName.trim() || undefined,
      startTime: toLocal(start),
      endTime: toLocal(end),
    })
    comparisons.value = res.data || []
    await loadDetails()
  } catch (e) {
    ElMessage.error(e.message || '对比失败')
  } finally {
    loading.value = false
  }
}

async function loadDetails() {
  detailLoading.value = true
  try {
    const end = new Date()
    const start = new Date(end.getTime() - RANGE_MS[filter.rangeKey])
    // 命中设备：逐机房查询（按类型/名称过滤同口径）
    const deviceResults = await Promise.all(
      comparisons.value.map((c) => {
        const params = { current: 1, size: 100, cloudRoomId: c.roomId }
        if (filter.typeId != null) params.typeId = filter.typeId
        if (filter.deviceName.trim()) params.name = filter.deviceName.trim()
        return getDevicePage(params).then((r) => r.data?.records || []).catch(() => [])
      }),
    )
    let devices = deviceResults.flat()
    if (filter.typeId != null) devices = devices.filter((d) => d.typeId === filter.typeId)
    if (filter.deviceName.trim()) {
      const kw = filter.deviceName.trim().toLowerCase()
      devices = devices.filter((d) => (d.deviceName || '').toLowerCase().includes(kw))
    }
    detailDevices.value = devices

    // 范围内告警：逐机房查询（时间范围同口径）
    const alarmResults = await Promise.all(
      comparisons.value.map((c) =>
        getAlarmEventPage({
          current: 1,
          size: 100,
          cloudRoomId: c.roomId,
          startTime: toLocal(start),
          endTime: toLocal(end),
        }).then((r) => r.data?.records || []).catch(() => []),
      ),
    )
    detailAlarms.value = alarmResults.flat().sort((a, b) => (b.triggeredTime || '').localeCompare(a.triggeredTime || ''))
  } finally {
    detailLoading.value = false
  }
}

function toLocal(date) {
  const offset = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offset).toISOString().slice(0, 19)
}

function goWorkspace(roomId) {
  router.push({ name: 'room-overview', params: { roomId } })
}

onMounted(async () => {
  const [roomRes, typeRes] = await Promise.all([
    getCloudRoomPage({ current: 1, size: 100 }),
    getDeviceTypes(),
  ])
  rooms.value = roomRes.data?.records || []
  deviceTypes.value = typeRes.data || []
  // 默认选中前两个机房
  if (rooms.value.length >= 2) {
    selectedRoomIds.value = [rooms.value[0].id, rooms.value[1].id]
  }
})
</script>

<style scoped>
.room-multi {
  width: 300px;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.compare-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 18px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.compare-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.compare-card-name {
  color: var(--color-text);
  font-size: 15px;
  font-weight: 600;
}

.compare-kpi-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.compare-kpi {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.compare-kpi-label {
  color: var(--color-text-3);
  font-size: 12px;
}

.compare-kpi-sub {
  color: var(--color-text-3);
  font-size: 11px;
}

.level-band-list {
  display: grid;
  gap: 8px;
}

.level-band-row {
  display: grid;
  grid-template-columns: 36px minmax(50px, 1fr) 30px;
  align-items: center;
  gap: 8px;
}

.level-band-label {
  color: var(--color-text-2);
  font-size: 12px;
}

.level-band-bar {
  height: 7px;
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
  font-size: 12px;
  text-align: right;
}

.detail-section {
  padding: 16px 18px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
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
  font-weight: 500;
}
</style>
