import http from './http'

export interface CitationItem {
  documentId: number | null
  chunkId: number | null
  chunkIndex: number | null
  fileName: string
  score: number
  snippet: string | null
}

export interface AskQuestionPayload {
  groupId: number
  question: string
}

export interface AskQuestionResponse {
  answered: boolean
  answer: string | null
  reasonCode: string | null
  reasonMessage: string | null
  citations: CitationItem[]
}

export async function askQuestion(payload: AskQuestionPayload): Promise<AskQuestionResponse> {
  const { data } = await http.post<AskQuestionResponse>('/qa/ask', payload)

  return {
    answered: data.answered,
    answer: data.answer ?? null,
    reasonCode: data.reasonCode ?? null,
    reasonMessage: data.reasonMessage ?? null,
    citations: data.citations ?? [],
  }
}
