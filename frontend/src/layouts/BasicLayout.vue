<template>
  <div class="cf-shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="cf-sidebar">
      <div class="cf-sidebar-inner">
        <div class="cf-brand">
          <div class="cf-brand-mark">CF</div>
          <span v-if="!collapsed" class="cf-brand-text">CodeForge AI</span>
        </div>

        <div class="cf-sidebar-new">
          <button class="cf-new-btn" @click="router.push('/apps/create')">
            <PlusOutlined />
            <span v-if="!collapsed" class="cf-new-label">新建生成</span>
          </button>
        </div>

        <nav class="cf-nav">
          <div v-for="item in navItems" :key="item.key"
               class="cf-nav-item" :class="{ active: isActive(item.path) }"
               @click="router.push(item.path)">
            <component :is="item.icon" class="cf-nav-icon" />
            <span v-if="!collapsed" class="cf-nav-label">{{ item.label }}</span>
          </div>
          <template v-if="isAdmin">
            <div class="cf-nav-divider"></div>
            <div v-for="item in adminItems" :key="item.key"
                 class="cf-nav-item" :class="{ active: isActive(item.path) }"
                 @click="router.push(item.path)">
              <component :is="item.icon" class="cf-nav-icon" />
              <span v-if="!collapsed" class="cf-nav-label">{{ item.label }}</span>
            </div>
          </template>
        </nav>

        <div class="cf-user-footer" v-if="loginUserStore.loginUser">
          <a-avatar :src="loginUserStore.loginUser.avatarUrl" :size="30" />
          <div v-if="!collapsed" class="cf-user-meta">
            <span class="cf-user-name">{{ loginUserStore.loginUser.displayName || loginUserStore.loginUser.account }}</span>
            <button class="cf-logout-btn" @click="doLogout">退出</button>
          </div>
        </div>
        <div class="cf-user-footer" v-else>
          <a-button size="small" block @click="router.push('/user/login')">登录</a-button>
        </div>
      </div>
    </aside>

    <main class="cf-main">
      <header class="cf-topbar">
        <button class="cf-collapse-btn" @click="collapsed = !collapsed">
          <MenuFoldOutlined v-if="!collapsed" /><MenuUnfoldOutlined v-else />
        </button>
        <div>
          <div class="cf-page-title">{{ pageTitle }}</div>
          <div class="cf-page-subtitle" v-if="pageSubtitle">{{ pageSubtitle }}</div>
        </div>
        <div class="cf-topbar-spacer"></div>

        <button v-if="showRightPanel && !rightPanelVisible" class="cf-context-toggle" @click="rightPanelVisible = true">
          显示上下文
        </button>
        <button v-else-if="showRightPanel && rightPanelVisible" class="cf-context-toggle" @click="rightPanelVisible = false">
          隐藏上下文
        </button>

        <div class="cf-topbar-right" v-if="loginUserStore.loginUser">
          <a-avatar :src="loginUserStore.loginUser.avatarUrl" :size="28" />
          <span class="cf-topbar-user">{{ loginUserStore.loginUser.displayName || loginUserStore.loginUser.account }}</span>
        </div>
        <div class="cf-topbar-right" v-else>
          <a-button size="small" @click="router.push('/user/login')">登录</a-button>
        </div>
      </header>

      <section class="cf-content">
        <router-view @update-app="currentApp = $event" />
      </section>
    </main>

    <RightPanel v-if="showRightPanel && rightPanelVisible" :app="currentApp" class="cf-right-panel" @close="rightPanelVisible = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, MenuFoldOutlined, MenuUnfoldOutlined, HomeOutlined, AppstoreOutlined, FileTextOutlined, BarChartOutlined, CompassOutlined } from '@ant-design/icons-vue'
import { logoutUser } from '@/api/auth'
import { useLoginUserStore } from '@/stores/loginUser'
import type { AiAppDetailResponse } from '@/api/app'
import RightPanel from '@/components/RightPanel.vue'
import { ADMIN_NAV_ITEMS, isPlatformAdminUser } from '@/utils/adminNavigation'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()

// sidebar collapse with localStorage
const savedCollapsed = localStorage.getItem('codeforge:sidebarCollapsed')
const collapsed = ref(savedCollapsed === 'true')
watch(collapsed, (v) => localStorage.setItem('codeforge:sidebarCollapsed', String(v)))

// right panel visibility with localStorage
const savedRightPanel = localStorage.getItem('codeforge:rightPanelVisible')
const rightPanelVisible = ref(savedRightPanel !== 'false') // default to visible
watch(rightPanelVisible, (v) => localStorage.setItem('codeforge:rightPanelVisible', String(v)))

const currentApp = ref<AiAppDetailResponse | null>(null)
const isAdmin = computed(() => isPlatformAdminUser(loginUserStore.loginUser))
const adminItems = computed(() => (isAdmin.value ? ADMIN_NAV_ITEMS : []))

const pageTitles: Record<string, string> = {
  '/': 'AI 工作台', '/apps': '我的应用', '/explore': '应用广场', '/apps/create': '新建应用',
  '/prompt-templates': '提示词模板', '/admin/dashboard': '管理概览',
  '/admin/users': '用户管理', '/admin/apps': '应用管理',
  '/admin/model-providers': '模型供应商', '/admin/model-call-logs': '模型调用日志',
  '/admin/prompt-templates': '提示词模板管理',
}
const pageSubtitles: Record<string, string> = {
  '/': '输入需求，让 CodeForge AI 生成应用原型',
  '/apps': '管理你创建的 AI 应用原型、版本和生成记录',
  '/apps/create': '填写基础信息，创建 AI 应用原型',
}
const pageTitle = computed(() => {
  if (route.path.startsWith('/explore/apps/')) return '公开应用详情'
  if (route.path.startsWith('/apps/') && route.path !== '/apps' && route.path !== '/apps/create') return '应用详情'
  return pageTitles[route.path] || 'CodeForge AI'
})
const pageSubtitle = computed(() => pageSubtitles[route.path] || '')

const navItems = [
  { key: 'wb', label: 'AI 工作台', icon: HomeOutlined, path: '/' },
  { key: 'apps', label: '我的应用', icon: AppstoreOutlined, path: '/apps' },
  { key: 'explore', label: '应用广场', icon: CompassOutlined, path: '/explore' },
  { key: 'tpl', label: '提示词模板', icon: FileTextOutlined, path: '/prompt-templates' },
]
const showRightPanel = computed(() => route.path === '/' || route.path.startsWith('/apps/'))
const isActive = (path: string) => route.path === path || (path === '/explore' && route.path.startsWith('/explore'))

const doLogout = async () => {
  logoutUser()
  loginUserStore.setLoginUser(null)
  message.success('已退出登录')
  await router.push('/user/login')
}
</script>

<style scoped>
.cf-shell { display: flex; height: 100vh; width: 100vw; overflow: hidden; background: var(--cf-bg); }
.cf-sidebar { width: 260px; min-width: 260px; background: var(--cf-sidebar-bg); border-right: 1px solid var(--cf-sidebar-border); display: flex; flex-direction: column; transition: width 0.15s, min-width 0.15s; overflow: hidden; }
.cf-shell.is-collapsed .cf-sidebar { width: 72px; min-width: 72px; }
.cf-sidebar-inner { display: flex; flex-direction: column; height: 100%; }
.cf-brand { height: 56px; display: flex; align-items: center; gap: 10px; padding: 0 16px; overflow: hidden; }
.cf-brand-mark { width: 32px; height: 32px; border-radius: 8px; background: #111; color: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 13px; font-weight: 700; }
.cf-brand-text { font-size: 16px; font-weight: 700; color: var(--cf-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cf-shell.is-collapsed .cf-brand { justify-content: center; padding: 0; }
.cf-shell.is-collapsed .cf-brand-text { display: none; }
.cf-sidebar-new { padding: 0 14px 12px; }
.cf-shell.is-collapsed .cf-sidebar-new { padding: 0 12px 12px; }
.cf-new-btn { width: 100%; height: 40px; border-radius: 10px; background: var(--cf-primary); border: none; color: #fff; font-weight: 500; font-size: 13px; display: flex; align-items: center; justify-content: center; gap: 6px; cursor: pointer; overflow: hidden; }
.cf-new-btn:hover { background: var(--cf-primary-hover); }
.cf-shell.is-collapsed .cf-new-btn { width: 44px; padding: 0; }
.cf-shell.is-collapsed .cf-new-label { display: none; }
.cf-nav { flex: 1; min-height: 0; padding: 0 10px; overflow-y: auto; }
.cf-nav-item { height: 42px; border-radius: 10px; padding: 0 12px; display: flex; align-items: center; gap: 12px; color: var(--cf-text-secondary); cursor: pointer; overflow: hidden; white-space: nowrap; font-size: 13px; margin-bottom: 2px; }
.cf-nav-item:hover { background: var(--cf-surface-hover); color: var(--cf-text); }
.cf-nav-item.active { background: var(--cf-surface-active); color: var(--cf-text); font-weight: 600; }
.cf-nav-icon { width: 18px; height: 18px; flex-shrink: 0; }
.cf-nav-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cf-shell.is-collapsed .cf-nav-item { justify-content: center; padding: 0; }
.cf-shell.is-collapsed .cf-nav-label { display: none; }
.cf-nav-divider { height: 1px; background: var(--cf-border); margin: 8px 12px; }
.cf-shell.is-collapsed .cf-nav-divider { margin: 8px 8px; }
.cf-user-footer { height: 56px; border-top: 1px solid var(--cf-border); padding: 10px 14px; display: flex; align-items: center; gap: 10px; overflow: hidden; flex-shrink: 0; }
.cf-user-meta { min-width: 0; display: flex; align-items: center; gap: 8px; flex: 1; }
.cf-user-name { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: var(--cf-text-secondary); }
.cf-logout-btn { color: var(--cf-danger); background: none; border: none; font-size: 12px; cursor: pointer; white-space: nowrap; }
.cf-shell.is-collapsed .cf-user-footer { justify-content: center; padding: 10px 0; }
.cf-shell.is-collapsed .cf-user-meta { display: none; }
.cf-main { min-width: 0; flex: 1; display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
.cf-topbar { height: 56px; min-height: 56px; border-bottom: 1px solid var(--cf-border); background: var(--cf-surface); display: flex; align-items: center; padding: 0 16px; gap: 12px; }
.cf-collapse-btn { width: 32px; height: 32px; border: none; background: transparent; color: var(--cf-text-secondary); border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
.cf-collapse-btn:hover { background: var(--cf-surface-hover); color: var(--cf-text); }
.cf-page-title { font-size: 14px; font-weight: 600; color: var(--cf-text); }
.cf-page-subtitle { font-size: 12px; color: var(--cf-text-tertiary); }
.cf-topbar-spacer { flex: 1; }
.cf-context-toggle { height: 32px; padding: 0 12px; border: 1px solid var(--cf-border); border-radius: 8px; background: var(--cf-surface); color: var(--cf-text-secondary); cursor: pointer; font-size: 12px; white-space: nowrap; }
.cf-context-toggle:hover { background: var(--cf-surface-hover); color: var(--cf-text); }
.cf-topbar-right { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--cf-text-secondary); }
.cf-topbar-user { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cf-content { flex: 1; min-height: 0; min-width: 0; overflow-x: hidden; overflow-y: auto; }
.cf-right-panel { width: 360px; min-width: 360px; border-left: 1px solid var(--cf-border); background: var(--cf-surface); height: 100vh; overflow-y: auto; flex-shrink: 0; }
@media (max-width: 1440px) { .cf-right-panel { display: none; } }
</style>
