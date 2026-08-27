<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>接入管理</h1>
        <p>网关接入凭证：采集网关凭 accessKey/secret 签名上报数据，凭证绑定机房后只能上报该机房设备</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增凭证</el-button>
    </header>

    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="关键字">
        <el-input v-model="filters.keyword" placeholder="网关名 / 接入标识" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" placeholder="全部" clearable class="status-select">
          <el-option label="启用" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="filters.simulated" placeholder="全部" clearable class="status-select">
          <el-option label="真实网关" :value="0" />
          <el-option label="仿真网关" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="credentials" row-key="id">
      <el-table-column prop="gatewayName" label="网关名称" min-width="160" />
      <el-table-column prop="accessKey" label="接入标识" min-width="190" />
      <el-table-column label="绑定机房" min-width="140">
        <template #default="{ row }">{{ roomName(row.cloudRoomId) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.simulated === 1 ? 'warning' : 'info'">
            {{ row.simulated === 1 ? '仿真' : '真实' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'">
            {{ row.status === 0 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="自动注册" width="95">
        <template #default="{ row }">
          <el-tag v-if="row.autoRegister === 1" type="success">开启</el-tag>
          <el-tag v-else type="info" size="small">关闭</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近活跃" min-width="165">
        <template #default="{ row }">
          <span v-if="row.lastSeenAt" :class="{ stale: isStale(row) }">{{ row.lastSeenAt }}</span>
          <el-text v-else type="info" size="small">从未上报</el-text>
        </template>
      </el-table-column>
      <el-table-column prop="expireAt" label="过期时间" min-width="165">
        <template #default="{ row }">{{ row.expireAt || '永久' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" :icon="Key" @click="handleResetSecret(row)">重置密钥</el-button>
          <el-button
            link
            :type="row.status === 0 ? 'warning' : 'success'"
            :icon="Switch"
            @click="handleToggleStatus(row)"
          >
            {{ row.status === 0 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @change="fetchCredentials"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增接入凭证' : '编辑接入凭证'"
      width="540px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="网关名称" prop="gatewayName">
          <el-input v-model="form.gatewayName" maxlength="100" placeholder="如 机房A-动环主机01" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="绑定机房" prop="cloudRoomId">
          <el-select v-model="form.cloudRoomId" filterable class="full-width">
            <el-option
              v-for="item in activeRooms"
              :key="item.id"
              :label="item.roomName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="网关类型" prop="simulated">
          <el-radio-group v-model="form.simulated">
            <el-radio :value="0">真实网关</el-radio>
            <el-radio :value="1">仿真网关</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="首包自动注册" prop="autoRegister">
          <el-switch v-model="form.autoRegister" :active-value="1" :inactive-value="0" />
          <span class="form-tip">收到未知设备编码的数据时自动建档</span>
        </el-form-item>
        <el-form-item v-if="form.autoRegister === 1" label="默认设备类型" prop="defaultTypeId">
          <el-select v-model="form.defaultTypeId" filterable class="full-width">
            <el-option
              v-for="item in deviceTypes"
              :key="item.id"
              :label="`${item.typeName} (${item.typeCode})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="接入标识">
          <el-input v-model="form.accessKey" maxlength="64" placeholder="留空自动生成；4-64位字母数字短横线" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            placeholder="留空表示永久有效"
            class="full-width"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretVisible" title="凭证已生成" width="560px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="签名密钥仅此一次展示，关闭后无法再次查看"
        class="secret-alert"
      />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="接入标识">{{ secretInfo.accessKey }}</el-descriptions-item>
        <el-descriptions-item label="签名密钥">
          <span class="secret-text">{{ secretInfo.secret }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="上报地址">
          HTTP POST {平台地址}/api/ingest/samples 或 MQTT dcim/{{ secretInfo.accessKey }}/samples
        </el-descriptions-item>
        <el-descriptions-item label="签名方式">
          Authorization: Gateway {{ secretInfo.accessKey }}:sha256(secret + rawBody)
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Key, Plus, Refresh, Search, Switch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCredential,
  deleteCredential,
  getCredentialPage,
  resetCredentialSecret,
  updateCredential,
  updateCredentialStatus,
} from '@/api/ingestCredential'
import { getCloudRoomPage } from '@/api/cloudRoom'
import { getDeviceTypes } from '@/api/deviceType'

defineOptions({ name: 'IngestCredentialManagement' })

const loading = ref(false)
const submitting = ref(false)
const credentials = ref([])
const rooms = ref([])
const deviceTypes = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', status: null, simulated: null })
const pagination = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ gatewayName: '', cloudRoomId: null, simulated: 0, autoRegister: 0, defaultTypeId: null, accessKey: '', expireAt: null })
const secretVisible = ref(false)
const secretInfo = ref({})

const activeRooms = computed(() => rooms.value.filter((item) => item.status === 0))
const rules = {
  gatewayName: [{ required: true, message: '请输入网关名称', trigger: 'blur' }],
  cloudRoomId: [{ required: true, message: '请选择绑定机房', trigger: 'change' }],
  defaultTypeId: [{ required: true, message: '开启自动注册必须选择默认设备类型', trigger: 'change' }],
}

// 离线判定：最近活跃超 5 分钟视为疑似离线（与后端网关离线任务同口径）
function isStale(row) {
  if (!row.lastSeenAt) return false
  return new Date(row.lastSeenAt).getTime() < Date.now() - 5 * 60 * 1000
}

function roomName(id) {
  return rooms.value.find((item) => item.id === id)?.roomName || `机房 #${id}`
}

async function fetchRooms() {
  const result = await getCloudRoomPage({ current: 1, size: 100 })
  rooms.value = result.data?.records || []
}

async function fetchDeviceTypes() {
  const result = await getDeviceTypes()
  deviceTypes.value = result.data || []
}

async function fetchCredentials() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    if (filters.status !== null && filters.status !== '') params.status = filters.status
    if (filters.simulated !== null && filters.simulated !== '') params.simulated = filters.simulated
    const result = await getCredentialPage(params)
    credentials.value = result.data?.records || []
    total.value = result.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  void fetchCredentials()
}

function handleReset() {
  filters.keyword = ''
  filters.status = null
  filters.simulated = null
  pagination.current = 1
  void fetchCredentials()
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.gatewayName = row.gatewayName || ''
  form.expireAt = row.expireAt || null
  dialogVisible.value = true
}

function resetForm() {
  form.gatewayName = ''
  form.cloudRoomId = null
  form.simulated = 0
  form.autoRegister = 0
  form.defaultTypeId = null
  form.accessKey = ''
  form.expireAt = null
  formRef.value?.clearValidate()
}

function showSecret(created) {
  secretInfo.value = created
  secretVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      const result = await createCredential({
        gatewayName: form.gatewayName,
        cloudRoomId: form.cloudRoomId,
        simulated: form.simulated,
        autoRegister: form.autoRegister,
        defaultTypeId: form.autoRegister === 1 ? form.defaultTypeId : null,
        accessKey: form.accessKey || null,
        expireAt: form.expireAt || null,
      })
      ElMessage.success('凭证已创建')
      showSecret(result.data)
    } else {
      await updateCredential(editingId.value, {
        gatewayName: form.gatewayName,
        expireAt: form.expireAt || null,
      })
      ElMessage.success('凭证已更新')
    }
    dialogVisible.value = false
    await fetchCredentials()
  } catch {
    return
  } finally {
    submitting.value = false
  }
}

async function handleResetSecret(row) {
  const confirmed = await ElMessageBox.confirm(
    `确认重置网关“${row.gatewayName}”的签名密钥？旧密钥立即失效。`,
    '重置密钥确认',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  const result = await resetCredentialSecret(row.id)
  ElMessage.success('密钥已重置')
  showSecret(result.data)
}

async function handleToggleStatus(row) {
  const nextStatus = row.status === 0 ? 1 : 0
  const action = nextStatus === 0 ? '启用' : '停用'
  const confirmed = await ElMessageBox.confirm(
    `确认${action}网关“${row.gatewayName}”的接入凭证？${nextStatus === 1 ? '停用后该网关数据上报将被拒绝。' : ''}`,
    `${action}确认`,
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await updateCredentialStatus(row.id, nextStatus)
  ElMessage.success(`凭证已${action}`)
  await fetchCredentials()
}

async function handleDelete(row) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除网关“${row.gatewayName}”的接入凭证？网关侧持有的凭证立即失效，不可恢复。`,
    '删除确认',
    { type: 'error' },
  ).catch(() => false)
  if (!confirmed) return
  await deleteCredential(row.id)
  ElMessage.success('凭证已删除')
  await fetchCredentials()
}

onMounted(async () => {
  await Promise.all([fetchRooms(), fetchDeviceTypes()])
  await fetchCredentials()
})
</script>

<style scoped>
.page-shell {
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.page-header h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.filter-bar {
  padding: 14px 16px 0;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
  background: var(--color-surface);
}

.status-select {
  width: 130px;
}

.full-width {
  width: 100%;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.secret-alert {
  margin-bottom: 14px;
}

.stale {
  color: var(--el-color-danger);
}

.form-tip {
  margin-left: 10px;
  color: var(--color-text-3);
  font-size: 12px;
}

.secret-text {
  font-family: monospace;
  word-break: break-all;
}
</style>
