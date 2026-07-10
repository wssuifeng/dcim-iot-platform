<template>
  <el-dialog
    :model-value="visible"
    :title="`数据范围 · ${user?.username || ''}`"
    width="780px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="loadDialogData"
  >
    <el-form :model="form" label-width="90px" class="assignment-form">
      <el-form-item label="范围类型">
        <el-select v-model="form.scopeType" class="field" @change="handleScopeTypeChange">
          <el-option
            v-for="item in scopeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.scopeType !== 'PLATFORM'" label="组织">
        <el-select
          v-model="form.organizationId"
          class="field"
          filterable
          @change="handleOrganizationChange"
        >
          <el-option
            v-for="item in activeOrganizations"
            :key="item.id"
            :label="item.orgName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showsRoomSelector" label="机房">
        <el-select v-model="form.cloudRoomId" class="field" filterable clearable>
          <el-option
            v-for="item in availableRooms"
            :key="item.id"
            :label="item.roomName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Plus" :loading="submitting" @click="assignScope">
          添加范围
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="assignments" row-key="id" max-height="360">
      <el-table-column label="范围类型" width="120">
        <template #default="{ row }">
          <el-tag :type="scopeTagType(row.scopeType)">{{ scopeLabel(row.scopeType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="组织" min-width="180">
        <template #default="{ row }">{{ organizationName(row.organizationId) }}</template>
      </el-table-column>
      <el-table-column label="机房" min-width="180">
        <template #default="{ row }">{{ roomName(row.cloudRoomId) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="分配时间" min-width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" :icon="Delete" @click="removeScope(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrganizationPage } from '@/api/organization'
import { getCloudRoomPage } from '@/api/cloudRoom'
import {
  assignUserDataScope,
  getUserDataScopes,
  removeUserDataScope,
} from '@/api/user'

defineOptions({ name: 'DataScopeDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  user: { type: Object, default: null },
  callerRole: { type: String, default: '' },
})
const emit = defineEmits(['update:visible'])
const loading = ref(false)
const submitting = ref(false)
const assignments = ref([])
const organizations = ref([])
const rooms = ref([])
const form = reactive({ scopeType: 'ORG_ALL', organizationId: null, cloudRoomId: null })
const scopeOptions = computed(() => {
  const options = [
    { value: 'ORG_ALL', label: '组织全部机房' },
    { value: 'ROOMS', label: '指定机房' },
    { value: 'READ_ONLY', label: '只读范围' },
  ]
  if (props.callerRole === 'PLATFORM_ADMIN') {
    options.unshift({ value: 'PLATFORM', label: '平台范围' })
  }
  return options
})
const activeOrganizations = computed(() => organizations.value.filter((item) => item.status === 0))
const showsRoomSelector = computed(
  () => form.scopeType === 'ROOMS' || form.scopeType === 'READ_ONLY',
)
const availableRooms = computed(() =>
  rooms.value.filter(
    (item) => item.status === 0 && item.organizationId === form.organizationId,
  ),
)

async function loadDialogData() {
  if (!props.user?.id) return
  loading.value = true
  try {
    const [assignmentResult, organizationResult, roomResult] = await Promise.all([
      getUserDataScopes(props.user.id),
      getOrganizationPage({ current: 1, size: 100 }),
      getCloudRoomPage({ current: 1, size: 100 }),
    ])
    assignments.value = assignmentResult.data || []
    organizations.value = organizationResult.data?.records || []
    rooms.value = roomResult.data?.records || []
    resetForm()
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.scopeType = scopeOptions.value[0]?.value || 'ORG_ALL'
  form.organizationId = activeOrganizations.value.length === 1 ? activeOrganizations.value[0].id : null
  form.cloudRoomId = null
}

function handleScopeTypeChange() {
  form.cloudRoomId = null
  if (form.scopeType === 'PLATFORM') form.organizationId = null
}

function handleOrganizationChange() {
  form.cloudRoomId = null
}

function organizationName(id) {
  if (!id) return '全部平台组织'
  return organizations.value.find((item) => item.id === id)?.orgName || `组织 #${id}`
}

function roomName(id) {
  if (!id) return '全部机房'
  return rooms.value.find((item) => item.id === id)?.roomName || `机房 #${id}`
}

function scopeLabel(type) {
  return {
    PLATFORM: '平台',
    ORG_ALL: '组织全部',
    ROOMS: '指定机房',
    READ_ONLY: '只读',
  }[type] || type
}

function scopeTagType(type) {
  return { PLATFORM: 'danger', ORG_ALL: 'primary', ROOMS: 'success', READ_ONLY: 'info' }[type]
}

async function assignScope() {
  if (form.scopeType !== 'PLATFORM' && !form.organizationId) {
    ElMessage.warning('请选择组织')
    return
  }
  if (form.scopeType === 'ROOMS' && !form.cloudRoomId) {
    ElMessage.warning('请选择机房')
    return
  }
  submitting.value = true
  try {
    const payload = { scopeType: form.scopeType }
    if (form.scopeType !== 'PLATFORM') payload.organizationId = form.organizationId
    if (showsRoomSelector.value && form.cloudRoomId) payload.cloudRoomId = form.cloudRoomId
    await assignUserDataScope(props.user.id, payload)
    ElMessage.success('数据范围已添加')
    await loadDialogData()
  } catch {
    return
  } finally {
    submitting.value = false
  }
}

async function removeScope(row) {
  const confirmed = await ElMessageBox.confirm('确认移除这条数据范围？', '移除确认', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  await removeUserDataScope(props.user.id, row.id)
  ElMessage.success('数据范围已移除')
  await loadDialogData()
}
</script>

<style scoped>
.assignment-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
  padding: 14px 14px 0;
  margin-bottom: 18px;
  border: 1px solid #ebeef5;
}

.field {
  width: 100%;
}
</style>
