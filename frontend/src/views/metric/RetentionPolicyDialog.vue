<template>
  <el-dialog
    :model-value="visible"
    title="指标保留策略"
    width="500px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="initializeForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="95px">
      <el-form-item label="适用指标" prop="pointId">
        <el-select v-model="form.pointId" filterable class="full-width">
          <el-option label="全局默认" :value="null" />
          <el-option
            v-for="item in points"
            :key="item.id"
            :label="`${item.pointName} (${item.pointCode})`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="保留天数" prop="retentionDays">
        <el-input-number
          v-model="form.retentionDays"
          :min="1"
          :max="90"
          controls-position="right"
        />
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

defineOptions({ name: 'RetentionPolicyDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  policy: { type: Object, default: null },
  points: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'confirm'])
const formRef = ref(null)
const form = reactive({ pointId: null, retentionDays: 90 })
const rules = {
  retentionDays: [{ required: true, message: '请输入保留天数', trigger: 'change' }],
}

function initializeForm() {
  form.pointId = props.policy?.pointId ?? null
  form.retentionDays = props.policy?.retentionDays ?? 90
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('confirm', { ...form })
}
</script>

<style scoped>
.full-width {
  width: 100%;
}
</style>
