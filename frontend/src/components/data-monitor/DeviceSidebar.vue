<template>
  <div class="device-sidebar">
    <el-input v-model="searchText" placeholder="搜索设备" clearable style="margin-bottom: 12px" />
    <div class="device-list">
      <div
        v-for="d in filteredDevices" :key="d.id"
        :class="['device-item', { active: d.id === modelValue }]"
        @click="$emit('update:modelValue', d.id)"
      >
        <state-dot :state="dotState(d.status)" />
        <div class="device-item-main">
          <span class="device-item-name">{{ d.deviceName }}</span>
          <span class="device-item-seen">{{ relativeTime(d.lastOnlineTime) }}</span>
        </div>
      </div>
      <el-empty v-if="filteredDevices.length === 0" description="无设备" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

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

function dotState(status) {
  return { 0: 'offline', 1: 'online', 2: 'fault' }[status] || 'offline'
}

function relativeTime(time) {
  if (!time) return '从未上报'
  const diff = Date.now() - new Date(time).getTime()
  if (diff < 0) return '刚刚'
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return `${seconds} 秒前`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}
</script>

<style scoped>
.device-sidebar { width: 220px; padding: 12px; overflow-y: auto; }
.device-item { cursor: pointer; padding: 8px 12px; border-radius: var(--radius-sm); display: flex; align-items: center; gap: 8px; }
.device-item:hover { background: var(--color-bg); }
.device-item.active { background: var(--color-primary-light); }
.device-item.active .device-item-name { color: var(--color-primary); }
.device-item-main { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.device-item-name { color: var(--color-text); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.device-item-seen { color: var(--color-text-3); font-size: 11px; }
</style>
