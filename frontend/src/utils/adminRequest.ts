import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'
import router from '@/router'
import { API_BASE_URL } from '@/config/env'
import { clearAccessToken, getAccessToken } from '@/auth/token'
import {
  AdminApiError,
  isAdminPermissionError,
  isAuthLoginFailedError,
  isBusinessConflictError,
  isRateLimitError,
  isTokenExpiredError,
  type AdminApiErrorBody,
} from '@/types/adminError'
import { mapAxiosErrorToAdminApiError, mapResponseBodyError } from '@/utils/adminError'
import { adminErrorMessages } from '@/locales/zh-CN/admin/error'
import { showConflictModal } from '@/composables/showConflictModal'

let redirectingToLogin = false

function redirectToLogin(redirectPath?: string) {
  if (redirectingToLogin) return
  redirectingToLogin = true
  const path =
    redirectPath ||
    `${window.location.pathname}${window.location.search}${window.location.hash}`
  const loginPath = `/user/login?redirect=${encodeURIComponent(path)}`
  if (router.currentRoute.value.path.startsWith('/admin')) {
    void router.push(loginPath).finally(() => {
      redirectingToLogin = false
    })
  } else {
    window.location.href = loginPath
    redirectingToLogin = false
  }
}

function handleForbidden(error: AdminApiError) {
  if (router.currentRoute.value.path !== '/admin/403') {
    void router.push('/admin/403')
  }
  return Promise.reject(error)
}

const adminRequest = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true,
})

adminRequest.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

adminRequest.interceptors.response.use(
  (response: AxiosResponse) => {
    const body = response.data as AdminApiErrorBody & { data?: unknown }
    if (body && typeof body.code === 'number' && body.code !== 0) {
      const error = mapResponseBodyError(body)
      if (isTokenExpiredError(error)) {
        clearAccessToken()
        message.warning(adminErrorMessages.tokenExpired)
        redirectToLogin()
        return Promise.reject(error)
      }
      if (isAdminPermissionError(error)) {
        return handleForbidden(error)
      }
      if (isBusinessConflictError(error)) {
        showConflictModal(error.message)
        return Promise.reject(error)
      }
      if (isRateLimitError(error)) {
        message.warning(error.message || adminErrorMessages.rateLimit)
        return Promise.reject(error)
      }
      message.error(error.message)
      return Promise.reject(error)
    }
    return response
  },
  (error) => {
    const adminError = mapAxiosErrorToAdminApiError(error)

    if (isAuthLoginFailedError(adminError)) {
      return Promise.reject(adminError)
    }

    if (isTokenExpiredError(adminError) || adminError.httpStatus === 401) {
      clearAccessToken()
      message.warning(adminErrorMessages.tokenExpired)
      redirectToLogin()
      return Promise.reject(adminError)
    }

    if (isAdminPermissionError(adminError)) {
      return handleForbidden(adminError)
    }

    if (isBusinessConflictError(adminError)) {
      showConflictModal(adminError.message)
      return Promise.reject(adminError)
    }

    if (isRateLimitError(adminError)) {
      message.warning(adminError.message || adminErrorMessages.rateLimit)
      return Promise.reject(adminError)
    }

    if (adminError.httpStatus === 404) {
      return Promise.reject(adminError)
    }

    if ((adminError.httpStatus ?? 0) >= 500) {
      return Promise.reject(adminError)
    }

    message.error(adminError.message)
    return Promise.reject(adminError)
  },
)

export default adminRequest
