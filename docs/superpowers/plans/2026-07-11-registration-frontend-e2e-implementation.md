# 注册、组织切换与审批前端实施计划

> **面向执行 Agent：** 前端公共状态可在 CodeX 基础包合并后开始；具体页面必须等待对应后端 API 合入 main。只允许通过 `frontend/src/utils/request.js` 与 `frontend/src/api/` 调用后端。

**目标：** 提供首次管理员初始化、邮箱注册、双标识登录、组织申请/加入、组织切换以及平台和组织审批的完整展示链路。

**架构：** `auth` store 只管理全局身份和平台角色；新 `organization` store 管理成员关系和当前组织。请求拦截器自动发送 `X-Organization-Id`，切换组织后刷新范围、菜单、WebSocket 和当前页面数据。

**技术栈：** Vue 3.5、Pinia 3、Vue Router 4、Element Plus、Axios、Vite 8、Playwright。

## 全局约束

- 账号注册与组织注册是两个独立入口，组织注册开关关闭时不影响已有账号登录。
- 无组织成员关系的普通账号必须进入组织引导页，不能显示空白业务后台。
- 多组织普通成员必须明确选择当前组织；单组织自动选中。
- `PLATFORM_ADMIN` 默认进入平台运维区，不自动进入组织业务区。
- 权限显隐只改善交互，后端拒绝仍是安全边界。
- 当前前端没有单元测试脚本，本轮最低自动门控为 `npm run build`、`npm run lint` 和 Playwright E2E。

---

### Task 1：重构全局身份和当前组织状态

**Files:**
- Modify: `frontend/src/stores/auth.js`
- Replace behavior: `frontend/src/stores/scope.js`
- Create: `frontend/src/stores/organization.js`
- Modify: `frontend/src/utils/request.js`
- Create: `frontend/src/api/membership.js`
- Modify: `frontend/src/api/auth.js`

**Interfaces:**
- `auth.platformRole` 只读取 JWT 的 `platformRole`。
- `organization.memberships`、`currentOrganizationId`、`currentMembershipRole`。
- localStorage key: `currentOrganizationId`。

- [ ] **Step 1: 实现组织状态结构**

```javascript
export const useOrganizationStore = defineStore('organization', () => {
  const memberships = ref([])
  const currentOrganizationId = ref(localStorage.getItem('currentOrganizationId') || '')
  const currentMembership = computed(() =>
    memberships.value.find((item) => String(item.organizationId) === String(currentOrganizationId.value)),
  )
  function selectOrganization(id) {
    if (!memberships.value.some((item) => String(item.organizationId) === String(id))) {
      throw new Error('无权切换到该组织')
    }
    currentOrganizationId.value = String(id)
    localStorage.setItem('currentOrganizationId', String(id))
  }
  return { memberships, currentOrganizationId, currentMembership, selectOrganization }
})
```

- [ ] **Step 2: 请求自动附加当前组织**

```javascript
const organizationId = localStorage.getItem('currentOrganizationId')
if (organizationId) {
  config.headers = config.headers || {}
  config.headers['X-Organization-Id'] = organizationId
}
```

- [ ] **Step 3: 构建验证并提交**

Run: `cd frontend; npm run build`

Expected: build succeeds。

```powershell
git add frontend/src/stores frontend/src/utils/request.js frontend/src/api/auth.js frontend/src/api/membership.js
git commit -m "feat(frontend): separate account and organization context"
```

### Task 2：实现首次初始化、注册和双标识登录

**Files:**
- Create: `frontend/src/views/setup/index.vue`
- Create: `frontend/src/views/register/index.vue`
- Modify: `frontend/src/views/login/index.vue`
- Modify: `frontend/src/router/index.js`
- Create: `frontend/src/api/setup.js`

**Interfaces:**
- `/setup` 根据 `GET /setup/status` 决定显示初始化表单或跳转登录。
- `/register` 包含邮箱、验证码、用户名、昵称、密码和确认密码。
- `/login` 使用 `identifier` 字段，文案为“邮箱或用户名”。

- [ ] **Step 1: 注册验证码按钮实现 60 秒本地倒计时**

```javascript
const cooldown = ref(0)
async function sendCode() {
  await sendRegistrationCode({ email: form.email })
  cooldown.value = 60
  const timer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) window.clearInterval(timer)
  }, 1000)
}
```

服务端 `429` 必须覆盖本地倒计时结果并展示后端剩余时间；前端倒计时不能作为限流边界。

- [ ] **Step 2: 配置公开路由**

```javascript
{ path: '/setup', component: () => import('@/views/setup/index.vue'), meta: { public: true } },
{ path: '/register', component: () => import('@/views/register/index.vue'), meta: { public: true } },
{ path: '/login', component: () => import('@/views/login/index.vue'), meta: { public: true } },
```

- [ ] **Step 3: 构建验证并提交**

Run: `cd frontend; npm run build`

Expected: build succeeds。

```powershell
git add frontend/src/views/setup frontend/src/views/register frontend/src/views/login frontend/src/router/index.js frontend/src/api/setup.js
git commit -m "feat(frontend): add setup and email registration"
```

### Task 3：实现组织引导和组织切换

**Files:**
- Create: `frontend/src/views/account/organizations/index.vue`
- Create: `frontend/src/views/organization/apply.vue`
- Create: `frontend/src/views/organization/join.vue`
- Create: `frontend/src/components/OrganizationSwitcher.vue`
- Modify: `frontend/src/layout/index.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 登录后按成员数量导航**

```javascript
await organizationStore.loadMemberships()
if (organizationStore.memberships.length === 0) {
  return router.replace('/account/organizations')
}
if (organizationStore.memberships.length === 1) {
  organizationStore.selectOrganization(organizationStore.memberships[0].organizationId)
}
```

- [ ] **Step 2: 切换组织时清理旧范围和实时连接**

```javascript
async function switchOrganization(id) {
  organizationStore.selectOrganization(id)
  scopeStore.resetCurrentScope()
  websocketStore.disconnect()
  await scopeStore.loadCurrentScope({ force: true })
  websocketStore.connect({ organizationId: id })
  await router.replace('/dashboard')
}
```

- [ ] **Step 3: 构建验证并提交**

Run: `cd frontend; npm run build`

Expected: build succeeds，组织切换器宽度固定且长组织名使用省略号与 tooltip。

```powershell
git add frontend/src/views/account frontend/src/views/organization frontend/src/components/OrganizationSwitcher.vue frontend/src/layout/index.vue frontend/src/router/index.js
git commit -m "feat(frontend): add organization onboarding and switcher"
```

### Task 4：实现平台审核与组织成员管理页面

**Files:**
- Create: `frontend/src/views/platform/registration-settings.vue`
- Create: `frontend/src/views/platform/organization-applications.vue`
- Create: `frontend/src/views/organization/member-applications.vue`
- Create: `frontend/src/views/organization/members.vue`
- Create: `frontend/src/api/platformRegistration.js`
- Create: `frontend/src/api/organizationMembership.js`
- Modify: `frontend/src/layout/index.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 使用对应控件表达策略**

`accountRegistrationEnabled` 使用 switch；`organizationRegistrationMode` 使用三段式 segmented/radio control；成员角色使用下拉菜单；审批使用明确的通过和拒绝命令按钮。

- [ ] **Step 2: 最后管理员保护交互**

前端不自行判断最终安全结果；后端返回 `LAST_ORG_ADMIN_REQUIRED` 时，保留成员列表并提示先提升另一名管理员。

- [ ] **Step 3: 构建和 lint**

Run: `cd frontend; npm run build; npm run lint`

Expected: 两条命令成功，lint 自动修复后工作树只包含预期格式变化。

- [ ] **Step 4: 提交**

```powershell
git add frontend/src/views/platform frontend/src/views/organization frontend/src/api frontend/src/layout/index.vue frontend/src/router/index.js
git commit -m "feat(frontend): add organization approval administration"
```

### Task 5：执行浏览器端到端验收

**Files:**
- Create: `frontend/e2e/registration-organization.spec.js`
- Modify: `frontend/package.json` only if Playwright dependency is approved by SCR; otherwise use workspace bundled Playwright runner without changing dependencies
- Evidence: `开发控制文档/08-门控证据/GATE-注册与组织前端.md`，本地分发包文件不得提交

- [ ] **Step 1: 编写关键链路 E2E**

```javascript
test('register, apply, approve and enter organization', async ({ page }) => {
  await page.goto('/register')
  await page.getByLabel('邮箱').fill('member@example.com')
  await page.getByLabel('用户名').fill('member_user')
  await page.getByRole('button', { name: '注册' }).click()
  await expect(page).toHaveURL(/account\/organizations/)
  await page.getByRole('link', { name: '创建组织' }).click()
  await page.getByLabel('组织名称').fill('演示数据中心')
  await page.getByLabel('组织编码').fill('DEMO-DC')
  await page.getByRole('button', { name: '提交申请' }).click()
  await expect(page.getByText('待平台审核')).toBeVisible()
})
```

测试邮箱由后端测试 profile 的可观察 MailSender 提供验证码，不连接真实 QQ SMTP。

- [ ] **Step 2: 验证多组织切换与越权拒绝**

普通成员切换组织后，请求头和页面数据必须变化；手工伪造无成员关系的组织 ID 返回 403，页面清除无效选择并回到组织选择页。

- [ ] **Step 3: 桌面与移动视口截图检查**

Run: `npx playwright test frontend/e2e/registration-organization.spec.js`

Expected: setup、register、organization switcher、审批表格在 `1440x900` 和 `390x844` 无文字重叠、横向溢出或遮挡。

- [ ] **Step 4: 最终门控**

```powershell
.\mvnw.cmd clean test
Set-Location frontend
npm run build
npm run lint
```

Expected: 后端、前端构建和 lint 全部成功。

## 前端合并门控

1. 分支名固定为 `feature/auth-codex-frontend`。
2. 所有页面调用真实已合并 API，不允许用静态 mock 标记完成。
3. 组织切换同步刷新请求头、范围上下文、菜单和 WebSocket。
4. 普通账号、平台管理员、组织管理员三种首屏路径均有 E2E 证据。
5. QQ SMTP 只用于人工演示；自动测试不得依赖外部邮箱服务。
