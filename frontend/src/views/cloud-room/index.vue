<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>机房管理</h1>
        <p>机房资源与运行状态</p>
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

    <el-table v-loading="loading" :data="rooms" row-key="id">
      <el-table-column prop="roomName" label="机房名称" min-width="170" />
      <el-table-column prop="roomCode" label="机房编码" min-width="140" />
      <el-table-column prop="location" label="位置" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" min-width="170" />
      <el-table-column v-if="canManage" label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
          <el-button
            link
            :type="row.status === 0 ? 'warning' : 'success'"
            :icon="Switch"
            @click="toggleStatus(row)"
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
          <el-input v-model="form.roomCode" maxlength="50" placeholder="例如 ROOM-A01" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, Search, Switch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createCloudRoom,
  deleteCloudRoom,
  getCloudRoomPage,
  updateCloudRoom,
  updateCloudRoomStatus,
} from '@/api/cloudRoom'

defineOptions({ name: 'CloudRoomManagement' })

const authStore = useAuthStore()
const canManage = computed(() => authStore.isAdmin)
const loading = ref(false)
const submitting = ref(false)
const rooms = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', status: null })
const pagination = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ roomName: '', roomCode: '', location: '' })
const rules = {
  roomName: [{ required: true, message: '请输入机房名称', trigger: 'blur' }],
  roomCode: [
    { required: true, message: '请输入机房编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线和短横线', trigger: 'blur' },
  ],
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
  } finally {
    loading.value = false
  }
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
  dialogVisible.value = true
}

function resetForm() {
  form.roomName = ''
  form.roomCode = ''
  form.location = ''
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createCloudRoom({ ...form })
      ElMessage.success('机房已创建')
    } else {
      await updateCloudRoom(editingId.value, {
        roomName: form.roomName,
        location: form.location,
      })
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

async function handleDelete(row) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除机房“${row.roomName}”？机房下存在设备或网关凭证时将拒绝删除。`,
    '删除确认',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await deleteCloudRoom(row.id)
  ElMessage.success('机房已删除')
  await fetchRooms()
}

async function toggleStatus(row) {
  const nextStatus = row.status === 0 ? 1 : 0
  const action = nextStatus === 0 ? '启用' : '停用'
  const confirmed = await ElMessageBox.confirm(
    `确认${action}机房“${row.roomName}”？`,
    `${action}确认`,
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await updateCloudRoomStatus(row.id, nextStatus)
  ElMessage.success(`机房已${action}`)
  await fetchRooms()
}

onMounted(() => {
  void fetchRooms()
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
</style>
