/*
 * 文件功能:最近告警列表组件，展示最近 N 条未处理告警（含级别颜色标记）
 *
 * 【告警级别颜色映射】
 * 使用 constants.js 中的 ALERT_LEVEL 枚举：
 *   1(紧急) → danger(红)  |  2(严重) → warning(橙)
 *   3(一般) → primary(蓝)  |  4(警告) → info(灰)  |  5(提示) → info(灰)
 */
<template>
  <el-table :data="alerts" v-loading="loading" size="small" max-height="300">
    <el-table-column prop="deviceName" label="设备" min-width="100" />
    <el-table-column prop="alertType" label="告警类型" min-width="80" />
    <!-- 告警级别：使用 ElTag + 颜色映射，直观展示紧急程度 -->
    <el-table-column prop="alertLevel" label="级别" width="70">
      <template #default="{ row }">
        <el-tag :type="levelType(row.alertLevel)" size="small">
          {{ ALERT_LEVEL[row.alertLevel]?.label || row.alertLevel }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="createTime" label="时间" min-width="140" />
  </el-table>
</template>

<script setup>
import { ALERT_LEVEL } from '@/utils/constants'

defineProps({
  alerts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

/*
 * 方法功能:将告警级别数字映射为 Element Plus Tag 的 type 属性
 * 参数:level - 告警级别（1-5）
 * 返回数据:string - ElTag type 值
 */
function levelType(level) {
  const map = { 1: 'danger', 2: 'warning', 3: 'primary', 4: 'info', 5: 'info' }
  return map[level] || ''
}
</script>
