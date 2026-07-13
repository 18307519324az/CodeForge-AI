export const DEFAULT_API_PROXY_TARGET = 'http://127.0.0.1:8150'

export const resolveApiProxyTarget = (
  env: Record<string, string | undefined>,
): string => {
  const configured = env.VITE_API_PROXY_TARGET?.trim()
  if (configured) {
    return configured.replace(/\/+$/, '')
  }
  return DEFAULT_API_PROXY_TARGET
}
