<template>
  <header class="page-header">
    <div class="page-header-text">
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.action" class="page-header-action">
      <slot name="action" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.page-header-text {
  min-width: 0;
}

.page-header-text h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
  font-weight: 600;
}

.page-header-text p {
  margin: 6px 0 0;
  color: var(--color-text-2);
  font-size: 13px;
}

.page-header-action {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
