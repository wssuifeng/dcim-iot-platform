import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import '@/styles/theme.css'
import '@/styles/echarts-theme'
import App from './App.vue'
import router from './router'
import { useThemeStore } from '@/stores/theme'
import PageShell from '@/components/PageShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import DataTableShell from '@/components/DataTableShell.vue'
import StatValue from '@/components/StatValue.vue'
import StateDot from '@/components/StateDot.vue'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

/* 全局布局与数据展示组件（驾驶舱统一视觉语言） */
app.component('PageShell', PageShell)
app.component('PageHeader', PageHeader)
app.component('FilterBar', FilterBar)
app.component('DataTableShell', DataTableShell)
app.component('StatValue', StatValue)
app.component('StateDot', StateDot)

app.use(createPinia())

/* 在挂载前初始化主题，避免首屏闪烁 */
const themeStore = useThemeStore()
themeStore.init()

app.use(router)
app.use(ElementPlus)

app.mount('#app')
