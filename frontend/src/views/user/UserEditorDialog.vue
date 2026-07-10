<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'create' ? '新增用户' : '编辑用户'"
    width="540px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @open="initializeForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          :disabled="mode === 'edit'"
          maxlength="20"
          placeholder="4-20 位字母、数字或下划线"
        />
      </el-form-item>
      <el-form-item v-if="mode === 'create'" label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password maxlength="32" />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="form.nickname" maxlength="50" />
      </el-form-item>
      <el-form-item label="角色" prop="role">
        <el-select v-model="form.role" class="full-width">
          <el-option
            v-for="item in roleOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'

defineOptions({ name: 'UserEditorDialog' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'create' },
  user: { type: Object, default: null },
  roleOptions: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'confirm'])
const formRef = ref(null)
const form = reactive({ username: '', password: '', nickname: '', role: '' })
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,20}$/, message: '用户名格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/,
      message: '密码须包含大小写字母和数字，长度 8-32 位',
      trigger: 'blur',
    },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function initializeForm() {
  form.username = props.user?.username || ''
  form.password = ''
  form.nickname = props.user?.nickname || ''
  form.role = props.user?.role || props.roleOptions[0]?.value || ''
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = {
    username: form.username,
    nickname: form.nickname,
    role: form.role,
  }
  if (props.mode === 'create') payload.password = form.password
  emit('confirm', payload)
}
</script>

<style scoped>
.full-width {
  width: 100%;
}
</style>
