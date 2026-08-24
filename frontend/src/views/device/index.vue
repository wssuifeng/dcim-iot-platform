<template>
  <section class="device-page">
    <header class="page-header">
      <div>
        <h1>设备管理</h1>
        <p>设备归属、状态、点位绑定及告警配置</p>
      </div>
    </header>

    <device-filter :filter="filter" @search="handleSearch" @reset="handleReset" />
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
      :organization-names="organizationNames"
      :room-names="roomNames"
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
      :organizations="organizations"
      :rooms="rooms"
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
import { Download, Plus, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useScopeStore } from '@/stores/scope'
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
import { getOrganizationPage } from '@/api/organization'
import { getCloudRoomPage } from '@/api/cloudRoom'
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
const scopeStore = useScopeStore()
const effectiveRole = computed(() => scopeStore.role)
const hasWriteScope = computed(() => scopeStore.isReady && scopeStore.hasWritePermission)
const canWrite = computed(
  () => ['ORG_ADMIN', 'OPERATOR'].includes(effectiveRole.value) && hasWriteScope.value,
)
const canImport = computed(() => effectiveRole.value === 'ORG_ADMIN' && hasWriteScope.value)
const canExport = computed(
  () => ['ORG_ADMIN', 'OPERATOR'].includes(effectiveRole.value) && hasWriteScope.value,
)
const isOrgAdmin = computed(
  () => effectiveRole.value === 'ORG_ADMIN' && hasWriteScope.value,
)
const loading = ref(false)
const deviceList = ref([])
const total = ref(0)
const deviceTypes = ref([])
const organizations = ref([])
const rooms = ref([])
const metrics = ref([])
const pagination = reactive({ current: 1, size: 10 })
const filter = reactive({ status: null, name: '' })
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
const organizationNames = computed(() =>
  Object.fromEntries(organizations.value.map((item) => [item.id, item.orgName])),
)
const roomNames = computed(() =>
  Object.fromEntries(rooms.value.map((item) => [item.id, item.roomName])),
)

async function fetchReferences() {
  const [typeResult, organizationResult, roomResult, metricResult] = await Promise.all([
    getDeviceTypes(),
    getOrganizationPage({ current: 1, size: 100, status: 0 }),
    getCloudRoomPage({ current: 1, size: 100, status: 0 }),
    getPointDefinitionPage({ current: 1, size: 100 }),
  ])
  deviceTypes.value = typeResult.data || []
  organizations.value = organizationResult.data?.records || []
  rooms.value = roomResult.data?.records || []
  metrics.value = metricResult.data?.records || []
}

async function fetchDeviceList() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filter.status !== null && filter.status !== '') params.status = filter.status
    if (filter.name.trim()) params.name = filter.name.trim()
    const result = await getDevicePage(params)
    deviceList.value = result.data?.records || []
    total.value = result.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  void fetchDeviceList()
}

function handleReset() {
  filter.status = null
  filter.name = ''
  pagination.current = 1
  void fetchDeviceList()
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
      await updateDevice(currentDevice.value.id, data)
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

onMounted(async () => {
  await fetchReferences()
  await fetchDeviceList()
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
