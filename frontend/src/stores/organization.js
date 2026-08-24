import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMemberships } from '@/api/accountOrganization'

export const useOrganizationStore = defineStore('organization', () => {
  const memberships = ref([])
  const currentOrganizationId = ref(localStorage.getItem('currentOrganizationId') || '')
  const loaded = ref(false)
  const currentMembership = computed(() =>
    memberships.value.find(
      (item) => String(item.organizationId) === String(currentOrganizationId.value),
    ),
  )
  const currentRole = computed(() => currentMembership.value?.membershipRole || '')

  async function loadMemberships() {
    const result = await getMemberships()
    memberships.value = Array.isArray(result.data) ? result.data : []
    loaded.value = true
    if (
      currentOrganizationId.value &&
      !memberships.value.some(
        (item) => String(item.organizationId) === String(currentOrganizationId.value),
      )
    ) {
      clearSelection()
    }
    if (!currentOrganizationId.value && memberships.value.length === 1) {
      selectOrganization(memberships.value[0].organizationId)
    }
    return memberships.value
  }

  function selectOrganization(id) {
    const membership = memberships.value.find(
      (item) => String(item.organizationId) === String(id),
    )
    if (!membership) {
      throw new Error('无权切换到该组织')
    }
    const previousId = currentOrganizationId.value
    currentOrganizationId.value = String(membership.organizationId)
    localStorage.setItem('currentOrganizationId', currentOrganizationId.value)
    return previousId
  }

  function restoreSelection(id) {
    currentOrganizationId.value = id ? String(id) : ''
    if (currentOrganizationId.value) {
      localStorage.setItem('currentOrganizationId', currentOrganizationId.value)
    } else {
      localStorage.removeItem('currentOrganizationId')
    }
  }

  function clearSelection() {
    restoreSelection('')
  }

  function reset() {
    memberships.value = []
    loaded.value = false
    clearSelection()
  }

  window.addEventListener('auth:unauthorized', reset)

  return {
    memberships,
    currentOrganizationId,
    currentMembership,
    currentRole,
    loaded,
    loadMemberships,
    selectOrganization,
    restoreSelection,
    reset,
  }
})
