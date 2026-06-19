> [已归档] 本文件已移至 archive/UI模块/。

# 登录页 — 前端实现方案

> **版本**: V1.0.0 | **创建**: 2026-05-31 | **需求**: REQ-UI-FR-001
> **关联基础设施**: UI-00-InfrastructureREADME.md
> **关联规范**: FrontendPageImplementationPrompt.md V3.1.1
> **页面类型**: 特殊页面（认证入口，非后台CRUD）

---

## §A 实现边界（必须首先声明）

本页面**仅实现**需求文档明确要求的功能。

**禁止主动增加以下内容**（除非需求文档明确要求）：
- 国际化 (i18n)
- 虚拟滚动 / 无限滚动
- 微前端 / SSR / PWA
- WebWorker / Service Worker
- 第三方 OAuth 登录（微信/GitHub 等）
- 图形验证码（验证码登录）
- 短信登录
- "忘记密码"功能
- 注册功能（仅 ADMIN 可通过后台创建用户）
- 无痕模式 / 隐私模式适配

---

## §0 基础设施约定

全部引用 `UI-00-InfrastructureREADME.md`，本章仅补充登录页特化内容。

### §0.1 技术栈

同 UI-00-InfrastructureREADME §0.1，登录页额外使用：

| 技术 | 版本 | 用途 |
|------|:---:|------|
| jwt-decode | 3.x | 解析 JWT Token 获取用户角色 |
| 无 ECharts | — | 登录页不使用图表 |

### §0.2 后端接口

| 接口 | 方法 | 实际路径 | 认证 | 说明 |
|------|:---:|------|:---:|------|
| 登录 | POST | `/api/auth/login` | **无需认证** | 唯一前端可无需 Token 调用的接口 |

### §0.3 Axios 拦截器

同 UI-00-InfrastructureREADME §0.3。

### §0.4 路由守卫

同 UI-00-InfrastructureREADME §0.4。

**登录页特殊行为**：
- 访问 `/login` 时如果已有有效 Token，自动跳转 `/dashboard`
- 该逻辑复用全局守卫，无需额外处理

### §0.5 按钮级权限

登录页为**未认证状态**页面，不存在按钮级权限控制。

### §0.6 RBAC 权限矩阵

登录页仅涉及 1 个端点：

| 端点 | ADMIN | OPERATOR | VIEWER | 未认证 |
|:---|:---:|:---:|:---:|:---:|
| `POST /api/auth/login` | ✅ | ✅ | ✅ | ✅ |

### §0.7 枚举映射表

无登录页专用枚举。

### §0.8 输入缺失处理

- ✅ `ControllerAPIREADME.md` — 已读取
- ✅ `IoT-Platform需求规格说明书.md` — 已读取
- ✅ `SecurityConfig.java` — 已读取

---

## §1 页面任务拆解

### P0 — 核心功能（必须完成，缺一则页面不可交付）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P0-1** | 用户名+密码输入框 + 登录按钮 + Element Plus 表单校验 | 低 |
| **P0-2** | 调用 `POST /api/auth/login`，成功后 Token 写入 localStorage | 低 |
| **P0-3** | 登录失败展示 `ElMessage.error` 错误提示 | 低 |
| **P0-4** | 登录成功跳转 `/dashboard` | 低 |
| **P0-5** | 登录中 Loading 状态 + 防重复提交 | 低 |
| **P0-6** | Token 解析（jwt-decode）存入 Pinia AuthStore | 低 |

### P1 — 重要功能（提升用户体验）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P1-1** | "记住我" 功能（7天内免登录，使用 localStorage 标记） | 低 |
| **P1-2** | Token 过期自动跳转回登录页（复用 Axios 401 拦截器） | 已有 |

### P2 — 增量优化（可选）

| 任务 | 描述 | 预估复杂度 |
|:---|------|:---:|
| **P2-1** | 登录页居中卡片式布局 + 系统名称/Logo | 低 |
| **P2-2** | 密码输入框 Eye 图标切换明文/密文 | 低 |

---

## §2 目录结构

```
src/
├── views/
│   └── login/
│       └── index.vue              # 登录页入口（单文件组件）
├── api/
│   └── auth.js                    # 登录接口封装
├── stores/
│   └── auth.js                    # Pinia AuthStore（token/role 管理）
├── utils/
│   ├── request.js                 # Axios 封装（已存在于 §0.3）
│   └── constants.js               # 枚举常量（已存在于 §0.7）
└── router/
    └── index.js                   # 路由配置（已存在于 §0.4）
```

> **说明**: 登录页为简单单页表单，不需要拆分多个子组件。AuthStore 是所有后续页面的基础设施，在此优先创建。

---

## §3 数据流图

```
用户输入用户名/密码 → 点击登录
    ↓
前端表单校验（Element Plus ElForm.validate）
    ↓ 通过
禁用登录按钮 + 显示 Loading
    ↓
POST /api/auth/login { username, password }
    ↓
    ├─ 成功 (HTTP 200) → 
    │   ├─ localStorage.setItem('token', response.data.token)
    │   ├─ jwtDecode(token) → Pinia AuthStore.role
    │   ├─ router.push('/dashboard')
    │   └─ （"记住我"时：localStorage.setItem('rememberMe', 'true')）
    │
    ├─ 失败 (HTTP 401/400/500) →
    │   ├─ ElMessage.error(response.message)
    │   └─ 启用登录按钮
    │
    └─ 网络异常 →
        ├─ ElMessage.error('网络连接失败')
        └─ 启用登录按钮
```

---

## §4 接口实现清单

### §4.1 接口定义

| 接口 | 方法 | 路径 | 请求体 | 返回体 |
|------|:---:|------|------|------|
| 登录 | POST | `/api/auth/login` | `LoginDTO { username, password }` | `Result<LoginResponseDTO> { token }` |

### §4.2 API 层封装

```js
// src/api/auth.js
import request from '@/utils/request';

export function login(data) {
  return request.post('/auth/login', data);
}
```

### §4.3 接口调用示例

```js
try {
  const res = await login({ username: 'admin', password: 'xxx' });
  // res.code === 200
  // res.data.token === "eyJhbG..."
} catch (error) {
  // 错误已由 Axios 拦截器统一弹窗提示
}
```

### §4.4 接口权限映射

| 接口 | SecurityConfig 规则 | 前端策略 |
|------|------|------|
| `POST /api/auth/login` | `.permitAll()` | 无需携带 Token，无需角色校验 |

### §4.5 接口实现顺序

```
第一优先级（本页面唯一接口）:
  P0: POST /api/auth/login
```

---

## §5 组件树

```
LoginPage (src/views/login/index.vue)
├── ElCard（登录卡片容器）
│   ├── 系统 Logo / 标题
│   ├── ElForm（登录表单，ref="loginFormRef"）
│   │   ├── ElFormItem（用户名）
│   │   │   └── ElInput（text，prefix-icon: User）
│   │   ├── ElFormItem（密码）
│   │   │   └── ElInput（password，prefix-icon: Lock，show-password）
│   │   ├── ElCheckbox（记住我）
│   │   └── ElButton（登录，type: primary，loading）
│   └── 版权信息 / 版本号
└── （无其他组件）
```

---

## §6 核心实现

### §6.1 登录页面组件

**文件**: `src/views/login/index.vue`

```vue
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
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
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
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { User, Lock } from '@element-plus/icons-vue';
import { useAuthStore } from '@/stores/auth';
import { login } from '@/api/auth';

const router = useRouter();
const authStore = useAuthStore();
const loginFormRef = ref(null);
const loading = ref(false);

const loginForm = reactive({
  username: '',
  password: '',
  rememberMe: false,
});

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function handleLogin() {
  const valid = await loginFormRef.value.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    const res = await login({
      username: loginForm.username,
      password: loginForm.password,
    });

    localStorage.setItem('token', res.data.token);
    authStore.parseToken();

    if (loginForm.rememberMe) {
      localStorage.setItem('rememberMe', 'true');
    }

    ElMessage.success('登录成功');
    router.push('/dashboard');
  } catch {
    // 错误已由 Axios 拦截器统一处理
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f5f7fa;
}
.login-card {
  width: 400px;
}
.login-title {
  text-align: center;
  margin-bottom: 24px;
  color: #303133;
}
.login-btn {
  width: 100%;
}
</style>
```

### §6.2 AuthStore

**文件**: `src/stores/auth.js`

```js
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { jwtDecode } from 'jwt-decode';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '');
  const role = ref('');
  const username = ref('');

  function parseToken() {
    if (!token.value) return;
    try {
      const payload = jwtDecode(token.value);
      role.value = payload.role || '';
      username.value = payload.sub || '';
    } catch {
      token.value = '';
      role.value = '';
      username.value = '';
    }
  }

  function logout() {
    token.value = '';
    role.value = '';
    username.value = '';
    localStorage.removeItem('token');
    localStorage.removeItem('rememberMe');
  }

  function isLoggedIn() {
    return !!token.value;
  }

  parseToken();

  return { token, role, username, parseToken, logout, isLoggedIn };
});
```

### §6.3 Auth API

**文件**: `src/api/auth.js`

```js
import request from '@/utils/request';

export function login(data) {
  return request.post('/auth/login', data);
}
```

---

## §7 状态管理

| Store | 用途 | 关键状态 | 页面依赖 |
|-------|------|----------|:---:|
| `useAuthStore` | 全局认证状态 | `token`, `role`, `username` | ✅ 必须 |
| — | — | — | 无需其他 Store |

### 状态流向

```
login() 成功 
  → localStorage.setItem('token', ...)
  → authStore.parseToken()（从 token 解析 role/username）
  → router.push('/dashboard')
  → Dashboard 页读取 authStore.role 控制按钮显隐
```

---

## §8 页面完成定义 (DoD)

登录页"完成"必须满足以下全部条件：

- [x] 用户名/密码输入框可用，回车键触发表单提交
- [x] 表单校验：空输入时 ElMessage 提示
- [x] `POST /api/auth/login` 调用成功 → Token 写 localStorage → 跳转 `/dashboard`
- [x] 登录失败 → ElMessage.error 展示后端返回的错误消息
- [x] 登录中 Loading 状态 + 按钮禁用，防止重复提交
- [x] 已登录状态访问 `/login` → 自动跳转 `/dashboard`（路由守卫）
- [x] Token 过期（401） → 自动跳转 `/login`（Axios 拦截器）
- [x] "记住我" 勾选框可用

---

## §9 集成检查清单

> 开发者实现完毕后逐项确认。

| # | 检查项 | 验证方式 |
|:---:|------|------|
| 1 | 未登录访问 `/dashboard` | 自动跳转 `/login` |
| 2 | 已登录访问 `/login` | 自动跳转 `/dashboard` |
| 3 | 输入正确账号密码 | 跳转 `/dashboard`，localStorage 有 token |
| 4 | 输入错误密码 | 底部 ElMessage.error 提示，不跳转 |
| 5 | 空用户名点击登录 | 表单校验提示 "请输入用户名" |
| 6 | 空密码点击登录 | 表单校验提示 "请输入密码" |
| 7 | 快速双击登录按钮 | 仅发送 1 次请求 |
| 8 | 登录成功后刷新页面 | 仍能正常访问（Token 在 localStorage） |
| 9 | 勾选"记住我"后登录 | localStorage 有 `rememberMe` 标记 |
| 10 | 手动清除 localStorage token | 自动跳转 `/login` |
| 11 | Token 过期后发起请求 | 自动跳转 `/login` |
| 12 | 查看浏览器 DevTools Network | `/api/auth/login` 请求体正确 |

---

## §10 学习重点

| # | 知识点 | 文件/位置 | 说明 |
|:---:|------|------|------|
| 1 | Vue Router 全局守卫 | `router/index.js` §0.4 | 理解 `beforeEach` 中 token 校验逻辑 |
| 2 | Axios 拦截器流程 | `utils/request.js` §0.3 | 理解 401 → 跳转登录的链路 |
| 3 | JWT Token 结构 | `stores/auth.js` §6.2 | jwt-decode 解析 token payload 获取 role |
| 4 | Pinia Setup Store 语法 | `stores/auth.js` §6.2 | Composition API 风格写 Pinia |
| 5 | Element Plus ElForm 校验 | `views/login/index.vue` §6.1 | `:rules` + `validate()` 异步校验 |
| 6 | 防重复提交模式 | `views/login/index.vue` §6.1 | `loading` + `try/finally` 范式 |

---

## §11 面试准备

### §11.1 核心交互流程

1. 用户访问任意页面 → Vue Router `beforeEach` 检查 Token → 无 Token 跳转 `/login`
2. 用户输入用户名密码 → 点击登录 → `POST /api/auth/login`
3. 成功 → `localStorage.setItem('token')` → `authStore.parseToken()` → `router.push('/dashboard')`
4. 失败 → Axios 响应拦截器捕获 → `ElMessage.error`
5. Token 过期 → Axios 收到 401 → 清除 Token → 跳转 `/login`

### §11.2 面试模拟问题

**问题 1**: 如果用户直接访问 `/dashboard` 且 Token 已过期，会发生什么？

> 参考答案：路由守卫放行（Token 存在但未校验有效性）→ Dashboard 页加载 → 接口调用 → 服务端返回 401 → Axios 响应拦截器清除 Token → `router.push('/login')`。

**问题 2**: 登录页的"记住我"功能如何实现？

> 参考答案：勾选后在 localStorage 写入 `rememberMe=true` 标记。下次用户访问登录页时，如果检测到 `rememberMe=true`，可自动填充用户名（需扩展实现）。Token 本身不会因"记住我"而延长有效期（仍为 2 小时），目前版本不做 Token 自动刷新，"记住我"仅做 UI 层面的用户名记忆。

**问题 3**: 为什么 `POST /api/auth/login` 不需要 Token？

> 参考答案：SecurityConfig 中配置了 `.requestMatchers("/auth/login").permitAll()`，该端点被 Spring Security 无条件放行，不经过 JWT 过滤器校验。

---

## §12 页面复杂度评估

| 维度 | 评级 | 说明 |
|------|:---:|------|
| 组件数量 | ⭐ | 单文件组件，无子组件 |
| 接口数量 | ⭐ | 仅 1 个 POST 接口 |
| 状态复杂度 | ⭐ | 仅 AuthStore，3 个状态字段 |
| 权限逻辑 | ⭐ | 无权限校验（未认证状态） |
| 交互复杂度 | ⭐⭐ | ElForm 校验 + Loading 防重复提交 |
| **综合** | **⭐** | **最简单页面，适合作为项目入门** |

---

## §13 硬性约束

- **禁止**在登录页引入 ECharts 或其他重型图表库
- **禁止**创建超过 2 个 `.vue` 文件（登录页仅需 `index.vue`）
- **禁止**在 `login()` 调用前手动附加 Authorization Header（request.js 拦截器自动处理没有 Token 时不附加）
- **禁止**使用 `this.$router`（Vue Router 4 + Composition API 使用 `useRouter()`）
- **必须**使用 `ElForm.validate()` 异步校验（不是同步 `validateSync`）
- **必须**在 `try/finally` 中重置 `loading`，确保异常时按钮可恢复

---

## 附录 B：登录页特殊处理

> 登录页属于"特殊页面"（非后台管理 CRUD 页面），以下为特化说明：

| 处理项 | 说明 |
|------|------|
| 无需侧边栏/顶部栏 | 登录页为独立全屏页面，不需要 Layout 组件 |
| 无需面包屑导航 | 独立页面无面包屑 |
| 路由配置 | `/login` 为顶级路由，不在 Layout 的 children 中 |
| 无需 Store 初始化 | 登录前 AuthStore 无有效状态，无需依赖其他 Store |
| "记住我" 实现方向 | 当前版本仅做 localStorage 标记，不扩展自动填充 |
| 登录成功后 | 路由跳转 `/dashboard` 触发 Dashboard 页的 Layout 初始化 |

---

*本文档基于项目 V3.1.1 规范生成，最后更新于 2026-05-31。*