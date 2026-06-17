<template>
  <el-dialog
    :model-value="visible"
    title="设备告警规则配置"
    width="680px"
    @update:model-value="$emit('update:visible', $event)"
    @open="handleOpen"
  >
    <el-table :data="rules" border size="small">
      <el-table-column label="指标" min-width="140">
        <template #default="{ row }">
          <el-select v-model="row.metricKey" placeholder="选择指标" style="width: 100%">
            <el-option v-for="m in metrics" :key="m.metricKey" :label="m.metricName" :value="m.metricKey" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="运算符" width="110">
        <template #default="{ row }">
          <el-select v-model="row.operator" placeholder="运算符" style="width: 100%">
            <el-option v-for="op in OPERATORS" :key="op.value" :label="op.label" :value="op.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="阈值" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.threshold" :controls="false" style="width: 100%" />
        </template>
      </el-table-column>
      <el-table-column label="告警级别" width="110">
        <template #default="{ row }">
          <el-select v-model="row.alertLevel" placeholder="级别" style="width: 100%">
            <el-option v-for="l in ALERT_LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70">
        <template #default="{ $index }">
          <el-button type="danger" size="small" link @click="removeRule($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button type="primary" link style="margin-top: 12px" @click="addRule">+ 添加规则</el-button>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ALERT_LEVEL } from '@/utils/constants'
import { updateDeviceAlertConfig } from '@/api/device'
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: { type: Boolean, default: false },
  deviceId: { type: Number, default: null },
  metrics: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'confirm'])

const saving = ref(false)
const rules = ref([])

const OPERATORS = [
  { value: 'GT', label: '大于' }, { value: 'LT', label: '小于' },
  { value: 'GTE', label: '大于等于' }, { value: 'LTE', label: '小于等于' }, { value: 'EQ', label: '等于' },
]
const ALERT_LEVEL_OPTIONS = Object.entries(ALERT_LEVEL).map(([value, item]) => ({ value: Number(value), label: item.label }))

function addRule() { rules.value.push({ metricKey: '', operator: 'GT', threshold: 0, alertLevel: 3 }) }
function removeRule(index) { rules.value.splice(index, 1) }

function handleOpen() { if (rules.value.length === 0) addRule() }

async function handleSave() {
  const valid = rules.value.every(r => r.metricKey && r.operator && r.threshold !== null && r.alertLevel)
  if (!valid) { ElMessage.warning('请完整填写所有规则'); return }
  saving.value = true
  try {
    await updateDeviceAlertConfig(props.deviceId, { rules: rules.value })
    ElMessage.success('告警规则已保存')
    emit('update:visible', false)
    emit('confirm')
  } finally { saving.value = false }
}
</script>
