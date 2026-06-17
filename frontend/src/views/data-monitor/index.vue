<template>
  <div class="data-monitor-page">
    <el-row :gutter="16">
      <el-col :span="5">
        <device-sidebar v-model="selectedDeviceId" :devices="deviceList" />
      </el-col>
      <el-col :span="19">
        <div v-if="!selectedDeviceId" class="placeholder">
          <el-empty description="请从左侧选择一个设备" />
        </div>
        <template v-else>
          <el-card v-if="latestData" class="latest-card" shadow="never">
            <template #header>最新数据</template>
            <el-tag :type="latestData.value > 80 ? 'danger' : 'success'" size="large" effect="dark">
              {{ latestData.metricKey }}: {{ latestData.value }}{{ latestData.unit }}
            </el-tag>
            <span style="margin-left: 12px; color: #909399">采集时间: {{ latestData.createTime }}</span>
          </el-card>
          <data-trend-chart :data="rangeData" @range-change="handleRangeChange" />
          <data-stats-panel :stats="stats" />
          <data-table :list="historyData" :loading="historyLoading" :total="historyTotal" @page-change="page => fetchHistory(selectedDeviceId, page)" />
        </template>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { getAllDevices } from '@/api/device'
import { getDeviceLatestData, getDeviceData, getDeviceDataByRange, getDeviceDataStatistics } from '@/api/deviceData'
import DeviceSidebar from '@/components/data-monitor/DeviceSidebar.vue'
import DataTrendChart from '@/components/data-monitor/DataTrendChart.vue'
import DataStatsPanel from '@/components/data-monitor/DataStatsPanel.vue'
import DataTable from '@/components/data-monitor/DataTable.vue'

const deviceList = ref([])
const selectedDeviceId = ref(null)
const latestData = ref(null)
const historyData = ref([])
const historyTotal = ref(0)
const historyLoading = ref(false)
const rangeData = ref([])
const stats = ref({})

const rangeMap = { '1h': 3600000, '6h': 21600000, '24h': 86400000, '7d': 604800000, '30d': 2592000000 }
let currentRange = '24h'

function formatDate(d) {
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function loadDevice(deviceId) {
  if (!deviceId) return
  try {
    const [latestRes, historyRes] = await Promise.all([
      getDeviceLatestData(deviceId),
      getDeviceData(deviceId, { current: 1, size: 20 }),
    ])
    latestData.value = latestRes.data
    historyData.value = historyRes.data?.records || []
    historyTotal.value = historyRes.data?.total || 0
  } catch {}
  await loadRange(deviceId, currentRange)
}

async function loadRange(deviceId, range) {
  const now = new Date()
  const startTime = formatDate(new Date(now - rangeMap[range]))
  const endTime = formatDate(now)
  try {
    const [rangeRes, statsRes] = await Promise.all([
      getDeviceDataByRange(deviceId, { startTime, endTime }),
      getDeviceDataStatistics(deviceId, { startTime, endTime }),
    ])
    rangeData.value = rangeRes.data || []
    stats.value = statsRes.data || {}
  } catch {}
}

async function fetchHistory(deviceId, page) {
  historyLoading.value = true
  try {
    const res = await getDeviceData(deviceId, { current: page, size: 20 })
    historyData.value = res.data?.records || []
    historyTotal.value = res.data?.total || 0
  } finally { historyLoading.value = false }
}

async function handleRangeChange(range) {
  currentRange = range
  await loadRange(selectedDeviceId.value, range)
}

// watch selected device
watch(selectedDeviceId, val => { if (val) loadDevice(val) })

onMounted(async () => {
  try { const res = await getAllDevices(); deviceList.value = res.data || [] } catch {}
})
</script>

<style scoped>
.data-monitor-page { padding: 0; }
.placeholder { text-align: center; padding: 100px 0; }
.latest-card { margin-bottom: 16px; }
</style>
