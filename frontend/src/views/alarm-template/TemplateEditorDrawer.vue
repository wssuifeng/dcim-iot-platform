<template>
  <el-drawer
    :model-value="visible"
    :title="`规则行编辑 — ${templateName}`"
    size="680px"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
    @open="fetchAll"
  >
    <div class="editor-body">
      <div v-if="canWrite" class="editor-toolbar">
        <el-button type="primary" :icon="Plus" size="small" @click="handleAdd">新增规则行</el-button>
        <span class="editor-tip">METRIC 规则须选指标；OFFLINE 规则阈值=离线分钟数</span>
      </div>

      <!-- 通用规则组（OFFLINE/COMM） -->
      <div v-if="genericItems.length" class="rule-group">
        <div class="rule-group-title">通用规则</div>
        <div v-for="item in genericItems" :key="item.id" :class="['rule-row', `alarm-band-l${item.alarmLevel}`]">
          <div class="rule-row-main">
            <span class="rule-name">{{ item.ruleName }}</span>
            <span class="rule-expr num">
              {{ item.ruleType === 'OFFLINE' ? `离线>${item.threshold}分钟` : item.ruleType }}
            </span>
          </div>
          <level-tag :level="item.alarmLevel" />
          <div class="rule-row-actions">
            <el-switch v-if="canWrite" :model-value="item.enabled === 1" size="small"
                       @change="v => toggleItem(item, v)" />
            <el-button v-if="canWrite" link type="primary" size="small" @click="handleEdit(item)">编辑</el-button>
            <el-button v-if="canWrite" link type="danger" size="small" @click="handleDeleteItem(item)">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 指标分组（METRIC） -->
      <div v-for="group in pointGroups" :key="group.pointId" class="rule-group">
        <div class="rule-group-title">
          {{ group.pointName }}<span v-if="group.unit" class="rule-group-unit">{{ group.unit }}</span>
        </div>
        <div v-for="item in group.items" :key="item.id" :class="['rule-row', `alarm-band-l${item.alarmLevel}`]">
          <div class="rule-row-main">
            <span class="rule-name">{{ item.ruleName }}</span>
            <span class="rule-expr num">{{ item.operator }} {{ item.threshold }}</span>
            <span v-if="(item.breachCount ?? 0) > 1" class="mech-badge">连续×{{ item.breachCount }}</span>
            <span v-if="item.rateLimit != null" class="mech-badge">变化量</span>
            <span v-if="(item.reAlarmSec ?? 0) > 0" class="mech-badge">再提醒</span>
            <span v-if="(item.hysteresis ?? 0) > 0" class="mech-badge">迟滞</span>
          </div>
          <level-tag :level="item.alarmLevel" />
          <div class="rule-row-actions">
            <el-switch v-if="canWrite" :model-value="item.enabled === 1" size="small"
                       @change="v => toggleItem(item, v)" />
            <el-button v-if="canWrite" link type="primary" size="small" @click="handleEdit(item)">编辑</el-button>
            <el-button v-if="canWrite" link type="primary" size="small" @click="handleCopy(item)">复制</el-button>
            <el-button v-if="canWrite" link type="danger" size="small" @click="handleDeleteItem(item)">删除</el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="!loadingItems && items.length === 0" description="模版暂无规则行，点击上方新增" />
    </div>

    <!-- 规则行编辑对话框 -->
    <el-dialog v-model="itemFormVisible" :title="itemForm.id ? '编辑规则行' : '新增规则行'" width="460px" append-to-body>
      <el-form :model="itemForm" label-width="90px">
        <el-form-item label="规则类型">
          <el-radio-group v-model="itemForm.ruleType" :disabled="!!itemForm.id">
            <el-radio-button value="METRIC">阈值</el-radio-button>
            <el-radio-button value="OFFLINE">离线</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="itemForm.ruleType === 'METRIC'" label="指标" required>
          <el-select v-model="itemForm.pointId" :disabled="!!itemForm.id" filterable placeholder="选择指标" style="width: 100%">
            <el-option v-for="p in points" :key="p.id" :value="p.id" :label="`${p.pointName}（${p.pointCode}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则名" required>
          <el-input v-model="itemForm.ruleName" maxlength="100" placeholder="如 高温警告" />
        </el-form-item>
        <el-form-item v-if="itemForm.ruleType === 'METRIC'" label="比较符" required>
          <el-select v-model="itemForm.operator" style="width: 100%">
            <el-option label="大于（GT）" value="GT" />
            <el-option label="小于（LT）" value="LT" />
            <el-option label="大于等于（GE）" value="GE" />
            <el-option label="小于等于（LE）" value="LE" />
            <el-option label="等于（EQ）" value="EQ" />
            <el-option label="不等于（NE）" value="NE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="itemForm.ruleType === 'OFFLINE' ? '离线分钟' : '阈值'" required>
          <el-input-number v-model="itemForm.threshold" :step="1" :min="null" style="width: 100%" />
        </el-form-item>
        <template v-if="itemForm.ruleType === 'METRIC'">
          <el-form-item label="机制增强">
            <el-checkbox v-model="showMechanisms">配置高级机制</el-checkbox>
          </el-form-item>
          <template v-if="showMechanisms">
            <el-form-item label="连续越限">
              <el-input-number v-model="itemForm.breachCount" :min="0" :max="999" :step="1" style="width: 100%" />
              <div class="mech-tip">连续 N 个采样点越限才告警；≤1 为首点越限即告警</div>
            </el-form-item>
            <el-form-item label="变化量限值">
              <el-input-number v-model="itemForm.rateLimit" :min="0" :step="1" :precision="2"
                               :controls="false" placeholder="留空不启用" style="width: 100%" />
              <div class="mech-tip">窗口内变化量超过限值直接告警（疑似火灾/急变）；累计量指标（如累计电量）禁用</div>
            </el-form-item>
            <el-form-item v-if="itemForm.rateLimit != null" label="变化量窗口">
              <el-input-number v-model="itemForm.rateWindowSec" :min="1" :max="86400" :step="10" style="width: 100%">
                <template #append>秒</template>
              </el-input-number>
              <div class="mech-tip">以当前采样为终点回看该窗口，取窗口内最早采样作基准</div>
            </el-form-item>
            <el-form-item label="再告警间隔">
              <el-input-number v-model="itemForm.reAlarmSec" :min="0" :max="604800" :step="60" style="width: 100%">
                <template #append>秒</template>
              </el-input-number>
              <div class="mech-tip">持续越限时按该间隔重发通知；0 不启用</div>
            </el-form-item>
            <el-form-item label="恢复迟滞">
              <el-input-number v-model="itemForm.hysteresis" :min="0" :step="0.5" :precision="2" style="width: 100%" />
              <div class="mech-tip">恢复需回落进安全区（GT：低于阈值-迟滞；LT：高于阈值+迟滞）；0 不启用</div>
            </el-form-item>
          </template>
        </template>
        <el-form-item label="告警级别" required>
          <el-select v-model="itemForm.alarmLevel" style="width: 100%">
            <el-option v-for="l in 5" :key="l" :value="l" :label="`${l} - ${LEVEL_NAMES[l]}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="itemForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveItem">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createTemplateItem,
  deleteTemplateItem,
  getTemplate,
  listTemplateItems,
  updateTemplateItem,
} from '@/api/alarmTemplate'
import LevelTag from './LevelTag.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  templateId: { type: Number, default: null },
  canWrite: { type: Boolean, default: false },
  points: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'refresh'])
const LEVEL_NAMES = { 1: '紧急', 2: '严重', 3: '一般', 4: '警告', 5: '提示' }

const templateName = ref('')
const items = ref([])
const loadingItems = ref(false)
const saving = ref(false)

const itemFormVisible = ref(false)
const showMechanisms = ref(false)
const itemForm = reactive({
  id: null, pointId: null, ruleType: 'METRIC', ruleName: '',
  operator: 'GT', threshold: null, alarmLevel: 3, enabled: true,
  breachCount: 0, rateLimit: null, rateWindowSec: 60, reAlarmSec: 0, hysteresis: 0,
})

const genericItems = computed(() => items.value.filter(i => !i.pointId))
const pointGroups = computed(() => {
  const groups = new Map()
  for (const item of items.value.filter(i => i.pointId)) {
    if (!groups.has(item.pointId)) {
      groups.set(item.pointId, {
        pointId: item.pointId,
        pointName: item.pointName || `指标${item.pointId}`,
        unit: item.unit || '',
        items: [],
      })
    }
    groups.get(item.pointId).items.push(item)
  }
  return [...groups.values()]
})

async function fetchAll() {
  if (!props.templateId) return
  loadingItems.value = true
  try {
    const [tplRes, itemsRes] = await Promise.all([
      getTemplate(props.templateId),
      listTemplateItems(props.templateId),
    ])
    templateName.value = tplRes.data.templateName
    items.value = itemsRes.data || []
  } catch (e) {
    ElMessage.error(e.message || '规则行加载失败')
  } finally {
    loadingItems.value = false
  }
}

watch(() => props.templateId, () => {
  if (props.visible) fetchAll()
})

function handleAdd() {
  Object.assign(itemForm, {
    id: null, pointId: null, ruleType: 'METRIC', ruleName: '',
    operator: 'GT', threshold: null, alarmLevel: 3, enabled: true,
    breachCount: 0, rateLimit: null, rateWindowSec: 60, reAlarmSec: 0, hysteresis: 0,
  })
  showMechanisms.value = false
  itemFormVisible.value = true
}

function applyMechanisms(item) {
  itemForm.breachCount = item.breachCount ?? 0
  itemForm.rateLimit = item.rateLimit !== null && item.rateLimit !== undefined ? Number(item.rateLimit) : null
  itemForm.rateWindowSec = item.rateWindowSec ?? 60
  itemForm.reAlarmSec = item.reAlarmSec ?? 0
  itemForm.hysteresis = item.hysteresis !== null && item.hysteresis !== undefined ? Number(item.hysteresis) : 0
  showMechanisms.value = (item.breachCount ?? 0) > 1 || itemForm.rateLimit !== null
    || (item.reAlarmSec ?? 0) > 0 || itemForm.hysteresis > 0
}

function handleEdit(item) {
  Object.assign(itemForm, {
    id: item.id,
    pointId: item.pointId,
    ruleType: item.ruleType,
    ruleName: item.ruleName,
    operator: item.operator || 'GT',
    threshold: item.threshold !== null ? Number(item.threshold) : null,
    alarmLevel: item.alarmLevel,
    enabled: item.enabled === 1,
  })
  applyMechanisms(item)
  itemFormVisible.value = true
}

function handleCopy(item) {
  Object.assign(itemForm, {
    id: null,
    pointId: item.pointId,
    ruleType: item.ruleType,
    ruleName: item.ruleName,
    operator: item.operator || 'GT',
    threshold: item.threshold !== null ? Number(item.threshold) : null,
    alarmLevel: item.alarmLevel,
    enabled: item.enabled === 1,
  })
  applyMechanisms(item)
  itemFormVisible.value = true
}

async function handleSaveItem() {
  if (!itemForm.ruleName.trim()) {
    ElMessage.warning('规则名不能为空')
    return
  }
  if (itemForm.ruleType === 'METRIC' && !itemForm.pointId) {
    ElMessage.warning('METRIC 规则必须选择指标')
    return
  }
  if (itemForm.threshold === null || itemForm.threshold === undefined) {
    ElMessage.warning('阈值不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      pointId: itemForm.ruleType === 'METRIC' ? itemForm.pointId : null,
      ruleType: itemForm.ruleType,
      ruleName: itemForm.ruleName.trim(),
      operator: itemForm.ruleType === 'METRIC' ? itemForm.operator : null,
      threshold: itemForm.threshold,
      alarmLevel: itemForm.alarmLevel,
      enabled: itemForm.enabled,
    }
    // 机制字段整组提交（组内 rateLimit null=禁用 M2）；OFFLINE/COMM 无机制字段
    if (itemForm.ruleType === 'METRIC') {
      payload.mechanism = {
        breachCount: itemForm.breachCount ?? 0,
        rateLimit: showMechanisms.value ? itemForm.rateLimit : null,
        rateWindowSec: itemForm.rateWindowSec ?? 60,
        reAlarmSec: itemForm.reAlarmSec ?? 0,
        hysteresis: itemForm.hysteresis ?? 0,
      }
    }
    if (itemForm.id) {
      await updateTemplateItem(itemForm.id, payload)
      ElMessage.success('规则行已更新')
    } else {
      await createTemplateItem(props.templateId, payload)
      ElMessage.success('规则行已新增')
    }
    itemFormVisible.value = false
    fetchAll()
    emitRefresh()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleItem(item, enabled) {
  try {
    await updateTemplateItem(item.id, {
      ruleName: item.ruleName,
      operator: item.operator,
      threshold: item.threshold,
      alarmLevel: item.alarmLevel,
      enabled,
    })
    item.enabled = enabled ? 1 : 0
  } catch (e) {
    ElMessage.error(e.message || '状态更新失败')
  }
}

async function handleDeleteItem(item) {
  try {
    await ElMessageBox.confirm(`确定删除规则行「${item.ruleName}」？`, '删除规则行', { type: 'warning' })
    await deleteTemplateItem(item.id)
    ElMessage.success('规则行已删除')
    fetchAll()
    emitRefresh()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

function emitRefresh() {
  emit('refresh')
}
</script>

<style scoped>
.editor-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.editor-tip {
  color: var(--color-text-3);
  font-size: 12px;
}

.rule-group-title {
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  padding-left: 10px;
}

.rule-group-unit {
  color: var(--color-text-3);
  font-weight: 400;
  margin-left: 6px;
}

.rule-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 10px 9px 13px;
  margin-bottom: 6px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--alarm-band, var(--color-border));
  border-radius: var(--radius-sm);
}

.rule-row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.rule-name {
  color: var(--color-text);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-expr {
  color: var(--color-text-2);
  font-size: 12px;
  flex-shrink: 0;
}

.mech-badge {
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
}

.mech-tip {
  width: 100%;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.5;
}

.rule-row-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
</style>
