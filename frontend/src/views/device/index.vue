<template>
  <section class="device-page">
    <header class="page-header">
      <div>
        <h1>设备管理</h1>
        <p>设备归属、状态、点位绑定及告警配置</p>
      </div>
    </header>

    <device-filter :filter="filter" :rooms="rooms" @search="handleSearch" @reset="handleReset" />
    <div class="toolbar">
      <el-button v-if="canWrite" type="primary" :icon="Plus" @click="handleCreate">
        新增设备
      </el-button>
      <el-button v-if="canImport" type="success" :icon="Upload" @click="handleImport">
        批量导入
      </el-button>
      <el-button :icon="Download" @click="handleDownloadTemplate">下载模板</el-button>
      <el-button v-if="canExport" type="warning" :icon="Download" @click="handleExport">导出设备</el-button>
      <input ref="fileInput" type="file" accept=".xlsx" hidden @change="handleFileChange" />
    </div>

    <device-management-table
      :devices="deviceList"
      :loading="loading"
      :can-write="canWrite"
      :can-delete="isOrgAdmin"
      :type-names="typeNames"
      :room-names="roomNames"
      :gateway-names="gatewayNames"
      :effective-template-names="effectiveTemplateNames"
      @edit="handleEdit"
      @delete="handleDelete"
      @status-change="handleStatusChange"
      @points="handlePoints"
    />

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @change="fetchDeviceList"
    />

    <device-editor-dialog
      v-model:visible="formVisible"
      :mode="formMode"
      :device="currentDevice"
      :device-types="supportedDeviceTypes"
      :rooms="rooms"
      :alarm-templates="enabledAlarmTemplates"
      @confirm="handleFormConfirm"
    />
    <device-point-drawer
      v-model:visible="pointDrawerVisible"
      :device="currentDevice"
      :can-manage="isOrgAdmin"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download, Plus, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createDevice,
  deleteDevice,
  downloadDeviceImportTemplate,
  exportDevices,
  getDevicePage,
  importDevices,
  updateDevice,
  updateDeviceStatus,
} from '@/api/device'
import { getDeviceTypes } from '@/api/deviceType'
import { getPointDefinitionPage } from '@/api/pointDefinition'
import { getCloudRoomPage } from '@/api/cloudRoom'
import { getEffectiveTemplate, listEnabledTemplates, bindDeviceTemplate } from '@/api/alarmTemplate'

import DeviceFilter from '@/components/device/DeviceFilter.vue'
import DeviceEditorDialog from './DeviceEditorDialog.vue'
import DeviceManagementTable from './DeviceManagementTable.vue'
import DevicePointDrawer from './DevicePointDrawer.vue'

defineOptions({ name: 'ScopedDeviceManagement' })

const SUPPORTED_TYPE_CODES = new Set([
  'UPS',
  'PDU_METER',
  'PRECISION_AC',
  'TEMP_HUMIDITY',
  'LEAK_DETECTOR',
])
const authStore = useAuthStore()
const canWrite = computed(() => ['ADMIN', 'OPERATOR'].includes(authStore.normalizedRole))
const canImport = computed(() => authStore.isAdmin)
const canExport = computed(() => canWrite.value)
const isOrgAdmin = computed(() => authStore.isAdmin)
const loading = ref(false)
const deviceList = ref([])
const total = ref(0)
const deviceTypes = ref([])
const rooms = ref([])
const metrics = ref([])
const pagination = reactive({ current: 1, size: 10 })
const filter = reactive({ status: null, name: '', cloudRoomId: null })
const formVisible = ref(false)
const formMode = ref('create')
const currentDevice = ref(null)
const pointDrawerVisible = ref(false)
const fileInput = ref(null)
const supportedDeviceTypes = computed(() =>
  deviceTypes.value.filter((item) => SUPPORTED_TYPE_CODES.has(item.typeCode?.toUpperCase())),
)
const typeNames = computed(() =>
  Object.fromEntries(deviceTypes.value.map((item) => [item.id, item.typeName])),
)
const roomNames = computed(() =>
  Object.fromEntries(rooms.value.map((item) => [item.id, item.roomName])),
)
// 接入视角映射：凭证ID → 机房名（经机房凭证视图；凭证与机房 1:1）
const roomCredentials = ref([])
const gatewayNames = computed(() =>
  Object.fromEntries(roomCredentials.value.map((item) => [item.id,
    rooms.value.find((r) => r.id === item.cloudRoomId)?.roomName || `机房 #${item.cloudRoomId}`])),
)

async function fetchGateways() {
  const result = await import('@/api/cloudRoom').then((m) => m.getCloudRoomPage({ current: 1, size: 100 }))
  const roomList = result.data?.records || []
  const credResults = await Promise.all(roomList.map((r) =>
    import('@/api/cloudRoom').then((m) => m.getRoomCredential(r.id).catch(() => null))))
  roomCredentials.value = credResults
    .map((r) => r?.data)
    .filter(Boolean)
}

async function fetchReferences() {
  const [typeResult, roomResult, metricResult, templateResult] = await Promise.all([
    getDeviceTypes(),
    getCloudRoomPage({ current: 1, size: 100, status: 0 }),
    getPointDefinitionPage({ current: 1, size: 100 }),
    listEnabledTemplates().catch(() => ({ data: [] })),
  ])
  deviceTypes.value = typeResult.data || []
  rooms.value = roomResult.data?.records || []
  metrics.value = metricResult.data?.records || []
  alarmTemplates.value = templateResult.data || []
}

// 告警模版：启用列表 + 各设备生效模版名（跟随类型时显示类型模版名）
const alarmTemplates = ref([])
const enabledAlarmTemplates = computed(() => alarmTemplates.value.filter(t => t.status === 0))
const effectiveTemplateNames = ref({})
const templateById = computed(() => Object.fromEntries(alarmTemplates.value.map(t => [t.id, t])))

async function fetchEffectiveTemplateNames() {
  const entries = await Promise.all(
    deviceList.value.map(async (d) => {
      try {
        const res = await getEffectiveTemplate(d.id)
        return [d.id, res.data || {}]
      } catch {
        return [d.id, null]
      }
    }),
  )
  effectiveTemplateNames.value = Object.fromEntries(entries)
}

async function fetchDeviceList() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filter.status !== null && filter.status !== '') params.status = filter.status
    if (filter.name.trim()) params.name = filter.name.trim()
    if (filter.cloudRoomId != null) params.cloudRoomId = filter.cloudRoomId
    const result = await getDevicePage(params)
    deviceList.value = result.data?.records || []
    total.value = result.data?.total || 0
    void fetchEffectiveTemplateNames()
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  syncRoomQuery()
  void fetchDeviceList()
}

function handleReset() {
  filter.status = null
  filter.name = ''
  filter.cloudRoomId = null
  pagination.current = 1
  syncRoomQuery()
  void fetchDeviceList()
}

// 机房筛选与 URL query 双向同步（roomId 可从仪表盘/数据监控携带跳转）
function syncRoomQuery() {
  const query = { ...route.query }
  if (filter.cloudRoomId != null) query.roomId = filter.cloudRoomId
  else delete query.roomId
  router.replace({ query })
}

function handleCreate() {
  formMode.value = 'create'
  currentDevice.value = null
  formVisible.value = true
}

function handleEdit(device) {
  formMode.value = 'edit'
  currentDevice.value = { ...device }
  formVisible.value = true
}

function handlePoints(device) {
  currentDevice.value = device
  pointDrawerVisible.value = true
}

async function handleDelete(device) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除设备“${device.deviceName}”？`,
    '删除确认',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await deleteDevice(device.id)
  ElMessage.success('设备已删除')
  await fetchDeviceList()
}

async function handleStatusChange(device, status) {
  await updateDeviceStatus(device.id, status)
  ElMessage.success('设备状态已更新')
  await fetchDeviceList()
}

async function handleFormConfirm(data) {
  try {
    if (formMode.value === 'create') {
      await createDevice(data)
      ElMessage.success('设备已创建')
    } else {
      const { alarmTemplateId, ...deviceData } = data
      await updateDevice(currentDevice.value.id, deviceData)
      // 改绑与常规更新分离：独立绑定端点（停用模版校验在服务端）
      const targetId = alarmTemplateId || null
      if ((currentDevice.value.alarmTemplateId || null) !== targetId) {
        await bindDeviceTemplate(currentDevice.value.id, targetId)
      }
      ElMessage.success('设备已更新')
    }
    formVisible.value = false
    await fetchDeviceList()
  } catch {
    return
  }
}

function handleImport() {
  if (fileInput.value) fileInput.value.click()
}

async function handleFileChange(event) {
  const file = event.target.files[0]
  if (!file) return
  event.target.value = ''
  try {
    const result = await importDevices(file)
    const successCount = result.data?.successCount || 0
    const failCount = result.data?.failCount || 0
    if (failCount > 0) {
      ElMessage.warning(`导入完成：成功 ${successCount} 条，失败 ${failCount} 条`)
    } else {
      ElMessage.success(`成功导入 ${successCount} 条设备`)
    }
    await fetchDeviceList()
  } catch {
    return
  }
}

async function handleDownloadTemplate() {
  try {
    const response = await downloadDeviceImportTemplate()
    downloadBlob(response.data, '设备导入模板.xlsx')
  } catch {
    return
  }
}

async function handleExport() {
  try {
    const params = {}
    if (filter.status !== null && filter.status !== '') params.status = filter.status
    if (filter.name.trim()) params.name = filter.name.trim()
    const response = await exportDevices(params)
    downloadBlob(response.data, `设备列表_${formatDate(new Date())}.xlsx`)
  } catch {
    return
  }
}

function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}${month}${day}`
}

const route = useRoute()
const router = useRouter()

onMounted(async () => {
  // URL query 携带的机房上下文初始化筛选（可分享/可刷新）
  const queryRoom = Number(route.query.roomId)
  if (queryRoom && !Number.isNaN(queryRoom)) filter.cloudRoomId = queryRoom
  await fetchReferences()
  await fetchDeviceList()
  await fetchGateways()
})
</script>

<style scoped>
.device-page {
  min-width: 0;
}

.page-header {
  margin-bottom: 18px;
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

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
