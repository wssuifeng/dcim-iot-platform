<template>
  <div class="stat-value" :class="{ 'stat-value--lg': size === 'lg' }">
    <span class="stat-value-num num" :class="{ 'is-danger': danger, 'is-warning': warning }">{{
      displayValue
    }}</span>
    <span v-if="unit" class="stat-value-unit">{{ unit }}</span>
    <span v-if="hint" class="stat-value-hint">{{ hint }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: [String, Number], default: '' },
  unit: { type: String, default: '' },
  hint: { type: String, default: '' },
  size: { type: String, default: 'md' }, // md | lg
  danger: { type: Boolean, default: false },
  warning: { type: Boolean, default: false },
})

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined) return '—'
  return props.value
})
</script>

<style scoped>
.stat-value {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.stat-value-num {
  color: var(--color-text);
  font-size: 15px;
  font-weight: 600;
}

.stat-value--lg .stat-value-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.15;
}

.stat-value-num.is-danger {
  color: var(--color-danger);
}

.stat-value-num.is-warning {
  color: var(--color-warning);
}

.stat-value-unit {
  color: var(--color-text-3);
  font-size: 12px;
  flex-shrink: 0;
}

.stat-value-hint {
  color: var(--color-text-3);
  font-size: 12px;
  flex-shrink: 0;
}
</style>
