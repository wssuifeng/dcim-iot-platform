<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>指标与点位</h1>
        <p>点位定义、数据保留策略及兼容指标字典</p>
      </div>
    </header>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="点位定义" name="points">
        <div class="tab-toolbar">
          <el-form :inline="true" :model="pointFilters">
            <el-form-item label="关键字">
              <el-input
                v-model="pointFilters.keyword"
                placeholder="编码或名称"
                clearable
                @keyup.enter="searchPoints"
              />
            </el-form-item>
            <el-form-item label="数据类型">
              <el-select v-model="pointFilters.dataType" clearable placeholder="全部" class="type-select">
                <el-option label="NUMBER" value="NUMBER" />
                <el-option label="BOOLEAN" value="BOOLEAN" />
                <el-option label="STRING" value="STRING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="searchPoints">查询</el-button>
              <el-button :icon="Refresh" @click="resetPointFilters">重置</el-button>
            </el-form-item>
          </el-form>
          <el-button v-if="canManagePoints" type="primary" :icon="Plus" @click="createPoint">
            新增点位
          </el-button>
        </div>

        <el-table v-loading="pointLoading" :data="points" row-key="id">
          <el-table-column prop="pointCode" label="点位编码" min-width="160" />
          <el-table-column prop="pointName" label="点位名称" min-width="160" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }"><el-tag>{{ row.dataType }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="100" />
          <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
          <el-table-column prop="updateTime" label="更新时间" min-width="170" />
          <el-table-column v-if="canManagePoints" label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="editPoint(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="pointTotal > 0"
          v-model:current-page="pointPagination.current"
          v-model:page-size="pointPagination.size"
          :total="pointTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="pagination"
          @change="fetchPoints"
        />
      </el-tab-pane>

      <el-tab-pane label="保留策略" name="retention">
        <div class="tab-toolbar compact">
          <span class="section-note">点位策略优先于全局默认，最大保留 90 天</span>
          <el-button
            v-if="canManagePoints"
            type="primary"
            :icon="Setting"
            @click="createPolicy"
          >
            配置策略
          </el-button>
        </div>
        <el-table v-loading="retentionLoading" :data="retentionPolicies" row-key="id">
          <el-table-column label="适用范围" min-width="220">
            <template #default="{ row }">
              {{ row.pointId ? pointName(row.pointId) : '全局默认' }}
            </template>
          </el-table-column>
          <el-table-column prop="retentionDays" label="保留天数" width="140" />
          <el-table-column prop="updateTime" label="更新时间" min-width="180" />
          <el-table-column v-if="canManagePoints" label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="editPolicy(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canReadLegacy" label="兼容指标字典" name="legacy">
        <metric-filter :filter="legacyFilters" @search="searchLegacy" @reset="resetLegacy" />
        <div class="legacy-toolbar">
          <el-button v-if="legacyCanWrite" type="primary" :icon="Plus" @click="createLegacyMetric">
            新增指标
          </el-button>
          <el-button
            v-if="legacyCanWrite"
            :icon="Refresh"
            :loading="cacheLoading"
            @click="refreshLegacyCache"
          >
            刷新缓存
          </el-button>
        </div>
        <metric-table
          :list="legacyMetrics"
          :loading="legacyLoading"
          :can-operate="legacyCanWrite"
          :is-admin="isOrgAdmin"
          @edit="editLegacyMetric"
          @delete="removeLegacyMetric"
        />
        <el-pagination
          v-if="legacyTotal > 0"
          v-model:current-page="legacyPagination.current"
          v-model:page-size="legacyPagination.size"
          :total="legacyTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="pagination"
          @change="fetchLegacyMetrics"
        />
      </el-tab-pane>
    </el-tabs>

    <point-definition-dialog
      v-model:visible="pointDialogVisible"
      :mode="pointDialogMode"
      :point="currentPoint"
      @confirm="savePoint"
    />
    <retention-policy-dialog
      v-model:visible="policyDialogVisible"
      :policy="currentPolicy"
      :points="allPoints"
      @confirm="savePolicy"
    />
    <metric-form-dialog
      v-model:visible="legacyDialogVisible"
      :mode="legacyDialogMode"
      :current="currentLegacyMetric"
      @confirm="saveLegacyMetric"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Edit, Plus, Refresh, Search, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useScopeStore } from '@/stores/scope'
import {
  createMetric,
  deleteMetric,
  getMetricPage,
  refreshMetricCache,
  updateMetric,
} from '@/api/metric'
import {
  createPointDefinition,
  getPointDefinitionPage,
  updatePointDefinition,
} from '@/api/pointDefinition'
import { getMetricRetentionList, upsertMetricRetention } from '@/api/metricRetention'
import MetricFilter from '@/components/metric/MetricFilter.vue'
import MetricFormDialog from '@/components/metric/MetricFormDialog.vue'
import MetricTable from '@/components/metric/MetricTable.vue'
import PointDefinitionDialog from './PointDefinitionDialog.vue'
import RetentionPolicyDialog from './RetentionPolicyDialog.vue'

defineOptions({ name: 'MetricPointManagement' })

const authStore = useAuthStore()
const scopeStore = useScopeStore()
const effectiveRole = computed(() => scopeStore.role || authStore.normalizedRole)
const hasWriteScope = computed(() => !scopeStore.isReady || scopeStore.hasWritePermission)
const canManagePoints = computed(() => effectiveRole.value === 'PLATFORM_ADMIN')
const canReadLegacy = computed(() =>
  ['ORG_ADMIN', 'OPERATOR', 'VIEWER'].includes(effectiveRole.value),
)
const legacyCanWrite = computed(
  () => ['ORG_ADMIN', 'OPERATOR'].includes(effectiveRole.value) && hasWriteScope.value,
)
const isOrgAdmin = computed(
  () => effectiveRole.value === 'ORG_ADMIN' && hasWriteScope.value,
)
const activeTab = ref('points')
const pointLoading = ref(false)
const points = ref([])
const allPoints = ref([])
const pointTotal = ref(0)
const pointFilters = reactive({ keyword: '', dataType: '' })
const pointPagination = reactive({ current: 1, size: 10 })
const pointDialogVisible = ref(false)
const pointDialogMode = ref('create')
const currentPoint = ref(null)
const retentionLoading = ref(false)
const retentionPolicies = ref([])
const policyDialogVisible = ref(false)
const currentPolicy = ref(null)
const legacyLoading = ref(false)
const cacheLoading = ref(false)
const legacyMetrics = ref([])
const legacyTotal = ref(0)
const legacyFilters = reactive({ metricKey: '', metricName: '' })
const legacyPagination = reactive({ current: 1, size: 10 })
const legacyDialogVisible = ref(false)
const legacyDialogMode = ref('create')
const currentLegacyMetric = ref(null)

async function fetchPoints() {
  pointLoading.value = true
  try {
    const params = { current: pointPagination.current, size: pointPagination.size }
    if (pointFilters.keyword.trim()) params.keyword = pointFilters.keyword.trim()
    if (pointFilters.dataType) params.dataType = pointFilters.dataType
    const result = await getPointDefinitionPage(params)
    points.value = result.data?.records || []
    pointTotal.value = result.data?.total || 0
  } finally {
    pointLoading.value = false
  }
}

async function fetchAllPoints() {
  const result = await getPointDefinitionPage({ current: 1, size: 100 })
  allPoints.value = result.data?.records || []
}

function searchPoints() {
  pointPagination.current = 1
  void fetchPoints()
}

function resetPointFilters() {
  pointFilters.keyword = ''
  pointFilters.dataType = ''
  pointPagination.current = 1
  void fetchPoints()
}

function createPoint() {
  pointDialogMode.value = 'create'
  currentPoint.value = null
  pointDialogVisible.value = true
}

function editPoint(row) {
  pointDialogMode.value = 'edit'
  currentPoint.value = row
  pointDialogVisible.value = true
}

async function savePoint(payload) {
  if (pointDialogMode.value === 'create') {
    await createPointDefinition(payload)
    ElMessage.success('点位定义已创建')
  } else {
    const updatePayload = { ...payload }
    delete updatePayload.pointCode
    await updatePointDefinition(currentPoint.value.id, updatePayload)
    ElMessage.success('点位定义已更新')
  }
  pointDialogVisible.value = false
  await Promise.all([fetchPoints(), fetchAllPoints(), fetchRetentionPolicies()])
}

async function fetchRetentionPolicies() {
  retentionLoading.value = true
  try {
    const result = await getMetricRetentionList()
    retentionPolicies.value = result.data || []
  } finally {
    retentionLoading.value = false
  }
}

function pointName(pointId) {
  const point = allPoints.value.find((item) => item.id === pointId)
  return point ? `${point.pointName} (${point.pointCode})` : `点位 #${pointId}`
}

function createPolicy() {
  currentPolicy.value = null
  policyDialogVisible.value = true
}

function editPolicy(row) {
  currentPolicy.value = row
  policyDialogVisible.value = true
}

async function savePolicy(payload) {
  await upsertMetricRetention(payload)
  ElMessage.success('保留策略已保存')
  policyDialogVisible.value = false
  await fetchRetentionPolicies()
}

async function fetchLegacyMetrics() {
  legacyLoading.value = true
  try {
    const params = { current: legacyPagination.current, size: legacyPagination.size }
    if (legacyFilters.metricKey.trim()) params.metricKey = legacyFilters.metricKey.trim()
    if (legacyFilters.metricName.trim()) params.metricName = legacyFilters.metricName.trim()
    const result = await getMetricPage(params)
    legacyMetrics.value = result.data?.records || []
    legacyTotal.value = result.data?.total || 0
  } finally {
    legacyLoading.value = false
  }
}

function searchLegacy() {
  legacyPagination.current = 1
  void fetchLegacyMetrics()
}

function resetLegacy() {
  legacyFilters.metricKey = ''
  legacyFilters.metricName = ''
  legacyPagination.current = 1
  void fetchLegacyMetrics()
}

function createLegacyMetric() {
  legacyDialogMode.value = 'create'
  currentLegacyMetric.value = null
  legacyDialogVisible.value = true
}

function editLegacyMetric(row) {
  legacyDialogMode.value = 'edit'
  currentLegacyMetric.value = row
  legacyDialogVisible.value = true
}

async function saveLegacyMetric(payload) {
  if (legacyDialogMode.value === 'create') {
    await createMetric(payload)
    ElMessage.success('兼容指标已创建')
  } else {
    await updateMetric(payload)
    ElMessage.success('兼容指标已更新')
  }
  legacyDialogVisible.value = false
  await fetchLegacyMetrics()
}

async function removeLegacyMetric(row) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除指标“${row.metricName}”？`,
    '删除确认',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) return
  await deleteMetric(row.id)
  ElMessage.success('兼容指标已删除')
  await fetchLegacyMetrics()
}

async function refreshLegacyCache() {
  cacheLoading.value = true
  try {
    await refreshMetricCache()
    ElMessage.success('指标缓存已刷新')
  } finally {
    cacheLoading.value = false
  }
}

onMounted(async () => {
  const tasks = [
    fetchPoints(),
    fetchAllPoints(),
    fetchRetentionPolicies(),
  ]
  if (canReadLegacy.value) tasks.push(fetchLegacyMetrics())
  await Promise.all(tasks)
})
</script>

<style scoped>
.page-shell {
  min-width: 0;
}

.page-header {
  margin-bottom: 14px;
}

.page-header h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.tab-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 12px;
}

.tab-toolbar.compact {
  align-items: center;
}

.type-select {
  width: 150px;
}

.section-note {
  color: var(--color-text-3);
  font-size: 13px;
}

.legacy-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
