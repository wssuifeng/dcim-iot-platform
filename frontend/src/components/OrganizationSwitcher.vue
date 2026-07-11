<template>
  <el-select v-if="store.memberships.length" :model-value="store.currentOrganizationId" class="switcher" placeholder="选择组织" @change="change">
    <el-option v-for="item in store.memberships" :key="item.organizationId" :label="`组织 ${item.organizationId}`" :value="String(item.organizationId)" />
  </el-select>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useOrganizationStore } from '@/stores/organization'
import { useScopeStore } from '@/stores/scope'

const router = useRouter(); const store = useOrganizationStore(); const scopeStore = useScopeStore()
async function change(id) {
  store.selectOrganization(id)
  scopeStore.resetCurrentScope()
  await scopeStore.loadCurrentScope({ force: true, silent: true })
  await router.replace('/dashboard')
}
</script>

<style scoped>.switcher { width: 180px; }</style>
