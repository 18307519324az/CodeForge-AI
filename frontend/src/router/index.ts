import { createRouter, createWebHistory } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
import UserLayout from '@/layouts/UserLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/user',
      component: UserLayout,
      children: [
        {
          path: 'login',
          name: 'user-login',
          component: () => import('@/pages/user/UserLoginPage.vue'),
        },
        {
          path: 'register',
          name: 'user-register',
          component: () => import('@/pages/user/UserRegisterPage.vue'),
        },
      ],
    },
    {
      path: '/',
      component: BasicLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/pages/HomePage.vue'),
        },
        {
          path: 'apps',
          name: 'app-list',
          component: () => import('@/pages/app/AppListPage.vue'),
        },
        {
          path: 'explore',
          name: 'explore',
          component: () => import('@/pages/explore/ExplorePage.vue'),
        },
        {
          path: 'explore/apps/:slug',
          name: 'public-app-detail',
          component: () => import('@/pages/explore/PublicAppDetailPage.vue'),
        },
        {
          path: 'apps/create',
          name: 'app-create',
          component: () => import('@/pages/app/AppCreatePage.vue'),
        },
        {
          path: 'apps/:appId',
          name: 'app-detail',
          component: () => import('@/pages/app/AppDetailPage.vue'),
        },
        {
          path: 'apps/:appId/generate',
          name: 'app-generate',
          component: () => import('@/pages/app/AppGeneratePage.vue'),
        },
        {
          path: 'apps/:appId/versions',
          name: 'app-versions',
          component: () => import('@/pages/app/AppVersionPage.vue'),
        },
        {
          path: 'apps/:appId/files',
          name: 'app-files',
          component: () => import('@/pages/app/AppFilePreviewPage.vue'),
        },
        {
          path: 'prompt-templates',
          name: 'prompt-templates',
          component: () => import('@/pages/prompt/PromptTemplateListPage.vue'),
        },
        {
          path: 'admin/dashboard',
          name: 'admin-dashboard',
          meta: { requiresAdmin: true },
          component: () => import('@/pages/admin/DashboardPage.vue'),
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          meta: { requiresAdmin: true },
          component: () => import('@/pages/admin/UserManagePage.vue'),
        },
        {
          path: 'admin/apps',
          name: 'admin-apps',
          meta: { requiresAdmin: true },
          component: () => import('@/pages/admin/AppManagePage.vue'),
        },
        {
          path: 'admin/model-providers',
          name: 'admin-model-providers',
          meta: { requiresAdmin: true },
          component: () => import('@/pages/admin/ModelProviderPage.vue'),
        },
        {
          path: 'admin/model-call-logs',
          name: 'admin-model-call-logs',
          meta: { requiresAdmin: true },
          component: () => import('@/pages/admin/ModelCallLogPage.vue'),
        },
        {
          path: 'admin/prompt-templates',
          name: 'admin-prompt-templates',
          meta: { requiresAdmin: true },
          component: () => import('@/pages/admin/PromptTemplateManagePage.vue'),
        },
        {
          path: 'admin/403',
          name: 'admin-forbidden',
          component: () => import('@/pages/admin/AdminForbiddenPage.vue'),
        },
      ],
    },
  ],
})

export default router
