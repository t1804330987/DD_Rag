import http, { type ApiResponse } from './http'
import type {
  ModelCatalogItem,
  ModelCatalogRefreshOutcome,
  ModelConnection,
  ModelConnectionCommand,
  ModelTestOutcome,
  ModelUsageReport,
} from '../types/model-platform'

export interface PlatformGrant {
  connectionId: number
  allBusinessUsers: boolean
  userIds: number[]
}

export interface ScenarioRoute {
  scenario: string
  connectionId: number
  modelId: number
  enabled: boolean
  updatedAt: string
}

export interface AdminUsageFilter {
  userId?: number
  providerType?: string
  modelName?: string
  scenario?: string
  logicalStatus?: string
  transportStatus?: string
  startedAt?: string
  endedAt?: string
}

const connectionPath = '/admin/model-connections'

function unwrap<T>(payload: ApiResponse<T>, fallbackMessage: string): T {
  if (!payload.success || payload.data == null) throw new Error(payload.message ?? fallbackMessage)
  return payload.data
}

export async function fetchPlatformConnections(): Promise<ModelConnection[]> {
  const { data } = await http.get<ApiResponse<ModelConnection[]>>(connectionPath)
  return unwrap(data, '加载平台模型连接失败')
}

export async function createPlatformConnection(command: ModelConnectionCommand): Promise<ModelConnection> {
  const { data } = await http.post<ApiResponse<ModelConnection>>(connectionPath, command)
  return unwrap(data, '创建平台模型连接失败')
}

export async function updatePlatformConnection(connectionId: number, command: ModelConnectionCommand): Promise<ModelConnection> {
  const { data } = await http.put<ApiResponse<ModelConnection>>(`${connectionPath}/${connectionId}`, command)
  return unwrap(data, '更新平台模型连接失败')
}

export async function deletePlatformConnection(connectionId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`${connectionPath}/${connectionId}`)
  if (!data.success) throw new Error(data.message ?? '删除平台模型连接失败')
}

export async function updatePlatformConnectionStatus(connectionId: number, status: 'DISABLED' | 'UNVERIFIED'): Promise<ModelConnection> {
  const { data } = await http.patch<ApiResponse<ModelConnection>>(`${connectionPath}/${connectionId}/status/${status}`)
  return unwrap(data, '更新平台模型连接状态失败')
}

export async function testPlatformConnection(connectionId: number): Promise<ModelTestOutcome> {
  const { data } = await http.post<ApiResponse<ModelTestOutcome>>(`${connectionPath}/${connectionId}/test`)
  return unwrap(data, '测试平台模型连接失败')
}

export async function fetchPlatformConnectionModels(connectionId: number): Promise<ModelCatalogItem[]> {
  const { data } = await http.get<ApiResponse<ModelCatalogItem[]>>(`${connectionPath}/${connectionId}/models`)
  return unwrap(data, '加载平台模型目录失败')
}

export async function refreshPlatformConnectionModels(connectionId: number): Promise<ModelCatalogRefreshOutcome> {
  const { data } = await http.post<ApiResponse<ModelCatalogRefreshOutcome>>(
    `${connectionPath}/${connectionId}/models/refresh`,
  )
  return unwrap(data, '刷新平台模型列表失败')
}

export async function mergePlatformManualModels(connectionId: number, manualModels: string[]): Promise<ModelCatalogItem[]> {
  const { data } = await http.post<ApiResponse<ModelCatalogItem[]>>(`${connectionPath}/${connectionId}/catalog`, {
    manualModels,
  })
  return unwrap(data, '合并平台模型目录失败')
}

export async function testPlatformModel(connectionId: number, modelId: number): Promise<ModelTestOutcome> {
  const { data } = await http.post<ApiResponse<ModelTestOutcome>>(
    `${connectionPath}/${connectionId}/models/${modelId}/test`,
  )
  return unwrap(data, '测试平台模型失败')
}

export async function setPlatformModelEnabled(
  connectionId: number,
  modelId: number,
  enabled: boolean,
): Promise<ModelCatalogItem> {
  const { data } = await http.patch<ApiResponse<ModelCatalogItem>>(
    `${connectionPath}/${connectionId}/models/${modelId}/enabled/${enabled}`,
  )
  return unwrap(data, '更新平台模型启用状态失败')
}

export async function replacePlatformGrants(connectionId: number, command: Omit<PlatformGrant, 'connectionId'>): Promise<PlatformGrant> {
  const { data } = await http.put<ApiResponse<PlatformGrant>>(`/admin/model-governance/connections/${connectionId}/grants`, command)
  return unwrap(data, '替换平台连接授权失败')
}

export async function fetchPlatformGrants(connectionId: number): Promise<PlatformGrant> {
  const { data } = await http.get<ApiResponse<PlatformGrant>>(`/admin/model-governance/connections/${connectionId}/grants`)
  return unwrap(data, '加载平台连接授权失败')
}

export async function fetchScenarioRoute(scenario: string): Promise<ScenarioRoute> {
  const { data } = await http.get<ApiResponse<ScenarioRoute>>(`/admin/model-governance/routes/${scenario}`)
  return unwrap(data, '加载场景路由失败')
}

export async function bindScenarioRoute(scenario: string, connectionId: number, modelId: number): Promise<ScenarioRoute> {
  const { data } = await http.put<ApiResponse<ScenarioRoute>>(`/admin/model-governance/routes/${scenario}`, {
    connectionId,
    modelId,
  })
  return unwrap(data, '绑定场景路由失败')
}

export async function fetchAdminModelUsage(filter: AdminUsageFilter): Promise<ModelUsageReport> {
  const { data } = await http.get<ApiResponse<ModelUsageReport>>('/admin/model-usage', { params: filter })
  return unwrap(data, '加载全局模型用量失败')
}
