<template>
  <div class="table-shell">
    <!-- 骨架态：首次加载 -->
    <div v-if="loading && !hasData" class="table-skeleton">
      <el-skeleton :rows="skeletonRows" animated />
    </div>

    <!-- 错误态：重试入口 -->
    <div v-else-if="error" class="table-error">
      <el-empty :image-size="72" description="数据加载失败">
        <el-button type="primary" plain @click="$emit('retry')">重试</el-button>
      </el-empty>
    </div>

    <!-- 空态：引导创建 -->
    <div v-else-if="!hasData" class="table-empty">
      <el-empty :image-size="72" :description="emptyText">
        <slot name="empty-action" />
      </el-empty>
    </div>

    <!-- 数据态 -->
    <template v-else>
      <slot />
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  dataCount: { type: Number, default: 0 },
  emptyText: { type: String, default: '暂无数据' },
  skeletonRows: { type: Number, default: 6 },
})

defineEmits(['retry'])

const hasData = computed(() => props.dataCount > 0)
</script>

<style scoped>
.table-skeleton {
  padding: 20px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.table-error,
.table-empty {
  padding: 28px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
</style>
