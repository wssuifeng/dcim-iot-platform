<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>机房管理</h1>
        <p>机房资源与网关接入（创建机房即初始化该机房接入凭证，机房与凭证合一）</p>
      </div>
      <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">
        新增机房
      </el-button>
    </header>

    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="关键字">
        <el-input v-model="filters.keyword" placeholder="机房名称" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" placeholder="全部" clearable class="status-select">
          <el-option label="正常" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 机房卡片网格（默认按未处理告警降序，点击进入机房工作台） -->
    <div v-loading="loading" class="room-card-grid">
      <div
        v-for="room in sortedRooms"
        :key="room.id"
        :class="['room-card', `room-card--${roomCardHealth(room)}`]"
        @click="goWorkspace(room)"
      >
        <span v-if="roomTriggeredAlarms[room.id] > 0" class="room-card-badge num">{{ roomTriggeredAlarms[room.id] }}</span>
        <div class="room-card-head">
          <div class="room-card-title">
            <span class="room-card-name">{{ room.roomName }}</span>
            <span class="room-card-code num">{{ room.roomCode }}</span>
          </div>
          <span class="room-card-gateway">
            <state-dot :state="gatewayDotState(room)" />
            <span class="room-card-gateway-text">{{ gatewayStateLabel(room) }}</span>
          </span>
        </div>
        <div class="room-card-ak" v-if="credentialOf(room.id)">
          <span class="num">{{ credentialOf(room.id).accessKey }}</span>
          <el-icon class="copy-icon" @click.stop="copyText(credentialOf(room.id).accessKey)"><CopyDocument /></el-icon>
        </div>
        <div class="room-card-ak" v-else><span class="ak-empty">未初始化</span></div>
        <div class="room-card-meta">
          <span class="meta-item">
            <span class="meta-label">最近活跃</span>
            <span class="num meta-value">{{ credentialOf(room.id)?.lastSeenAt || '从未上报' }}</span>
          </span>
          <span class="meta-item">
            <span class="meta-label">自动注册</span>
            <span class="meta-value">
              <el-tag v-if="credentialOf(room.id)?.autoRegister === 1" type="success" size="small">开启</el-tag>
              <el-tag v-else type="info" size="small">关闭</el-tag>
            </span>
          </span>
          <span class="meta-item">
            <span class="meta-label">机房状态</span>
            <span class="meta-value">
              <el-tag :type="room.status === 0 ? 'success' : 'info'" size="small">
                {{ room.status === 0 ? '正常' : '停用' }}
              </el-tag>
            </span>
          </span>
          <span class="meta-item meta-item-wide">
            <span class="meta-label">位置</span>
            <span class="meta-value meta-wrap">{{ room.location || '未设置' }}</span>
          </span>
        </div>
        <div v-if="canManage" class="room-card-actions" @click.stop>
          <el-button link type="primary" :icon="Edit" @click="openEdit(room)">编辑</el-button>
          <el-button link type="warning" :icon="Key" @click="handleResetSecret(room)">重置密钥</el-button>
          <el-button
            link
            :type="room.status === 0 ? 'warning' : 'success'"
            :icon="Switch"
            @click="toggleStatus(room)"
          >
            {{ room.status === 0 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(room)">删除</el-button>
        </div>
      </div>
      <el-empty v-if="!loading && rooms.length === 0" description="暂无机房" class="grid-empty" />
    </div>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @change="fetchRooms"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增机房' : '编辑机房'"
      width="540px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="机房名称" prop="roomName">
          <el-input v-model="form.roomName" maxlength="100" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="机房编码" prop="roomCode">
          <el-input v-model="form.roomCode" maxlength="50" placeholder="例如 B01" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" maxlength="200" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="自动注册" prop="autoRegister">
          <el-switch v-model="form.autoRegister" :active-value="1" :inactive-value="0" />
          <span class="form-tip">未知设备编码首包自动建档（类型优先按点位推断）</span>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit' && form.autoRegister === 1" label="默认类型" prop="defaultTypeId">
          <el-select v-model="form.defaultTypeId" filterable clearable class="full-width" placeholder="点位推断失败时兜底">
            <el-option
              v-for="item in deviceTypes"
              :key="item.id"
              :label="`${item.typeName} (${item.typeCode})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretVisible" title="机房接入凭证" width="560px" :close-on-click-modal="false">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="SecretKey 仅此一次展示，关闭后无法再次查看"
        class="secret-alert"
      />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="AccessKey">{{ secretInfo.accessKey }}</el-descriptions-item>
        <el-descriptions-item label="SecretKey">
          <span class="secret-text">{{ secretInfo.secret }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="网关侧配置（仅三样）">
          平台地址 + AccessKey + SecretKey
        </el-descriptions-item>
        <el-descriptions-item label="数据上报">
          POST {平台地址}/api/ingest/samples，头 Authorization: Gateway {AccessKey}:sha256(SecretKey + 报文)
        </el-descriptions-item>
        <el-descriptions-item label="心跳保活">
          POST {平台地址}/api/ingest/heartbeat（同签名方式）
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
import { useRouter } from 'vue-router'
import { CopyDocument, Delete, Edit, Key, Plus, Refresh, Search, Switch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getDeviceTypes } from '@/api/deviceType'
import { getAlarmEventPage } from '@/api/alarmEvent'
import {
  createCloudRoom,
  deleteCloudRoom,
  getCloudRoomPage,
  getRoomCredential,
  resetRoomSecret,
  updateCloudRoom,
  updateCloudRoomStatus,
  updateRoomCredential,
} from '@/api/cloudRoom'

defineOptions({ name: 'CloudRoomManagement' })

const authStore = useAuthStore()
const router = useRouter()
const canManage = computed(() => authStore.role === 'ADMIN')
const loading = ref(false)
const submitting = ref(false)
const rooms = ref([])
const credentials = ref({})
const deviceTypes = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', status: null })
const pagination = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ roomName: '', roomCode: '', location: '', autoRegister: 1, defaultTypeId: null })
const secretVisible = ref(false)
const secretInfo = ref({})

const rules = {
  roomName: [{ required: true, message: '请输入机房名称', trigger: 'blur' }],
  roomCode: [
    { required: true, message: '请输入机房编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线和短横线', trigger: 'blur' },
  ],
}

function credentialOf(roomId) {
  return credentials.value[roomId]
}

// 每机房未处理告警数（卡片排序主指标与角标）
const roomTriggeredAlarms = ref({})

// 卡片排序：未处理告警降序 → 网关离线 → 停用靠后（用户决策）
const sortedRooms = computed(() =>
  [...rooms.value].sort((a, b) => {
    const at = roomTriggeredAlarms.value[a.id] || 0
    const bt = roomTriggeredAlarms.value[b.id] || 0
    if (bt !== at) return bt - at
    const aDown = gatewayStateLabel(a) === '离线' ? 1 : 0
    const bDown = gatewayStateLabel(b) === '离线' ? 1 : 0
    if (aDown !== bDown) return bDown - aDown
    return (a.status === 1 ? 1 : 0) - (b.status === 1 ? 1 : 0)
  }),
)

// 卡片健康色条：停用灰 / 网关离线红 / 有告警黄 / 正常绿（口径同仪表盘）
function roomCardHealth(room) {
  if (room.status === 1) return 'idle'
  const label = gatewayStateLabel(room)
  if (label === '离线') return 'down'
  if (label === '在线') return (roomTriggeredAlarms.value[room.id] || 0) > 0 ? 'warn' : 'ok'
  return 'idle'
}

function goWorkspace(room) {
  router.push({ name: 'room-overview', params: { roomId: room.id } })
}

function gatewayStateLabel(room) {
  const cred = credentialOf(room.id)
  if (!cred) return '未初始化'
  if (cred.status !== 0) return '已停用'
  if (!cred.lastSeenAt) return '从未上线'
  return new Date(cred.lastSeenAt).getTime() > Date.now() - 5 * 60 * 1000 ? '在线' : '离线'
}

// StateDot 语义映射：在线/离线/从未上线/停用(灰)
function gatewayDotState(room) {
  const label = gatewayStateLabel(room)
  return { 在线: 'online', 离线: 'offline', 从未上线: 'stale', 已停用: 'stale', 未初始化: 'stale' }[label] || 'stale'
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}

async function fetchRooms() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    if (filters.status !== null && filters.status !== '') params.status = filters.status
    const result = await getCloudRoomPage(params)
    rooms.value = result.data?.records || []
    total.value = result.data?.total || 0
    // 拉取各机房凭证视图（合一模型）
    const credResults = await Promise.all(rooms.value.map((r) =>
      getRoomCredential(r.id).catch(() => null)))
    const credMap = {}
    rooms.value.forEach((room, i) => {
      if (credResults[i]?.data) credMap[room.id] = credResults[i].data
    })
    credentials.value = credMap
    // 各机房未处理告警数（排序+角标）
    const alarmResults = await Promise.all(rooms.value.map((r) =>
      getAlarmEventPage({ current: 1, size: 1, cloudRoomId: r.id, state: 'TRIGGERED' }).catch(() => null)))
    const alarmMap = {}
    rooms.value.forEach((room, i) => {
      alarmMap[room.id] = Number(alarmResults[i]?.data?.total || 0)
    })
    roomTriggeredAlarms.value = alarmMap
  } finally {
    loading.value = false
  }
}

async function fetchDeviceTypes() {
  const result = await getDeviceTypes()
  deviceTypes.value = result.data || []
}

function handleSearch() {
  pagination.current = 1
  void fetchRooms()
}

function handleReset() {
  filters.keyword = ''
  filters.status = null
  pagination.current = 1
  void fetchRooms()
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.roomName = row.roomName || ''
  form.roomCode = row.roomCode || ''
  form.location = row.location || ''
  const cred = credentialOf(row.id)
  form.autoRegister = cred?.autoRegister ?? 1
  form.defaultTypeId = cred?.defaultTypeId ?? null
  dialogVisible.value = true
}

function resetForm() {
  form.roomName = ''
  form.roomCode = ''
  form.location = ''
  form.autoRegister = 1
  form.defaultTypeId = null
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
      const result = await createCloudRoom({
        roomName: form.roomName,
        roomCode: form.roomCode,
        location: form.location,
      })
      ElMessage.success('机房已创建，接入凭证已初始化')
      showSecret(result.data)
    } else {
      await updateCloudRoom(editingId.value, {
        roomName: form.roomName,
        location: form.location,
      })
      // 凭证自动注册配置随编辑保存
      await updateRoomCredential(editingId.value, {
        autoRegister: form.autoRegister,
        defaultTypeId: form.autoRegister === 1 ? form.defaultTypeId : null,
      }).catch(() => null)
      ElMessage.success('机房已更新')
    }
    dialogVisible.value = false
    await fetchRooms()
  } catch {
    return
  } finally {
    submitting.value = false
  }
}

async function handleResetSecret(row) {
  const confirmed = await ElMessageBox.confirm(
    `确认重置机房“${row.roomName}”的接入密钥？网关侧旧密钥立即失效。`,
    '重置密钥确认',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  const result = await resetRoomSecret(row.id)
  ElMessage.success('密钥已重置')
  showSecret(result.data)
}

async function toggleStatus(row) {
  const nextStatus = row.status === 0 ? 1 : 0
  const action = nextStatus === 0 ? '启用' : '停用'
  const confirmed = await ElMessageBox.confirm(
    `确认${action}机房“${row.roomName}”？${nextStatus === 1 ? '停用后该机房网关数据上报将被拒绝。' : ''}`,
    `${action}确认`,
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await updateCloudRoomStatus(row.id, nextStatus)
  ElMessage.success(`机房已${action}（接入凭证已联动）`)
  await fetchRooms()
}

async function handleDelete(row) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除机房“${row.roomName}”？接入凭证将级联删除；机房下存在设备时将拒绝删除。`,
    '删除确认',
    { type: 'error' },
  ).catch(() => false)
  if (!confirmed) return
  await deleteCloudRoom(row.id)
  ElMessage.success('机房已删除')
  await fetchRooms()
}

onMounted(async () => {
  await Promise.all([fetchRooms(), fetchDeviceTypes()])
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
  border: 1px solid var(--color-border);
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

.secret-text {
  font-family: monospace;
  word-break: break-all;
}

.form-tip {
  margin-left: 10px;
  color: var(--color-text-3);
  font-size: 12px;
}

/* ===== 机房卡片网格（卡片式表格，视觉同仪表盘机房卡） ===== */
.room-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
  min-height: 120px;
}

.grid-empty {
  grid-column: 1 / -1;
}

.room-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.room-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.room-card--ok { border-left-color: var(--color-success); }
.room-card--warn { border-left-color: var(--color-warning); }
.room-card--down { border-left-color: var(--color-danger); }
.room-card--idle { border-left-color: var(--color-text-3); }

/* 未处理告警角标 */
.room-card-badge {
  position: absolute;
  top: -8px;
  right: 10px;
  min-width: 22px;
  padding: 1px 7px;
  border-radius: 11px;
  background: var(--color-danger);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.room-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.room-card-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.room-card-name {
  color: var(--color-text);
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-card-code {
  color: var(--color-text-3);
  font-size: 11px;
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

/* AccessKey 行：等宽 + 悬停复制 */
.room-card-ak {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  background: var(--color-surface-2);
  border-radius: var(--radius-sm);
  color: var(--color-text-2);
  font-size: 12px;
}

.room-card-ak .num {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ak-empty {
  color: var(--color-text-3);
}

.copy-icon {
  cursor: pointer;
  color: var(--color-text-3);
  font-size: 14px;
  flex-shrink: 0;
}

.copy-icon:hover {
  color: var(--color-primary);
}

.room-card-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 14px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.meta-item-wide {
  grid-column: 1 / -1;
}

.meta-label {
  color: var(--color-text-3);
  font-size: 11px;
}

.meta-value {
  color: var(--color-text-2);
  font-size: 12px;
}

/* 位置长文本换行显示（卡片不截断） */
.meta-wrap {
  word-break: break-word;
  white-space: normal;
  line-height: 1.5;
}

.room-card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 2px;
  border-top: 1px solid var(--color-border-light);
  padding-top: 8px;
}
</style>
