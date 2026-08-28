<template>
  <page-shell title="告警模版" subtitle="可命名的告警规则融合模版：类型绑定基础版，设备可改绑自定义模版">
    <filter-bar>
      <el-input
        v-model="filter.keyword"
        placeholder="搜索名称/编码"
        clearable
        style="width: 220px"
        @keyup.enter="fetchList"
        @clear="fetchList"
      />
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 120px" @change="fetchList">
        <el-option label="启用" :value="0" />
        <el-option label="停用" :value="1" />
      </el-select>
      <el-button :icon="Search" @click="fetchList">查询</el-button>
      <div style="flex: 1" />
      <el-button v-if="canWrite" type="primary" :icon="Plus" @click="handleCreate">新建模版</el-button>
    </filter-bar>

    <data-table-shell
      :loading="loading"
      :error="!!error"
      :data-count="templates.length"
      empty-text="暂无告警模版，点击右上角新建"
      @retry="fetchList"
    >
      <el-table :data="templates" style="width: 100%">
        <el-table-column label="编码" min-width="160">
          <template #default="{ row }">
            <span class="num">{{ row.templateCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="templateName" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="规则行" width="90" align="center">
          <template #default="{ row }">
            <span class="num">{{ row.itemCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="绑定引用" min-width="160">
          <template #default="{ row }">
            <span v-if="row.typeRefCount === 0 && row.deviceRefCount === 0" class="text-muted">未被引用</span>
            <span v-else>
              <el-tag v-if="row.typeRefCount > 0" size="small" type="primary" class="num">
                类型×{{ row.typeRefCount }}
              </el-tag>
              <el-tag v-if="row.deviceRefCount > 0" size="small" type="warning" class="num">
                设备×{{ row.deviceRefCount }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
              {{ row.status === 0 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">
            <span class="num text-muted">{{ formatTime(row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canWrite" label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            <el-button link type="primary" @click="handleDuplicate(row)">复制</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </data-table-shell>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="pagination.current"
      v-model:page-size="pagination.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @change="fetchList"
    />

    <!-- 模版信息编辑 -->
    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建告警模版' : '编辑告警模版'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="模版编码" required>
          <el-input v-model="form.templateCode" placeholder="如 TEMP_HUM_STD" maxlength="50" />
        </el-form-item>
        <el-form-item label="模版名称" required>
          <el-input v-model="form.templateName" placeholder="如 温湿度标准监控" maxlength="100" />
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="300" placeholder="如 机房通用温湿度阈值" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 规则行编辑器 -->
    <template-editor-drawer
      v-model:visible="editorVisible"
      :template-id="editorTemplateId"
      :can-write="canWrite"
      :points="points"
      @refresh="fetchList"
    />
  </page-shell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore, ROLES } from '@/stores/auth'
import {
  createTemplate,
  deleteTemplate,
  duplicateTemplate,
  pageTemplates,
  updateTemplate,
} from '@/api/alarmTemplate'
import { getPointDefinitionPage } from '@/api/pointDefinition'
import TemplateEditorDrawer from './TemplateEditorDrawer.vue'

const authStore = useAuthStore()
const canWrite = computed(() => authStore.role === ROLES.ADMIN || authStore.role === ROLES.OPERATOR)

const filter = reactive({ keyword: '', status: null })
const pagination = reactive({ current: 1, size: 10 })
const templates = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref(false)
const points = ref([])

const formVisible = ref(false)
const formMode = ref('create')
const form = reactive({ id: null, templateCode: '', templateName: '', description: '', enabled: true })
const saving = ref(false)

const editorVisible = ref(false)
const editorTemplateId = ref(null)

async function fetchList() {
  loading.value = true
  error.value = false
  try {
    const res = await pageTemplates({
      current: pagination.current,
      size: pagination.size,
      keyword: filter.keyword || undefined,
      status: filter.status ?? undefined,
    })
    templates.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function fetchPoints() {
  try {
    const res = await getPointDefinitionPage({ current: 1, size: 200 })
    points.value = (res.data.records || []).map(p => ({ id: p.id, pointCode: p.pointCode, pointName: p.pointName, unit: p.unit }))
  } catch (e) {
    points.value = []
  }
}

function handleCreate() {
  formMode.value = 'create'
  Object.assign(form, { id: null, templateCode: '', templateName: '', description: '', enabled: true })
  formVisible.value = true
}

function openEditor(row) {
  editorTemplateId.value = row.id
  editorVisible.value = true
}

async function handleSave() {
  if (!form.templateCode.trim() || !form.templateName.trim()) {
    ElMessage.warning('模版编码与名称不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      templateCode: form.templateCode.trim(),
      templateName: form.templateName.trim(),
      description: form.description || null,
      status: form.enabled,
    }
    if (formMode.value === 'create') {
      await createTemplate(payload)
      ElMessage.success('模版已创建')
    } else {
      await updateTemplate(form.id, payload)
      ElMessage.success('模版已更新')
    }
    formVisible.value = false
    fetchList()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDuplicate(row) {
  try {
    const { value } = await ElMessageBox.prompt('新模版编码（全局唯一）', `复制「${row.templateName}」`, {
      inputValue: `${row.templateCode}_COPY`,
      inputPattern: /^[A-Za-z0-9_-]+$/,
      inputErrorMessage: '编码仅允许字母数字下划线短横线',
    })
    await duplicateTemplate(row.id, value.trim(), `${row.templateName}-副本`)
    ElMessage.success('模版已复制')
    fetchList()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除模版「${row.templateName}」？${row.typeRefCount + row.deviceRefCount > 0 ? '该模版仍被绑定引用，删除将被拒绝' : '规则行将一并删除'}`,
      '删除告警模版',
      { type: 'warning' },
    )
    await deleteTemplate(row.id)
    ElMessage.success('模版已删除')
    fetchList()
  } catch (e) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  fetchList()
  fetchPoints()
})
</script>

<style scoped>
.pagination {
  justify-content: flex-end;
  margin-top: 14px;
}
.text-muted {
  color: var(--color-text-3);
  font-size: 13px;
}
</style>
