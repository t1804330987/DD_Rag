import http, { type ApiResponse } from './http'
import type {
  AssistantChatPayload,
  AssistantChatResult,
  AssistantChatStreamEvent,
  AssistantConversationContext,
  AssistantSessionDetail,
  AssistantSessionListItem,
} from '../types/assistant'

export async function createAssistantSession(): Promise<AssistantSessionDetail> {
  const { data } = await http.post<ApiResponse<AssistantSessionDetail>>('/assistant/sessions')
  if (!data.success || data.data == null) {
    throw new Error(data.message ?? '创建会话失败')
  }
  return data.data
}

export async function fetchAssistantSessions(): Promise<AssistantSessionListItem[]> {
  const { data } = await http.get<AssistantSessionListItem[]>('/assistant/sessions')
  return data
}

export async function fetchAssistantSessionDetail(sessionId: number): Promise<AssistantSessionDetail> {
  const { data } = await http.get<AssistantSessionDetail>(`/assistant/sessions/${sessionId}`)
  return data
}

export async function renameAssistantSession(sessionId: number, title: string): Promise<AssistantSessionDetail> {
  const { data } = await http.patch<ApiResponse<AssistantSessionDetail>>(`/assistant/sessions/${sessionId}`, { title })
  if (!data.success || data.data == null) {
    throw new Error(data.message ?? '重命名会话失败')
  }
  return data.data
}

export async function deleteAssistantSession(sessionId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/assistant/sessions/${sessionId}`)
  if (!data.success) {
    throw new Error(data.message ?? '删除会话失败')
  }
}

export async function fetchAssistantConversationContext(
  sessionId: number,
  recentLimit = 12,
): Promise<AssistantConversationContext> {
  const { data } = await http.get<AssistantConversationContext>(`/assistant/sessions/${sessionId}/context`, {
    params: { recentLimit },
  })
  return data
}

export async function sendAssistantMessage(payload: AssistantChatPayload): Promise<AssistantChatResult> {
  const { data } = await http.post<ApiResponse<AssistantChatResult>>('/assistant/chat', payload)
  if (!data.success || data.data == null) {
    throw new Error(data.message ?? '发送消息失败')
  }
  return data.data
}

export async function streamAssistantMessage(
  payload: AssistantChatPayload,
  accessToken: string,
  handlers: {
    onEvent: (event: AssistantChatStreamEvent) => void
    signal?: AbortSignal
  },
): Promise<void> {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '')
  const response = await fetch(`${baseUrl}/assistant/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(payload),
    signal: handlers.signal,
  })

  if (!response.ok || response.body == null) {
    const message = await response.text()
    throw new Error(message || '发送流式消息失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    let separatorIndex = buffer.indexOf('\n\n')
    while (separatorIndex >= 0) {
      const rawEvent = buffer.slice(0, separatorIndex)
      buffer = buffer.slice(separatorIndex + 2)
      const parsed = parseSseEvent(rawEvent)
      if (parsed !== null) {
        handlers.onEvent(parsed)
      }
      separatorIndex = buffer.indexOf('\n\n')
    }
  }
}

function parseSseEvent(rawEvent: string): AssistantChatStreamEvent | null {
  const lines = rawEvent.split(/\r?\n/)
  let eventName = ''
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  const parsed = JSON.parse(dataLines.join('\n')) as AssistantChatStreamEvent
  return {
    ...parsed,
    event: (eventName || parsed.event) as AssistantChatStreamEvent['event'],
  }
}
