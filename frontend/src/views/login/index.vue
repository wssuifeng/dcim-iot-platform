/*
 * 文件功能:登录页，用户认证入口，提供用户名密码表单 + "记住我" 功能
 *
 * 【交互流程】（参见 UI-01-LoginPage.md §3 数据流图）
 *   用户输入 → 点击登录
 *     → ElForm.validate() 前端校验
 *     → POST /api/auth/login
 *       → 成功：localStorage.setItem('token') → authStore.parseToken() → router.push('/dashboard')
 *       → 失败：Axios 响应拦截器统一 ElMessage.error 提示
 *
 * 【防重复提交】
 * loading 状态控制按钮禁用 + ElButton loading 动画
 * try/finally 确保异常时 loading 也能恢复
 */
<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="login-title">IoT-Platform</h2>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="0"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="identifier">
          <el-input
            v-model="loginForm.identifier"
            placeholder="邮箱或用户名"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
        </el-form-item>
        <div class="login-links">
          <router-link to="/register">注册账号</router-link>
          <router-link to="/setup">首次初始化</router-link>
        </div>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
defineOptions({ name: 'AccountLoginPage' })
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { login } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

// el-form 组件引用，用于手动触发 validate()
const loginFormRef = ref(null)
const loading = ref(false)

// 登录表单数据（reactive 实现双向绑定）
const loginForm = reactive({
  identifier: '',
  password: '',
  rememberMe: false,
})

// 表单校验规则（Element Plus ElForm rules）
const loginRules = {
  identifier: [{ required: true, message: '请输入邮箱或用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/*
 * 方法功能:处理登录按钮点击事件
 * 参数:无
 * 返回数据:无
 *
 * 【执行流程】
 *   1. ElForm.validate() 异步校验表单
 *      - 通过 → 继续
 *      - 失败 → return（Element Plus 自动显示校验提示）
 *   2. 设置 loading → 调用 login() API
 *   3. 成功 → 存储 Token → 解析角色 → 跳转仪表盘
 *   4. 失败 → 错误由 Axios 拦截器统一处理（ElMessage.error）
 *   5. finally → 恢复 loading（确保异常时按钮可恢复）
 */
async function handleLogin() {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login({
      identifier: loginForm.identifier,
      password: loginForm.password,
    })

    // Token 持久化到 localStorage + Pinia Store
    localStorage.setItem('token', res.data.token)
    authStore.token = res.data.token  // 同步更新 store 中的 token ref，确保 parseToken 读到最新值
    authStore.parseToken()  // 解析 Token 提取 role 和 username

    // "记住我"标记（后续可用于自动填充用户名）
    if (loginForm.rememberMe) {
      localStorage.setItem('rememberMe', 'true')
    }

    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    // 错误已由 Axios 拦截器统一处理（ElMessage.error）
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  background-color: #f5f7fa;
}

.login-card {
  width: min(400px, 100%);
}

.login-title {
  text-align: center;
  margin-bottom: 24px;
  color: #303133;
}

.login-btn {
  width: 100%;
}

.login-links {
  display: flex;
  justify-content: space-between;
  margin-bottom: 18px;
  font-size: 14px;
}

.login-card :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}
</style>
