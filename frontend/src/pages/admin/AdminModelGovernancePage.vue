<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createPlatformConnection,
  deletePlatformConnection,
  fetchAdminModelUsage,
  fetchPlatformConnectionModels,
  fetchPlatformConnections,
  fetchPlatformGrants,
  fetchScenarioRoute,
  bindScenarioRoute,
  replacePlatformGrants,
  testPlatformConnection,
  updatePlatformConnection,
  updatePlatformConnectionStatus,
  type AdminUsageFilter,
} from '../../api/admin-model-governance'
import { extractApiError } from '../../api/http'
import type { ModelCatalogItem, ModelConnection, ModelConnectionCommand, ModelProviderType, ModelUsageReport } from '../../types/model-platform'

interface ConnectionForm {
  providerType: ModelProviderType
  name: string
  baseUrl: string
  apiKey: string
  optionsText: string
  maxConcurrency: string
}

const scenarios = ['ASSISTANT_CHAT', 'QA_ANSWER', 'QUERY_PLANNING', 'SESSION_SUMMARY', 'RUNTIME_MEMORY_EXTRACTION']
const connections = ref<ModelConnection[]>([])
const modelsByConnectionId = ref<Record<number, ModelCatalogItem[]>>({})
const isLoadingConnections = ref(false)
const isSavingConnection = ref(false)
const activeConnectionId = ref<number | null>(null)
const editingConnectionId = ref<number | null>(null)
const pageError = ref('')
const pageFeedback = ref('')
const form = reactive<ConnectionForm>(emptyConnectionForm())

const grantConnectionId = ref<number | null>(null)
const grantAllBusinessUsers = ref(true)
const grantUserIdsText = ref('')
const isSavingGrants = ref(false)
const isLoadingGrants = ref(false)
const routeScenario = ref(scenarios[0])
const routeConnectionId = ref<number | null>(null)
const routeModelId = ref<number | null>(null)
const routeSummary = ref('')
const isLoadingRoute = ref(false)
const isSavingRoute = ref(false)
const usageFilter = reactive({
  userId: '', providerType: '', modelName: '', scenario: '', logicalStatus: '', transportStatus: '', startedAt: '', endedAt: '',
})
const usageReport = ref<ModelUsageReport | null>(null)
const isLoadingUsage = ref(false)
const isEditing = computed(() => editingConnectionId.value !== null)
const selectedGrantConnection = computed(() => connections.value.find((item) => item.id === grantConnectionId.value) ?? null)
const routeCandidates = computed(() => connections.value.flatMap((connection) =>
  (modelsByConnectionId.value[connection.id] ?? [])
    .filter((model) => model.testStatus === 'PASSED' && model.enabled)
    .map((model) => ({ connection, model })),
))
const selectedRouteModels = computed(() => routeConnectionId.value === null
  ? []
  : routeCandidates.value.filter((candidate) => candidate.connection.id === routeConnectionId.value))

onMounted(() => { void loadConnections() })
watch(grantConnectionId, () => { void loadGrants() })

function emptyConnectionForm(): ConnectionForm {
  return { providerType: 'DASHSCOPE', name: '', baseUrl: '', apiKey: '', optionsText: '{}', maxConcurrency: '' }
}

function resetConnectionForm() {
  Object.assign(form, emptyConnectionForm())
  editingConnectionId.value = null
}

function beginEdit(connection: ModelConnection) {
  editingConnectionId.value = connection.id
  Object.assign(form, {
    providerType: connection.providerType,
    name: connection.name,
    baseUrl: connection.baseUrl ?? '',
    apiKey: '',
    optionsText: JSON.stringify(connection.options ?? {}, null, 2),
    maxConcurrency: connection.maxConcurrency?.toString() ?? '',
  })
  pageError.value = ''
  pageFeedback.value = ''
}

function buildConnectionCommand(): ModelConnectionCommand | null {
  let options: Record<string, unknown>
  try {
    const parsed: unknown = JSON.parse(form.optionsText || '{}')
    if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error()
    options = parsed as Record<string, unknown>
  } catch {
    pageError.value = '扩展选项必须是 JSON 对象。'
    return null
  }
  const maxConcurrency = form.maxConcurrency.trim() === '' ? null : Number(form.maxConcurrency)
  if (maxConcurrency !== null && (!Number.isInteger(maxConcurrency) || maxConcurrency <= 0)) {
    pageError.value = '连接并发上限必须是正整数。'
    return null
  }
  return { providerType: form.providerType, name: form.name.trim(), baseUrl: form.baseUrl.trim() || null, apiKey: form.apiKey || null, options, maxConcurrency }
}

async function loadConnections() {
  isLoadingConnections.value = true
  pageError.value = ''
  try {
    const loadedConnections = await fetchPlatformConnections()
    const catalogs = await Promise.all(loadedConnections.map(async (connection) => [
      connection.id,
      await fetchPlatformConnectionModels(connection.id),
    ] as const))
    connections.value = loadedConnections
    modelsByConnectionId.value = Object.fromEntries(catalogs)
  } catch (error) {
    pageError.value = extractApiError(error, '加载平台模型连接失败')
  } finally {
    isLoadingConnections.value = false
  }
}

async function saveConnection() {
  const command = buildConnectionCommand()
  if (command === null) return
  isSavingConnection.value = true
  pageError.value = ''
  pageFeedback.value = ''
  try {
    await (editingConnectionId.value === null
      ? createPlatformConnection(command)
      : updatePlatformConnection(editingConnectionId.value, command))
    form.apiKey = ''
    resetConnectionForm()
    pageFeedback.value = '平台连接已保存；修改关键配置后需要重新测试。'
    await loadConnections()
  } catch (error) {
    pageError.value = extractApiError(error, '保存平台模型连接失败')
  } finally {
    isSavingConnection.value = false
  }
}

async function runConnectionAction(connection: ModelConnection, action: () => Promise<void>, fallbackMessage: string) {
  activeConnectionId.value = connection.id
  pageError.value = ''
  pageFeedback.value = ''
  try {
    await action()
    await loadConnections()
  } catch (error) {
    pageError.value = extractApiError(error, fallbackMessage)
  } finally {
    activeConnectionId.value = null
  }
}

async function changeConnectionStatus(connection: ModelConnection) {
  const target = connection.status === 'DISABLED' ? 'UNVERIFIED' : 'DISABLED'
  const label = target === 'DISABLED' ? '停用' : '重新启用'
  if (!window.confirm(`确认${label}平台连接「${connection.name}」吗？`)) return
  await runConnectionAction(connection, async () => {
    await updatePlatformConnectionStatus(connection.id, target)
    pageFeedback.value = target === 'DISABLED' ? '连接已停用。' : '连接已重新启用，需要再次测试后才能投入使用。'
  }, `${label}平台连接失败`)
}

async function testConnection(connection: ModelConnection) {
  await runConnectionAction(connection, async () => {
    const result = await testPlatformConnection(connection.id)
    pageFeedback.value = result.errorCode ? `连接测试状态：${result.status}（${result.errorCode}）` : `连接测试状态：${result.status}`
  }, '测试平台连接失败')
}

async function removeConnection(connection: ModelConnection) {
  if (!window.confirm(`确认删除平台连接「${connection.name}」吗？此操作会使该连接无法继续使用。`)) return
  await runConnectionAction(connection, async () => {
    await deletePlatformConnection(connection.id)
    if (editingConnectionId.value === connection.id) resetConnectionForm()
    pageFeedback.value = '平台连接已删除。'
  }, '删除平台连接失败')
}

function readGrantUserIds(): number[] | null {
  const values = grantUserIdsText.value.split(/[\s,]+/).filter(Boolean).map(Number)
  if (values.some((item) => !Number.isInteger(item) || item <= 0)) {
    pageError.value = '授权用户 ID 必须是正整数，使用逗号或空格分隔。'
    return null
  }
  return [...new Set(values)]
}

async function saveGrants() {
  if (grantConnectionId.value === null) {
    pageError.value = '请选择要替换授权范围的平台连接。'
    return
  }
  const userIds = readGrantUserIds()
  if (userIds === null) return
  if (!grantAllBusinessUsers.value && userIds.length === 0) {
    pageError.value = '关闭全体业务用户授权后，至少需要指定一个用户 ID。'
    return
  }
  if (!window.confirm('确认替换该平台连接的授权范围吗？该操作会覆盖已有授权。')) return
  isSavingGrants.value = true
  pageError.value = ''
  pageFeedback.value = ''
  try {
    const grant = await replacePlatformGrants(grantConnectionId.value, { allBusinessUsers: grantAllBusinessUsers.value, userIds })
    pageFeedback.value = grant.allBusinessUsers ? '授权已替换为全部业务用户。' : `授权已替换为 ${grant.userIds.length} 个指定用户。`
  } catch (error) {
    pageError.value = extractApiError(error, '替换平台连接授权失败')
  } finally {
    isSavingGrants.value = false
  }
}

async function loadGrants() {
  if (grantConnectionId.value === null) {
    grantAllBusinessUsers.value = true
    grantUserIdsText.value = ''
    return
  }
  isLoadingGrants.value = true
  pageError.value = ''
  try {
    const grant = await fetchPlatformGrants(grantConnectionId.value)
    grantAllBusinessUsers.value = grant.allBusinessUsers
    grantUserIdsText.value = grant.userIds.join(', ')
  } catch (error) {
    pageError.value = extractApiError(error, '加载平台连接授权失败')
  } finally {
    isLoadingGrants.value = false
  }
}

async function loadRoute() {
  isLoadingRoute.value = true
  routeSummary.value = ''
  pageError.value = ''
  try {
    const route = await fetchScenarioRoute(routeScenario.value)
    routeSummary.value = `当前路由：连接 #${route.connectionId}，模型 #${route.modelId}，${route.enabled ? '已启用' : '未启用'}。`
    routeConnectionId.value = route.connectionId
    routeModelId.value = routeCandidates.value.some((candidate) => candidate.model.id === route.modelId)
      ? route.modelId
      : null
  } catch (error) {
    routeSummary.value = extractApiError(error, '该场景尚未配置路由')
  } finally {
    isLoadingRoute.value = false
  }
}

function selectRouteConnection() {
  routeModelId.value = null
}

async function saveRoute() {
  if (routeConnectionId.value === null || routeModelId.value === null) {
    pageError.value = '请选择已通过测试且已启用的模型。'
    return
  }
  isSavingRoute.value = true
  pageError.value = ''
  pageFeedback.value = ''
  try {
    const route = await bindScenarioRoute(routeScenario.value, routeConnectionId.value, routeModelId.value)
    routeSummary.value = `当前路由：连接 #${route.connectionId}，模型 #${route.modelId}，${route.enabled ? '已启用' : '未启用'}。`
    pageFeedback.value = '场景路由已保存。'
  } catch (error) {
    pageError.value = extractApiError(error, '绑定场景路由失败')
  } finally {
    isSavingRoute.value = false
  }
}

function buildUsageFilter(): AdminUsageFilter | null {
  const userId = usageFilter.userId.trim() === '' ? undefined : Number(usageFilter.userId)
  if (userId !== undefined && (!Number.isInteger(userId) || userId <= 0)) {
    pageError.value = '用户 ID 必须是正整数。'
    return null
  }
  return Object.fromEntries(Object.entries({
    userId, providerType: usageFilter.providerType, modelName: usageFilter.modelName, scenario: usageFilter.scenario,
    logicalStatus: usageFilter.logicalStatus, transportStatus: usageFilter.transportStatus,
    startedAt: usageFilter.startedAt, endedAt: usageFilter.endedAt,
  }).filter(([, value]) => value !== '' && value !== undefined)) as AdminUsageFilter
}

async function loadUsage() {
  const filter = buildUsageFilter()
  if (filter === null) return
  isLoadingUsage.value = true
  pageError.value = ''
  try {
    usageReport.value = await fetchAdminModelUsage(filter)
  } catch (error) {
    pageError.value = extractApiError(error, '加载全局模型用量失败')
  } finally {
    isLoadingUsage.value = false
  }
}

function formatDate(value: string | null) {
  if (!value) return '未记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}
</script>

<template>
  <section class="admin-page-section admin-model-governance-page">
    <article class="admin-panel admin-panel--hero">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">模型治理</p>
          <h2>平台模型治理</h2>
          <p class="admin-panel__description">平台连接、授权范围、场景路由与调用账本均由后端管理员接口执行。</p>
        </div>
        <button class="ghost-button" type="button" :disabled="isLoadingConnections" @click="void loadConnections()">刷新连接</button>
      </div>
      <p v-if="pageFeedback" class="feedback feedback--success">{{ pageFeedback }}</p>
      <p v-if="pageError" class="feedback feedback--error">{{ pageError }}</p>
    </article>

    <div class="admin-model-governance-grid">
      <form class="admin-panel admin-form" @submit.prevent="saveConnection">
        <div class="admin-panel__header"><div><p class="panel__eyebrow">平台连接</p><h2>{{ isEditing ? '编辑连接' : '新增连接' }}</h2></div></div>
        <label><span>服务商</span><select v-model="form.providerType"><option value="DASHSCOPE">DASHSCOPE</option><option value="OPENAI">OPENAI</option><option value="GEMINI">GEMINI</option><option value="ANTHROPIC">ANTHROPIC</option></select></label>
        <label><span>连接名称</span><input v-model="form.name" required maxlength="100" placeholder="例如：生产主连接" /></label>
        <label><span>Base URL</span><input v-model="form.baseUrl" type="url" placeholder="使用服务商默认地址可留空" /></label>
        <label><span>API Key</span><input v-model="form.apiKey" type="password" :required="!isEditing" autocomplete="off" placeholder="编辑时留空表示保留原密钥" /></label>
        <label><span>连接并发上限</span><input v-model="form.maxConcurrency" inputmode="numeric" placeholder="使用平台默认值" /></label>
        <label><span>扩展选项（JSON）</span><textarea v-model="form.optionsText" rows="4" spellcheck="false" /></label>
        <div class="admin-form__footer"><small class="admin-form__hint">密钥仅提交给服务端，页面不会回显完整值。</small><div class="admin-actions"><button v-if="isEditing" class="ghost-button" type="button" @click="resetConnectionForm">取消</button><button class="primary-button" type="submit" :disabled="isSavingConnection">{{ isSavingConnection ? '正在保存' : isEditing ? '保存修改' : '创建连接' }}</button></div></div>
      </form>

      <article class="admin-panel admin-panel--table">
        <div class="admin-panel__header"><div><p class="panel__eyebrow">已配置连接</p><h2>连接状态</h2></div></div>
        <p v-if="isLoadingConnections" class="placeholder-text">正在加载平台连接...</p>
        <p v-else-if="connections.length === 0" class="placeholder-text">尚未配置平台连接。</p>
        <div v-else class="admin-table-wrap"><table><thead><tr><th>连接</th><th>状态</th><th>测试</th><th>并发</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="connection in connections" :key="connection.id"><td><div class="admin-user-cell"><strong>{{ connection.name }}</strong><span>{{ connection.providerType }} · {{ connection.maskedApiKey ?? '未返回掩码' }}</span><small>#{{ connection.id }} · {{ connection.baseUrl ?? '服务商默认地址' }}</small></div></td><td><span class="admin-status" :data-status="connection.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE'">{{ connection.status }}</span></td><td>{{ connection.connectionTestStatus }}</td><td>{{ connection.maxConcurrency ?? '平台默认' }}</td><td>{{ formatDate(connection.updatedAt) }}</td><td><div class="admin-actions"><button class="ghost-button" type="button" :disabled="activeConnectionId === connection.id" @click="beginEdit(connection)">编辑</button><button class="ghost-button" type="button" :disabled="activeConnectionId === connection.id" @click="void testConnection(connection)">{{ activeConnectionId === connection.id ? '处理中' : '测试' }}</button><button class="ghost-button" type="button" :disabled="activeConnectionId === connection.id" @click="void changeConnectionStatus(connection)">{{ connection.status === 'DISABLED' ? '重新启用' : '停用' }}</button><button class="ghost-button ghost-button--danger" type="button" :disabled="activeConnectionId === connection.id" @click="void removeConnection(connection)">删除</button></div></td></tr></tbody></table></div>
        <p class="admin-capability-note">已加载各连接的已保存模型目录；路由绑定只接受当前通过测试且已启用的模型。</p>
      </article>
    </div>

    <div class="admin-model-governance-grid">
      <form class="admin-panel admin-form" @submit.prevent="saveGrants">
        <div><p class="panel__eyebrow">授权替换</p><h2>业务用户范围</h2><p class="admin-panel__description">提交会覆盖当前连接的已有授权。</p></div>
        <label><span>平台连接</span><select v-model.number="grantConnectionId"><option :value="null" disabled>请选择平台连接</option><option v-for="connection in connections" :key="connection.id" :value="connection.id">#{{ connection.id }} · {{ connection.name }}</option></select></label>
        <label class="admin-checkbox-field"><input v-model="grantAllBusinessUsers" type="checkbox" /><span>授权所有业务用户</span></label>
        <label v-if="!grantAllBusinessUsers"><span>指定用户 ID</span><textarea v-model="grantUserIdsText" rows="3" placeholder="例如：101, 102, 103" /></label>
        <small>当前所选：{{ selectedGrantConnection?.name ?? '未选择连接' }}。{{ isLoadingGrants ? '正在读取当前授权范围...' : '保存会覆盖当前授权范围。' }}</small>
        <button class="primary-button" type="submit" :disabled="isSavingGrants || isLoadingGrants">{{ isSavingGrants ? '正在替换' : '替换授权范围' }}</button>
      </form>

      <article class="admin-panel admin-form">
        <div><p class="panel__eyebrow">场景路由</p><h2>当前绑定状态</h2><p class="admin-panel__description">仅可绑定当前已通过模型测试且已启用的模型。</p></div>
        <label><span>场景</span><select v-model="routeScenario"><option v-for="scenario in scenarios" :key="scenario" :value="scenario">{{ scenario }}</option></select></label>
        <label><span>平台连接</span><select v-model.number="routeConnectionId" @change="selectRouteConnection"><option :value="null" disabled>请选择包含合格模型的连接</option><option v-for="connection in connections.filter((item) => routeCandidates.some((candidate) => candidate.connection.id === item.id))" :key="connection.id" :value="connection.id">#{{ connection.id }} · {{ connection.name }}</option></select></label>
        <label><span>模型</span><select v-model.number="routeModelId" :disabled="routeConnectionId === null"><option :value="null" disabled>请选择已通过测试且已启用的模型</option><option v-for="candidate in selectedRouteModels" :key="candidate.model.id" :value="candidate.model.id">{{ candidate.model.modelName }} · #{{ candidate.model.id }}</option></select></label>
        <button class="ghost-button" type="button" :disabled="isLoadingRoute" @click="void loadRoute()">{{ isLoadingRoute ? '正在查询' : '查询路由' }}</button>
        <button class="primary-button" type="button" :disabled="isSavingRoute || routeModelId === null" @click="void saveRoute()">{{ isSavingRoute ? '正在保存' : '保存路由' }}</button>
        <p v-if="routeSummary" class="placeholder-text">{{ routeSummary }}</p>
      </article>
    </div>

    <article class="admin-panel admin-panel--table admin-model-usage">
      <div class="admin-panel__header"><div><p class="panel__eyebrow">调用账本</p><h2>全局用量筛选</h2><p class="admin-panel__description">按用户、服务商、模型、场景、状态和时间范围查询后端聚合数据。</p></div><button class="primary-button" type="button" :disabled="isLoadingUsage" @click="void loadUsage()">{{ isLoadingUsage ? '正在汇总' : '查询用量' }}</button></div>
      <div class="admin-usage-filter-grid"><label><span>用户 ID</span><input v-model="usageFilter.userId" inputmode="numeric" /></label><label><span>服务商</span><input v-model="usageFilter.providerType" placeholder="例如 OPENAI" /></label><label><span>模型</span><input v-model="usageFilter.modelName" /></label><label><span>场景</span><input v-model="usageFilter.scenario" placeholder="例如 QA_ANSWER" /></label><label><span>逻辑状态</span><input v-model="usageFilter.logicalStatus" placeholder="例如 SUCCEEDED" /></label><label><span>传输状态</span><input v-model="usageFilter.transportStatus" placeholder="例如 TERMINATED" /></label><label><span>开始时间</span><input v-model="usageFilter.startedAt" type="datetime-local" /></label><label><span>结束时间</span><input v-model="usageFilter.endedAt" type="datetime-local" /></label></div>
      <template v-if="usageReport"><div class="admin-stats"><article><span>调用次数</span><strong>{{ usageReport.invocationCount }}</strong></article><article><span>总 Token</span><strong>{{ usageReport.totalTokens }}</strong></article><article><span>累计耗时</span><strong>{{ usageReport.durationMs }}ms</strong></article></div><p v-if="usageReport.groups.length === 0" class="placeholder-text">当前筛选条件下没有调用记录。</p><div v-else class="admin-table-wrap"><table><thead><tr><th>服务商 / 模型</th><th>场景</th><th>状态</th><th>调用</th><th>Token</th><th>耗时</th></tr></thead><tbody><tr v-for="group in usageReport.groups" :key="[group.providerType, group.modelName, group.scenario, group.logicalStatus, group.transportStatus].join('-')"><td>{{ group.providerType }} / {{ group.modelName }}</td><td>{{ group.scenario }}</td><td>{{ group.logicalStatus }} / {{ group.transportStatus }}</td><td>{{ group.invocationCount }}</td><td>{{ group.totalTokens }}</td><td>{{ group.durationMs }}ms</td></tr></tbody></table></div></template>
    </article>
  </section>
</template>

<style scoped>
.admin-model-governance-page { display: grid; gap: 1.15rem; }
.admin-model-governance-grid { display: grid; grid-template-columns: minmax(18rem, .9fr) minmax(0, 1.5fr); gap: 1.15rem; }
.admin-capability-note { margin: 1rem 0 0; color: var(--admin-text-muted); line-height: 1.6; }
.admin-checkbox-field { display: flex !important; align-items: center; gap: .6rem; }
.admin-checkbox-field input { width: 1rem; height: 1rem; }
.admin-checkbox-field span { font-size: .95rem !important; }
.admin-usage-filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .85rem; margin-bottom: 1rem; }
.admin-usage-filter-grid label { display: grid; gap: .45rem; }
.admin-usage-filter-grid span { color: #4d657b; font-size: .84rem; font-weight: 700; }
.admin-usage-filter-grid input { min-height: 2.7rem; width: 100%; border: 1px solid rgba(110, 136, 161, .16); border-radius: 1rem; background: rgba(255,255,255,.94); padding: 0 .85rem; color: var(--wb-color-text); }
@media (max-width: 1180px) { .admin-model-governance-grid { grid-template-columns: 1fr; } .admin-usage-filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) { .admin-usage-filter-grid { grid-template-columns: 1fr; } }
</style>
