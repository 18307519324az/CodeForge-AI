const ACCESS_TOKEN_KEY = 'codeforge_access_token'

export const getAccessToken = () => localStorage.getItem(ACCESS_TOKEN_KEY) ?? ''

export const setAccessToken = (token: string) => {
  localStorage.setItem(ACCESS_TOKEN_KEY, token)
}

export const clearAccessToken = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}
