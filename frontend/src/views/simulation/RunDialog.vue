<template>
  <el-dialog
    :model-value="visible"
    :title="`启动模拟 · ${scene?.sceneName || ''}`"
    width="520px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="initialize"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="目标设备" prop="targetDeviceId">
        <el-select v-model="form.targetDeviceId" filterable class="full-width" @change="syncRoom">
          <el-option v-for="item in availableDevices" :key="item.id" :label="item.deviceName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标机房" prop="cloudRoomId">
        <el-select v-model="form.cloudRoomId" clearable filterable class="full-width">
          <el-option v-for="item in availableRooms" :key="item.id" :label="item.roomName" :value="item.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">启动</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'

defineOptions({ name: 'SimulationRunDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  scene: { type: Object, default: null },
  devices: { type: Array, default: () => [] },
  rooms: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'confirm'])
const formRef = ref(null)
const form = reactive({ targetDeviceId: null, cloudRoomId: null })
const availableDevices = computed(() =>
  props.devices.filter(
    (item) => !props.scene?.cloudRoomId || item.cloudRoomId === props.scene.cloudRoomId,
  ),
)
const availableRooms = computed(() => props.rooms.filter((item) => item.status === 0))
const rules = {
  targetDeviceId: [{ required: true, message: '请选择目标设备', trigger: 'change' }],
}

function initialize() {
  form.targetDeviceId = availableDevices.value[0]?.id || null
  form.cloudRoomId = props.scene?.cloudRoomId || availableDevices.value[0]?.cloudRoomId || null
  formRef.value?.clearValidate()
}

function syncRoom(deviceId) {
  const device = props.devices.find((item) => item.id === deviceId)
  if (device?.cloudRoomId) form.cloudRoomId = device.cloudRoomId
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('confirm', {
    sceneId: props.scene.id,
    targetDeviceId: form.targetDeviceId,
    cloudRoomId: form.cloudRoomId,
  })
}
</script>

<style scoped>
.full-width {
  width: 100%;
}
</style>
