<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">零散设备接入</h2>
        <p class="page-desc">
          自定义报文映射（ADR-009）：设备直连开放通道（MQTT 子凭证 / TB token 通道）后，把原始报文里的字段
          手动映射到平台指标字典，平台自动转译入库——归属校验、首包自动注册、幂等与告警全走既有链路。
          模板按机房生效，机内多种设备用特征匹配路由；未匹配报文每凭证留存最新一条供配置参照。
        </p>
      </div>
      <div class="header-actions">
        <el-select v-model="roomId" placeholder="选择机房" style="width: 220px" @change="loadMappings">
          <el-option v-for="room in rooms" :key="room.id" :value="room.id" :label="room.roomName" />
        </el-select>
        <el-button type="primary" :icon="Plus" :disabled="!roomId" @click="handleAdd">新增映射模板</el-button>
      </div>
    </div>

    <el-table :data="mappings" v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="模板名称" min-width="140" />
      <el-table-column label="特征匹配" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag v-for="(f, i) in row.features" :key="i" size="small" type="info" class="feature-tag">
            {{ f.path }} {{ f.op }}{{ f.value ? ' ' + f.value : '' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="设备编码" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.deviceCodePath || row.deviceCodeFixed }}</template>
      </el-table-column>
      <el-table-column label="字段映射" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="num">{{ row.mappings.map(m => `${m.path}→${m.pointCode}`).join('，') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="v => toggleEnabled(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>选择机房后展示该机房的映射模板</template>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑映射模板' : '新增映射模板'" width="720px" top="6vh">
      <el-form :model="form" label-width="110px">
        <el-form-item label="模板名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="如 某牌温湿度开关" />
        </el-form-item>

        <el-form-item label="特征匹配" required>
          <div class="rule-list">
            <div v-for="(f, i) in form.features" :key="i" class="rule-row">
              <el-input v-model="f.path" placeholder="报文路径，如 model" class="rule-path" />
              <el-select v-model="f.op" class="rule-op">
                <el-option value="eq" label="等于" />
                <el-option value="contains" label="包含" />
                <el-option value="exists" label="存在" />
              </el-select>
              <el-input v-if="f.op !== 'exists'" v-model="f.value" placeholder="比对值" class="rule-value" />
              <el-button link type="danger" :icon="Delete" @click="form.features.splice(i, 1)" />
            </div>
            <el-button size="small" :icon="Plus" @click="form.features.push({ path: '', op: 'eq', value: '' })">
              加特征
            </el-button>
            <div class="form-tip">全部特征命中才路由到本模板；同一机房多种设备靠特征区分型号</div>
          </div>
        </el-form-item>

        <el-form-item label="设备编码" required>
          <el-radio-group v-model="form.deviceMode" @change="onDeviceModeChange">
            <el-radio value="path">从报文提取</el-radio>
            <el-radio value="fixed">固定值</el-radio>
          </el-radio-group>
          <el-input
            v-if="form.deviceMode === 'path'"
            v-model="form.deviceCodePath"
            placeholder="报文路径，如 sn 或 data.deviceId"
          />
          <el-input v-else v-model="form.deviceCodeFixed" placeholder="固定设备编码，如 esp-thermo-01" />
        </el-form-item>

        <el-form-item label="采样时间路径">
          <el-input v-model="form.sampleTimePath" placeholder="可空=服务器接收时刻；填毫秒/秒级时间戳路径，如 ts" />
        </el-form-item>

        <el-form-item label="字段映射" required>
          <div class="rule-list">
            <div v-for="(m, i) in form.mappings" :key="i" class="rule-row">
              <el-input v-model="m.path" placeholder="报文路径，如 data.temp" class="rule-path" />
              <el-select v-model="m.pointCode" filterable placeholder="平台指标" class="rule-point">
                <el-option v-for="p in points" :key="p.pointCode" :value="p.pointCode"
                  :label="`${p.pointName || p.pointCode}（${p.pointCode}）`" />
              </el-select>
              <el-input v-model="m.scale" placeholder="缩放(可空)" class="rule-scale" />
              <el-button link type="danger" :icon="Delete" @click="form.mappings.splice(i, 1)" />
            </div>
            <el-button size="small" :icon="Plus" @click="form.mappings.push({ path: '', pointCode: '', scale: '' })">
              加映射
            </el-button>
            <div class="form-tip">路径=报文 JSON 内部点路径（支持数组下标 items[0].temp）；缩放=原值×系数（如 raw 452 × 0.1 = 45.2）</div>
          </div>
        </el-form-item>

        <el-form-item label="原始报文">
          <div class="raw-block">
            <div class="raw-toolbar">
              <el-button size="small" :disabled="!roomId" @click="pullRawMessage">
                拉取平台留存的最新报文
              </el-button>
              <el-button size="small" type="primary" :loading="dryRunning" @click="runDryRun">测试转译</el-button>
            </div>
            <el-input v-model="rawMessage" type="textarea" :rows="5"
              placeholder='设备上报的原始 JSON 报文，如 {"model":"TH-100","sn":"dev-9","data":{"temp":"452"}}' />
            <div v-if="dryRunResult" class="dry-result">
              <el-alert v-for="(note, i) in dryRunResult.notes" :key="'n' + i" :title="note"
                type="warning" :closable="false" class="dry-note" />
              <template v-if="dryRunResult.deviceCode">
                <div class="dry-summary">
                  设备编码：<b>{{ dryRunResult.deviceCode }}</b>
                  <span v-if="!dryRunResult.matchedMapping" class="dry-warn">（特征未命中，以下为估算）</span>
                </div>
                <el-table :data="dryRunResult.samples" size="small" max-height="200">
                  <el-table-column prop="pointCode" label="指标" width="140" />
                  <el-table-column prop="value" label="数值" width="120" />
                  <el-table-column prop="sampleTime" label="采样时间" min-width="160" />
                </el-table>
              </template>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { getCloudRoomPage } from '@/api/cloudRoom'
import { getPointDefinitionPage } from '@/api/pointDefinition'
import {
  listPayloadMappings,
  createPayloadMapping,
  updatePayloadMapping,
  deletePayloadMapping,
  dryRunPayloadMapping,
  getLatestRawMessage,
} from '@/api/payloadMapping'

defineOptions({ name: 'IngestMapping' })

const rooms = ref([])
const roomId = ref(null)
const mappings = ref([])
const loading = ref(false)
const points = ref([])

const formVisible = ref(false)
const saving = ref(false)
const dryRunning = ref(false)
const rawMessage = ref('')
const dryRunResult = ref(null)
const form = ref(emptyForm())

function emptyForm() {
  return {
    id: null,
    name: '',
    cloudRoomId: null,
    enabled: 1,
    features: [{ path: '', op: 'eq', value: '' }],
    deviceMode: 'path',
    deviceCodePath: '',
    deviceCodeFixed: '',
    sampleTimePath: '',
    mappings: [{ path: '', pointCode: '', scale: '' }],
  }
}

async function loadRooms() {
  const res = await getCloudRoomPage({ current: 1, size: 100 }).catch(() => null)
  rooms.value = res?.data?.records || res?.data || []
  if (rooms.value.length && !roomId.value) {
    roomId.value = rooms.value[0].id
  }
  if (roomId.value) await loadMappings()
}

async function loadMappings() {
  loading.value = true
  try {
    const res = await listPayloadMappings(roomId.value)
    mappings.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadPoints() {
  const res = await getPointDefinitionPage({ current: 1, size: 100 }).catch(() => null)
  const data = res?.data
  points.value = data?.records || data || []
}

function handleAdd() {
  form.value = emptyForm()
  form.value.cloudRoomId = roomId.value
  dryRunResult.value = null
  rawMessage.value = ''
  formVisible.value = true
}

function handleEdit(row) {
  form.value = {
    id: row.id,
    name: row.name,
    cloudRoomId: row.cloudRoomId,
    enabled: row.enabled,
    features: row.features.length ? row.features.map(f => ({ ...f })) : [{ path: '', op: 'eq', value: '' }],
    deviceMode: row.deviceCodePath ? 'path' : 'fixed',
    deviceCodePath: row.deviceCodePath || '',
    deviceCodeFixed: row.deviceCodeFixed || '',
    sampleTimePath: row.sampleTimePath || '',
    mappings: row.mappings.length ? row.mappings.map(m => ({ ...m, scale: m.scale ?? '' }))
      : [{ path: '', pointCode: '', scale: '' }],
  }
  dryRunResult.value = null
  formVisible.value = true
}

function onDeviceModeChange() {
  // 切换来源模式时清空另一侧，避免提交歧义
  if (form.value.deviceMode === 'path') form.value.deviceCodeFixed = ''
  else form.value.deviceCodePath = ''
}

async function toggleEnabled(row, value) {
  await updatePayloadMapping(row.id, { ...row, enabled: value ? 1 : 0 })
  ElMessage.success(value ? '已启用' : '已停用')
  await loadMappings()
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`删除映射模板「${row.name}」？`, '确认', { type: 'warning' })
  await deletePayloadMapping(row.id)
  ElMessage.success('已删除')
  await loadMappings()
}

async function pullRawMessage() {
  const res = await getLatestRawMessage(roomId.value).catch(() => null)
  if (res?.data?.content) {
    rawMessage.value = res.data.content
    ElMessage.success('已填充平台留存的最新报文')
  } else {
    ElMessage.info('暂无留存报文：让设备先发一条报文到开放通道，再回来拉取')
  }
}

function buildRequest() {
  const f = form.value
  return {
    mapping: {
      id: f.id,
      name: f.name,
      cloudRoomId: f.cloudRoomId,
      enabled: f.enabled,
      features: f.features.filter(x => x.path),
      deviceCodePath: f.deviceMode === 'path' ? f.deviceCodePath : '',
      deviceCodeFixed: f.deviceMode === 'fixed' ? f.deviceCodeFixed : '',
      sampleTimePath: f.sampleTimePath,
      mappings: f.mappings
        .filter(x => x.path && x.pointCode)
        .map(x => ({ path: x.path, pointCode: x.pointCode, scale: x.scale === '' ? null : x.scale })),
    },
    rawMessage: rawMessage.value,
  }
}

async function runDryRun() {
  if (!rawMessage.value.trim()) {
    ElMessage.warning('先粘贴或拉取一条原始报文')
    return
  }
  dryRunning.value = true
  try {
    const res = await dryRunPayloadMapping(buildRequest())
    dryRunResult.value = res.data
  } finally {
    dryRunning.value = false
  }
}

async function handleSave() {
  const { mapping } = buildRequest()
  if (!mapping.name) return ElMessage.warning('请填写模板名称')
  if (!mapping.features.length) return ElMessage.warning('至少配置一条特征匹配')
  if (!mapping.mappings.length) return ElMessage.warning('至少配置一条字段映射')
  if (!mapping.deviceCodePath && !mapping.deviceCodeFixed) {
    return ElMessage.warning('设备编码来源必须二选一')
  }
  saving.value = true
  try {
    if (mapping.id) await updatePayloadMapping(mapping.id, mapping)
    else await createPayloadMapping(mapping)
    ElMessage.success('已保存')
    formVisible.value = false
    await loadMappings()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadRooms(), loadPoints()])
})
</script>

<style scoped>
.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.feature-tag {
  margin-right: 6px;
}

.num {
  font-variant-numeric: tabular-nums;
}

.rule-list {
  width: 100%;
}

.rule-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  align-items: center;
}

.rule-path {
  flex: 2;
}

.rule-op {
  width: 90px;
  flex-shrink: 0;
}

.rule-value {
  flex: 1.4;
}

.rule-point {
  flex: 2;
}

.rule-scale {
  width: 110px;
  flex-shrink: 0;
}

.form-tip {
  color: var(--color-text-3);
  font-size: 12px;
  margin-top: 4px;
}

.raw-block {
  width: 100%;
}

.raw-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.dry-result {
  margin-top: 10px;
}

.dry-note {
  margin-bottom: 6px;
}

.dry-summary {
  margin: 6px 0;
  font-size: 13px;
}

.dry-warn {
  color: var(--el-color-warning);
  margin-left: 8px;
  font-size: 12px;
}
</style>
