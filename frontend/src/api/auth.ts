import request from '@/request'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/auth/token'
import type { LongId } from '@/types/id'

export interface ApiResponse<T> {
  code: number
  message: string
  data?: T
  requestId?: string
}

export interface CurrentUserResponse {
  id: LongId
  account: string
  displayName?: string
  avatarUrl?: string
  email?: string
  phone?: string
  status?: string
  lastLoginAt?: string
  platformRoles: string[]
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: CurrentUserResponse
  platformRoles: string[]
}

export interface RegisterResponse {
  user: CurrentUserResponse
  platformRoles: string[]
}

export interface UserLoginRequest {
  account: string
  password: string
}

export interface UserRegisterRequest {
  account: string
  password: string
  confirmPassword: string
  displayName?: string
  email?: string
}

export interface UserUpdateRequest {
  displayName?: string
  avatarUrl?: string
  email?: string
  phone?: string
}

export const registerUser = (body: UserRegisterRequest) =>
  request<ApiResponse<RegisterResponse>>('/auth/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const loginUser = async (body: UserLoginRequest) => {
  const response = await request<ApiResponse<LoginResponse>>('/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })
  const accessToken = response.data?.data?.accessToken
  if (response.data?.code === 0 && accessToken) {
    setAccessToken(accessToken)
  }
  return response
}

export const getCurrentUser = () =>
  request<ApiResponse<CurrentUserResponse>>('/users/me', {
    method: 'GET',
  })

export const updateCurrentUser = (body: UserUpdateRequest) =>
  request<ApiResponse<CurrentUserResponse>>('/users/me', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
  })

export const hasAccessToken = () => Boolean(getAccessToken())

export const logoutUser = () => {
  clearAccessToken()
}
