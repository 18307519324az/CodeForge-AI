import { CodeGenTypeEnum } from '@/utils/codeGenTypes'
import { resolveApiBaseUrl, resolveAppBaseUrl } from '@/config/apiBaseUrl'

const normalizeUrlBase = (value: string) => value.trim().replace(/\/+$/, '')

const env = import.meta.env as Record<string, string | undefined>
const rawDeployDomain = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

export const DEPLOY_DOMAIN = normalizeUrlBase(rawDeployDomain)
export const API_BASE_URL = resolveApiBaseUrl(env)
export const APP_BASE_URL = resolveAppBaseUrl(env, API_BASE_URL)
export const STATIC_BASE_URL = `${APP_BASE_URL}/static`

export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${deployKey}`
}

export const getStaticPreviewUrl = (codeGenType: string, appId: string) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}
