<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'create' ? '新增指标定义' : '编辑指标定义'"
    width="560px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="initializeForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="95px">
      <el-form-item label="指标编码" prop="pointCode">
        <el-input v-model="form.pointCode" :disabled="mode === 'edit'" maxlength="64" />
      </el-form-item>
      <el-form-item label="指标名称" prop="pointName">
        <el-input v-model="form.pointName" maxlength="100" />
      </el-form-item>
      <el-form-item label="数据类型" prop="dataType">
        <el-segmented v-model="form.dataType" :options="dataTypeOptions" />
      </el-form-item>
      <el-form-item label="单位" prop="unit">
        <el-input v-model="form.unit" maxlength="32" placeholder="无单位可留空" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'

defineOptions({ name: 'PointDefinitionDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  point: { type: Object, default: null },
})
const emit = defineEmits(['update:visible', 'confirm'])
const formRef = ref(null)
const dataTypeOptions = ['NUMBER', 'BOOLEAN', 'STRING']
const form = reactive({
  pointCode: '',
  pointName: '',
  dataType: 'NUMBER',
  unit: '',
  description: '',
})
const rules = {
  pointCode: [
    { required: true, message: '请输入指标编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.-]+$/, message: '指标编码格式不正确', trigger: 'blur' },
  ],
  pointName: [{ required: true, message: '请输入指标名称', trigger: 'blur' }],
  dataType: [{ required: true, message: '请选择数据类型', trigger: 'change' }],
}

function initializeForm() {
  const source = props.point || {}
  form.pointCode = source.pointCode || ''
  form.pointName = source.pointName || ''
  form.dataType = source.dataType || 'NUMBER'
  form.unit = source.unit || ''
  form.description = source.description || ''
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('confirm', { ...form })
}
</script>
