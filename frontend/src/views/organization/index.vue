<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>组织管理</h1>
        <p>平台组织及生命周期状态</p>
      </div>
      <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">
        新增组织
      </el-button>
    </header>

    <el-form :inline="true" :model="filters" class="filter-bar">
      <el-form-item label="关键字">
        <el-input
          v-model="filters.keyword"
          placeholder="组织名称"
          clearable
          @keyup.enter="handleSearch"
        />
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

    <el-table v-loading="loading" :data="organizations" row-key="id">
      <el-table-column prop="orgName" label="组织名称" min-width="180" />
      <el-table-column prop="orgCode" label="组织编码" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="170" />
      <el-table-column v-if="canManage" label="操作" width="190" fixed="right">
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
      @change="fetchOrganizations"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增组织' : '编辑组织'"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="组织名称" prop="orgName">
          <el-input v-model="form.orgName" maxlength="100" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="组织编码" prop="orgCode">
          <el-input v-model="form.orgCode" maxlength="50" placeholder="例如 DC-EAST" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
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
import { Edit, Plus, Refresh, Search, Switch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createOrganization,
  getOrganizationPage,
  updateOrganization,
  updateOrganizationStatus,
} from '@/api/organization'

defineOptions({ name: 'OrganizationManagement' })

const authStore = useAuthStore()
const canManage = computed(() => authStore.normalizedRole === 'PLATFORM_ADMIN')
const loading = ref(false)
const submitting = ref(false)
const organizations = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', status: null })
const pagination = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({ orgName: '', orgCode: '', description: '' })
const rules = {
  orgName: [{ required: true, message: '请输入组织名称', trigger: 'blur' }],
  orgCode: [
    { required: true, message: '请输入组织编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线和短横线', trigger: 'blur' },
  ],
}

async function fetchOrganizations() {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filters.keyword.trim()) params.keyword = filters.keyword.trim()
    if (filters.status !== null && filters.status !== '') params.status = filters.status
    const result = await getOrganizationPage(params)
    organizations.value = result.data?.records || []
    total.value = result.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  void fetchOrganizations()
}

function handleReset() {
  filters.keyword = ''
  filters.status = null
  pagination.current = 1
  void fetchOrganizations()
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
  form.orgName = row.orgName || ''
  form.orgCode = row.orgCode || ''
  form.description = row.description || ''
  dialogVisible.value = true
}

function resetForm() {
  form.orgName = ''
  form.orgCode = ''
  form.description = ''
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createOrganization({ ...form })
      ElMessage.success('组织已创建')
    } else {
      await updateOrganization(editingId.value, {
        orgName: form.orgName,
        description: form.description,
      })
      ElMessage.success('组织已更新')
    }
    dialogVisible.value = false
    await fetchOrganizations()
  } catch {
    return
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const nextStatus = row.status === 0 ? 1 : 0
  const action = nextStatus === 0 ? '启用' : '停用'
  const confirmed = await ElMessageBox.confirm(
    `确认${action}组织“${row.orgName}”？`,
    `${action}确认`,
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await updateOrganizationStatus(row.id, nextStatus)
  ElMessage.success(`组织已${action}`)
  await fetchOrganizations()
}

onMounted(fetchOrganizations)
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
  color: #303133;
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.filter-bar {
  padding: 14px 16px 0;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
  background: #ffffff;
}

.status-select {
  width: 140px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
