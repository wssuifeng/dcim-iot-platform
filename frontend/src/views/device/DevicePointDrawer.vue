<template>
  <el-drawer
    :model-value="visible"
    :title="`设备点位 · ${device?.deviceName || ''}`"
    size="720px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="loadData"
  >
    <div v-if="canManage" class="point-toolbar">
      <el-select v-model="selectedPointId" filterable placeholder="选择点位" class="point-select">
        <el-option
          v-for="item in availablePoints"
          :key="item.id"
          :label="`${item.pointName} (${item.pointCode})`"
          :value="item.id"
        />
      </el-select>
      <el-button type="primary" :icon="Link" :disabled="!selectedPointId" @click="bindPoint">
        绑定
      </el-button>
      <el-button :icon="MagicStick" @click="initializeFromTemplate">从类型模板初始化</el-button>
    </div>

    <el-table v-loading="loading" :data="bindings" row-key="id">
      <el-table-column label="点位" min-width="210">
        <template #default="{ row }">
          <div>{{ pointMeta(row.pointId).pointName }}</div>
          <div class="secondary">{{ pointMeta(row.pointId).pointCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型 / 单位" min-width="150">
        <template #default="{ row }">
          {{ pointMeta(row.pointId).dataType }}
          <span v-if="pointMeta(row.pointId).unit"> · {{ pointMeta(row.pointId).unit }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link :type="row.status === 0 ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 0 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" :icon="Delete" @click="removeBinding(row)">解绑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Delete, Link, MagicStick } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPointDefinitionPage } from '@/api/pointDefinition'
import {
  bindDevicePoint,
  getDevicePointBindings,
  initializeDevicePoints,
  unbindDevicePoint,
  updateDevicePointStatus,
} from '@/api/devicePoint'

defineOptions({ name: 'DevicePointDrawer' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  device: { type: Object, default: null },
  canManage: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible'])
const loading = ref(false)
const bindings = ref([])
const points = ref([])
const selectedPointId = ref(null)
const pointMap = computed(() => Object.fromEntries(points.value.map((item) => [item.id, item])))
const availablePoints = computed(() => {
  const boundIds = new Set(bindings.value.map((item) => item.pointId))
  return points.value.filter((item) => !boundIds.has(item.id))
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
  if (!props.device?.id) return
  loading.value = true
  try {
    const [bindingResult, pointResult] = await Promise.all([
      getDevicePointBindings(props.device.id),
      getPointDefinitionPage({ current: 1, size: 100 }),
    ])
    bindings.value = bindingResult.data || []
    points.value = pointResult.data?.records || []
    selectedPointId.value = null
  } finally {
    loading.value = false
  }
}

async function bindPoint() {
  await bindDevicePoint({
    deviceId: props.device.id,
    pointId: selectedPointId.value,
    status: 0,
  })
  ElMessage.success('点位已绑定')
  await loadData()
}

async function initializeFromTemplate() {
  await initializeDevicePoints(props.device.id, props.device.typeId)
  ElMessage.success('已按设备类型模板初始化')
  await loadData()
}

async function toggleStatus(row) {
  await updateDevicePointStatus(row.id, row.status === 0 ? 1 : 0)
  ElMessage.success('点位状态已更新')
  await loadData()
}

async function removeBinding(row) {
  const confirmed = await ElMessageBox.confirm('确认解除这个设备点位绑定？', '解绑确认', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  await unbindDevicePoint(row.id)
  ElMessage.success('点位已解绑')
  await loadData()
}
</script>

<style scoped>
.point-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.point-select {
  width: 280px;
}

.secondary {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
