import { hasAccessToken } from '@/api/auth'
import { useLoginUserStore } from '@/stores/loginUser'
import router from '@/router'
import { isPlatformAdminUser } from '@/utils/adminNavigation'

let firstFetchLoginUser = true

router.beforeEach(async (to, _from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }

  const requiresAdmin = to.matched.some((record) => record.meta.requiresAdmin === true)

  if (to.path === '/admin/403') {
    next()
    return
  }

  if (requiresAdmin || to.fullPath.startsWith('/admin')) {
    if (!hasAccessToken()) {
      next(`/user/login?redirect=${encodeURIComponent(to.fullPath)}`)
      return
    }
    if (!isPlatformAdminUser(loginUser)) {
      next('/admin/403')
      return
    }
  }

  next()
})
