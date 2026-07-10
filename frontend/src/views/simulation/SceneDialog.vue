<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'create' ? '新增模拟场景' : '编辑模拟场景'"
    width="620px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="initialize"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item v-if="mode === 'create'" label="场景编码" prop="sceneCode">
        <el-input v-model="form.sceneCode" maxlength="50" placeholder="例如 UPS_LOAD_TEST" />
      </el-form-item>
      <el-form-item label="场景名称" prop="sceneName">
        <el-input v-model="form.sceneName" maxlength="100" />
      </el-form-item>
      <el-form-item v-if="mode === 'create'" label="所属组织" prop="organizationId">
        <el-select v-model="form.organizationId" filterable class="full-width" @change="form.cloudRoomId = null">
          <el-option
            v-for="item in activeOrganizations"
            :key="item.id"
            :label="item.orgName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="所属机房" prop="cloudRoomId">
        <el-select v-model="form.cloudRoomId" clearable filterable class="full-width">
          <el-option
            v-for="item in availableRooms"
            :key="item.id"
            :label="item.roomName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="设备类型" prop="deviceTypeCode">
        <el-select v-model="form.deviceTypeCode" clearable class="full-width">
          <el-option v-for="item in deviceTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'

defineOptions({ name: 'SimulationSceneDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  scene: { type: Object, default: null },
  organizations: { type: Array, default: () => [] },
  rooms: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'confirm'])
const formRef = ref(null)
const form = reactive({
  sceneCode: '',
  sceneName: '',
  organizationId: null,
  cloudRoomId: null,
  deviceTypeCode: '',
  description: '',
})
const deviceTypes = Object.freeze([
  { label: 'UPS', value: 'UPS' },
  { label: 'PDU / 电表', value: 'PDU_METER' },
  { label: '精密空调', value: 'PRECISION_AC' },
  { label: '温湿度', value: 'TEMP_HUMIDITY' },
  { label: '漏水检测', value: 'LEAK_DETECTOR' },
])
const activeOrganizations = computed(() => props.organizations.filter((item) => item.status === 0))
const availableRooms = computed(() =>
  props.rooms.filter(
    (item) => item.status === 0 && (!form.organizationId || item.organizationId === form.organizationId),
  ),
)
const rules = {
  sceneCode: [
    { required: true, message: '请输入场景编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线和短横线', trigger: 'blur' },
  ],
  sceneName: [{ required: true, message: '请输入场景名称', trigger: 'blur' }],
  organizationId: [{ required: true, message: '请选择组织', trigger: 'change' }],
}

function initialize() {
  form.sceneCode = props.scene?.sceneCode || ''
  form.sceneName = props.scene?.sceneName || ''
  form.organizationId = props.scene?.organizationId || activeOrganizations.value[0]?.id || null
  form.cloudRoomId = props.scene?.cloudRoomId || null
  form.deviceTypeCode = props.scene?.deviceTypeCode || ''
  form.description = props.scene?.description || ''
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = {
    sceneName: form.sceneName,
    cloudRoomId: form.cloudRoomId,
    deviceTypeCode: form.deviceTypeCode || null,
    description: form.description,
  }
  if (props.mode === 'create') {
    payload.sceneCode = form.sceneCode
    payload.organizationId = form.organizationId
  }
  emit('confirm', payload)
}
</script>

<style scoped>
.full-width {
  width: 100%;
}
</style>
