<template>
  <el-dialog
    :model-value="visible"
    :title="`点位模板 · ${deviceType?.typeName || ''}`"
    width="760px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="loadData"
  >
    <div v-if="canManage" class="template-toolbar">
      <el-select v-model="selectedPointId" filterable placeholder="选择点位" class="point-select">
        <el-option
          v-for="item in availablePoints"
          :key="item.id"
          :label="`${item.pointName} (${item.pointCode})`"
          :value="item.id"
        />
      </el-select>
      <el-button type="primary" :icon="Plus" :disabled="!selectedPointId" @click="addPoint">
        添加
      </el-button>
    </div>

    <el-table v-loading="loading" :data="drafts" row-key="pointId" max-height="420">
      <el-table-column label="点位" min-width="220">
        <template #default="{ row }">
          <div>{{ pointMeta(row.pointId).pointName }}</div>
          <div class="secondary">{{ pointMeta(row.pointId).pointCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型 / 单位" min-width="160">
        <template #default="{ row }">
          {{ pointMeta(row.pointId).dataType }}
          <span v-if="pointMeta(row.pointId).unit"> · {{ pointMeta(row.pointId).unit }}</span>
        </template>
      </el-table-column>
      <el-table-column label="必需" width="100">
        <template #default="{ row }">
          <el-checkbox
            :model-value="row.isRequired === 1"
            :disabled="!canManage"
            @change="(checked) => (row.isRequired = checked ? 1 : 0)"
          />
        </template>
      </el-table-column>
      <el-table-column label="排序" width="120">
        <template #default="{ row }">
          <el-input-number
            v-model="row.sortOrder"
            :min="0"
            :max="999"
            :disabled="!canManage"
            controls-position="right"
            class="sort-input"
          />
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" :icon="Delete" @click="removePoint(row.pointId)">
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
      <el-button v-if="canManage" type="primary" :loading="saving" @click="saveTemplates">
        保存模板
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getPointDefinitionPage } from '@/api/pointDefinition'
import {
  getDeviceTypePointTemplates,
  getPlatformDeviceTypePointTemplates,
  saveDeviceTypePointTemplates,
  savePlatformDeviceTypePointTemplates,
} from '@/api/pointTemplate'

defineOptions({ name: 'PointTemplateDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  deviceType: { type: Object, default: null },
  canManage: { type: Boolean, default: false },
  mode: { type: String, default: 'org' }, // 'platform' 平台级模板 / 'org' 组织级模板
})
const emit = defineEmits(['update:visible'])
const loading = ref(false)
const saving = ref(false)
const drafts = ref([])
const points = ref([])
const selectedPointId = ref(null)
const isPlatformMode = computed(() => props.mode === 'platform')
const pointMap = computed(() => Object.fromEntries(points.value.map((item) => [item.id, item])))
const availablePoints = computed(() => {
  const selectedIds = new Set(drafts.value.map((item) => item.pointId))
  return points.value.filter((item) => !selectedIds.has(item.id))
})

function pointMeta(id) {
  return pointMap.value[id] || {
    pointName: `点位 #${id}`,
    pointCode: '',
    dataType: '-',
    unit: '',
  }
}

async function loadData() {
  if (!props.deviceType?.id) return
  loading.value = true
  try {
    const templateApi = isPlatformMode.value
      ? getPlatformDeviceTypePointTemplates
      : getDeviceTypePointTemplates
    const [templateResult, pointResult] = await Promise.all([
      templateApi(props.deviceType.id),
      getPointDefinitionPage({ current: 1, size: 100 }),
    ])
    drafts.value = (templateResult.data || []).map((item) => ({
      pointId: item.pointId,
      isRequired: item.isRequired ?? 0,
      sortOrder: item.sortOrder ?? 0,
    }))
    // 平台级模板只允许引用平台级点位（organization_id=0）
    const allPoints = pointResult.data?.records || []
    points.value = isPlatformMode.value
      ? allPoints.filter((item) => Number(item.organizationId) === 0)
      : allPoints
    selectedPointId.value = null
  } finally {
    loading.value = false
  }
}

function addPoint() {
  drafts.value.push({
    pointId: selectedPointId.value,
    isRequired: 0,
    sortOrder: drafts.value.length + 1,
  })
  selectedPointId.value = null
}

function removePoint(pointId) {
  drafts.value = drafts.value.filter((item) => item.pointId !== pointId)
}

async function saveTemplates() {
  saving.value = true
  try {
    if (isPlatformMode.value) {
      await savePlatformDeviceTypePointTemplates(props.deviceType.id, drafts.value)
    } else {
      await saveDeviceTypePointTemplates(props.deviceType.id, drafts.value)
    }
    ElMessage.success('点位模板已保存')
    emit('update:visible', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.template-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.point-select {
  width: 300px;
}

.sort-input {
  width: 96px;
}

.secondary {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
