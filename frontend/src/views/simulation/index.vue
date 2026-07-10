<template>
  <section class="page-shell">
    <header class="page-header">
      <div>
        <h1>模拟运行</h1>
        <p>场景定义、运行控制及生成数据追溯</p>
      </div>
      <el-button v-if="canManageScenes && activeTab === 'scenes'" type="primary" :icon="Plus" @click="openCreate">
        新增场景
      </el-button>
    </header>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="场景" name="scenes">
        <el-form :inline="true" :model="sceneFilters" class="filter-bar">
          <el-form-item label="关键字">
            <el-input v-model="sceneFilters.keyword" placeholder="场景编码或名称" clearable @keyup.enter="searchScenes" />
          </el-form-item>
          <el-form-item label="组织">
            <el-select v-model="sceneFilters.organizationId" clearable filterable class="organization-select">
              <el-option v-for="item in organizations" :key="item.id" :label="item.orgName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="机房">
            <el-select v-model="sceneFilters.cloudRoomId" clearable filterable class="room-select">
              <el-option v-for="item in rooms" :key="item.id" :label="item.roomName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="sceneFilters.status" clearable class="status-select">
              <el-option label="启用" :value="0" />
              <el-option label="停用" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="searchScenes">查询</el-button>
            <el-button :icon="Refresh" @click="resetSceneFilters">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="sceneLoading" :data="scenes" row-key="id">
          <el-table-column prop="sceneName" label="场景名称" min-width="180" />
          <el-table-column prop="sceneCode" label="场景编码" min-width="150" />
          <el-table-column label="组织 / 机房" min-width="210">
            <template #default="{ row }">{{ organizationName(row.organizationId) }} / {{ roomName(row.cloudRoomId) }}</template>
          </el-table-column>
          <el-table-column prop="deviceTypeCode" label="设备类型" width="140" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" min-width="170" />
          <el-table-column label="操作" min-width="330">
            <template #default="{ row }">
              <el-button link type="primary" :icon="List" @click="openSteps(row)">步骤</el-button>
              <el-button v-if="canRun && row.status === 0" link type="success" :icon="VideoPlay" @click="openRun(row)">运行</el-button>
              <template v-if="canManageScenes">
                <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                <el-button link :type="row.status === 0 ? 'warning' : 'success'" :icon="Switch" @click="toggleSceneStatus(row)">
                  {{ row.status === 0 ? '停用' : '启用' }}
                </el-button>
                <el-button link type="danger" :icon="Delete" @click="removeScene(row)">删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="sceneTotal > 0"
          v-model:current-page="scenePage.current"
          v-model:page-size="scenePage.size"
          :total="sceneTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="pagination"
          @change="fetchScenes"
        />
      </el-tab-pane>

      <el-tab-pane label="运行记录" name="runs">
        <el-form :inline="true" :model="runFilters" class="filter-bar">
          <el-form-item label="场景">
            <el-select v-model="runFilters.sceneId" clearable filterable class="scene-select">
              <el-option v-for="item in scenes" :key="item.id" :label="item.sceneName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="runFilters.status" clearable class="status-select">
              <el-option v-for="item in runStatuses" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="searchRuns">查询</el-button>
            <el-button :icon="Refresh" @click="resetRunFilters">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="runLoading" :data="runs" row-key="id">
          <el-table-column prop="id" label="运行ID" width="90" />
          <el-table-column label="场景" min-width="170">
            <template #default="{ row }">{{ sceneName(row.sceneId) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }"><el-tag :type="runStatusType(row.status)">{{ runStatusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="进度" width="120">
            <template #default="{ row }">{{ row.successSteps || 0 }} / {{ row.totalSteps || 0 }}</template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" min-width="170" />
          <el-table-column prop="endTime" label="结束时间" min-width="170" />
          <el-table-column prop="errorMessage" label="结果" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="openEvents(row)">追溯</el-button>
              <el-button v-if="canRun && ['PENDING', 'RUNNING'].includes(row.status)" link type="danger" :icon="VideoPause" @click="cancelRun(row)">取消</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="runTotal > 0"
          v-model:current-page="runPage.current"
          v-model:page-size="runPage.size"
          :total="runTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="pagination"
          @change="fetchRuns"
        />
      </el-tab-pane>
    </el-tabs>

    <scene-dialog
      v-model:visible="sceneDialogVisible"
      :mode="sceneDialogMode"
      :scene="currentScene"
      :organizations="organizations"
      :rooms="rooms"
      :submitting="submitting"
      @confirm="saveScene"
    />
    <scene-steps-drawer
      v-model:visible="stepsVisible"
      :scene="currentScene"
      :points="points"
      :devices="devices"
      :can-manage="canManageScenes"
    />
    <run-dialog
      v-model:visible="runDialogVisible"
      :scene="currentScene"
      :devices="devices"
      :rooms="rooms"
      :submitting="submitting"
      @confirm="startRun"
    />
    <run-events-drawer v-model:visible="eventsVisible" :run="currentRun" />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Delete,
  Edit,
  List,
  Plus,
  Refresh,
  Search,
  Switch,
  VideoPause,
  VideoPlay,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useScopeStore } from '@/stores/scope'
import { getOrganizationPage } from '@/api/organization'
import { getCloudRoomPage } from '@/api/cloudRoom'
import { getDevicePage } from '@/api/device'
import { getPointDefinitionPage } from '@/api/pointDefinition'
import {
  cancelSimulationRun,
  createSimulationScene,
  deleteSimulationScene,
  getSimulationRunPage,
  getSimulationScenePage,
  startSimulationRun,
  updateSimulationScene,
  updateSimulationSceneStatus,
} from '@/api/simulation'
import RunDialog from './RunDialog.vue'
import RunEventsDrawer from './RunEventsDrawer.vue'
import SceneDialog from './SceneDialog.vue'
import SceneStepsDrawer from './SceneStepsDrawer.vue'

defineOptions({ name: 'SimulationWorkspace' })

const authStore = useAuthStore()
const scopeStore = useScopeStore()
const role = computed(() => scopeStore.role || authStore.normalizedRole)
const canManageScenes = computed(() => role.value === 'ORG_ADMIN' && scopeStore.hasWritePermission)
const canRun = computed(() => ['ORG_ADMIN', 'OPERATOR'].includes(role.value) && scopeStore.hasWritePermission)
const activeTab = ref('scenes')
const sceneLoading = ref(false)
const runLoading = ref(false)
const submitting = ref(false)
const scenes = ref([])
const runs = ref([])
const organizations = ref([])
const rooms = ref([])
const devices = ref([])
const points = ref([])
const sceneTotal = ref(0)
const runTotal = ref(0)
const scenePage = reactive({ current: 1, size: 10 })
const runPage = reactive({ current: 1, size: 10 })
const sceneFilters = reactive({ keyword: '', organizationId: null, cloudRoomId: null, status: null })
const runFilters = reactive({ sceneId: null, status: '' })
const currentScene = ref(null)
const currentRun = ref(null)
const sceneDialogVisible = ref(false)
const sceneDialogMode = ref('create')
const stepsVisible = ref(false)
const runDialogVisible = ref(false)
const eventsVisible = ref(false)
const runStatuses = Object.freeze([
  { label: '等待执行', value: 'PENDING' },
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
])

async function loadReferences() {
  const [organizationResult, roomResult, deviceResult, pointResult] = await Promise.all([
    getOrganizationPage({ current: 1, size: 100 }),
    getCloudRoomPage({ current: 1, size: 100 }),
    getDevicePage({ current: 1, size: 100 }),
    getPointDefinitionPage({ current: 1, size: 100 }),
  ])
  organizations.value = organizationResult.data?.records || []
  rooms.value = roomResult.data?.records || []
  devices.value = deviceResult.data?.records || []
  points.value = pointResult.data?.records || []
}

async function fetchScenes() {
  sceneLoading.value = true
  try {
    const params = { current: scenePage.current, size: scenePage.size }
    if (sceneFilters.keyword.trim()) params.keyword = sceneFilters.keyword.trim()
    if (sceneFilters.organizationId) params.organizationId = sceneFilters.organizationId
    if (sceneFilters.cloudRoomId) params.cloudRoomId = sceneFilters.cloudRoomId
    if (sceneFilters.status !== null && sceneFilters.status !== '') params.status = sceneFilters.status
    const result = await getSimulationScenePage(params)
    scenes.value = result.data?.records || []
    sceneTotal.value = result.data?.total || 0
  } finally {
    sceneLoading.value = false
  }
}

async function fetchRuns() {
  runLoading.value = true
  try {
    const params = { current: runPage.current, size: runPage.size }
    if (runFilters.sceneId) params.sceneId = runFilters.sceneId
    if (runFilters.status) params.status = runFilters.status
    const result = await getSimulationRunPage(params)
    runs.value = result.data?.records || []
    runTotal.value = result.data?.total || 0
  } finally {
    runLoading.value = false
  }
}

function searchScenes() {
  scenePage.current = 1
  void fetchScenes()
}

function resetSceneFilters() {
  Object.assign(sceneFilters, { keyword: '', organizationId: null, cloudRoomId: null, status: null })
  scenePage.current = 1
  void fetchScenes()
}

function searchRuns() {
  runPage.current = 1
  void fetchRuns()
}

function resetRunFilters() {
  Object.assign(runFilters, { sceneId: null, status: '' })
  runPage.current = 1
  void fetchRuns()
}

function handleTabChange(name) {
  if (name === 'runs') void fetchRuns()
}

function openCreate() {
  sceneDialogMode.value = 'create'
  currentScene.value = null
  sceneDialogVisible.value = true
}

function openEdit(row) {
  sceneDialogMode.value = 'edit'
  currentScene.value = row
  sceneDialogVisible.value = true
}

function openSteps(row) {
  currentScene.value = row
  stepsVisible.value = true
}

function openRun(row) {
  currentScene.value = row
  runDialogVisible.value = true
}

function openEvents(row) {
  currentRun.value = row
  eventsVisible.value = true
}

async function saveScene(payload) {
  submitting.value = true
  try {
    if (sceneDialogMode.value === 'create') {
      await createSimulationScene(payload)
      ElMessage.success('场景已创建')
    } else {
      await updateSimulationScene(currentScene.value.id, payload)
      ElMessage.success('场景已更新')
    }
    sceneDialogVisible.value = false
    await fetchScenes()
  } finally {
    submitting.value = false
  }
}

async function toggleSceneStatus(row) {
  const nextStatus = row.status === 0 ? 1 : 0
  await updateSimulationSceneStatus(row.id, nextStatus)
  ElMessage.success(nextStatus === 0 ? '场景已启用' : '场景已停用')
  await fetchScenes()
}

async function removeScene(row) {
  const confirmed = await ElMessageBox.confirm(`确认删除场景“${row.sceneName}”？`, '删除确认', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  await deleteSimulationScene(row.id)
  ElMessage.success('场景已删除')
  await fetchScenes()
}

async function startRun(payload) {
  submitting.value = true
  try {
    await startSimulationRun(payload)
    runDialogVisible.value = false
    activeTab.value = 'runs'
    ElMessage.success('模拟运行已启动')
    await fetchRuns()
  } finally {
    submitting.value = false
  }
}

async function cancelRun(row) {
  const confirmed = await ElMessageBox.confirm(`确认取消运行 #${row.id}？`, '取消确认', {
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  await cancelSimulationRun(row.id)
  ElMessage.success('运行已取消')
  await fetchRuns()
}

function organizationName(id) {
  return organizations.value.find((item) => item.id === id)?.orgName || `组织 #${id}`
}

function roomName(id) {
  if (!id) return '全部机房'
  return rooms.value.find((item) => item.id === id)?.roomName || `机房 #${id}`
}

function sceneName(id) {
  return scenes.value.find((item) => item.id === id)?.sceneName || `场景 #${id}`
}

function runStatusLabel(status) {
  return runStatuses.find((item) => item.value === status)?.label || status
}

function runStatusType(status) {
  return { PENDING: 'info', RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger', CANCELLED: 'warning' }[status]
}

onMounted(async () => {
  await scopeStore.ensureCurrentScope({ silent: true })
  await Promise.all([loadReferences(), fetchScenes()])
})
</script>

<style scoped>
.page-shell {
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 12px;
}

.page-header h1 {
  margin: 0;
  color: #303133;
  font-size: 22px;
}

.page-header p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.filter-bar {
  padding: 14px 16px 0;
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
  background: #ffffff;
}

.organization-select,
.scene-select {
  width: 210px;
}

.room-select {
  width: 190px;
}

.status-select {
  width: 130px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
