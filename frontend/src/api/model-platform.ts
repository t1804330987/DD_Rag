import http, { type ApiResponse } from './http'
import type {
  ModelCatalogItem,
  ModelCatalogRefreshOutcome,
  ModelConnection,
  ModelConnectionCommand,
  ModelTestOutcome,
  ModelUsageReport,
  ProviderConnectionSchema,
  InstructionProfile,
  UsageFilter,
} from '../types/model-platform'

const connectionPath = '/ai-settings/model-connections'

function unwrap<T>(response: ApiResponse<T>, fallbackMessage: string): T {
  if (!response.success || response.data == null) {
    throw new Error(response.message ?? fallbackMessage)
  }
  return response.data
}

export async function fetchModelConnections(): Promise<ModelConnection[]> {
  const { data } = await http.get<ApiResponse<ModelConnection[]>>(connectionPath)
  return unwrap(data, '加载模型连接失败')
}

export async function fetchProviderSchemas(): Promise<ProviderConnectionSchema[]> {
  const { data } = await http.get<ApiResponse<ProviderConnectionSchema[]>>('/ai-settings/providers')
  return unwrap(data, '加载服务商配置失败')
}

export async function fetchConnectionModels(connectionId: number): Promise<ModelCatalogItem[]> {
  const { data } = await http.get<ApiResponse<ModelCatalogItem[]>>(`${connectionPath}/${connectionId}/models`)
  return unwrap(data, '加载已保存模型失败')
}

export async function createModelConnection(command: ModelConnectionCommand): Promise<ModelConnection> {
  const { data } = await http.post<ApiResponse<ModelConnection>>(connectionPath, command)
  return unwrap(data, '创建模型连接失败')
}

export async function updateModelConnection(
  connectionId: number,
  command: ModelConnectionCommand,
): Promise<ModelConnection> {
  const { data } = await http.put<ApiResponse<ModelConnection>>(`${connectionPath}/${connectionId}`, command)
  return unwrap(data, '更新模型连接失败')
}

export async function deleteModelConnection(connectionId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`${connectionPath}/${connectionId}`)
  if (!data.success) throw new Error(data.message ?? '删除模型连接失败')
}

export async function mergeManualModels(connectionId: number, manualModels: string[]): Promise<ModelCatalogItem[]> {
  const { data } = await http.post<ApiResponse<ModelCatalogItem[]>>(`${connectionPath}/${connectionId}/catalog`, {
    manualModels,
  })
  return unwrap(data, '合并模型目录失败')
}

export async function testModelConnection(connectionId: number): Promise<ModelTestOutcome> {
  const { data } = await http.post<ApiResponse<ModelTestOutcome>>(`${connectionPath}/${connectionId}/test`)
  return unwrap(data, '测试连接失败')
}

export async function refreshConnectionModels(connectionId: number): Promise<ModelCatalogRefreshOutcome> {
  const { data } = await http.post<ApiResponse<ModelCatalogRefreshOutcome>>(
    `${connectionPath}/${connectionId}/models/refresh`,
  )
  return unwrap(data, '刷新模型列表失败')
}

export async function testConnectionModel(connectionId: number, modelId: number): Promise<ModelTestOutcome> {
  const { data } = await http.post<ApiResponse<ModelTestOutcome>>(
    `${connectionPath}/${connectionId}/models/${modelId}/test`,
  )
  return unwrap(data, '测试模型失败')
}

export async function testConnectionModels(connectionId: number, modelIds: number[]): Promise<ModelTestOutcome[]> {
  const { data } = await http.post<ApiResponse<ModelTestOutcome[]>>(`${connectionPath}/${connectionId}/models/test`, {
    modelIds,
  })
  return unwrap(data, '批量测试模型失败')
}

export async function setConnectionModelEnabled(
  connectionId: number,
  modelId: number,
  enabled: boolean,
): Promise<ModelCatalogItem> {
  const { data } = await http.patch<ApiResponse<ModelCatalogItem>>(
    `${connectionPath}/${connectionId}/models/${modelId}/enabled/${enabled}`,
  )
  return unwrap(data, '更新模型启用状态失败')
}

export async function hideConnectionModel(connectionId: number, modelId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`${connectionPath}/${connectionId}/models/${modelId}`)
  if (!data.success) throw new Error(data.message ?? '隐藏模型失败')
}

export async function fetchMyModelUsage(filter: UsageFilter = {}): Promise<ModelUsageReport> {
  const { data } = await http.get<ApiResponse<ModelUsageReport>>('/ai-settings/model-usage', { params: filter })
  return unwrap(data, '加载个人模型用量失败')
}

const instructionPath = '/ai-settings/instruction-profiles'

export async function fetchInstructionProfiles(): Promise<InstructionProfile[]> {
  const { data } = await http.get<ApiResponse<InstructionProfile[]>>(instructionPath)
  return unwrap(data, '加载个人指令失败')
}

export async function createInstructionProfile(command: { name: string; content: string; makeDefault: boolean }): Promise<InstructionProfile> {
  const { data } = await http.post<ApiResponse<InstructionProfile>>(instructionPath, command)
  return unwrap(data, '创建个人指令失败')
}

export async function updateInstructionProfile(profileId: number, command: { name: string; content: string; makeDefault: boolean }): Promise<InstructionProfile> {
  const { data } = await http.put<ApiResponse<InstructionProfile>>(`${instructionPath}/${profileId}`, command)
  return unwrap(data, '更新个人指令失败')
}

export async function copyInstructionProfile(profileId: number, name: string, makeDefault = false): Promise<InstructionProfile> {
  const { data } = await http.post<ApiResponse<InstructionProfile>>(`${instructionPath}/${profileId}/copy`, { name, makeDefault })
  return unwrap(data, '复制个人指令失败')
}

export async function setInstructionProfileDefault(profileId: number): Promise<void> {
  const { data } = await http.patch<ApiResponse<null>>(`${instructionPath}/${profileId}/default`)
  if (!data.success) throw new Error(data.message ?? '设置默认指令失败')
}

export async function setInstructionProfileEnabled(profileId: number, enabled: boolean): Promise<void> {
  const { data } = await http.patch<ApiResponse<null>>(`${instructionPath}/${profileId}/enabled/${enabled}`)
  if (!data.success) throw new Error(data.message ?? '更新指令状态失败')
}

export async function deleteInstructionProfile(profileId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`${instructionPath}/${profileId}`)
  if (!data.success) throw new Error(data.message ?? '删除个人指令失败')
}
