<template>
  <main class="auth-shell">
    <section class="auth-panel">
      <h1>平台首次初始化</h1>
      <el-alert v-if="!required" title="平台管理员已经初始化" type="info" :closable="false" />
      <el-form v-else ref="formRef" :model="form" label-position="top">
        <el-form-item label="部署初始化令牌"><el-input v-model="form.bootstrapToken" type="password" show-password /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="验证码">
          <div class="code-row"><el-input v-model="form.verificationCode" maxlength="6" /><el-button @click="sendCode">发送验证码</el-button></div>
        </el-form-item>
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="确认密码"><el-input v-model="form.confirmPassword" type="password" show-password /></el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="submit">创建平台管理员</el-button>
      </el-form>
      <router-link to="/login">返回登录</router-link>
    </section>
  </main>
</template>

<script setup>
defineOptions({ name: 'PlatformSetupPage' })
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSetupStatus, initializePlatformAdmin, sendSetupCode } from '@/api/setup'

const router = useRouter()
const required = ref(true)
const loading = ref(false)
const form = reactive({ bootstrapToken: '', email: '', verificationCode: '', username: '', nickname: '', password: '', confirmPassword: '' })
onMounted(async () => { required.value = Boolean((await getSetupStatus()).data?.required) })
async function sendCode() { await sendSetupCode(form.email); ElMessage.success('验证码已发送') }
async function submit() {
  loading.value = true
  try { await initializePlatformAdmin(form); ElMessage.success('初始化完成'); router.push('/login') }
  finally { loading.value = false }
}
</script>

<style scoped>
.auth-shell { min-height: 100vh; display: grid; place-items: center; background: #f3f5f7; padding: 24px; }
.auth-panel { width: min(440px, 100%); padding: 28px; background: #fff; border: 1px solid #dcdfe6; border-radius: 6px; }
h1 { margin: 0 0 22px; font-size: 24px; }
.code-row { display: grid; grid-template-columns: 1fr 120px; gap: 10px; width: 100%; }
.submit { width: 100%; margin-bottom: 18px; }
</style>
