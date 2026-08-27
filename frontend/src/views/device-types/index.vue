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
            v-if="canDeleteType"
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
    <point-template-dialog
      v-model:visible="templateVisible"
      :device-type="currentType"
      :can-manage="canManageTemplate"
      :mode="templateMode"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Grid, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createDeviceType,
  deleteDeviceType,
  getDeviceTypes,
  updateDeviceType,
} from '@/api/deviceType'
import DeviceTypeFilter from '@/components/device-types/DeviceTypeFilter.vue'
import DeviceTypeFormDialog from '@/components/device-types/DeviceTypeFormDialog.vue'
import PointTemplateDialog from './PointTemplateDialog.vue'

defineOptions({ name: 'DeviceTypeManagement' })

const authStore = useAuthStore()
// 设备类型目录 CRUD 仅平台管理员（后端已收紧为 PLATFORM_ADMIN）
const canEditType = computed(() => authStore.isAdmin)
const canDeleteType = computed(() => authStore.isAdmin)
// 点位模板：平台管理员维护平台级模板，组织管理员维护本组织模板
const canManageTemplate = computed(
  () => authStore.isAdmin,
)
const templateMode = computed(() => 'platform')
const loading = ref(false)
const typeList = ref([])
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
const templateVisible = ref(false)

async function fetchTypeList() {
  loading.value = true
  try {
    const result = await getDeviceTypes()
    typeList.value = result.data || []
  } finally {
    loading.value = false
  }
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

onMounted(async () => {
  await fetchTypeList().catch(() => undefined)
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

.secondary {
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
