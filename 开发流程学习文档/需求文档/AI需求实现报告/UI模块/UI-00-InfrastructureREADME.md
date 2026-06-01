# §0 前端基础设施约定

> **版本**: V1.0.0 | **创建**: 2026-05-31
>
> **用途**: 全部页面共享的基础设施约定。后续每个页面的实现方案仅引用本章，不重复。
>
> **关联规范**: `FrontendPageImplementationPrompt.md` V3.1.1

---

## §0.1 技术栈锁定

| 技术 | 版本 | 关键约束 |
|------|:---:|---------|
| Vue | 3.x | **必须** Composition API + `<script setup>`，**禁止** Options API |
| Vite | 5.x | 构建工具 |
| Vue Router | 4.x | **禁止** `this.$router` |
| Pinia | 2.x | **禁止** Vuex |
| Element Plus | 2.x | UI 组件库 |
| ECharts | 6.x | 图表渲染（仅仪表盘/数据监控页使用） |
| Axios | 1.x | HTTP 请求 |

---

## §0.2 后端接口基础信息

| 配置项 | 值 |
|--------|-----|
| `context-path` | `/api` |
| Token 传递方式 | `Authorization: Bearer <token>` |
| Token 有效期 | 2 小时 |
| Token 存储位置 | `localStorage`（key: `token`） |
| CORS `allowCredentials` | `true` |
| 401 响应体 | `{"code":401,"message":"未登录或Token已过期"}` |
| 403 响应体 | `{"code":403,"message":"权限不足，无法访问该资源"}` |
| 统一响应格式 | `{"code":200,"message":"操作成功","data":...}` |

> **路径拼接规则**: 所有接口实际路径 = `context-path`（`/api`）+ Controller 上 `@RequestMapping` 的值。例如 `DashboardController` 的 `@RequestMapping("/dashboard")` 对应实际路径 `/api/dashboard`。

---

## §0.3 Axios 全局拦截器

### 请求拦截器

从 `localStorage` 读取 token，自动附加到每个请求的 `Authorization` Header 中。

### 响应拦截器

- **HTTP 401**: 清除 token → 跳转 `/login`
- **HTTP 403**: `ElMessage.error('权限不足')`
- **其他错误**: 透传 `Promise.reject`

### 参考实现

```js
// src/utils/request.js
import axios from 'axios';
import { ElMessage } from 'element-plus';
import router from '@/router';

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
});

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

request.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      router.push('/login');
    } else if (error.response?.status === 403) {
      ElMessage.error('权限不足，无法执行此操作');
    } else {
      ElMessage.error(error.response?.data?.message || '请求失败');
    }
    return Promise.reject(error);
  }
);

export default request;
```

### 使用方式

```js
// ✅ 正确：统一使用封装后的 request
import request from '@/utils/request';
const data = await request.get('/devices/page', { params: { current: 1, size: 10 } });

// ❌ 禁止：直接使用 axios（会绕过拦截器）
import axios from 'axios';
const data = await axios.get('/api/devices/page');
```

---

## §0.4 路由守卫

### 守卫逻辑

| 场景 | 行为 |
|------|------|
| 访问 `/login` 且已有 token | 自动跳转 `/dashboard` |
| 访问其他路径且无 token | 跳转 `/login` |
| 访问其他路径且有 token | 正常放行 |

### 参考实现

```js
// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/login/index.vue') },
    { path: '/dashboard', component: () => import('@/views/dashboard/index.vue') },
    { path: '/devices', component: () => import('@/views/device/index.vue') },
    { path: '/data-monitor', component: () => import('@/views/data-monitor/index.vue') },
    { path: '/alerts', component: () => import('@/views/alert/index.vue') },
    { path: '/', redirect: '/dashboard' },
  ],
});

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token');

  if (to.path === '/login' && token) {
    return next('/dashboard');
  }

  if (to.path !== '/login' && !token) {
    return next('/login');
  }

  next();
});

export default router;
```

---

## §0.5 按钮级权限策略

### 角色来源

JWT Token 的 Payload 中包含 `role` 字段（值为 `ADMIN` / `OPERATOR` / `VIEWER`）。使用 `jwt-decode` 解析后存入 Pinia Store。

```js
// src/stores/auth.js
import { defineStore } from 'pinia';
import { jwtDecode } from 'jwt-decode';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '');
  const role = ref('');

  function parseToken() {
    if (!token.value) return;
    try {
      const payload = jwtDecode(token.value);
      role.value = payload.role;
    } catch { /* token 无效 */ }
  }

  parseToken();

  return { token, role, parseToken };
});
```

### 按钮显隐规则

| 后端鉴权规则（SecurityConfig） | 前端策略 |
|----------|---------|
| `.hasRole("ADMIN")` | `v-if="authStore.role === 'ADMIN'"` |
| `.hasAnyRole("ADMIN", "OPERATOR")` | `v-if="authStore.role !== 'VIEWER'"` |
| `.authenticated()` | 无需限制 |
| `.permitAll()` | 无需限制（仅登录接口） |

> **设计原则**: 按钮级权限只是 UI 层面的友好体验（避免用户点击按钮后才被后端拒绝），真正的安全由后端 SecurityConfig 保障。

---

## §0.6 完整 RBAC 权限矩阵（与 SecurityConfig 一一对应）

| 端点/方法 | ADMIN | OPERATOR | VIEWER | 未认证 |
|:---|:---:|:---:|:---:|:---:|
| `POST /auth/login` | ✅ | ✅ | ✅ | ✅ |
| `GET /**`（所有只读查询） | ✅ | ✅ | ✅ | ❌ |
| `POST /devices, /device-data, /device-data/batch, /metric` | ✅ | ✅ | ❌ | ❌ |
| `PUT /devices/**, /device-types/**, /metric` | ✅ | ✅ | ❌ | ❌ |
| `PUT /alerts/**`（告警处理） | ✅ | ✅ | ❌ | ❌ |
| `GET /audit-logs/**` | ✅ | ✅ | ❌ | ❌ |
| `DELETE /**`（所有删除操作） | ✅ | ❌ | ❌ | ❌ |
| `/users/**` | ✅ | ❌ | ❌ | ❌ |
| `/druid/**` | ✅ | ❌ | ❌ | ❌ |

---

## §0.7 枚举映射表

> 前端所有页面共用，建议存放于 `src/utils/constants.js`。

```js
// src/utils/constants.js

// 设备状态
export const DEVICE_STATUS = {
  0: { label: '离线', color: '#909399' },
  1: { label: '在线', color: '#67C23A' },
  2: { label: '故障', color: '#F56C6C' },
};

// 告警级别
export const ALERT_LEVEL = {
  1: { label: '紧急', color: '#F56C6C', bgColor: '#FEF0F0' },
  2: { label: '严重', color: '#E6A23C', bgColor: '#FDF6EC' },
  3: { label: '一般', color: '#409EFF', bgColor: '#ECF5FF' },
  4: { label: '警告', color: '#909399', bgColor: '#F5F7FA' },
  5: { label: '提示', color: '#C0C4CC', bgColor: '#FFFFFF' },
};

// 账号状态
export const USER_STATUS = {
  0: { label: '启用', color: '#67C23A' },
  1: { label: '停用', color: '#F56C6C' },
};

// 告警处理状态
export const HANDLE_STATUS = {
  0: { label: '未处理', color: '#F56C6C' },
  1: { label: '已处理', color: '#67C23A' },
};

// 时间格式化
export const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';
export const DATE_FORMAT_SHORT = 'YYYY-MM-DD';
```

---

## §0.8 输入缺失处理

若在生成页面实现方案时，发现以下任何一份文档缺失：

- `ControllerAPIREADME.md`
- `IoT-Platform需求规格说明书.md`
- `SecurityConfig.java`

AI 必须：
- **禁止**猜测接口路径、参数或返回值
- **禁止**虚构权限规则
- **禁止**补全不存在的 API

必须输出 `【缺少输入】` 提示，标明缺失的具体文档。

---

*本文档基于项目 V3.1.1 规范生成，最后更新于 2026-05-31。*