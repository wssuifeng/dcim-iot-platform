<template>
  <section>
    <h1>注册与组织审核</h1>
    <el-form :model="settings" inline>
      <el-form-item label="开放账号注册"><el-switch v-model="settings.accountRegistrationEnabled" /></el-form-item>
      <el-form-item label="组织注册模式">
        <el-radio-group v-model="settings.organizationRegistrationMode">
          <el-radio-button value="CLOSED">关闭</el-radio-button><el-radio-button value="REVIEW">审核</el-radio-button><el-radio-button value="OPEN">开放</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-button type="primary" @click="save">保存</el-button>
    </el-form>
    <el-divider />
    <el-table :data="applications">
      <el-table-column prop="proposedOrgName" label="组织名称" /><el-table-column prop="proposedOrgCode" label="编码" /><el-table-column prop="applicantUserId" label="申请人" />
      <el-table-column label="操作" width="160"><template #default="scope"><el-button type="success" link @click="approve(scope.row.id)">通过</el-button><el-button type="danger" link @click="reject(scope.row.id)">拒绝</el-button></template></el-table-column>
    </el-table>
  </section>
</template>

<script setup>
defineOptions({ name: 'PlatformRegistrationPage' })
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { approveOrganizationApplication, getOrganizationApplications, getRegistrationSettings, rejectOrganizationApplication, updateRegistrationSettings } from '@/api/platformRegistration'
const settings = reactive({ accountRegistrationEnabled: true, organizationRegistrationMode: 'REVIEW' }); const applications = ref([])
async function load() { Object.assign(settings, (await getRegistrationSettings()).data); applications.value = (await getOrganizationApplications()).data || [] }
async function save() { await updateRegistrationSettings(settings); ElMessage.success('设置已保存') }
async function approve(id) { await approveOrganizationApplication(id, '批准'); await load() }
async function reject(id) { await rejectOrganizationApplication(id, '拒绝'); await load() }
onMounted(load)
</script>
