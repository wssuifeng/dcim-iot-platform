<template>
  <section class="page-shell">
    <page-header v-if="title || $slots.action" :title="title" :subtitle="subtitle">
      <template v-if="$slots.action" #action>
        <slot name="action" />
      </template>
    </page-header>
    <slot />
  </section>
</template>

<script setup>
import PageHeader from './PageHeader.vue'

defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
})
</script>

<style scoped>
.page-shell {
  min-width: 0;
}
</style>
