<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'create' ? '新增设备' : '编辑设备'"
    width="520px"
    @update:model-value="$emit('update:visible', $event)"
    @open="handleOpen"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
      <el-form-item label="设备名称" prop="deviceName">
        <el-input v-model="form.deviceName" placeholder="请输入设备名称" />
      </el-form-item>
      <el-form-item label="设备编码" prop="deviceCode">
        <el-input v-model="form.deviceCode" placeholder="请输入设备编码" :disabled="mode === 'edit'" />
      </el-form-item>
      <el-form-item label="设备类型" prop="typeId">
        <el-select v-model="form.typeId" placeholder="请选择设备类型" style="width: 100%">
          <el-option v-for="item in deviceTypes" :key="item.id" :label="item.typeName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="位置" prop="location">
        <el-input v-model="form.location" placeholder="请输入位置" />
      </el-form-item>
      <el-form-item label="IP地址" prop="ipAddress">
        <el-input v-model="form.ipAddress" placeholder="请输入IP地址" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { DEVICE_STATUS } from '@/utils/constants'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  device: { type: Object, default: null },
  deviceTypes: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'confirm'])

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ deviceName: '', deviceCode: '', typeId: null, location: '', ipAddress: '', status: 1 })
const rules = {
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  typeId: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
}
const STATUS_OPTIONS = Object.entries(DEVICE_STATUS).map(([value, item]) => ({ value: Number(value), label: item.label }))

function handleOpen() {
  if (props.mode === 'edit' && props.device) {
    form.deviceName = props.device.deviceName || ''; form.deviceCode = props.device.deviceCode || ''
    form.typeId = props.device.typeId || null; form.location = props.device.location || ''
    form.ipAddress = props.device.ipAddress || ''; form.status = props.device.status ?? 1
  } else {
    form.deviceName = ''; form.deviceCode = ''; form.typeId = null
    form.location = ''; form.ipAddress = ''; form.status = 1
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try { emit('confirm', { ...form }) } finally { submitting.value = false }
}
</script>
