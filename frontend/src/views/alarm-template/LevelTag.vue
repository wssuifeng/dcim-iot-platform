<template>
  <span :class="['level-tag', `alarm-band-l${level}`]">{{ level }} {{ LEVEL_NAMES[level] || level }}</span>
</template>

<script setup>
defineProps({
  level: { type: Number, required: true },
})

const LEVEL_NAMES = { 1: '紧急', 2: '严重', 3: '一般', 4: '警告', 5: '提示' }
</script>

<style scoped>
.level-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
  color: var(--alarm-band, var(--color-text-2));
  background: var(--alarm-band-bg, var(--color-surface-2));
  font-weight: 500;
}
</style>
