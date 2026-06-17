<template>
  <div class="device-page">
    <device-filter :filter="filter" @search="handleSearch" @reset="handleReset" />
    <div class="toolbar">
      <el-button v-if="canOperate" type="primary" @click="handleCreate">新增设备</el-button>
    </div>
    <device-table
      :devices="deviceList" :loading="loading" :can-operate="canOperate" :is-admin="isAdmin"
      @edit="handleEdit" @delete="handleDelete" @status-change="handleStatusChange" @alert-config="handleAlertConfig"
    />
    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current" v-model:page-size="pagination.size"
      :total="total" :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next" @change="fetchDeviceList"
      style="margin-top: 16px; justify-content: flex-end"
    />
    <device-form-dialog
      v-model:visible="formVisible" :mode="formMode" :device="currentDevice" :device-types="deviceTypes"
      @confirm="handleFormConfirm"
    />
    <alert-config-dialog
      v-model:visible="alertConfigVisible" :device-id="currentDeviceId" :metrics="metrics"
      @confirm="handleAlertConfigConfirm"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getDevicePage, createDevice, updateDevice, deleteDevice, updateDeviceStatus } from '@/api/device'
import { getDeviceTypes } from '@/api/deviceType'
import DeviceFilter from '@/components/device/DeviceFilter.vue'
import DeviceTable from '@/components/device/DeviceTable.vue'
import DeviceFormDialog from '@/components/device/DeviceFormDialog.vue'
import AlertConfigDialog from '@/components/device/AlertConfigDialog.vue'

const authStore = useAuthStore()
const canOperate = computed(() => authStore.role !== 'VIEWER')
const isAdmin = computed(() => authStore.role === 'ADMIN')

const loading = ref(false); const deviceList = ref([]); const total = ref(0); const deviceTypes = ref([]); const metrics = ref([])
const pagination = reactive({ current: 1, size: 10 }); const filter = reactive({ status: null, name: '' })
const formVisible = ref(false); const formMode = ref('create'); const currentDevice = ref(null)
const alertConfigVisible = ref(false); const currentDeviceId = ref(null)

async function fetchDeviceList() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filter.status !== null && filter.status !== '') params.status = filter.status
    if (filter.name) params.name = filter.name
    const res = await getDevicePage(params)
    deviceList.value = res.data.records || []; total.value = res.data.total || 0
  } finally { loading.value = false }
}

function handleSearch() { pagination.current = 1; fetchDeviceList() }
function handleReset() { filter.status = null; filter.name = ''; pagination.current = 1; fetchDeviceList() }
function handleCreate() { formMode.value = 'create'; currentDevice.value = null; formVisible.value = true }
function handleEdit(device) { formMode.value = 'edit'; currentDevice.value = { ...device }; formVisible.value = true }

async function handleDelete(device) {
  try {
    await ElMessageBox.confirm(`确认删除设备 "${device.deviceName}"？`, '删除确认', { type: 'warning' })
    await deleteDevice(device.id); ElMessage.success('设备已删除'); fetchDeviceList()
  } catch {}
}

async function handleStatusChange(device, newStatus) {
  const oldStatus = device.status
  try {
    await updateDeviceStatus(device.id, newStatus)
    device.status = newStatus  // 乐观更新本地状态，避免开关回弹
    ElMessage.success('状态已更新')
  } catch { device.status = oldStatus }
}

function handleAlertConfig(device) { currentDeviceId.value = device.id; alertConfigVisible.value = true }

async function handleFormConfirm(data) {
  try {
    if (formMode.value === 'create') { await createDevice(data); ElMessage.success('设备已创建') }
    else { await updateDevice(currentDevice.value.id, data); ElMessage.success('设备已更新') }
    formVisible.value = false; fetchDeviceList()
  } catch {}
}

function handleAlertConfigConfirm() { alertConfigVisible.value = false; ElMessage.success('告警规则已更新') }

onMounted(async () => { try { const res = await getDeviceTypes(); deviceTypes.value = res.data || [] } catch {}; fetchDeviceList() })
</script>

<style scoped>
.device-page { padding: 0; }
.toolbar { margin-bottom: 16px; }
</style>
