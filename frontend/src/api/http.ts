import axios from 'axios'

export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string | null
}

interface ApiErrorPayload {
  success?: boolean
  message?: string | null
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15000,
  withCredentials: true,
})

export function extractApiError(error: unknown, fallbackMessage = '请求失败'): string {
  if (axios.isAxiosError<ApiErrorPayload>(error)) {
    const responseMessage = error.response?.data?.message

    if (typeof responseMessage === 'string' && responseMessage.trim().length > 0) {
      return responseMessage
    }

    if (typeof error.message === 'string' && error.message.trim().length > 0) {
      return error.message
    }
  }

  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }

  return fallbackMessage
}

export default http
