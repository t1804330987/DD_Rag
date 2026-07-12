<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  createAssistantSession,
  deleteAssistantSession,
  fetchAvailableAssistantModels,
  fetchAssistantConversationContext,
  fetchAssistantSessionDetail,
  fetchAssistantSessions,
  renameAssistantSession,
  selectAssistantSessionInstruction,
  selectAssistantSessionModel,
  streamAssistantMessage,
} from '../../api/assistant'
import { fetchGroups, type GroupItem } from '../../api/group'
import { extractApiError } from '../../api/http'
import { fetchInstructionProfiles } from '../../api/model-platform'
import AssistantChatPanel from '../../components/assistant/AssistantChatPanel.vue'
import AssistantComposer from '../../components/assistant/AssistantComposer.vue'
import AssistantInstructionSelector from '../../components/assistant/AssistantInstructionSelector.vue'
import AssistantModelSelector from '../../components/assistant/AssistantModelSelector.vue'
import AssistantSessionColumn from '../../components/assistant/AssistantSessionColumn.vue'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import type {
  AssistantChatResult,
  AssistantChatStreamEvent,
  AssistantConversationContext,
  AssistantAvailableModel,
  AssistantMessageItem,
  AssistantSessionListItem,
  AssistantToolMode,
} from '../../types/assistant'
import type { InstructionProfile } from '../../types/model-platform'
import { createRequestId } from '../../utils/id'
import '../../assets/assistant-page.css'
import '../../assets/page-shell.css'
import { useAuthStore } from '../../stores/auth'

type VisibleGroup = GroupItem & { relation: 'OWNER' | 'MEMBER' }

const sessions = ref<AssistantSessionListItem[]>([])
const selectedSessionId = ref<number | null>(null)
const selectedSessionTitle = ref('新会话')
const conversationContext = ref<AssistantConversationContext | null>(null)
const latestChatResult = ref<AssistantChatResult | null>(null)
const sessionError = ref('')
const chatError = ref('')
const editingSessionId = ref<number | null>(null)
const renamingSessionId = ref<number | null>(null)
const deletingSessionId = ref<number | null>(null)
const isSessionsLoading = ref(false)
const isConversationLoading = ref(false)
const isSending = ref(false)
const isStreaming = ref(false)
const composerDraft = ref('')
const pendingUserMessage = ref<AssistantMessageItem | null>(null)
const streamingReply = ref('')
const streamingToolMode = ref<AssistantToolMode | null>(null)
const selectedToolMode = ref<AssistantToolMode>('CHAT')
const selectedGroupId = ref<number | null>(null)
const visibleGroups = ref<VisibleGroup[]>([])
const availableModels = ref<AssistantAvailableModel[]>([])
const instructionProfiles = ref<InstructionProfile[]>([])
const selectedModelConnectionId = ref<number | null>(null)
const selectedModelId = ref<number | null>(null)
const selectedInstructionProfileId = ref<number | null>(null)
const isModelsLoading = ref(false)
const isInstructionsLoading = ref(false)
const modelError = ref('')
const instructionError = ref('')
const isSessionDrawerOpen = ref(false)
const authStore = useAuthStore()
let streamAbortController: AbortController | null = null

const heroDescription = computed(() =>
  selectedToolMode.value === 'CHAT'
    ? '多轮对话与会话恢复。适合持续协作；需要依据文档时，可切换到知识库检索。'
    : '知识库检索模式：回答限定在所选知识库内，便于核对出处。',
)
const canSend = computed(() => {
  if (composerDraft.value.trim().length === 0 || selectedModelId.value === null || selectedModelConnectionId.value === null) {
    return false
  }
  if (selectedToolMode.value === 'KB_SEARCH') {
    return selectedGroupId.value !== null
  }
  return true
})

onMounted(async () => {
  await refreshSessions()
  await Promise.all([refreshGroups(), refreshAvailableModels(), refreshInstructionProfiles()])
})

async function refreshAvailableModels() {
  isModelsLoading.value = true
  modelError.value = ''
  try {
    availableModels.value = await fetchAvailableAssistantModels()
    if (selectedSessionId.value === null && selectedModelId.value === null && availableModels.value.length > 0) {
      const [first] = availableModels.value
      selectedModelConnectionId.value = first.connectionId
      selectedModelId.value = first.modelId
    }
  } catch (error) {
    availableModels.value = []
    modelError.value = extractApiError(error, '加载可用模型失败')
  } finally {
    isModelsLoading.value = false
  }
}

async function refreshInstructionProfiles() {
  isInstructionsLoading.value = true
  instructionError.value = ''
  try {
    instructionProfiles.value = await fetchInstructionProfiles()
    if (selectedInstructionProfileId.value === null) {
      selectedInstructionProfileId.value = instructionProfiles.value.find((profile) => profile.enabled && profile.isDefault)?.profileId ?? null
    }
  } catch (error) {
    instructionProfiles.value = []
    instructionError.value = extractApiError(error, '加载个人指令失败')
  } finally {
    isInstructionsLoading.value = false
  }
}

async function refreshSessions(preferredSessionId: number | null = selectedSessionId.value) {
  isSessionsLoading.value = true
  sessionError.value = ''
  try {
    const nextSessions = (await fetchAssistantSessions()).slice().sort((left, right) => {
      if (left.lastMessageAt === null) {
        return right.lastMessageAt === null ? right.sessionId - left.sessionId : -1
      }
      if (right.lastMessageAt === null) {
        return 1
      }
      if (left.lastMessageAt === right.lastMessageAt) {
        return right.sessionId - left.sessionId
      }
      return new Date(right.lastMessageAt).getTime() - new Date(left.lastMessageAt).getTime()
    })
    sessions.value = nextSessions
    const nextSelectedId =
      preferredSessionId !== null && nextSessions.some((item) => item.sessionId === preferredSessionId)
        ? preferredSessionId
        : nextSessions[0]?.sessionId ?? null
    selectedSessionId.value = nextSelectedId
    if (nextSelectedId !== null) {
      await loadConversation(nextSelectedId)
    } else {
      selectedSessionTitle.value = '新会话'
      conversationContext.value = null
      latestChatResult.value = null
    }
  } catch (error) {
    sessions.value = []
    selectedSessionId.value = null
    conversationContext.value = null
    latestChatResult.value = null
    sessionError.value = extractApiError(error, '加载会话失败')
  } finally {
    isSessionsLoading.value = false
  }
}

async function refreshGroups() {
  try {
    const result = await fetchGroups()
    visibleGroups.value = [
      ...result.ownedGroups.map((group) => ({ ...group, relation: 'OWNER' as const })),
      ...result.joinedGroups.map((group) => ({ ...group, relation: 'MEMBER' as const })),
    ]
    if (
      selectedGroupId.value !== null &&
      visibleGroups.value.some((group) => group.groupId === selectedGroupId.value)
    ) {
      return
    }
    selectedGroupId.value = visibleGroups.value[0]?.groupId ?? null
  } catch (error) {
    chatError.value = extractApiError(error, '加载组列表失败')
  }
}

async function createSession() {
  if (isSending.value) {
    return
  }
  sessionError.value = ''
  editingSessionId.value = null
  try {
    const created = await createAssistantSession()
    selectedSessionId.value = created.sessionId
    selectedSessionTitle.value = created.title
    composerDraft.value = ''
    await refreshSessions(created.sessionId)
    isSessionDrawerOpen.value = false
  } catch (error) {
    sessionError.value = extractApiError(error, '创建会话失败')
  }
}

async function selectSession(sessionId: number) {
  if (isSending.value) {
    return
  }
  editingSessionId.value = null
  if (selectedSessionId.value === sessionId) {
    return
  }
  selectedSessionId.value = sessionId
  await loadConversation(sessionId)
  isSessionDrawerOpen.value = false
}

function startRenameSession(sessionId: number) {
  if (isSending.value) {
    return
  }
  editingSessionId.value = sessionId
}

function cancelRenameSession() {
  editingSessionId.value = null
}

async function loadConversation(sessionId: number) {
  isConversationLoading.value = true
  chatError.value = ''
  try {
    const [detail, context] = await Promise.all([
      fetchAssistantSessionDetail(sessionId),
      fetchAssistantConversationContext(sessionId),
    ])
    selectedSessionTitle.value = detail.title
    selectedModelConnectionId.value = detail.currentModelConnectionId
    selectedModelId.value = detail.currentModelId
    selectedInstructionProfileId.value = detail.currentInstructionProfileId
    conversationContext.value = context
  } catch (error) {
    conversationContext.value = null
    chatError.value = extractApiError(error, '恢复会话失败')
  } finally {
    isConversationLoading.value = false
  }
}

async function selectModel(model: AssistantAvailableModel) {
  if (isSending.value || (selectedModelConnectionId.value === model.connectionId && selectedModelId.value === model.modelId)) return
  const previousConnectionId = selectedModelConnectionId.value
  const previousModelId = selectedModelId.value
  selectedModelConnectionId.value = model.connectionId
  selectedModelId.value = model.modelId
  if (selectedSessionId.value === null) return
  try {
    await selectAssistantSessionModel(selectedSessionId.value, model.connectionId, model.modelId)
    await refreshSessions(selectedSessionId.value)
  } catch (error) {
    selectedModelConnectionId.value = previousConnectionId
    selectedModelId.value = previousModelId
    modelError.value = extractApiError(error, '更新会话模型失败')
  }
}

async function selectInstruction(profileId: number | null) {
  if (isSending.value || selectedInstructionProfileId.value === profileId) return
  const previousProfileId = selectedInstructionProfileId.value
  selectedInstructionProfileId.value = profileId
  if (selectedSessionId.value === null) return
  try {
    await selectAssistantSessionInstruction(selectedSessionId.value, profileId)
    await refreshSessions(selectedSessionId.value)
  } catch (error) {
    selectedInstructionProfileId.value = previousProfileId
    instructionError.value = extractApiError(error, '更新会话个人指令失败')
  }
}

async function renameSession(sessionId: number, title: string) {
  if (isSending.value) {
    return
  }
  const currentSession = sessions.value.find((item) => item.sessionId === sessionId)
  const nextTitle = title.trim()
  if (!nextTitle || currentSession?.title === nextTitle) {
    editingSessionId.value = null
    return
  }
  renamingSessionId.value = sessionId
  sessionError.value = ''
  try {
    await renameAssistantSession(sessionId, nextTitle)
    editingSessionId.value = null
    await refreshSessions(selectedSessionId.value)
  } catch (error) {
    sessionError.value = extractApiError(error, '重命名会话失败')
  } finally {
    renamingSessionId.value = null
  }
}

async function removeSession(sessionId: number) {
  if (isSending.value) {
    return
  }
  const target = sessions.value.find((item) => item.sessionId === sessionId)
  if (!target) {
    return
  }
  if (!window.confirm(`确认删除会话「${target.title}」吗？删除后消息记录将一并清空。`)) {
    return
  }
  deletingSessionId.value = sessionId
  sessionError.value = ''
  chatError.value = ''
  try {
    const remainingSessions = sessions.value.filter((item) => item.sessionId !== sessionId)
    const fallbackSessionId =
      selectedSessionId.value === sessionId
        ? (remainingSessions[0]?.sessionId ?? null)
        : selectedSessionId.value
    await deleteAssistantSession(sessionId)
    if (selectedSessionId.value === sessionId) {
      selectedSessionId.value = fallbackSessionId
      selectedSessionTitle.value = fallbackSessionId === null ? '新会话' : selectedSessionTitle.value
      if (fallbackSessionId === null) {
        conversationContext.value = null
        latestChatResult.value = null
      }
    }
    await refreshSessions(fallbackSessionId)
  } catch (error) {
    sessionError.value = extractApiError(error, '删除会话失败')
  } finally {
    deletingSessionId.value = null
  }
}

async function submitChat() {
  if (!canSend.value) {
    chatError.value =
      selectedToolMode.value === 'KB_SEARCH' && selectedGroupId.value === null
        ? '知识库检索模式必须先选择组。'
        : selectedModelId.value === null
          ? '请先选择一个可用模型。'
          : '请输入内容后再发送。'
    return
  }
  isSending.value = true
  isStreaming.value = true
  chatError.value = ''
  latestChatResult.value = null
  streamingReply.value = ''
  streamingToolMode.value = selectedToolMode.value
  const message = composerDraft.value.trim()
  pendingUserMessage.value = {
    messageId: 0,
    sessionId: selectedSessionId.value ?? 0,
    role: 'USER',
    toolMode: selectedToolMode.value,
    groupId: selectedToolMode.value === 'KB_SEARCH' ? selectedGroupId.value : null,
    content: message,
    structuredPayload: null,
    createdAt: new Date().toISOString(),
  }
  composerDraft.value = ''
  try {
    const accessToken = authStore.accessToken
    if (accessToken == null) {
      throw new Error('登录态已失效，请重新登录')
    }
    const sessionId = selectedSessionId.value
    streamAbortController = new AbortController()
    await streamAssistantMessage({
      sessionId,
      message,
      toolMode: selectedToolMode.value,
      groupId: selectedToolMode.value === 'KB_SEARCH' ? selectedGroupId.value : null,
      requestId: createRequestId(),
      modelConnectionId: sessionId === null ? selectedModelConnectionId.value : null,
      modelId: sessionId === null ? selectedModelId.value : null,
      instructionProfileId: sessionId === null ? selectedInstructionProfileId.value : null,
    }, accessToken, {
      signal: streamAbortController.signal,
      onEvent: handleStreamEvent,
    })
    await refreshSessions(selectedSessionId.value)
    latestChatResult.value = null
    pendingUserMessage.value = null
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      chatError.value = '已停止生成'
    } else {
      chatError.value = extractApiError(error, '发送消息失败')
    }
  } finally {
    isSending.value = false
    isStreaming.value = false
    streamAbortController = null
  }
}

function handleStreamEvent(event: AssistantChatStreamEvent) {
  if (event.event === 'start' && selectedSessionId.value === null) {
    selectedSessionId.value = event.sessionId
  }
  if (event.event === 'delta' && event.delta !== null) {
    streamingReply.value += event.delta
    return
  }
  if (event.event === 'done') {
    latestChatResult.value = {
      sessionId: event.sessionId,
      messageId: event.messageId ?? 0,
      reply: event.reply ?? streamingReply.value,
      toolMode: event.toolMode,
      groupId: event.groupId,
      citations: event.citations ?? [],
    }
    streamingReply.value = ''
    return
  }
  if (event.event === 'error') {
    throw new Error(toChatError(event.error))
  }
}

function toChatError(code: string | null) {
  return {
    SESSION_BUSY: '当前会话正在生成，请等待或停止后重试。',
    FIRST_TOKEN_TIMEOUT: '模型响应超时，请稍后重试。',
    STREAM_IDLE_TIMEOUT: '模型输出中断超时，请稍后重试。',
    CALL_TIMEOUT: '本轮回答超时，请缩短问题后重试。',
    PROVIDER_RATE_LIMITED: '模型服务当前限流，请稍后重试。',
    MODEL_NOT_AVAILABLE: '当前模型已不可用，请重新选择模型。',
    MODEL_NOT_AUTHORIZED: '你已无权使用当前模型，请重新选择模型。',
    MODEL_NOT_CONFIGURED: '请先选择一个可用模型。',
    MODEL_CONFIGURATION_CHANGED: '当前模型配置已更新，请重新选择模型后重试。',
    MODEL_CONNECTION_NOT_ACTIVE: '当前模型连接已停用，请重新选择模型。',
  }[code ?? ''] ?? '模型服务暂时不可用，请稍后重试。'
}

function stopStreaming() {
  streamAbortController?.abort()
}
</script>

<template>
  <WorkbenchShell class="page-shell--assistant">
    <template #sidebar>
      <WorkbenchSidebar />
    </template>

    <template #main>
      <main class="assistant-page">
        <PageHeaderHero eyebrow="助手" title="个人助手" :description="heroDescription">
          <template #actions>
            <div class="assistant-page__hero-actions">
              <div class="assistant-page__hero-chip">
                <span>模式</span>
                <strong>{{ selectedToolMode === 'CHAT' ? '对话' : '知识库' }}</strong>
              </div>
              <div class="assistant-page__hero-chip">
                <span>会话</span>
                <strong>{{ sessions.length }}</strong>
              </div>
            </div>
          </template>
        </PageHeaderHero>

        <section class="assistant-page__layout">
          <button
            class="assistant-page__session-toggle ghost-button"
            type="button"
            :aria-expanded="isSessionDrawerOpen"
            @click="isSessionDrawerOpen = true"
          >
            会话列表
          </button>
          <button
            v-if="isSessionDrawerOpen"
            class="assistant-page__drawer-backdrop"
            type="button"
            aria-label="关闭会话列表"
            @click="isSessionDrawerOpen = false"
          />
          <AssistantSessionColumn
            :sessions="sessions"
            :selected-session-id="selectedSessionId"
            :editing-session-id="editingSessionId"
            :renaming-session-id="renamingSessionId"
            :deleting-session-id="deletingSessionId"
            :is-loading="isSessionsLoading"
            :interaction-disabled="isSending"
            :error="sessionError"
            :drawer-open="isSessionDrawerOpen"
            @create="createSession"
            @select="selectSession"
            @start-rename="startRenameSession"
            @rename="renameSession"
            @cancel-rename="cancelRenameSession"
            @remove="removeSession"
            @close="isSessionDrawerOpen = false"
          />

          <div class="assistant-page__center">
            <section class="assistant-session-controls" aria-label="当前会话设置">
              <div class="assistant-session-controls__heading">
                <span>当前会话</span>
                <strong>{{ selectedSessionId === null ? '新会话将在首次发送时创建' : selectedSessionTitle }}</strong>
              </div>
              <AssistantModelSelector
                :models="availableModels"
                :connection-id="selectedModelConnectionId"
                :model-id="selectedModelId"
                :disabled="isSending"
                :loading="isModelsLoading"
                :error="modelError"
                @select="selectModel"
              />
              <AssistantInstructionSelector
                :profiles="instructionProfiles"
                :profile-id="selectedInstructionProfileId"
                :disabled="isSending"
                :loading="isInstructionsLoading"
                :error="instructionError"
                @select="selectInstruction"
              />
            </section>
            <AssistantChatPanel
              :session-title="selectedSessionTitle"
              :selected-session-id="selectedSessionId"
              :conversation-context="conversationContext"
              :latest-chat-result="latestChatResult"
              :pending-user-message="pendingUserMessage"
              :streaming-reply="streamingReply"
              :streaming-tool-mode="streamingToolMode"
              :is-loading="isConversationLoading"
              :is-sending="isSending"
              :error="chatError"
            />

            <AssistantComposer
              :draft="composerDraft"
              :tool-mode="selectedToolMode"
              :selected-group-id="selectedGroupId"
              :groups="visibleGroups"
              :is-sending="isSending"
              :is-streaming="isStreaming"
              :can-send="canSend"
              @update:draft="composerDraft = $event"
              @update:tool-mode="selectedToolMode = $event"
              @update:group-id="selectedGroupId = $event"
              @submit="submitChat"
              @stop="stopStreaming"
            />
          </div>
        </section>
      </main>
    </template>
  </WorkbenchShell>
</template>
