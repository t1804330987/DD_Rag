export type ModelProviderType = 'DASHSCOPE' | 'OPENAI' | 'GEMINI' | 'ANTHROPIC'

export interface ModelConnectionCommand {
  providerType: ModelProviderType
  name: string
  baseUrl: string | null
  apiKey?: string | null
  options: Record<string, unknown>
  maxConcurrency: number | null
}

export interface ModelConnection {
  id: number
  providerType: ModelProviderType
  ownerType: 'USER' | 'PLATFORM'
  name: string
  baseUrl: string | null
  maskedApiKey: string | null
  options: Record<string, unknown>
  maxConcurrency: number | null
  status: string
  configVersion: number
  connectionTestStatus: string
  connectionTestAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ModelCatalogItem {
  id: number
  connectionId: number
  modelName: string
  sourceType: string
  testStatus: string
  testedConfigVersion: number | null
  lastTestAt: string | null
  enabled: boolean
  syncedAt: string
}

export interface ModelTestOutcome {
  connectionId: number
  modelId: number | null
  configVersion: number
  status: string
  applied: boolean
  errorCode: string | null
}

export interface ModelCatalogRefreshOutcome {
  connectionId: number
  configVersion: number
  success: boolean
  errorCode: string | null
  discoveredCount: number
}

export interface UsageFilter {
  providerType?: string
  modelName?: string
  scenario?: string
  logicalStatus?: string
  transportStatus?: string
  startedAt?: string
  endedAt?: string
}

export interface ModelUsageGroup {
  providerType: string
  modelName: string
  scenario: string
  logicalStatus: string
  transportStatus: string
  invocationCount: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  durationMs: number
}

export interface ModelUsageReport {
  userId: number
  invocationCount: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  durationMs: number
  groups: ModelUsageGroup[]
}

export interface ProviderFieldSchema {
  name: string
  type: 'text' | 'url' | 'password'
  required: boolean
  sensitive: boolean
  defaultValue: string | null
}

export interface ProviderConnectionSchema {
  providerType: ModelProviderType
  defaultBaseUrl: string | null
  fields: ProviderFieldSchema[]
}

export interface InstructionProfile {
  profileId: number
  name: string
  enabled: boolean
  isDefault: boolean
  versionId: number
  version: number
  content: string
}
