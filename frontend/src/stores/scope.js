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

const ROLE_ALIASES = Object.freeze({ ADMIN: 'ORG_ADMIN' })
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
    throw new Error('当前范围响应缺少 userId')
  }
  if (!VALID_ROLES.has(role)) {
    throw new Error(`当前范围响应包含未知角色: ${rawRole || 'empty'}`)
  }
  if (!VALID_SCOPE_TYPES.has(scopeType)) {
    throw new Error(`当前范围响应包含未知范围类型: ${scopeType || 'empty'}`)
  }

  return {
    userId: data.userId,
    username: data.username || '',
    role,
    organizationIds: normalizeIdList(data.organizationIds),
    cloudRoomIds: normalizeIdList(data.cloudRoomIds),
    scopeType,
    writePermission: data.writePermission === true,
  }
}

function resolveErrorMessage(error) {
  return error?.response?.data?.message || error?.message || '当前范围加载失败'
}

function getScopeKey(token, organizationId) {
  return `${token}::${organizationId || 'platform'}`
}

export const useScopeStore = defineStore('scope', () => {
  const currentScope = ref(null)
  const status = ref(SCOPE_STATUS.IDLE)
  const errorMessage = ref('')
  const currentKey = ref('')
  const scopeCache = new Map()
  let pendingRequest = null
  let pendingKey = ''
  let requestVersion = 0

  const isReady = computed(() => status.value === SCOPE_STATUS.READY)
  const role = computed(() => currentScope.value?.role || '')
  const scopeType = computed(() => currentScope.value?.scopeType || '')
  const organizationIds = computed(() => currentScope.value?.organizationIds || [])
  const cloudRoomIds = computed(() => currentScope.value?.cloudRoomIds || [])
  const hasWritePermission = computed(
    () => isReady.value && currentScope.value?.writePermission === true,
  )
  const scopeLabel = computed(() => SCOPE_LABELS[scopeType.value] || scopeType.value)

  function clearCacheForToken(token) {
    if (!token) return
    for (const key of scopeCache.keys()) {
      if (key.startsWith(`${token}::`)) scopeCache.delete(key)
    }
  }

  function resetCurrentScope({ clearCache = false } = {}) {
    requestVersion += 1
    currentScope.value = null
    status.value = SCOPE_STATUS.IDLE
    errorMessage.value = ''
    currentKey.value = ''
    pendingRequest = null
    pendingKey = ''
    if (clearCache) scopeCache.clear()
  }

  function applyScope(scope, key) {
    currentScope.value = scope
    currentKey.value = key
    status.value = SCOPE_STATUS.READY
    errorMessage.value = ''
    return scope
  }

  async function loadCurrentScope({ force = false, silent = false } = {}) {
    const token = localStorage.getItem('token') || ''
    if (!token) {
      resetCurrentScope({ clearCache: true })
      return null
    }

    const organizationId = localStorage.getItem('currentOrganizationId') || ''
    const key = getScopeKey(token, organizationId)
    const previousToken = currentKey.value.split('::')[0]
    if (previousToken && previousToken !== token) {
      clearCacheForToken(previousToken)
    }

    if (currentKey.value !== key) {
      requestVersion += 1
      currentScope.value = null
      status.value = SCOPE_STATUS.IDLE
      errorMessage.value = ''
      currentKey.value = key
      pendingRequest = null
      pendingKey = ''
    }

    if (!force && isReady.value && currentKey.value === key) {
      return currentScope.value
    }

    if (!force && scopeCache.has(key)) {
      return applyScope(scopeCache.get(key), key)
    }

    if (!force && pendingRequest && pendingKey === key) {
      return pendingRequest
    }

    status.value = SCOPE_STATUS.LOADING
    errorMessage.value = ''
    const currentRequestVersion = ++requestVersion
    pendingKey = key

    pendingRequest = getCurrentScope({ skipErrorMessage: silent })
      .then((result) => {
        if (
          currentRequestVersion !== requestVersion ||
          localStorage.getItem('token') !== token ||
          getScopeKey(token, localStorage.getItem('currentOrganizationId') || '') !== key
        ) {
          return null
        }

        if (result?.code !== 200) {
          throw new Error(result?.message || '当前范围接口返回失败')
        }

        const normalizedScope = normalizeCurrentScope(result.data)
        scopeCache.set(key, normalizedScope)
        return applyScope(normalizedScope, key)
      })
      .catch((error) => {
        if (currentRequestVersion === requestVersion && currentKey.value === key) {
          currentScope.value = null
          status.value = SCOPE_STATUS.ERROR
          errorMessage.value = resolveErrorMessage(error)
        }
        if (silent) return null
        throw error
      })
      .finally(() => {
        if (currentRequestVersion === requestVersion && pendingKey === key) {
          pendingRequest = null
          pendingKey = ''
        }
      })

    return pendingRequest
  }

  function ensureCurrentScope(options = {}) {
    return loadCurrentScope(options)
  }

  window.addEventListener('auth:unauthorized', () => resetCurrentScope({ clearCache: true }))

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
