<template>
  <section class="organization-account">
    <header><h1>我的组织</h1><el-button :icon="Refresh" @click="load">刷新</el-button></header>
    <el-table v-if="store.memberships.length" :data="store.memberships">
      <el-table-column prop="organizationId" label="组织ID" width="120" />
      <el-table-column prop="membershipRole" label="角色" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作" width="120">
        <template #default="scope"><el-button type="primary" link @click="enter(scope.row.organizationId)">进入</el-button></template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="尚未加入组织" />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="创建组织" name="create">
        <el-form :model="createForm" label-width="90px" class="form">
          <el-form-item label="组织名称"><el-input v-model="createForm.orgName" /></el-form-item>
          <el-form-item label="组织编码"><el-input v-model="createForm.orgCode" /></el-form-item>
          <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" /></el-form-item>
          <el-button type="primary" @click="applyCreate">提交申请</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="加入组织" name="join">
        <div class="search"><el-input v-model="keyword" placeholder="组织名称或编码" /><el-button :icon="Search" @click="search">搜索</el-button></div>
        <el-table :data="organizations">
          <el-table-column prop="orgName" label="组织名称" /><el-table-column prop="orgCode" label="组织编码" />
          <el-table-column label="操作" width="120"><template #default="scope"><el-button type="primary" link :disabled="!scope.row.joinApplicationEnabled" @click="join(scope.row.id)">申请加入</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup>
defineOptions({ name: 'AccountOrganizationsPage' })
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getPublicOrganizations, submitJoinApplication, submitOrganizationApplication } from '@/api/accountOrganization'
import { useOrganizationStore } from '@/stores/organization'

const router = useRouter(); const store = useOrganizationStore(); const activeTab = ref('create')
const keyword = ref(''); const organizations = ref([])
const createForm = reactive({ orgName: '', orgCode: '', description: '' })
const load = () => store.loadMemberships()
async function enter(id) { store.selectOrganization(id); await router.push('/dashboard') }
async function applyCreate() { await submitOrganizationApplication(createForm); ElMessage.success('申请已提交') }
async function search() { organizations.value = (await getPublicOrganizations(keyword.value)).data || [] }
async function join(id) { await submitJoinApplication(id, '申请加入组织'); ElMessage.success('加入申请已提交') }
onMounted(load)
</script>

<style scoped>
.organization-account { max-width: 980px; margin: 0 auto; }
header, .search { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
h1 { font-size: 24px; }
.form { max-width: 560px; padding-top: 12px; }
.el-tabs { margin-top: 24px; }
</style>
