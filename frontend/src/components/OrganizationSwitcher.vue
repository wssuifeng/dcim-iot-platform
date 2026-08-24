<template>
  <el-select
    v-if="store.memberships.length"
    :model-value="store.currentOrganizationId"
    class="switcher"
    placeholder="选择组织"
    :disabled="switching"
    @change="change"
  >
    <el-option
      v-for="item in store.memberships"
      :key="item.organizationId"
      :label="`组织 ${item.organizationId}`"
      :value="String(item.organizationId)"
    />
  </el-select>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useOrganizationStore } from '@/stores/organization'
import { useScopeStore } from '@/stores/scope'

const router = useRouter()
const store = useOrganizationStore()
const scopeStore = useScopeStore()
const switching = ref(false)

async function change(id) {
  const previousId = store.currentOrganizationId
  switching.value = true
  try {
    store.selectOrganization(id)
    scopeStore.resetCurrentScope()
    const scope = await scopeStore.loadCurrentScope({ force: true })
    if (!scope || !scopeStore.isReady) {
      throw new Error(scopeStore.errorMessage || '组织范围加载失败')
    }
    await router.replace('/dashboard')
  } catch (error) {
    store.restoreSelection(previousId)
    scopeStore.resetCurrentScope()
    await scopeStore.loadCurrentScope({ force: true, silent: true }).catch(() => null)
    ElMessage.error(error?.message || '组织切换失败，已恢复原组织')
  } finally {
    switching.value = false
  }
}
</script>

<style scoped>
.switcher {
  width: 180px;
}
</style>
