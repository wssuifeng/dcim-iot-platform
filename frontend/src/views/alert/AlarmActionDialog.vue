<template>
  <el-dialog
    :model-value="visible"
    :title="`${actionMeta.label} · 告警 #${alarm?.id || ''}`"
    width="520px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="note = ''"
  >
    <el-alert :title="`${stateLabel(alarm?.state)} → ${actionMeta.targetLabel}`" type="info" :closable="false" />
    <el-form label-width="90px" class="action-form">
      <el-form-item label="处理说明">
        <el-input v-model="note" type="textarea" :rows="4" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="emit('confirm', { action, note: note.trim() })">
        确认操作
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'

defineOptions({ name: 'AlarmActionDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  alarm: { type: Object, default: null },
  action: { type: String, default: 'ack' },
  submitting: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'confirm'])
const note = ref('')
const actionMeta = computed(
  () =>
    ({
      ack: { label: '确认', targetLabel: '已确认' },
      handling: { label: '开始处理', targetLabel: '处理中' },
      recover: { label: '恢复', targetLabel: '已恢复' },
      close: { label: '关闭', targetLabel: '已关闭' },
    })[props.action],
)

function stateLabel(state) {
  return {
    TRIGGERED: '已触发',
    ACKED: '已确认',
    HANDLING: '处理中',
    RECOVERED: '已恢复',
    CLOSED: '已关闭',
  }[state] || state
}
</script>

<style scoped>
.action-form {
  margin-top: 18px;
}
</style>
