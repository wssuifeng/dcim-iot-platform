<template>
  <div class="user-page">
    <user-filter :filter="filter" @search="handleSearch" @reset="handleReset" />
    <div class="toolbar"><el-button type="primary" @click="handleCreate">新增用户</el-button></div>
    <user-table :list="userList" :loading="loading" @edit="handleEdit" @reset-password="handleResetPassword" @enable="row=>handleToggleStatus(row,0)" @disable="row=>handleToggleStatus(row,1)" @delete="handleDelete" />
    <el-pagination v-if="total>0" v-model:current-page="pagination.current" v-model:page-size="pagination.size" :total="total" :page-sizes="[10,20,50]" layout="total,sizes,prev,pager,next" @change="fetchUserList" style="margin-top:16px;justify-content:flex-end" />
    <user-form-dialog v-model:visible="formVisible" :mode="formMode" :current="currentUser" @confirm="handleFormConfirm" />
    <password-reset-dialog v-model:visible="passwordVisible" :user="currentUser" @confirm="handlePasswordConfirm" />
  </div>
</template>
<script setup>
import {ref,reactive,onMounted} from 'vue';import {ElMessage,ElMessageBox} from 'element-plus'
import {getUserPage,createUser,updateUser,resetUserPassword,updateUserStatus,deleteUser} from '@/api/user'
import UserFilter from '@/components/user/UserFilter.vue';import UserTable from '@/components/user/UserTable.vue'
import UserFormDialog from '@/components/user/UserFormDialog.vue';import PasswordResetDialog from '@/components/user/PasswordResetDialog.vue'

const loading=ref(false),userList=ref([]),total=ref(0)
const pagination=reactive({current:1,size:10}),filter=reactive({keyword:''})
const formVisible=ref(false),formMode=ref('create'),currentUser=ref(null),passwordVisible=ref(false)

async function fetchUserList(){loading.value=true;try{const params={current:pagination.current,size:pagination.size};if(filter.keyword)params.keyword=filter.keyword;const res=await getUserPage(params);userList.value=res.data.records||[];total.value=res.data.total||0}finally{loading.value=false}}
function handleSearch(){pagination.current=1;fetchUserList()}
function handleReset(){filter.keyword='';pagination.current=1;fetchUserList()}
function handleCreate(){formMode.value='create';currentUser.value=null;formVisible.value=true}
function handleEdit(row){formMode.value='edit';currentUser.value={...row};formVisible.value=true}
function handleResetPassword(row){currentUser.value={...row};passwordVisible.value=true}
async function handleToggleStatus(row,status){const a=status===0?'启用':'停用';try{await ElMessageBox.confirm(`确认${a}用户"${row.username}"？`,`${a}确认`,{type:'warning'});await updateUserStatus(row.id,status);ElMessage.success(`已${a}`);fetchUserList()}catch{}}
async function handleDelete(row){try{await ElMessageBox.confirm(`确认删除用户"${row.username}"？`,'删除确认',{type:'warning'});await deleteUser(row.id);ElMessage.success('用户已删除');fetchUserList()}catch{}}
async function handleFormConfirm(data){try{if(formMode.value==='create'){await createUser(data);ElMessage.success('用户已创建')}else{await updateUser(currentUser.value.id,data);ElMessage.success('用户已更新')}formVisible.value=false;fetchUserList()}catch{}}
async function handlePasswordConfirm(payload){try{await resetUserPassword(currentUser.value.id,payload);ElMessage.success('密码已重置');passwordVisible.value=false}catch{}}
onMounted(fetchUserList)
</script>
<style scoped>.user-page{padding:0}.toolbar{margin-bottom:16px}</style>
