<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'batch' ? '批量处理告警' : '处理告警'"
    width="500px"
    @update:model-value="$emit('update:visible', $event)"
    @open="handleOpen"
  >
    <p class="dialog-tip">
      正在处理 <strong>{{ alertIds.length }}</strong> 条告警
    </p>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
      <el-form-item label="处理说明" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入处理说明"
        />
      </el-form-item>
      <el-form-item label="处理人">
        <el-input v-model="form.handleUser" disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        确认处理
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  alertIds: { type: Array, default: () => [] },
  mode: { type: String, default: 'single' },
  username: { type: String, default: '' },
})

const emit = defineEmits(['update:visible', 'confirm'])

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  description: '',
  handleUser: '',
})

const rules = {
  description: [{ required: true, message: '请输入处理说明', trigger: 'blur' }],
}

function handleOpen() {
  form.description = ''
  form.handleUser = props.username
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  emit('confirm', form.handleUser)
}
</script>

<style scoped>
.dialog-tip {
  margin-bottom: 16px;
  color: var(--color-text-2);
}
</style>
