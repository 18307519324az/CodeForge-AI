<template>
  <div class="register-page">
    <div class="split-container">
      <div class="brand-side">
        <h1 class="brand-name">CodeForge AI</h1>
        <p class="brand-tagline">智能应用生成与发布平台</p>
        <div class="feature-list">
          <div class="feature-item">对话式生成应用</div>
          <div class="feature-item">版本快照追踪</div>
          <div class="feature-item">文件预览与导出</div>
        </div>
      </div>
      <div class="form-side">
        <div class="form-card">
          <h2 class="form-title">创建账号</h2>
          <a-form :model="form" autocomplete="off" @finish="handleSubmit" :label-col="{ span: 0 }">
            <a-form-item name="account" :rules="[{ required: true, message: '请输入账号' }]">
              <a-input v-model:value="form.account" placeholder="请输入账号" size="large" />
            </a-form-item>
            <a-form-item name="displayName" :rules="[{ required: true, message: '请输入显示名称' }]">
              <a-input v-model:value="form.displayName" placeholder="请输入显示名称" size="large" />
            </a-form-item>
            <a-form-item name="email" :rules="[{ required: true, message: '请输入邮箱' }, { type: 'email', message: '邮箱格式不正确' }]">
              <a-input v-model:value="form.email" placeholder="请输入邮箱" size="large" />
            </a-form-item>
            <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }, { min: 8, message: '密码不少于 8 位' }]">
              <a-input-password v-model:value="form.password" placeholder="请输入密码（至少 8 位）" size="large" />
            </a-form-item>
            <a-form-item name="confirmPassword" :rules="[{ required: true, message: '请确认密码' }, { validator: validateCheckPwd }]">
              <a-input-password v-model:value="form.confirmPassword" placeholder="请再次输入密码" size="large" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" html-type="submit" size="large" block :loading="submitting">注册</a-button>
            </a-form-item>
          </a-form>
          <div class="form-footer">
            已有账号？<RouterLink to="/user/login">去登录</RouterLink>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { registerUser, type UserRegisterRequest } from '@/api/auth'

const router = useRouter()
const submitting = ref(false)
const form = reactive<UserRegisterRequest>({ account: '', displayName: '', email: '', password: '', confirmPassword: '' })

const validateCheckPwd = (_: unknown, value: string, cb: (e?: Error) => void) => {
  cb(value && value !== form.password ? new Error('两次输入的密码不一致') : undefined)
}

const handleSubmit = async (values: UserRegisterRequest) => {
  submitting.value = true
  try {
    const res = await registerUser(values)
    if (res.data.code === 0) {
      message.success('注册成功，请登录')
      await router.push({ path: '/user/login', replace: true })
      return
    }
    message.error(res.data.message || '注册失败')
  } catch (e: any) { message.error(e?.message || '注册失败') }
  finally { submitting.value = false }
}
</script>

<style scoped>
.register-page { height: 100vh; background: var(--cf-bg); }
.split-container { display: flex; height: 100%; max-width: 1120px; margin: 0 auto; }
.brand-side { flex: 1; display: flex; flex-direction: column; justify-content: center; padding: 80px 60px; }
.brand-name { font-size: 32px; font-weight: 800; color: var(--cf-text); margin: 0 0 8px; }
.brand-tagline { font-size: 16px; color: var(--cf-text-secondary); margin: 0 0 40px; }
.feature-list { display: flex; flex-direction: column; gap: 16px; }
.feature-item { font-size: 14px; color: var(--cf-text-secondary); padding-left: 20px; position: relative; }
.feature-item::before { content: ''; position: absolute; left: 0; top: 6px; width: 8px; height: 8px; border-radius: 50%; background: var(--cf-text); }
.form-side { flex: 1; display: flex; align-items: center; justify-content: center; }
.form-card { width: 400px; background: var(--cf-surface); border: 1px solid var(--cf-border); border-radius: var(--cf-radius-lg); padding: 32px; }
.form-title { font-size: 20px; font-weight: 600; margin: 0 0 24px; color: var(--cf-text); }
.form-footer { text-align: center; font-size: 13px; color: var(--cf-text-tertiary); margin-top: 4px; }
.form-footer a { color: var(--cf-text); font-weight: 500; }
</style>
