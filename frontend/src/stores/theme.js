/*
 * 类功能:全局主题状态管理（Pinia Setup Store），支持日间/夜间模式切换与持久化
 *
 * 【主题值】
 * - light: 方案 B（现代 SaaS 浅色）
 * - dark: 方案 A（工业 SCADA 深色）
 *
 * 【持久化】
 * - 用户选择写入 localStorage.key = 'app-theme'
 * - 刷新页面后从 localStorage 恢复，无记录则跟随系统 prefers-color-scheme
 */
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const THEME_KEY = 'app-theme'
export const THEME_LIGHT = 'light'
export const THEME_DARK = 'dark'

function getSystemTheme() {
  if (typeof window === 'undefined' || !window.matchMedia) return THEME_LIGHT
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? THEME_DARK : THEME_LIGHT
}

function getStoredTheme() {
  if (typeof window === 'undefined') return null
  const stored = localStorage.getItem(THEME_KEY)
  return stored === THEME_LIGHT || stored === THEME_DARK ? stored : null
}

function applyTheme(value) {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', value)
}

export const useThemeStore = defineStore('theme', () => {
  const theme = ref(THEME_LIGHT)
  const isReady = ref(false)

  /* 方法功能:初始化主题，优先从 localStorage 恢复，否则跟随系统偏好
   * 参数:无
   * 返回数据:无
   */
  function init() {
    const stored = getStoredTheme()
    const initial = stored || getSystemTheme()
    theme.value = initial
    applyTheme(initial)
    isReady.value = true
  }

  /* 方法功能:手动切换主题并持久化
   * 参数:无
   * 返回数据:无
   */
  function toggle() {
    theme.value = theme.value === THEME_LIGHT ? THEME_DARK : THEME_LIGHT
  }

  /* 方法功能:设置指定主题
   * 参数:value - 'light' 或 'dark'
   * 返回数据:无
   */
  function setTheme(value) {
    if (value !== THEME_LIGHT && value !== THEME_DARK) return
    theme.value = value
  }

  watch(theme, (value) => {
    applyTheme(value)
    if (typeof window !== 'undefined') {
      localStorage.setItem(THEME_KEY, value)
    }
  })

  return {
    theme,
    isReady,
    init,
    toggle,
    setTheme,
  }
})
