<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createModelConnection,
  deleteModelConnection,
  fetchConnectionModels,
  fetchModelConnections,
  fetchProviderSchemas,
  mergeManualModels,
  refreshConnectionModels,
  setConnectionModelEnabled,
  testConnectionModel,
  testConnectionModels,
  testModelConnection,
  updateModelConnection,
} from '../../api/model-platform'
import { extractApiError } from '../../api/http'
import type { ModelCatalogItem, ModelConnection, ModelConnectionCommand, ModelProviderType, ProviderConnectionSchema } from '../../types/model-platform'

interface ConnectionForm {
  providerType: ModelProviderType
  name: string
  baseUrl: string
  apiKey: string
  optionsText: string
  maxConcurrency: string
}

const connections = ref<ModelConnection[]>([])
const providerSchemas = ref<ProviderConnectionSchema[]>([])
const catalogByConnection = reactive<Record<number, ModelCatalogItem[]>>({})
const isLoading = ref(false)
const isSubmitting = ref(false)
const activeTestKey = ref('')
const feedback = ref('')
const error = ref('')
const editingConnectionId = ref<number | null>(null)
const manualModels = ref<Record<number, string>>({})
const expandedModels = ref<Record<number, boolean>>({})
const form = reactive<ConnectionForm>(emptyForm())

const isEditing = computed(() => editingConnectionId.value !== null)
const formTitle = computed(() => (isEditing.value ? '更新连接' : '新增个人连接'))

onMounted(() => { void loadInitialData() })

const currentSchema = computed(() => providerSchemas.value.find((schema) => schema.providerType === form.providerType))
const optionFields = computed(() => (currentSchema.value?.fields ?? []).filter((field) => field.name !== 'baseUrl' && field.name !== 'apiKey'))

async function loadInitialData() {
  await Promise.all([loadConnections(), loadProviderSchemas()])
}

async function loadProviderSchemas() {
  try {
    providerSchemas.value = await fetchProviderSchemas()
  } catch (cause) {
    error.value = extractApiError(cause, '加载服务商配置失败')
  }
}

function emptyForm(): ConnectionForm {
  return { providerType: 'DASHSCOPE', name: '', baseUrl: '', apiKey: '', optionsText: '{}', maxConcurrency: '' }
}

async function loadConnections() {
  isLoading.value = true
  error.value = ''
  try {
    connections.value = await fetchModelConnections()
    await Promise.all(connections.value.map(async (connection) => {
      const models = await fetchConnectionModels(connection.id)
      catalogByConnection[connection.id] = models
      if (expandedModels.value[connection.id] === undefined) {
        expandedModels.value[connection.id] = models.length <= 20 || models.some((model) => model.enabled)
      }
    }))
  } catch (cause) {
    error.value = extractApiError(cause, '加载模型连接失败')
  } finally {
    isLoading.value = false
  }
}

function clearHealthState(connectionId?: number) {
  feedback.value = ''
  activeTestKey.value = ''
  if (connectionId !== undefined) delete catalogByConnection[connectionId]
}

function resetForm() {
  Object.assign(form, emptyForm())
  editingConnectionId.value = null
  error.value = ''
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
  feedback.value = ''
  error.value = ''
}

function readOption(name: string) {
  const value = safeOptions()[name]
  return value === undefined || value === null ? '' : String(value)
}

function writeOption(name: string, value: string) {
  const options = safeOptions()
  if (value.trim() === '') delete options[name]
  else options[name] = value
  form.optionsText = JSON.stringify(options, null, 2)
}

function safeOptions(): Record<string, unknown> {
  try {
    const parsed: unknown = JSON.parse(form.optionsText || '{}')
    return parsed !== null && !Array.isArray(parsed) && typeof parsed === 'object' ? { ...(parsed as Record<string, unknown>) } : {}
  } catch { return {} }
}

function buildCommand(): ModelConnectionCommand | null {
  let options: Record<string, unknown>
  try {
    const parsed: unknown = JSON.parse(form.optionsText || '{}')
    if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error()
    options = parsed as Record<string, unknown>
  } catch {
    error.value = '扩展选项必须是 JSON 对象。'
    return null
  }
  const maxConcurrency = form.maxConcurrency.trim() === '' ? null : Number(form.maxConcurrency)
  if (maxConcurrency !== null && (!Number.isInteger(maxConcurrency) || maxConcurrency <= 0)) {
    error.value = '连接并发上限必须是正整数。'
    return null
  }
  return {
    providerType: form.providerType,
    name: form.name.trim(),
    baseUrl: form.baseUrl.trim() || null,
    apiKey: form.apiKey || null,
    options,
    maxConcurrency,
  }
}

async function saveConnection() {
  const command = buildCommand()
  if (command === null) return
  isSubmitting.value = true
  error.value = ''
  feedback.value = ''
  try {
    const connection = editingConnectionId.value === null
      ? await createModelConnection(command)
      : await updateModelConnection(editingConnectionId.value, command)
    clearHealthState(connection.id)
    form.apiKey = ''
    resetForm()
    feedback.value = '连接配置已保存。配置变更后的旧测试状态已清除。'
    await loadConnections()
  } catch (cause) {
    error.value = extractApiError(cause, '保存模型连接失败')
  } finally {
    isSubmitting.value = false
  }
}

async function removeConnection(connection: ModelConnection) {
  if (!window.confirm(`确认删除连接「${connection.name}」吗？`)) return
  error.value = ''
  try {
    await deleteModelConnection(connection.id)
    clearHealthState(connection.id)
    feedback.value = '连接已删除。'
    if (editingConnectionId.value === connection.id) resetForm()
    await loadConnections()
  } catch (cause) {
    error.value = extractApiError(cause, '删除模型连接失败')
  }
}

async function runConnectionTest(connection: ModelConnection) {
  await runTest(`connection-${connection.id}`, async () => {
    const result = await testModelConnection(connection.id)
    feedback.value = testMessage(result.status, result.errorCode)
    expandedModels.value[connection.id] = true
    await loadConnections()
  })
}

async function refreshModels(connection: ModelConnection) {
  await runTest(`refresh-${connection.id}`, async () => {
    const result = await refreshConnectionModels(connection.id)
    expandedModels.value[connection.id] = true
    catalogByConnection[connection.id] = await fetchConnectionModels(connection.id)
    if (result.success) {
      feedback.value = `已获取当前 API Key 可用的 ${result.discoveredCount} 个模型。`
    } else {
      error.value = result.errorCode ? `刷新模型失败：${result.errorCode}` : '刷新模型失败。'
    }
  })
}

async function addManualModels(connection: ModelConnection) {
  const values = (manualModels.value[connection.id] ?? '').split(/[\n,]/).map((value) => value.trim()).filter(Boolean)
  if (values.length === 0) {
    error.value = '请至少输入一个模型名称。'
    return
  }
  await runTest(`catalog-${connection.id}`, async () => {
    catalogByConnection[connection.id] = await mergeManualModels(connection.id, values)
    expandedModels.value[connection.id] = true
    manualModels.value[connection.id] = ''
    feedback.value = '模型目录已合并。新模型仍需逐个测试后才能启用。'
  })
}

async function runModelTest(connection: ModelConnection, model: ModelCatalogItem) {
  await runTest(`model-${model.id}`, async () => {
    const result = await testConnectionModel(connection.id, model.id)
    expandedModels.value[connection.id] = true
    if (result.status === 'PASSED') {
      feedback.value = `「${model.modelName}」测试通过，可以启用。`
    } else {
      feedback.value = testMessage(result.status, result.errorCode)
    }
    if (catalogByConnection[connection.id]) {
      catalogByConnection[connection.id] = catalogByConnection[connection.id].map((item) =>
        item.id === model.id ? { ...item, testStatus: result.status, testedConfigVersion: result.configVersion } : item,
      )
    }
    await loadConnections()
  })
}

async function runBatchTest(connection: ModelConnection) {
  const models = catalogByConnection[connection.id] ?? []
  if (models.length === 0) {
    error.value = '请先通过“手工模型”合并当前会话内要测试的模型。'
    return
  }
  await runTest(`batch-${connection.id}`, async () => {
    const results = await testConnectionModels(connection.id, models.map((model) => model.id))
    const byId = new Map(results.filter((result) => result.modelId !== null).map((result) => [result.modelId, result]))
    catalogByConnection[connection.id] = models.map((model) => {
      const result = byId.get(model.id)
      return result ? { ...model, testStatus: result.status, testedConfigVersion: result.configVersion } : model
    })
    feedback.value = '批量测试已按服务端顺序完成。'
    await loadConnections()
  })
}

async function toggleModelEnabled(connection: ModelConnection, model: ModelCatalogItem) {
  await runTest(`enabled-${model.id}`, async () => {
    const updated = await setConnectionModelEnabled(connection.id, model.id, !model.enabled)
    expandedModels.value[connection.id] = true
    catalogByConnection[connection.id] = (catalogByConnection[connection.id] ?? []).map((item) =>
      item.id === model.id ? updated : item,
    )
    feedback.value = updated.enabled ? '模型已启用，可在对话中选择。' : '模型已停用。'
    await loadConnections()
  })
}

function toggleModelsExpanded(connectionId: number) {
  expandedModels.value[connectionId] = !expandedModels.value[connectionId]
}

async function runTest(key: string, operation: () => Promise<void>) {
  activeTestKey.value = key
  error.value = ''
  feedback.value = ''
  try {
    await operation()
  } catch (cause) {
    error.value = extractApiError(cause, '模型测试失败')
  } finally {
    activeTestKey.value = ''
  }
}

function testMessage(status: string, errorCode: string | null) {
  const label = formatTestStatus(status)
  return errorCode ? `${label}（${errorCode}）` : label
}

function formatSourceType(sourceType: string) {
  if (sourceType === 'DISCOVERED') return '已发现'
  if (sourceType === 'MANUAL') return '手工添加'
  return sourceType
}

function formatTestStatus(status: string) {
  if (status === 'PASSED') return '测试通过'
  if (status === 'FAILED') return '测试失败'
  if (status === 'PENDING' || status === 'NOT_TESTED' || status === 'UNKNOWN' || status === '') {
    return '未测试'
  }
  return status
}

function testStatusTone(status: string) {
  if (status === 'PASSED') return 'pass'
  if (status === 'FAILED') return 'fail'
  return 'pending'
}

function canEnableModel(model: ModelCatalogItem) {
  return model.enabled || model.testStatus === 'PASSED'
}

function modelRowClass(model: ModelCatalogItem) {
  return {
    'is-enabled': model.enabled,
    'is-test-passed': model.testStatus === 'PASSED' && !model.enabled,
    'is-test-failed': model.testStatus === 'FAILED',
    'is-test-pending': model.testStatus !== 'PASSED' && model.testStatus !== 'FAILED',
  }
}
</script>

<template>
  <section class="ai-settings-grid ai-settings-grid--connections">
    <form class="ai-settings-panel ai-settings-connection-form" @submit.prevent="saveConnection">
      <div class="ai-settings-panel__head">
        <div><span>Personal BYOK</span><h2>{{ formTitle }}</h2></div>
        <button v-if="isEditing" class="ai-settings-icon-button" type="button" title="取消编辑" @click="resetForm">×</button>
      </div>
      <p class="ai-settings-panel__hint">密钥只保存在当前表单内。留空表示编辑时保留服务端已有密钥。</p>
      <label>服务商<select v-model="form.providerType"><option v-for="schema in providerSchemas" :key="schema.providerType" :value="schema.providerType">{{ schema.providerType }}</option></select></label>
      <label>连接名称<input v-model="form.name" required maxlength="100" placeholder="例如：我的 OpenAI" /></label>
      <label v-for="field in currentSchema?.fields.filter((item) => item.name === 'baseUrl') ?? []" :key="field.name">Base URL<input v-model="form.baseUrl" :type="field.type === 'url' ? 'url' : 'text'" :placeholder="field.defaultValue ?? '使用服务商默认地址可留空'" /></label>
      <label v-for="field in currentSchema?.fields.filter((item) => item.name === 'apiKey') ?? []" :key="field.name">API Key<input v-model="form.apiKey" type="password" :required="field.required && !isEditing" autocomplete="off" placeholder="仅提交，不写入浏览器" /></label>
      <label v-for="field in optionFields" :key="field.name">{{ field.name }}<input :type="field.sensitive ? 'password' : field.type === 'url' ? 'url' : 'text'" :required="field.required" :value="readOption(field.name)" @input="writeOption(field.name, ($event.target as HTMLInputElement).value)" /></label>
      <label>连接并发上限<input v-model="form.maxConcurrency" inputmode="numeric" placeholder="使用平台默认值" /></label>
      <label>扩展选项（JSON）<textarea v-model="form.optionsText" rows="4" spellcheck="false" /></label>
      <p class="ai-settings-capability-note">字段由服务端 Provider schema 提供。API Key 只保存在当前表单内，提交后立即清除。</p>
      <button class="primary-button" type="submit" :disabled="isSubmitting">{{ isSubmitting ? '正在保存' : isEditing ? '保存修改' : '创建连接' }}</button>
    </form>

    <section class="ai-settings-panel ai-settings-connections-list">
      <div class="ai-settings-panel__head"><div><span>Connections</span><h2>已有连接</h2></div><button class="ghost-button" type="button" :disabled="isLoading" @click="loadConnections">重新加载</button></div>
      <p v-if="error" class="feedback feedback--error">{{ error }}</p><p v-else-if="feedback" class="feedback feedback--success">{{ feedback }}</p>
      <p v-if="isLoading" class="placeholder-text">正在加载个人连接…</p>
      <p v-else-if="connections.length === 0" class="placeholder-text">尚未配置个人模型连接。</p>
      <article v-for="connection in connections" :key="connection.id" class="ai-settings-connection-card">
        <header><div><strong>{{ connection.name }}</strong><span>{{ connection.providerType }} · {{ connection.maskedApiKey ?? '未返回密钥掩码' }}</span></div><b :class="`status-${connection.status.toLowerCase()}`">{{ connection.status }}</b></header>
        <dl><div><dt>配置版本</dt><dd>v{{ connection.configVersion }}</dd></div><div><dt>连接测试</dt><dd>{{ connection.connectionTestStatus }}</dd></div><div><dt>并发</dt><dd>{{ connection.maxConcurrency ?? '平台默认' }}</dd></div></dl>
        <div class="ai-settings-card-actions"><button class="ghost-button" type="button" @click="beginEdit(connection)">编辑</button><button class="ghost-button" type="button" :disabled="activeTestKey !== ''" @click="refreshModels(connection)">{{ activeTestKey === `refresh-${connection.id}` ? '刷新中' : '刷新模型' }}</button><button class="ghost-button" type="button" :disabled="activeTestKey !== ''" @click="runConnectionTest(connection)">{{ activeTestKey === `connection-${connection.id}` ? '测试中' : '测试并刷新模型' }}</button><button class="ai-settings-danger-button" type="button" @click="removeConnection(connection)">删除</button></div>
        <div class="ai-settings-model-workspace">
          <div class="ai-settings-model-workspace__head">
            <strong>模型目录（{{ catalogByConnection[connection.id]?.length ?? 0 }}）</strong>
            <button class="ghost-button" type="button" :aria-expanded="expandedModels[connection.id] ?? false" @click="toggleModelsExpanded(connection.id)">{{ expandedModels[connection.id] ? '收起模型' : '展开模型' }}</button>
          </div>
          <template v-if="expandedModels[connection.id]">
            <label>手工模型<input v-model="manualModels[connection.id]" placeholder="用逗号或换行分隔" /></label>
            <div><button class="ghost-button" type="button" :disabled="activeTestKey !== ''" @click="addManualModels(connection)">合并模型</button><button class="ghost-button" type="button" :disabled="activeTestKey !== ''" @click="runBatchTest(connection)">顺序批测</button></div>
            <p v-if="!catalogByConnection[connection.id]" class="ai-settings-model-empty">正在加载已保存模型。</p>
            <ul v-else class="ai-settings-model-list">
              <li
                v-for="model in catalogByConnection[connection.id]"
                :key="model.id"
                class="ai-settings-model-item"
                :class="modelRowClass(model)"
              >
                <div class="ai-settings-model-item__main">
                  <strong>{{ model.modelName }}</strong>
                  <div class="ai-settings-model-item__badges">
                    <span class="ai-settings-badge ai-settings-badge--muted">{{ formatSourceType(model.sourceType) }}</span>
                    <span
                      class="ai-settings-badge"
                      :class="`ai-settings-badge--${testStatusTone(model.testStatus)}`"
                    >
                      {{ formatTestStatus(model.testStatus) }}
                    </span>
                    <span
                      class="ai-settings-badge"
                      :class="model.enabled ? 'ai-settings-badge--enabled' : 'ai-settings-badge--disabled'"
                    >
                      {{ model.enabled ? '使用中' : '未启用' }}
                    </span>
                  </div>
                  <p v-if="model.testStatus !== 'PASSED' && !model.enabled" class="ai-settings-model-item__hint">
                    需先测试通过，才能启用到对话中。
                  </p>
                  <p v-else-if="model.enabled" class="ai-settings-model-item__hint ai-settings-model-item__hint--ok">
                    已可在助手/问答中选择。
                  </p>
                  <p v-else class="ai-settings-model-item__hint ai-settings-model-item__hint--ready">
                    测试已通过，可点击启用。
                  </p>
                </div>
                <div class="ai-settings-model-item__actions">
                  <button
                    class="ghost-button"
                    type="button"
                    :disabled="activeTestKey !== ''"
                    @click="runModelTest(connection, model)"
                  >
                    {{ activeTestKey === `model-${model.id}` ? '测试中…' : '重新测试' }}
                  </button>
                  <button
                    class="ghost-button"
                    :class="model.enabled ? 'ai-settings-toggle-button--off' : 'ai-settings-toggle-button--on'"
                    type="button"
                    :disabled="activeTestKey !== '' || !canEnableModel(model)"
                    :title="!canEnableModel(model) ? '请先测试通过' : model.enabled ? '停用后对话中不可选' : '启用后可在对话中选择'"
                    @click="toggleModelEnabled(connection, model)"
                  >
                    {{
                      activeTestKey === `enabled-${model.id}`
                        ? '处理中…'
                        : model.enabled
                          ? '停用'
                          : '启用'
                    }}
                  </button>
                </div>
              </li>
            </ul>
          </template>
        </div>
      </article>
    </section>
  </section>
</template>
