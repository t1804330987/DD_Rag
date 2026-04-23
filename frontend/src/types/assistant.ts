export type AssistantToolMode = 'CHAT' | 'KB_SEARCH'

export type AssistantMessageRole = 'USER' | 'ASSISTANT' | 'TOOL'

export interface AssistantCitationItem {
  documentId: number | null
  chunkId: number | null
  chunkIndex: number | null
  fileName: string
  score: number
  snippet: string | null
}

export interface AssistantSessionListItem {
  sessionId: number
  title: string
  lastMessageAt: string | null
}

export interface AssistantSessionDetail {
  sessionId: number
  title: string
  status: string
  lastMessageAt: string | null
  createdAt: string
}

export interface AssistantMessageItem {
  messageId: number
  sessionId: number
  role: AssistantMessageRole
  toolMode: AssistantToolMode | null
  groupId: number | null
  content: string
  structuredPayload: string | null
  createdAt: string
}

export interface AssistantConversationContext {
  summaryText: string | null
  recentMessages: AssistantMessageItem[]
}

export interface AssistantChatPayload {
  sessionId: number
  message: string
  toolMode: AssistantToolMode
  groupId?: number | null
}

export interface AssistantChatResult {
  sessionId: number
  messageId: number
  reply: string
  toolMode: AssistantToolMode
  groupId: number | null
  citations: AssistantCitationItem[]
}

export interface AssistantChatStreamEvent {
  event: 'start' | 'delta' | 'done' | 'error'
  sessionId: number
  toolMode: AssistantToolMode
  groupId: number | null
  delta: string | null
  messageId: number | null
  reply: string | null
  citations: AssistantCitationItem[]
  error: string | null
}
