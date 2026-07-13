<template>
  <a-layout-header class="header">
    <a-row :wrap="false" align="middle">
      <a-col flex="200px">
        <RouterLink to="/" class="header-brand">
          <h1 class="site-title">CodeForge AI</h1>
        </RouterLink>
      </a-col>
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="selectedKeys"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-col>
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser">
            <a-dropdown>
              <a-space class="user-info">
                <a-avatar :src="loginUserStore.loginUser.avatarUrl" size="small" />
                <span>{{ loginUserStore.loginUser.displayName || loginUserStore.loginUser.account || '用户' }}</span>
              </a-space>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-space>
              <a-button type="primary" size="small" href="/user/login">登录</a-button>
              <a-button size="small" href="/user/register">注册</a-button>
            </a-space>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { LogoutOutlined, HomeOutlined } from '@ant-design/icons-vue'
import { logoutUser } from '@/api/auth'
import { useLoginUserStore } from '@/stores/loginUser'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const selectedKeys = ref<string[]>(['/'])

router.afterEach((to) => {
  selectedKeys.value = [to.path]
})

const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '首页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '模型供应商',
  },
  {
    key: '/admin/chatManage',
    label: '生成记录',
  },
]

const filterMenus = (menus = [] as MenuProps['items']) =>
  menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      return Boolean(loginUserStore.loginUser?.platformRoles?.includes('PLATFORM_ADMIN'))
    }
    return true
  })

const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

const handleMenuClick: MenuProps['onClick'] = (event) => {
  const key = event.key as string
  if (key.startsWith('/')) {
    router.push(key)
  }
}

const doLogout = async () => {
  logoutUser()
  loginUserStore.setLoginUser(null)
  message.success('已退出登录')
  await router.push('/user/login')
}
</script>

<style scoped>
.header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  padding: 0 24px;
  border-bottom: 1px solid rgba(102, 126, 234, 0.08);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.header-brand {
  display: flex;
  align-items: center;
  text-decoration: none;
}

.site-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.user-info {
  cursor: pointer;
}
</style>
