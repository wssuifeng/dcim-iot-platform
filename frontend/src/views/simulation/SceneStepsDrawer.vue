<template>
  <el-drawer
    :model-value="visible"
    :title="`场景步骤 · ${scene?.sceneName || ''}`"
    size="min(760px, 100%)"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="loadSteps"
  >
    <el-form v-if="canManage" :model="form" inline class="step-form">
      <el-form-item label="顺序">
        <el-input-number v-model="form.stepOrder" :min="1" :max="999" controls-position="right" />
      </el-form-item>
      <el-form-item label="动作">
        <el-select v-model="form.action" class="action-select">
          <el-option label="写入采样" value="WRITE_SAMPLE" />
          <el-option label="延时" value="SLEEP" />
        </el-select>
      </el-form-item>
      <el-form-item label="点位">
        <el-select v-model="form.pointId" filterable class="point-select">
          <el-option
            v-for="item in points"
            :key="item.id"
            :label="`${item.pointName} (${item.pointCode})`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="设备">
        <el-select v-model="form.targetDeviceId" clearable filterable class="device-select">
          <el-option v-for="item in availableDevices" :key="item.id" :label="item.deviceName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.action === 'WRITE_SAMPLE'" label="采样值">
        <el-input-number v-model="form.sampleValue" :precision="3" controls-position="right" />
      </el-form-item>
      <el-form-item label="延迟(ms)">
        <el-input-number v-model="form.delayMs" :min="0" :max="600000" controls-position="right" />
      </el-form-item>
      <el-form-item class="step-description" label="描述">
        <el-input v-model="form.description" maxlength="200" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Plus" :loading="submitting" @click="addStep">添加步骤</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="steps" row-key="id">
      <el-table-column prop="stepOrder" label="#" width="60" />
      <el-table-column label="动作" width="110">
        <template #default="{ row }">
          <el-tag :type="row.action === 'SLEEP' ? 'info' : 'primary'">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="点位" min-width="170">
        <template #default="{ row }">{{ pointLabel(row.pointId) }}</template>
      </el-table-column>
      <el-table-column label="设备" min-width="140">
        <template #default="{ row }">{{ deviceLabel(row.targetDeviceId) }}</template>
      </el-table-column>
      <el-table-column prop="sampleValue" label="采样值" width="100" />
      <el-table-column prop="delayMs" label="延迟(ms)" width="100" />
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column v-if="canManage" label="操作" width="80">
        <template #default="{ row }">
          <el-button link type="danger" :icon="Delete" @click="removeStep(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addSimulationSceneStep,
  deleteSimulationSceneStep,
  getSimulationSceneSteps,
} from '@/api/simulation'

defineOptions({ name: 'SimulationSceneStepsDrawer' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  scene: { type: Object, default: null },
  points: { type: Array, default: () => [] },
  devices: { type: Array, default: () => [] },
  canManage: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible'])
const loading = ref(false)
const submitting = ref(false)
const steps = ref([])
const form = reactive({
  stepOrder: 1,
  action: 'WRITE_SAMPLE',
  pointId: null,
  targetDeviceId: null,
  sampleValue: 0,
  delayMs: 0,
  description: '',
})
const availableDevices = computed(() =>
  props.devices.filter(
    (item) =>
      (!props.scene?.organizationId || item.organizationId === props.scene.organizationId) &&
      (!props.scene?.cloudRoomId || item.cloudRoomId === props.scene.cloudRoomId),
  ),
)

async function loadSteps() {
  if (!props.scene?.id) return
  loading.value = true
  try {
    const result = await getSimulationSceneSteps(props.scene.id)
    steps.value = result.data || []
    form.stepOrder = Math.max(0, ...steps.value.map((item) => item.stepOrder || 0)) + 1
    form.pointId ||= props.points[0]?.id || null
  } finally {
    loading.value = false
  }
}

async function addStep() {
  if (!form.pointId) {
    ElMessage.warning('请选择点位')
    return
  }
  submitting.value = true
  try {
    await addSimulationSceneStep(props.scene.id, {
      stepOrder: form.stepOrder,
      action: form.action,
      pointId: form.pointId,
      targetDeviceId: form.targetDeviceId,
      sampleValue: form.action === 'WRITE_SAMPLE' ? form.sampleValue : null,
      delayMs: form.delayMs,
      description: form.description,
    })
    ElMessage.success('步骤已添加')
    form.description = ''
    await loadSteps()
  } finally {
    submitting.value = false
  }
}

async function removeStep(row) {
  const confirmed = await ElMessageBox.confirm(`确认删除步骤 ${row.stepOrder}？`, '删除确认', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  await deleteSimulationSceneStep(row.id)
  ElMessage.success('步骤已删除')
  await loadSteps()
}

function pointLabel(id) {
  const point = props.points.find((item) => item.id === id)
  return point ? `${point.pointName} (${point.pointCode})` : `点位 #${id}`
}

function deviceLabel(id) {
  if (!id) return '运行时指定'
  return props.devices.find((item) => item.id === id)?.deviceName || `设备 #${id}`
}

function actionLabel(action) {
  return action === 'SLEEP' ? '延时' : '写入采样'
}
</script>

<style scoped>
.step-form {
  padding: 14px 14px 0;
  margin-bottom: 18px;
  border: 1px solid #ebeef5;
  background: #fafafa;
}

.action-select {
  width: 130px;
}

.point-select {
  width: 240px;
}

.device-select {
  width: 200px;
}

.step-description {
  width: 330px;
}
</style>
