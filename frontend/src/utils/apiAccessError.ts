export type ApiAccessErrorKind = 'unauthorized' | 'forbidden' | 'not-found' | 'server' | 'generic'

export interface ApiAccessErrorView {
  kind: ApiAccessErrorKind
  title: string
  message: string
  clearToken: boolean
  redirectToLogin: boolean
}

interface ApiErrorPayload {
  code?: number
  message?: string
}

const readPayload = (error: unknown): ApiErrorPayload => {
  if (typeof error !== 'object' || error === null) {
    return {}
  }
  const response = 'response' in error ? (error as { response?: { data?: ApiErrorPayload; status?: number } }).response : undefined
  return {
    code: response?.data?.code,
    message: response?.data?.message,
  }
}

const readStatus = (error: unknown): number | undefined => {
  if (typeof error !== 'object' || error === null || !('response' in error)) {
    return undefined
  }
  return (error as { response?: { status?: number } }).response?.status
}

export const resolveApiAccessError = (error: unknown): ApiAccessErrorView => {
  const status = readStatus(error)
  const payload = readPayload(error)

  if (status === 401 || payload.code === 40100 || payload.code === 40101 || payload.code === 40102) {
    return {
      kind: 'unauthorized',
      title: '登录状态已失效',
      message: '登录状态已失效，请重新登录',
      clearToken: true,
      redirectToLogin: true,
    }
  }

  if (status === 403 || payload.code === 40301 || payload.code === 40300) {
    return {
      kind: 'forbidden',
      title: '无权访问该应用',
      message: '无权访问该应用',
      clearToken: false,
      redirectToLogin: false,
    }
  }

  if (status === 404 || payload.code === 40400 || payload.code === 40403) {
    return {
      kind: 'not-found',
      title: '应用不存在或已删除',
      message: '应用不存在或已删除',
      clearToken: false,
      redirectToLogin: false,
    }
  }

  if (status === 500 || (payload.code != null && payload.code >= 50000)) {
    return {
      kind: 'server',
      title: '服务暂时不可用',
      message: '服务暂时不可用，请稍后重试',
      clearToken: false,
      redirectToLogin: false,
    }
  }

  return {
    kind: 'generic',
    title: '加载失败',
    message: payload.message || (error instanceof Error ? error.message : '请求失败'),
    clearToken: false,
    redirectToLogin: false,
  }
}
