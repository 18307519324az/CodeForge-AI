export const DEFAULT_API_BASE_URL = '/api/v1'

const normalizeUrlBase = (value: string) => value.trim().replace(/\/+$/, '')

export const resolveApiBaseUrl = (
  env: Record<string, string | undefined>,
): string => {
  const configured = env.VITE_API_BASE_URL?.trim()
  if (configured) {
    return normalizeUrlBase(configured)
  }
  return DEFAULT_API_BASE_URL
}

export const resolveAppBaseUrl = (
  env: Record<string, string | undefined>,
  apiBaseUrl: string = resolveApiBaseUrl(env),
): string => {
  const configured = env.VITE_APP_BASE_URL?.trim()
  if (configured) {
    return normalizeUrlBase(configured)
  }
  return normalizeUrlBase(apiBaseUrl.replace(/\/v1$/, ''))
}
