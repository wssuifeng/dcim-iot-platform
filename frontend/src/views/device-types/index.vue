<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>设备类型</h1>
        <p>五类设备目录、基础点位清单与告警模版绑定</p>
      </div>
      <el-button v-if="canEditType" type="primary" :icon="Plus" @click="handleCreate">
        新增设备类型
      </el-button>
    </header>

    <device-type-filter :filter="filter" @search="handleSearch" @reset="handleReset" />

    <el-table v-loading="loading" :data="filteredList" row-key="id">
      <el-table-column prop="typeName" label="类型名称" min-width="140" />
      <el-table-column prop="typeCode" label="类型编码" min-width="140" />
      <el-table-column label="基础告警模版" min-width="170">
        <template #default="{ row }">
          <span v-if="row.alarmTemplateId && templateNameById(row.alarmTemplateId)">
            {{ templateNameById(row.alarmTemplateId).templateName }}
          </span>
          <span v-else class="secondary">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" min-width="170" />
      <el-table-column label="操作" width="350" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" :icon="Grid" @click="openTemplate(row)">
            点位清单
          </el-button>
          <el-button v-if="canEditType" link type="warning" :icon="AlarmClock" @click="openBindTemplate(row)">
            绑定模版
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
    />

    <!-- 基础告警模版绑定 -->
    <el-dialog v-model="bindVisible" :title="`基础告警模版 — ${bindType?.typeName || ''}`" width="440px">
      <el-select v-model="bindTemplateId" filterable clearable placeholder="选择启用中的告警模版（空=解绑）" style="width: 100%">
        <el-option v-for="t in enabledTemplates" :key="t.id" :value="t.id" :label="`${t.templateName}（${t.templateCode}）`" />
      </el-select>
      <p class="bind-tip">
        类型绑定的基础模版是该类型全部设备的默认告警规则；改绑过的设备不受影响。
      </p>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindSaving" @click="handleBindConfirm">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { AlarmClock, Delete, Edit, Grid, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createDeviceType,
  deleteDeviceType,
  getDeviceTypes,
  updateDeviceType,
} from '@/api/deviceType'
import { bindTypeTemplate, listEnabledTemplates } from '@/api/alarmTemplate'
import DeviceTypeFilter from '@/components/device-types/DeviceTypeFilter.vue'
import DeviceTypeFormDialog from '@/components/device-types/DeviceTypeFormDialog.vue'
import PointTemplateDialog from './PointTemplateDialog.vue'

defineOptions({ name: 'DeviceTypeManagement' })

const authStore = useAuthStore()
// 设备类型目录 CRUD 仅平台管理员（后端已收紧为 PLATFORM_ADMIN）
const canEditType = computed(() => authStore.isAdmin)
const canDeleteType = computed(() => authStore.isAdmin)
// 基础点位清单：ADMIN 维护（新设备建档/自动注册的绑定来源）
const canManageTemplate = computed(() => authStore.isAdmin)
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

// 基础告警模版绑定
const alarmTemplates = ref([])
const enabledTemplates = computed(() => alarmTemplates.value.filter(t => t.status === 0))
const bindVisible = ref(false)
const bindType = ref(null)
const bindTemplateId = ref(null)
const bindSaving = ref(false)

function templateNameById(id) {
  return alarmTemplates.value.find(t => t.id === id)
}

async function fetchTemplates() {
  try {
    const res = await listEnabledTemplates()
    alarmTemplates.value = res.data || []
  } catch {
    alarmTemplates.value = []
  }
}

function openBindTemplate(row) {
  bindType.value = row
  bindTemplateId.value = row.alarmTemplateId || null
  bindVisible.value = true
}

async function handleBindConfirm() {
  bindSaving.value = true
  try {
    await bindTypeTemplate(bindType.value.id, bindTemplateId.value || null)
    ElMessage.success(bindTemplateId.value ? '基础告警模版已绑定' : '已解绑，该类型设备将无告警规则')
    bindVisible.value = false
    await fetchTypeList()
  } catch (e) {
    ElMessage.error(e.message || '绑定失败')
  } finally {
    bindSaving.value = false
  }
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
  await fetchTemplates()
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

.bind-tip {
  margin: 10px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}
</style>
