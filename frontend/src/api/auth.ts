import http, { type ApiResponse } from './http'

export type SystemRole = 'ADMIN' | 'USER'

export interface CurrentUserProfile {
  userId: number
  userCode: string
  displayName: string
  systemRole: SystemRole
  mustChangePassword: boolean
}

export interface LoginPayload {
  loginId: string
  password: string
}

export interface RegisterPayload {
  username: string
  email: string
  displayName: string
  password: string
}

export interface AuthSessionResponse {
  accessToken: string
  currentUser: CurrentUserProfile
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

export async function login(payload: LoginPayload): Promise<AuthSessionResponse> {
  const { data } = await http.post<ApiResponse<AuthSessionResponse>>('/auth/login', payload, {
    withCredentials: true,
  })

  return unwrapApiResponse(data, '登录失败')
}

export async function register(payload: RegisterPayload): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/auth/register', payload)
  unwrapApiResponse(data, '注册失败')
}

export async function refreshSession(): Promise<AuthSessionResponse> {
  const { data } = await http.post<ApiResponse<AuthSessionResponse>>('/auth/refresh', null, {
    withCredentials: true,
  })

  return unwrapApiResponse(data, '登录状态已过期')
}

export async function logout(): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/auth/logout', null, {
    withCredentials: true,
  })
  unwrapApiResponse(data, '退出登录失败')
}

export async function fetchCurrentUser(): Promise<CurrentUserProfile> {
  const { data } = await http.get<ApiResponse<CurrentUserProfile>>('/auth/me')

  return unwrapApiResponse(data, '获取当前用户失败')
}

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/account/change-password', payload)
  unwrapApiResponse(data, '修改密码失败')
}

function unwrapApiResponse<T>(payload: ApiResponse<T>, fallbackMessage: string): T {
  if (!payload.success) {
    throw new Error(payload.message ?? fallbackMessage)
  }

  return payload.data
}
