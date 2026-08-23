<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>用户与数据范围</h1>
        <p>用户角色、账号状态及后端授权范围</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
    </header>

    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="关键字">
        <el-input
          v-model="filters.keyword"
          placeholder="用户名"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="users" row-key="id">
      <el-table-column prop="username" label="用户名" min-width="150" />
      <el-table-column prop="nickname" label="昵称" min-width="150" />
      <el-table-column label="角色" width="130">
        <template #default="{ row }">
          <el-tag :type="roleType(row.role)">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="170" />
      <el-table-column label="操作" min-width="390" fixed="right">
        <template #default="{ row }">
          <template v-if="canManageTarget(row)">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" :icon="Connection" @click="openScopes(row)">
              数据范围
            </el-button>
            <el-button link type="warning" :icon="Key" @click="openPassword(row)">
              重置密码
            </el-button>
            <el-dropdown trigger="click" @command="(command) => handleCommand(command, row)">
              <el-button link type="info" :icon="MoreFilled">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="enable">启用账号</el-dropdown-item>
                  <el-dropdown-item command="disable">停用账号</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除用户</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <span v-else class="restricted">不可管理</span>
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
      @change="fetchUsers"
    />

    <user-editor-dialog
      v-model:visible="editorVisible"
      :mode="editorMode"
      :user="currentUser"
      :role-options="roleOptions"
      @confirm="saveUser"
    />
    <password-reset-dialog
      v-model:visible="passwordVisible"
      :user="currentUser"
      @confirm="savePassword"
    />
    <data-scope-dialog
      v-model:visible="scopeVisible"
      :user="currentUser"
      :caller-role="callerRole"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Connection,
  Edit,
  Key,
  MoreFilled,
  Plus,
  Refresh,
  Search,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useScopeStore } from '@/stores/scope'
import {
  createUser,
  deleteUser,
  getUserPage,
  resetUserPassword,
  updateUser,
  updateUserStatus,
} from '@/api/user'
import PasswordResetDialog from '@/components/user/PasswordResetDialog.vue'
import DataScopeDialog from './DataScopeDialog.vue'
import UserEditorDialog from './UserEditorDialog.vue'

defineOptions({ name: 'UserScopeManagement' })

const ROLE_META = Object.freeze({
  PLATFORM_ADMIN: { label: '平台管理员', type: 'danger' },
  ORG_ADMIN: { label: '组织管理员', type: 'warning' },
  ADMIN: { label: '组织管理员', type: 'warning' },
  OPERATOR: { label: '运维人员', type: 'primary' },
  VIEWER: { label: '查看者', type: 'info' },
})
const authStore = useAuthStore()
const scopeStore = useScopeStore()
const callerRole = computed(() => scopeStore.role || authStore.normalizedRole)
const roleOptions = computed(() => {
  const roles =
    callerRole.value === 'PLATFORM_ADMIN'
      ? ['PLATFORM_ADMIN', 'ORG_ADMIN', 'OPERATOR', 'VIEWER']
      : ['ORG_ADMIN', 'OPERATOR', 'VIEWER']
  return roles.map((value) => ({ value, label: ROLE_META[value].label }))
})
const loading = ref(false)
const users = ref([])
const total = ref(0)
const filters = reactive({ keyword: '' })
const pagination = reactive({ current: 1, size: 10 })
const editorVisible = ref(false)
const editorMode = ref('create')
const passwordVisible = ref(false)
const scopeVisible = ref(false)
const currentUser = ref(null)

function normalizeRole(role) {
  return role === 'ADMIN' ? 'ORG_ADMIN' : role
}

function roleLabel(role) {
  return ROLE_META[role]?.label || role
}

function roleType(role) {
  return ROLE_META[role]?.type || 'info'
}

function canManageTarget(user) {
  if (callerRole.value === 'PLATFORM_ADMIN') return true
  return callerRole.value === 'ORG_ADMIN' && normalizeRole(user.role) !== 'PLATFORM_ADMIN'
}

async function fetchUsers() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    const result = await getUserPage(params)
    users.value = result.data?.records || []
    total.value = result.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  void fetchUsers()
}

function handleReset() {
  filters.keyword = ''
  pagination.current = 1
  void fetchUsers()
}

function openCreate() {
  editorMode.value = 'create'
  currentUser.value = null
  editorVisible.value = true
}

function openEdit(row) {
  editorMode.value = 'edit'
  currentUser.value = { ...row, role: normalizeRole(row.role) }
  editorVisible.value = true
}

function openPassword(row) {
  currentUser.value = row
  passwordVisible.value = true
}

function openScopes(row) {
  currentUser.value = row
  scopeVisible.value = true
}

async function saveUser(payload) {
  try {
    if (editorMode.value === 'create') {
      await createUser(payload)
      ElMessage.success('用户已创建')
    } else {
      await updateUser(currentUser.value.id, payload)
      ElMessage.success('用户已更新')
    }
    editorVisible.value = false
    await fetchUsers()
  } catch {
    return
  }
}

async function savePassword(payload) {
  try {
    await resetUserPassword(currentUser.value.id, payload)
    passwordVisible.value = false
    ElMessage.success('密码已重置')
  } catch {
    return
  }
}

async function handleCommand(command, row) {
  if (command === 'delete') {
    const confirmed = await ElMessageBox.confirm(
      `确认删除用户“${row.username}”？`,
      '删除确认',
      { type: 'warning' },
    ).catch(() => false)
    if (!confirmed) return
    await deleteUser(row.id)
    ElMessage.success('用户已删除')
    await fetchUsers()
    return
  }
  const status = command === 'enable' ? 0 : 1
  await updateUserStatus(row.id, status)
  ElMessage.success(status === 0 ? '账号已启用' : '账号已停用')
  await fetchUsers()
}

onMounted(fetchUsers)
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

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.restricted {
  color: var(--color-text-3);
  font-size: 13px;
}
</style>
