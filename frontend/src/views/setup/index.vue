<template>
  <main class="auth-shell">
    <section class="auth-panel">
      <h1>平台首次初始化</h1>
      <el-alert v-if="!required" title="平台管理员已经初始化" type="info" :closable="false" />
      <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="部署初始化令牌" prop="bootstrapToken"><el-input v-model="form.bootstrapToken" type="password" show-password /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="form.email" autocomplete="email" /></el-form-item>
        <el-form-item label="验证码" prop="verificationCode">
          <div class="code-row"><el-input v-model="form.verificationCode" maxlength="6" /><el-button :disabled="cooldown > 0" @click="sendCode">{{ cooldown > 0 ? `${cooldown}s` : '发送验证码' }}</el-button></div>
        </el-form-item>
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password /></el-form-item>
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
const formRef = ref()
const required = ref(true)
const loading = ref(false)
const cooldown = ref(0)
const form = reactive({ bootstrapToken: '', email: '', verificationCode: '', username: '', nickname: '', password: '', confirmPassword: '' })
const rules = {
  bootstrapToken: [{ required: true, message: '请输入部署初始化令牌', trigger: 'blur' }],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  verificationCode: [{ required: true, pattern: /^\d{6}$/, message: '请输入6位验证码', trigger: 'blur' }],
  username: [{ required: true, pattern: /^[a-zA-Z0-9_]{4,20}$/, message: '4-20位字母数字下划线', trigger: 'blur' }],
  password: [{ required: true, pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/, message: '密码需包含大小写字母和数字', trigger: 'blur' }],
  confirmPassword: [{ validator: (_, value, callback) => value === form.password ? callback() : callback(new Error('两次密码不一致')), trigger: 'blur' }],
}
onMounted(async () => { required.value = Boolean((await getSetupStatus()).data?.required) })
async function sendCode() {
  const valid = await formRef.value.validateField('email').then(() => true).catch(() => false)
  if (!valid) return
  await sendSetupCode(form.email)
  ElMessage.success('验证码已发送')
  cooldown.value = 60
  const timer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) window.clearInterval(timer)
  }, 1000)
}
async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return
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
@media (max-width: 480px) {
  .auth-shell { padding: 12px; }
  .auth-panel { padding: 20px; }
  .code-row { grid-template-columns: 1fr; }
}
</style>
