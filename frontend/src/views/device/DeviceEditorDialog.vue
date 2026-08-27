<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'create' ? '新增设备' : '编辑设备'"
    width="600px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="initializeForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="95px">
      <div class="form-grid">
        <el-form-item label="设备名称" prop="deviceName">
          <el-input v-model="form.deviceName" />
        </el-form-item>
        <el-form-item label="设备编码" prop="deviceCode">
          <div class="code-row">
            <el-input
              v-model="form.deviceCode"
              :disabled="mode === 'edit'"
              placeholder="字母数字下划线短横线"
              @blur="checkCode"
            />
            <el-button
              v-if="mode === 'create'"
              :icon="MagicStick"
              @click="generateCode"
            >生成</el-button>
          </div>
          <div v-if="codeHint" :class="['code-hint', codeAvailable ? 'ok' : 'bad']">{{ codeHint }}</div>
        </el-form-item>
        <el-form-item label="所属机房" prop="cloudRoomId">
          <el-select v-model="form.cloudRoomId" filterable class="full-width">
            <el-option
              v-for="item in availableRooms"
              :key="item.id"
              :label="item.roomName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型" prop="typeId">
          <el-select v-model="form.typeId" filterable class="full-width">
            <el-option
              v-for="item in deviceTypes"
              :key="item.id"
              :label="`${item.typeName} (${item.typeCode})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" class="full-width">
            <el-option label="离线" :value="0" />
            <el-option label="在线" :value="1" />
            <el-option label="故障" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" />
        </el-form-item>
        <el-form-item label="IP 地址" prop="ipAddress">
          <el-input v-model="form.ipAddress" placeholder="可选" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { checkDeviceCodeAvailable } from '@/api/device'

defineOptions({ name: 'DeviceEditorDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  device: { type: Object, default: null },
  deviceTypes: { type: Array, default: () => [] },
  rooms: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'confirm'])
const codeHint = ref('')
const codeAvailable = ref(true)
const formRef = ref(null)
const form = reactive({
  deviceName: '',
  deviceCode: '',
  typeId: null,
  cloudRoomId: null,
  status: 0,
  location: '',
  ipAddress: '',
})
const availableRooms = computed(() => props.rooms.filter((item) => item.status === 0))
const rules = {
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceCode: [
    { required: true, message: '请输入设备编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '设备编码格式不正确', trigger: 'blur' },
  ],
  cloudRoomId: [{ required: true, message: '请选择机房', trigger: 'change' }],
  typeId: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
}

function initializeForm() {
  codeHint.value = ''
  codeAvailable.value = true
  const source = props.device || {}
  form.deviceName = source.deviceName || ''
  form.deviceCode = source.deviceCode || ''
  form.typeId = source.typeId || null
  form.cloudRoomId = source.cloudRoomId || null
  form.status = source.status ?? 0
  form.location = source.location || ''
  form.ipAddress = source.ipAddress || ''
  if (!form.cloudRoomId && availableRooms.value.length === 1) {
    form.cloudRoomId = availableRooms.value[0].id
  }
  formRef.value?.clearValidate()
}

async function checkCode() {
  const code = form.deviceCode?.trim()
  codeHint.value = ''
  if (!code || props.mode === 'edit') return
  const result = await checkDeviceCodeAvailable(code).catch(() => null)
  const available = result?.data?.available
  codeAvailable.value = !!available
  codeHint.value = available ? `编码 ${code} 可用` : `编码 ${code} 已被占用`
}

function generateCode() {
  const roomCode = props.rooms.find((item) => item.id === form.cloudRoomId)?.roomCode || 'ROOM'
  const suffix = Math.random().toString(36).slice(2, 6).toUpperCase()
  form.deviceCode = `${roomCode}-${suffix}`
  void checkCode()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (props.mode === 'create' && !codeAvailable.value) {
    formRef.value.validateField('deviceCode').catch(() => false)
    return
  }
  emit('confirm', { ...form })
}
</script>

<style scoped>
.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-hint {
  margin-top: 4px;
  font-size: 12px;
}

.code-hint.ok {
  color: var(--el-color-success);
}

.code-hint.bad {
  color: var(--el-color-danger);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.full-width {
  width: 100%;
}
</style>
