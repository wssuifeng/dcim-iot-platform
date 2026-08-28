<template>
  <span class="state-dot" :class="`state-dot--${state}`" role="img" :aria-label="label" />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // online-在线 / offline-离线 / fault-故障 / stale-从未上线
  state: { type: String, default: 'offline' },
})

const LABELS = {
  online: '在线',
  offline: '离线',
  fault: '故障',
  stale: '从未上线',
}

const label = computed(() => LABELS[props.state] || props.state)
</script>

<style scoped>
.state-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.state-dot--online {
  background: var(--color-success);
  box-shadow: 0 0 0 3px var(--color-success-light);
}

.state-dot--offline {
  background: var(--color-danger);
  box-shadow: 0 0 0 3px var(--color-danger-light);
}

.state-dot--fault {
  background: var(--color-warning);
  box-shadow: 0 0 0 3px var(--color-warning-light);
}

.state-dot--stale {
  background: var(--color-text-3);
  box-shadow: 0 0 0 3px var(--color-info-light);
}
</style>
