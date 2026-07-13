<template>
  <div class="login-page">
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
          <h2 class="form-title">欢迎回来</h2>
          <a-alert
            v-if="loginError"
            type="error"
            show-icon
            :message="loginError"
            class="login-error"
          />
          <a-form :model="form" autocomplete="off" @finish="handleSubmit" :label-col="{ span: 0 }">
            <a-form-item name="account" :rules="[{ required: true, message: '请输入账号' }]">
              <a-input v-model:value="form.account" placeholder="请输入账号" size="large" />
            </a-form-item>
            <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }, { min: 8, message: '密码不少于 8 位' }]">
              <a-input-password v-model:value="form.password" placeholder="请输入密码" size="large" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" html-type="submit" size="large" block :loading="submitting">登录</a-button>
            </a-form-item>
          </a-form>
          <div class="form-footer">
            没有账号？<RouterLink to="/user/register">去注册</RouterLink>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { loginUser, type UserLoginRequest } from '@/api/auth'
import { useLoginUserStore } from '@/stores/loginUser'

import { adminErrorMessages } from '@/locales/zh-CN/admin/error'

const form = reactive<UserLoginRequest>({ account: '', password: '' })
const router = useRouter()
const route = useRoute()
const store = useLoginUserStore()
const submitting = ref(false)
const loginError = ref('')

const handleSubmit = async (values: UserLoginRequest) => {
  submitting.value = true
  loginError.value = ''
  try {
    const res = await loginUser(values)
    if (res.data.code === 0 && res.data.data) {
      await store.fetchLoginUser()
      message.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
      await router.push(redirect || '/')
      return
    }
    loginError.value = res.data.message || adminErrorMessages.loginFailed
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : adminErrorMessages.loginFailed
    loginError.value = msg.includes('超时') ? adminErrorMessages.loginFailed : msg
  }
  finally { submitting.value = false }
}
</script>

<style scoped>
.login-page { height: 100vh; background: var(--cf-bg); }
.split-container { display: flex; height: 100%; max-width: 1120px; margin: 0 auto; }
.brand-side { flex: 1; display: flex; flex-direction: column; justify-content: center; padding: 80px 60px; }
.brand-name { font-size: 32px; font-weight: 800; color: var(--cf-text); margin: 0 0 8px; }
.brand-tagline { font-size: 16px; color: var(--cf-text-secondary); margin: 0 0 40px; }
.feature-list { display: flex; flex-direction: column; gap: 16px; }
.feature-item { font-size: 14px; color: var(--cf-text-secondary); padding-left: 20px; position: relative; }
.feature-item::before { content: ''; position: absolute; left: 0; top: 6px; width: 8px; height: 8px; border-radius: 50%; background: var(--cf-text); }
.form-side { flex: 1; display: flex; align-items: center; justify-content: center; }
.form-card { width: 380px; background: var(--cf-surface); border: 1px solid var(--cf-border); border-radius: var(--cf-radius-lg); padding: 32px; }
.form-title { font-size: 20px; font-weight: 600; margin: 0 0 24px; color: var(--cf-text); }
.login-error { margin-bottom: 16px; }
.form-footer { text-align: center; font-size: 13px; color: var(--cf-text-tertiary); margin-top: 4px; }
.form-footer a { color: var(--cf-text); font-weight: 500; }
</style>
