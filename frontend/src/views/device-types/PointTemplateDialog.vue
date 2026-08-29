<template>
  <el-dialog
    :model-value="visible"
    :title="`基础指标清单 · ${deviceType?.typeName || ''}`"
    width="760px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="loadData"
  >
    <div v-if="canManage" class="template-toolbar">
      <el-select v-model="selectedPointId" filterable placeholder="选择指标" class="point-select">
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
      <span class="toolbar-tip">新设备建档与网关自动注册时按此清单初始化指标绑定</span>
    </div>
    <p v-else class="toolbar-tip read-only-tip">
      该类型的指标清单（新设备建档与网关自动注册时按此初始化指标绑定）——只读视图，编辑需管理员权限
    </p>

    <el-table v-loading="loading" :data="drafts" row-key="pointId" max-height="420">
      <el-table-column label="指标" min-width="220">
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
        保存清单
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
  saveDeviceTypePointTemplates,
} from '@/api/pointTemplate'

defineOptions({ name: 'PointTemplateDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  deviceType: { type: Object, default: null },
  canManage: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible'])
const loading = ref(false)
const saving = ref(false)
const drafts = ref([])
const points = ref([])
const selectedPointId = ref(null)
const pointMap = computed(() => Object.fromEntries(points.value.map((item) => [item.id, item])))
const availablePoints = computed(() => {
  const selectedIds = new Set(drafts.value.map((item) => item.pointId))
  return points.value.filter((item) => !selectedIds.has(item.id))
})

function pointMeta(id) {
  return pointMap.value[id] || {
    pointName: `指标 #${id}`,
    pointCode: '',
    dataType: '-',
    unit: '',
  }
}

async function loadData() {
  if (!props.deviceType?.id) return
  loading.value = true
  try {
    const [templateResult, pointResult] = await Promise.all([
      getDeviceTypePointTemplates(props.deviceType.id),
      getPointDefinitionPage({ current: 1, size: 100 }),
    ])
    drafts.value = (templateResult.data || []).map((item) => ({
      pointId: item.pointId,
      isRequired: item.isRequired ?? 0,
      sortOrder: item.sortOrder ?? 0,
    }))
    points.value = pointResult.data?.records || []
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
    await saveDeviceTypePointTemplates(props.deviceType.id, drafts.value)
    ElMessage.success('指标清单已保存')
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

.toolbar-tip {
  margin: 0 0 12px;
  color: var(--color-text-3);
  font-size: 12px;
}

.read-only-tip {
  margin-top: 0;
}
</style>
