<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>设备类型</h1>
        <p>五类设备目录、告警规则与点位模板</p>
      </div>
      <el-button v-if="canEditType" type="primary" :icon="Plus" @click="handleCreate">
        新增设备类型
      </el-button>
    </header>

    <device-type-filter :filter="filter" @search="handleSearch" @reset="handleReset" />

    <el-table v-loading="loading" :data="filteredList" row-key="id">
      <el-table-column prop="typeName" label="类型名称" min-width="150" />
      <el-table-column prop="typeCode" label="类型编码" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="告警规则" width="110">
        <template #default="{ row }">
          <el-tag v-if="ruleCount(row)" type="warning">{{ ruleCount(row) }} 条</el-tag>
          <span v-else class="secondary">未配置</span>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" min-width="170" />
      <el-table-column label="操作" width="310" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" :icon="Grid" @click="openTemplate(row)">
            点位模板
          </el-button>
          <el-button
            v-if="canEditType"
            link
            type="primary"
            :icon="Edit"
            @click="handleEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="canEditType"
            link
            type="warning"
            :icon="Bell"
            @click="handleAlertConfig(row)"
          >
            告警
          </el-button>
          <el-button
            v-if="isOrgAdmin"
            link
            type="danger"
            :icon="Delete"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <device-type-form-dialog
      v-model:visible="formVisible"
      :mode="formMode"
      :current="currentType"
      @confirm="handleFormConfirm"
    />
    <device-type-alert-config-dialog
      v-model:visible="alertConfigVisible"
      :device-type="currentType"
      :metrics="metrics"
      @confirm="handleAlertConfigConfirm"
    />
    <point-template-dialog
      v-model:visible="templateVisible"
      :device-type="currentType"
      :can-manage="canManageTemplate"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Bell, Delete, Edit, Grid, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useScopeStore } from '@/stores/scope'
import {
  createDeviceType,
  deleteDeviceType,
  getDeviceTypes,
  updateDeviceType,
} from '@/api/deviceType'
import { getMetricPage } from '@/api/metric'
import DeviceTypeFilter from '@/components/device-types/DeviceTypeFilter.vue'
import DeviceTypeFormDialog from '@/components/device-types/DeviceTypeFormDialog.vue'
import DeviceTypeAlertConfigDialog from '@/components/device-types/DeviceTypeAlertConfigDialog.vue'
import PointTemplateDialog from './PointTemplateDialog.vue'

defineOptions({ name: 'DeviceTypeManagement' })

const authStore = useAuthStore()
const scopeStore = useScopeStore()
const effectiveRole = computed(() => scopeStore.role || authStore.normalizedRole)
const hasWriteScope = computed(() => !scopeStore.isReady || scopeStore.hasWritePermission)
const canEditType = computed(
  () => ['ORG_ADMIN', 'OPERATOR'].includes(effectiveRole.value) && hasWriteScope.value,
)
const isOrgAdmin = computed(
  () => effectiveRole.value === 'ORG_ADMIN' && hasWriteScope.value,
)
const canManageTemplate = computed(() => effectiveRole.value === 'PLATFORM_ADMIN')
const loading = ref(false)
const typeList = ref([])
const metrics = ref([])
const filter = reactive({ typeName: '', typeCode: '' })
const filteredList = computed(() =>
  typeList.value.filter((item) => {
    const nameMatched =
      !filter.typeName || item.typeName?.toLowerCase().includes(filter.typeName.toLowerCase())
    const codeMatched =
      !filter.typeCode || item.typeCode?.toLowerCase().includes(filter.typeCode.toLowerCase())
    return nameMatched && codeMatched
  }),
)
const formVisible = ref(false)
const formMode = ref('create')
const currentType = ref(null)
const alertConfigVisible = ref(false)
const templateVisible = ref(false)

function ruleCount(row) {
  return Array.isArray(row.alertRuleConfig?.rules) ? row.alertRuleConfig.rules.length : 0
}

async function fetchTypeList() {
  loading.value = true
  try {
    const result = await getDeviceTypes()
    typeList.value = result.data || []
  } finally {
    loading.value = false
  }
}

async function fetchMetrics() {
  const result = await getMetricPage({ current: 1, size: 100 })
  metrics.value = result.data?.records || []
}

function handleSearch() {
  filter.typeName = filter.typeName.trim()
  filter.typeCode = filter.typeCode.trim()
}

function handleReset() {
  filter.typeName = ''
  filter.typeCode = ''
}

function handleCreate() {
  formMode.value = 'create'
  currentType.value = null
  formVisible.value = true
}

function handleEdit(row) {
  formMode.value = 'edit'
  currentType.value = { ...row }
  formVisible.value = true
}

function handleAlertConfig(row) {
  currentType.value = { ...row }
  alertConfigVisible.value = true
}

function openTemplate(row) {
  currentType.value = row
  templateVisible.value = true
}

async function handleDelete(row) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除设备类型“${row.typeName}”？`,
    '删除确认',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await deleteDeviceType(row.id)
  ElMessage.success('设备类型已删除')
  await fetchTypeList()
}

async function handleFormConfirm(data) {
  try {
    if (formMode.value === 'create') {
      await createDeviceType(data)
      ElMessage.success('设备类型已创建')
    } else {
      await updateDeviceType(currentType.value.id, data)
      ElMessage.success('设备类型已更新')
    }
    formVisible.value = false
    await fetchTypeList()
  } catch {
    return
  }
}

function handleAlertConfigConfirm() {
  alertConfigVisible.value = false
  ElMessage.success('告警规则已更新')
  void fetchTypeList()
}

onMounted(async () => {
  await fetchTypeList().catch(() => undefined)
  if (canEditType.value) {
    await fetchMetrics().catch(() => undefined)
  }
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
  color: #303133;
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.secondary {
  color: #909399;
  font-size: 12px;
}
</style>
