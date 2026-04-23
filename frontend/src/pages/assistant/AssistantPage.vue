<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  createAssistantSession,
  deleteAssistantSession,
  fetchAssistantConversationContext,
  fetchAssistantSessionDetail,
  fetchAssistantSessions,
  renameAssistantSession,
  streamAssistantMessage,
} from '../../api/assistant'
import { fetchGroups, type GroupItem } from '../../api/group'
import { extractApiError } from '../../api/http'
import AssistantChatPanel from '../../components/assistant/AssistantChatPanel.vue'
import AssistantComposer from '../../components/assistant/AssistantComposer.vue'
import AssistantSessionColumn from '../../components/assistant/AssistantSessionColumn.vue'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import type {
  AssistantChatResult,
  AssistantChatStreamEvent,
  AssistantConversationContext,
  AssistantSessionListItem,
  AssistantToolMode,
} from '../../types/assistant'
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
const streamingReply = ref('')
const streamingToolMode = ref<AssistantToolMode | null>(null)
const selectedToolMode = ref<AssistantToolMode>('CHAT')
const selectedGroupId = ref<number | null>(null)
const visibleGroups = ref<VisibleGroup[]>([])
const authStore = useAuthStore()
let streamAbortController: AbortController | null = null

const heroDescription = computed(() =>
  selectedToolMode.value === 'CHAT'
    ? '这里专门承接连续对话与会话恢复，适合长期协作。'
    : '当前是知识库检索模式，回答会严格限制在你选中的知识库空间内。',
)
const canSend = computed(() => {
  if (selectedSessionId.value === null || composerDraft.value.trim().length === 0) {
    return false
  }
  if (selectedToolMode.value === 'KB_SEARCH') {
    return selectedGroupId.value !== null
  }
  return true
})

onMounted(async () => {
  await Promise.all([refreshSessions(), refreshGroups()])
})

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
  sessionError.value = ''
  editingSessionId.value = null
  try {
    const created = await createAssistantSession()
    selectedSessionId.value = created.sessionId
    selectedSessionTitle.value = created.title
    composerDraft.value = ''
    await refreshSessions(created.sessionId)
  } catch (error) {
    sessionError.value = extractApiError(error, '创建会话失败')
  }
}

async function selectSession(sessionId: number) {
  editingSessionId.value = null
  if (selectedSessionId.value === sessionId) {
    return
  }
  selectedSessionId.value = sessionId
  await loadConversation(sessionId)
}

function startRenameSession(sessionId: number) {
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
      fetchAssistantConversationContext(sessionId, 12),
    ])
    selectedSessionTitle.value = detail.title
    conversationContext.value = context
  } catch (error) {
    conversationContext.value = null
    chatError.value = extractApiError(error, '恢复会话失败')
  } finally {
    isConversationLoading.value = false
  }
}

async function renameSession(sessionId: number, title: string) {
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
  if (!canSend.value || selectedSessionId.value === null) {
    chatError.value =
      selectedToolMode.value === 'KB_SEARCH' && selectedGroupId.value === null
        ? '知识库检索模式必须先选择组。'
        : '请先创建会话并输入内容。'
    return
  }
  isSending.value = true
  isStreaming.value = true
  chatError.value = ''
  latestChatResult.value = null
  streamingReply.value = ''
  streamingToolMode.value = selectedToolMode.value
  try {
    const accessToken = authStore.accessToken
    if (accessToken == null) {
      throw new Error('登录态已失效，请重新登录')
    }
    streamAbortController = new AbortController()
    await streamAssistantMessage({
      sessionId: selectedSessionId.value,
      message: composerDraft.value.trim(),
      toolMode: selectedToolMode.value,
      groupId: selectedToolMode.value === 'KB_SEARCH' ? selectedGroupId.value : null,
    }, accessToken, {
      signal: streamAbortController.signal,
      onEvent: handleStreamEvent,
    })
    composerDraft.value = ''
    await refreshSessions(selectedSessionId.value)
    latestChatResult.value = null
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
    throw new Error(event.error ?? '流式输出失败')
  }
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
        <PageHeaderHero eyebrow="Personal Assistant" title="个人智能助手" :description="heroDescription">
          <template #actions>
            <div class="assistant-page__hero-actions">
              <div class="assistant-page__hero-chip">
                <span>当前模式</span>
                <strong>{{ selectedToolMode === 'CHAT' ? '仅对话' : '知识库检索' }}</strong>
              </div>
              <div class="assistant-page__hero-chip">
                <span>会话数量</span>
                <strong>{{ sessions.length }}</strong>
              </div>
            </div>
          </template>
        </PageHeaderHero>

        <section class="assistant-page__layout">
          <AssistantSessionColumn
            :sessions="sessions"
            :selected-session-id="selectedSessionId"
            :editing-session-id="editingSessionId"
            :renaming-session-id="renamingSessionId"
            :deleting-session-id="deletingSessionId"
            :is-loading="isSessionsLoading"
            :error="sessionError"
            @create="createSession"
            @select="selectSession"
            @start-rename="startRenameSession"
            @rename="renameSession"
            @cancel-rename="cancelRenameSession"
            @remove="removeSession"
          />

          <div class="assistant-page__center">
            <AssistantChatPanel
              :session-title="selectedSessionTitle"
              :selected-session-id="selectedSessionId"
              :conversation-context="conversationContext"
              :latest-chat-result="latestChatResult"
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
