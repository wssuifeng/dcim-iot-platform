<template>
  <div class="device-sidebar">
    <el-input v-model="searchText" placeholder="搜索设备" clearable style="margin-bottom: 12px" />
    <div class="device-list">
      <div
        v-for="d in filteredDevices" :key="d.id"
        :class="['device-item', { active: d.id === modelValue }]"
        @click="$emit('update:modelValue', d.id)"
      >
        <span class="dot" :style="{ background: statusColor(d.status) }" />
        <span>{{ d.deviceName }}</span>
      </div>
      <el-empty v-if="filteredDevices.length === 0" description="无设备" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { DEVICE_STATUS } from '@/utils/constants'

const props = defineProps({
  devices: { type: Array, default: () => [] },
  modelValue: { type: Number, default: null },
})
defineEmits(['update:modelValue'])

const searchText = ref('')

const filteredDevices = computed(() => {
  if (!searchText.value) return props.devices
  return props.devices.filter(d => (d.deviceName || '').toLowerCase().includes(searchText.value.toLowerCase()))
})

function statusColor(status) {
  return DEVICE_STATUS[status]?.color || 'var(--color-text-3)'
}
</script>

<style scoped>
.device-sidebar { width: 220px; padding: 12px; border-right: 1px solid var(--color-border); overflow-y: auto; }
.device-item { cursor: pointer; padding: 8px 12px; border-radius: 4px; display: flex; align-items: center; gap: 8px; }
.device-item:hover { background: var(--color-bg); }
.device-item.active { background: var(--color-primary-light); color: var(--color-primary); }
.dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
</style>
