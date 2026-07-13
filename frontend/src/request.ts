import axios from 'axios'
import { message } from 'ant-design-vue'
import { API_BASE_URL } from '@/config/env'
import { clearAccessToken, getAccessToken } from '@/auth/token'
import { isApiSuccessCode } from '@/utils/apiCode'

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipGlobalErrorToast?: boolean
  }
}

let isRedirectingToLogin = false

const isLoginRequest = (config?: { url?: string }) =>
  Boolean(config?.url?.includes('/auth/login'))

const myAxios = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true,
})

myAxios.interceptors.request.use(
  (config) => {
    const accessToken = getAccessToken()
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

myAxios.interceptors.response.use(
  (response) => {
    const { data } = response
    if (data.code === 40100 || data.code === 40101 || data.code === 40102) {
      clearAccessToken()
      if (
        !isRedirectingToLogin &&
        !response.request.responseURL.includes('/auth/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        isRedirectingToLogin = true
        message.warning('请先登录')
        const redirectPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
        window.location.href = `/user/login?redirect=${encodeURIComponent(redirectPath)}`
      }
    }
    if (data.code !== undefined && !isApiSuccessCode(data.code)) {
      const msg = data.message || '请求失败'
      if (!response.config.skipGlobalErrorToast) {
        message.error(msg)
      }
      return Promise.reject(new Error(msg))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    const responseMessage = error?.response?.data?.message as string | undefined
    const loginRequest = isLoginRequest(error?.config)

    if (status === 401) {
      clearAccessToken()
      if (!loginRequest && !isRedirectingToLogin && !window.location.pathname.includes('/user/login')) {
        isRedirectingToLogin = true
        message.error('登录状态已失效，请重新登录')
        setTimeout(() => {
          window.location.href = '/user/login'
        }, 500)
      }
      return Promise.reject(new Error(loginRequest ? (responseMessage || '账号或密码错误') : '登录状态已失效，请重新登录'))
    }

    const msg =
      error?.response?.data?.message ||
      error?.message ||
      '网络异常，请检查后端服务是否启动'

    if (status === 403) {
      if (!error?.config?.skipGlobalErrorToast) {
        message.error('暂无权限访问该资源')
      }
    } else if (status === 404) {
      if (!error?.config?.skipGlobalErrorToast) {
        message.error('接口不存在或服务未正确启动')
      }
    } else if (!error?.config?.skipGlobalErrorToast) {
      message.error(msg)
    }

    console.error('[request error]', error)
    return Promise.reject(new Error(msg))
  },
)

export default myAxios
