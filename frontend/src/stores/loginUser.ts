import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCurrentUser, hasAccessToken, type CurrentUserResponse } from '@/api/auth'

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<CurrentUserResponse | null>(null)

  async function fetchLoginUser() {
    if (!hasAccessToken()) {
      loginUser.value = null
      return null
    }
    try {
      const res = await getCurrentUser()
      if (res.data.code === 0 && res.data.data) {
        loginUser.value = res.data.data
        return res.data.data
      }
    } catch {
      loginUser.value = null
    }
    return null
  }

  function setLoginUser(newLoginUser: CurrentUserResponse | null) {
    loginUser.value = newLoginUser
  }

  return { loginUser, fetchLoginUser, setLoginUser }
})
