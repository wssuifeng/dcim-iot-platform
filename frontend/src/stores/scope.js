import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentScope } from '@/api/scope'

export const SCOPE_STATUS = Object.freeze({
  IDLE: 'idle',
  LOADING: 'loading',
  READY: 'ready',
  ERROR: 'error',
})

const SCOPE_LABELS = Object.freeze({
  PLATFORM: '平台范围',
  ORG_ALL: '组织范围',
  ROOMS: '机房范围',
  READ_ONLY: '只读范围',
})

const ROLE_ALIASES = Object.freeze({
  ADMIN: 'ORG_ADMIN',
})
const VALID_ROLES = new Set(['PLATFORM_ADMIN', 'ORG_ADMIN', 'OPERATOR', 'VIEWER'])
const VALID_SCOPE_TYPES = new Set(['PLATFORM', 'ORG_ALL', 'ROOMS', 'READ_ONLY'])

function normalizeIdList(value) {
  if (!Array.isArray(value)) return []
  return [
    ...new Set(
      value.filter(
        (id) =>
          (typeof id === 'number' && Number.isFinite(id)) ||
          (typeof id === 'string' && id.trim().length > 0),
      ),
    ),
  ]
}

function normalizeCurrentScope(data) {
  if (!data || typeof data !== 'object') {
    throw new Error('当前范围响应缺少 data 对象')
  }

  const rawRole = typeof data.role === 'string' ? data.role.trim().toUpperCase() : ''
  const role = ROLE_ALIASES[rawRole] || rawRole
  const scopeType =
    typeof data.scopeType === 'string' ? data.scopeType.trim().toUpperCase() : ''

  if (data.userId === null || data.userId === undefined || data.userId === '') {
    throw new Error('当前范围响应缺少 userId、role 或 scopeType')
  }
  if (!VALID_ROLES.has(role)) {
    throw new Error(`当前范围响应包含未知角色: ${rawRole || 'empty'}`)
  }
  if (!VALID_SCOPE_TYPES.has(scopeType)) {
    throw new Error(`当前范围响应包含未知范围类型: ${scopeType || 'empty'}`)
  }

  return {
    userId: data.userId,
    role,
    organizationIds: normalizeIdList(data.organizationIds),
    cloudRoomIds: normalizeIdList(data.cloudRoomIds),
    scopeType,
  }
}

function resolveErrorMessage(error) {
  return error?.response?.data?.message || error?.message || '当前范围加载失败'
}

export const useScopeStore = defineStore('scope', () => {
  const currentScope = ref(null)
  const status = ref(SCOPE_STATUS.IDLE)
  const errorMessage = ref('')
  const loadedForToken = ref('')
  let pendingRequest = null
  let pendingForToken = ''
  let requestVersion = 0

  const isReady = computed(() => status.value === SCOPE_STATUS.READY)
  const role = computed(() => currentScope.value?.role || '')
  const scopeType = computed(() => currentScope.value?.scopeType || '')
  const organizationIds = computed(() => currentScope.value?.organizationIds || [])
  const cloudRoomIds = computed(() => currentScope.value?.cloudRoomIds || [])
  const hasWritePermission = computed(
    () => isReady.value && scopeType.value !== 'READ_ONLY',
  )
  const scopeLabel = computed(() => SCOPE_LABELS[scopeType.value] || scopeType.value)

  function resetCurrentScope() {
    requestVersion += 1
    currentScope.value = null
    status.value = SCOPE_STATUS.IDLE
    errorMessage.value = ''
    loadedForToken.value = ''
    pendingRequest = null
    pendingForToken = ''
  }

  async function loadCurrentScope({ force = false, silent = false } = {}) {
    const token = localStorage.getItem('token') || ''
    if (!token) {
      resetCurrentScope()
      return null
    }

    if (
      (loadedForToken.value && loadedForToken.value !== token) ||
      (pendingForToken && pendingForToken !== token)
    ) {
      resetCurrentScope()
    }

    if (!force && isReady.value && loadedForToken.value === token) {
      return currentScope.value
    }

    if (!force && pendingRequest && pendingForToken === token) {
      return pendingRequest
    }

    status.value = SCOPE_STATUS.LOADING
    errorMessage.value = ''
    const currentRequestVersion = ++requestVersion
    pendingForToken = token

    pendingRequest = getCurrentScope({ skipErrorMessage: silent })
      .then((result) => {
        if (
          currentRequestVersion !== requestVersion ||
          localStorage.getItem('token') !== token
        ) {
          return null
        }

        if (result?.code !== 200) {
          throw new Error(result?.message || '当前范围接口返回失败')
        }

        const normalizedScope = normalizeCurrentScope(result.data)
        currentScope.value = normalizedScope
        loadedForToken.value = token
        status.value = SCOPE_STATUS.READY
        return normalizedScope
      })
      .catch((error) => {
        if (
          currentRequestVersion === requestVersion &&
          localStorage.getItem('token') === token
        ) {
          currentScope.value = null
          status.value = SCOPE_STATUS.ERROR
          errorMessage.value = resolveErrorMessage(error)
        }
        if (silent) return null
        throw error
      })
      .finally(() => {
        if (currentRequestVersion === requestVersion) {
          pendingRequest = null
          pendingForToken = ''
        }
      })

    return pendingRequest
  }

  function ensureCurrentScope(options = {}) {
    return loadCurrentScope(options)
  }

  return {
    currentScope,
    status,
    errorMessage,
    isReady,
    role,
    scopeType,
    organizationIds,
    cloudRoomIds,
    hasWritePermission,
    scopeLabel,
    loadCurrentScope,
    ensureCurrentScope,
    resetCurrentScope,
  }
})
