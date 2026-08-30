<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">告警通道</h2>
        <p class="page-desc">
          IM webhook 通知渠道：站内/邮件之外并行推送，支持企业微信群机器人、钉钉、飞书、ntfy、Telegram 与通用 JSON。
          推送范围含新告警、升级、再提醒与恢复；按最高推送级别过滤（数字越小越紧急）。
        </p>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增渠道</el-button>
    </div>

    <el-table :data="channels" v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="TYPE_TAGS[row.channelType] || 'info'">
            {{ TYPE_NAMES[row.channelType] || row.channelType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="webhookUrl" label="Webhook 地址" min-width="260" show-overflow-tooltip />
      <el-table-column label="最高推送级别" width="120">
        <template #default="{ row }">
          <span class="num">L{{ row.maxAlarmLevel }}（含）以上</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="v => toggleChannel(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleTest(row)">测试发送</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑渠道' : '新增渠道'" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="如 运维值班群" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.channelType" style="width: 100%" @change="form.credentialJson = defaultCredential(form.channelType)">
            <el-option v-for="t in TYPE_ORDER" :key="t.value" :value="t.value" :label="t.label" />
          </el-select>
          <div class="form-tip">{{ TYPE_HINTS[form.channelType] }}</div>
        </el-form-item>
        <el-form-item label="Webhook 地址" required>
          <el-input v-model="form.webhookUrl" placeholder="各平台机器人创建后获取" />
        </el-form-item>
        <el-form-item label="凭证 JSON">
          <el-input v-model="form.credentialJson" type="textarea" :rows="3" placeholder='按渠道填写，如 {"secret":"SECxxx"}；留空表示无附加凭证' />
          <div class="form-tip">{{ CREDENTIAL_HINTS[form.channelType] }}</div>
        </el-form-item>
        <el-form-item label="最高推送级别">
          <el-select v-model="form.maxAlarmLevel" style="width: 100%">
            <el-option v-for="l in 5" :key="l" :value="l" :label="`L${l} 及以上（${LEVEL_NAMES[l]}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
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
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createWebhookChannel,
  deleteWebhookChannel,
  listWebhookChannels,
  testWebhookChannel,
  updateWebhookChannel,
} from '@/api/webhookChannel'

const LEVEL_NAMES = { 1: '紧急', 2: '严重', 3: '一般', 4: '警告', 5: '提示' }
const TYPE_ORDER = [
  { value: 'WECOM', label: '企业微信（群机器人）' },
  { value: 'DINGTALK', label: '钉钉' },
  { value: 'FEISHU', label: '飞书' },
  { value: 'NTFY', label: 'ntfy' },
  { value: 'TELEGRAM', label: 'Telegram' },
  { value: 'GENERIC', label: '通用 JSON' },
]
const TYPE_NAMES = Object.fromEntries(TYPE_ORDER.map(t => [t.value, t.label]))
const TYPE_TAGS = {
  WECOM: 'success', DINGTALK: 'primary', FEISHU: 'warning', NTFY: 'info', TELEGRAM: 'info', GENERIC: 'info',
}
const TYPE_HINTS = {
  WECOM: '企微群机器人 URL 自带 key；每个机器人限 20 条/分钟',
  DINGTALK: '注意：钉钉已停建新自定义机器人，存量可用；新接入建议创建"企业内部应用机器人"（报文/加签兼容）',
  FEISHU: '飞书自定义机器人，支持关键词/IP/签名三种安全设置；限 100 条/分钟、5 条/秒',
  NTFY: 'ntfy 主题 URL（如 https://ntfy.sh/your-topic），告警级别自动映射推送优先级',
  TELEGRAM: '完整 sendMessage URL（含 bot token）；chat_id 填在凭证 JSON',
  GENERIC: 'POST 平台标准告警 JSON 到任意地址',
}
const CREDENTIAL_HINTS = {
  WECOM: '无需凭证（key 已在 URL 内）',
  DINGTALK: '{"secret":"SECxxx"}——加签安全设置时必填；关键词方式留空',
  FEISHU: '{"secret":"xxx"}——签名校验开启时必填',
  NTFY: '{"token":"tk_xxx"}——主题需要访问控制时填写',
  TELEGRAM: '{"chat_id":"数字ID或@频道名"}——必填',
  GENERIC: '{"authHeader":"Bearer xxx"}——可选鉴权头',
}

const channels = ref([])
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const form = reactive({
  id: null, name: '', channelType: 'WECOM', webhookUrl: '',
  credentialJson: '', maxAlarmLevel: 3, enabled: false,
})

function defaultCredential(type) {
  if (type === 'TELEGRAM') return '{"chat_id":""}'
  if (type === 'DINGTALK' || type === 'FEISHU') return '{"secret":""}'
  if (type === 'NTFY') return '{"token":""}'
  if (type === 'GENERIC') return '{"authHeader":""}'
  return ''
}

async function fetchChannels() {
  loading.value = true
  try {
    const res = await listWebhookChannels()
    channels.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message || '渠道加载失败')
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  Object.assign(form, {
    id: null, name: '', channelType: 'WECOM', webhookUrl: '',
    credentialJson: '', maxAlarmLevel: 3, enabled: false,
  })
  formVisible.value = true
}

function handleEdit(row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    channelType: row.channelType,
    webhookUrl: row.webhookUrl,
    credentialJson: row.credentialJson || '',
    maxAlarmLevel: row.maxAlarmLevel ?? 3,
    enabled: row.enabled === 1,
  })
  formVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  if (!form.webhookUrl.trim()) {
    ElMessage.warning('Webhook 地址不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      channelType: form.channelType,
      webhookUrl: form.webhookUrl.trim(),
      credentialJson: form.credentialJson || null,
      maxAlarmLevel: form.maxAlarmLevel,
      enabled: form.enabled,
    }
    if (form.id) {
      await updateWebhookChannel(form.id, payload)
      ElMessage.success('渠道已更新')
    } else {
      await createWebhookChannel(payload)
      ElMessage.success('渠道已创建')
    }
    formVisible.value = false
    fetchChannels()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleChannel(row, enabled) {
  try {
    await updateWebhookChannel(row.id, {
      name: row.name,
      channelType: row.channelType,
      webhookUrl: row.webhookUrl,
      credentialJson: row.credentialJson,
      maxAlarmLevel: row.maxAlarmLevel,
      enabled,
    })
    row.enabled = enabled ? 1 : 0
  } catch (e) {
    ElMessage.error(e.message || '状态更新失败')
  }
}

async function handleTest(row) {
  try {
    const res = await testWebhookChannel(row.id)
    ElMessage.success(`测试发送成功：${res.data || 'HTTP 2xx'}`)
  } catch (e) {
    ElMessage.error(e.message || '测试发送失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除渠道「${row.name}」？`, '删除渠道', { type: 'warning' })
    await deleteWebhookChannel(row.id)
    ElMessage.success('渠道已删除')
    fetchChannels()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

onMounted(fetchChannels)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  gap: 16px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 18px;
}

.page-desc {
  margin: 0;
  color: var(--color-text-3);
  font-size: 13px;
  max-width: 720px;
  line-height: 1.6;
}

.form-tip {
  width: 100%;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.5;
}
</style>
