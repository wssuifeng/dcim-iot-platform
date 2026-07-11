<template>
  <section>
    <header><h1>组织成员</h1><el-switch v-model="joinEnabled" active-text="开放加入申请" @change="saveJoinSetting" /></header>
    <el-table :data="members">
      <el-table-column prop="userId" label="用户ID" /><el-table-column prop="membershipRole" label="角色" />
      <el-table-column label="操作" width="230"><template #default="scope"><el-select :model-value="scope.row.membershipRole" size="small" class="role-select" @change="(role) => changeRole(scope.row.userId, role)"><el-option label="管理员" value="ORG_ADMIN" /><el-option label="运维" value="OPERATOR" /><el-option label="查看者" value="VIEWER" /></el-select><el-button type="danger" link @click="remove(scope.row.userId)">移除</el-button></template></el-table-column>
    </el-table>
    <el-divider content-position="left">待审批加入申请</el-divider>
    <el-table :data="applications"><el-table-column prop="applicantUserId" label="申请人" /><el-table-column prop="requestNote" label="说明" /><el-table-column label="操作" width="180"><template #default="scope"><el-button type="success" link @click="approve(scope.row.id)">通过</el-button><el-button type="danger" link @click="reject(scope.row.id)">拒绝</el-button></template></el-table-column></el-table>
  </section>
</template>

<script setup>
defineOptions({ name: 'OrganizationMembersPage' })
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useOrganizationStore } from '@/stores/organization'
import { approveJoinApplication, getJoinApplications, getMembers, rejectJoinApplication, removeMember, updateJoinSetting, updateMemberRole } from '@/api/organizationMembership'
const store = useOrganizationStore(); const members = ref([]); const applications = ref([]); const joinEnabled = ref(true)
const orgId = () => store.currentOrganizationId
async function load() { if (!orgId()) return; members.value = (await getMembers(orgId())).data || []; applications.value = (await getJoinApplications(orgId())).data || [] }
async function changeRole(userId, role) { await updateMemberRole(orgId(), userId, role); await load() }
async function remove(userId) { await ElMessageBox.confirm('确认移除该成员？'); await removeMember(orgId(), userId); await load() }
async function approve(id) { await approveJoinApplication(orgId(), id, { role: 'VIEWER', note: '批准' }); await load() }
async function reject(id) { await rejectJoinApplication(orgId(), id, '拒绝'); await load() }
async function saveJoinSetting(value) { await updateJoinSetting(orgId(), value) }
onMounted(load)
</script>
<style scoped>header { display: flex; justify-content: space-between; align-items: center; }.role-select { width: 120px; margin-right: 8px; }</style>
