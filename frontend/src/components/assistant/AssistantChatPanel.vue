<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type {
  AssistantChatResult,
  AssistantConversationContext,
  AssistantMessageItem,
  AssistantToolMode,
} from '../../types/assistant'
import MarkdownContent from '../MarkdownContent.vue'

const props = defineProps<{
  sessionTitle: string
  selectedSessionId: number | null
  conversationContext: AssistantConversationContext | null
  latestChatResult: AssistantChatResult | null
  pendingUserMessage: AssistantMessageItem | null
  streamingReply: string
  streamingToolMode: AssistantToolMode | null
  isLoading: boolean
  isSending: boolean
  error: string
}>()

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '刚刚'
    : new Intl.DateTimeFormat('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
      }).format(date)
}

function resolveCitations(message: AssistantMessageItem) {
  if (message.structuredPayload == null) {
    return []
  }
  try {
    const parsed = JSON.parse(message.structuredPayload) as { citations?: Array<{ fileName?: string }> }
    return parsed.citations?.filter((item) => typeof item.fileName === 'string' && item.fileName.length > 0) ?? []
  } catch {
    return []
  }
}

function resolveRoleLabel(message: AssistantMessageItem) {
  return message.role === 'USER' ? '你' : '助手'
}

function resolveToolModeLabel(mode: string | null | undefined) {
  if (mode === 'KB_SEARCH') return '知识库'
  if (mode === 'CHAT') return '对话'
  return mode ?? '对话'
}

const hasStreamingReply = computed(() => props.streamingReply.trim().length > 0)
const shouldShowStreamingMessage = computed(() => props.isSending || hasStreamingReply.value)
const messageListRef = ref<HTMLElement | null>(null)

async function scrollMessagesToBottom() {
  await nextTick()
  const container = messageListRef.value
  if (container == null) {
    return
  }
  container.scrollTop = container.scrollHeight
}

watch(
  () => [
    props.selectedSessionId,
    props.conversationContext?.recentMessages?.length ?? 0,
    props.streamingReply,
    props.latestChatResult?.messageId ?? 0,
  ],
  () => {
    void scrollMessagesToBottom()
  },
  { immediate: true },
)
</script>

<template>
  <section class="assistant-page__column assistant-page__column--chat">
    <div class="panel__header assistant-chat-panel__header">
      <div>
        <p class="panel__eyebrow">对话区</p>
        <h2>{{ selectedSessionId === null ? '新对话' : sessionTitle }}</h2>
      </div>
      <span class="panel__pill">{{ selectedSessionId === null ? '未创建' : `会话 #${selectedSessionId}` }}</span>
    </div>

    <p v-if="error" class="feedback feedback--error">{{ error }}</p>
    <div
      v-else-if="selectedSessionId === null && pendingUserMessage === null"
      class="assistant-chat-empty"
    >
      <strong>从第一条消息开始</strong>
      <p>选好模型和指令后直接发送。首次发送会自动建会话，后续可在左侧继续。</p>
    </div>
    <div v-else-if="isLoading" class="assistant-chat-loading" aria-live="polite">
      <div class="assistant-chat-loading__line" />
      <div class="assistant-chat-loading__line assistant-chat-loading__line--short" />
      <div class="assistant-chat-loading__line assistant-chat-loading__line--mid" />
      <span>正在恢复会话上下文…</span>
    </div>
    <div v-else class="assistant-chat-panel">
      <section
        v-if="conversationContext?.recentMessages?.length || pendingUserMessage !== null || shouldShowStreamingMessage"
        ref="messageListRef"
        class="assistant-message-list"
      >
        <article
          v-for="message in conversationContext?.recentMessages ?? []"
          :key="message.messageId"
          class="assistant-message"
          :class="message.role === 'USER' ? 'assistant-message--user' : 'assistant-message--assistant'"
        >
          <div class="assistant-message__avatar" aria-hidden="true">
            {{ message.role === 'USER' ? '你' : 'AI' }}
          </div>
          <div class="assistant-message__bubble">
            <header class="assistant-message__meta">
              <strong>{{ resolveRoleLabel(message) }}</strong>
              <span class="assistant-message__badge">{{ resolveToolModeLabel(message.toolMode) }}</span>
              <time :datetime="message.createdAt">{{ formatTime(message.createdAt) }}</time>
            </header>
            <MarkdownContent
              class="assistant-message__body"
              :content="message.content"
              :mode="message.role === 'USER' ? 'plain' : 'markdown'"
              :show-copy="message.role !== 'USER'"
            />
            <div v-if="resolveCitations(message).length > 0" class="assistant-message__citations">
              <span>引用文件</span>
              <ul>
                <li v-for="citation in resolveCitations(message)" :key="`${message.messageId}-${citation.fileName}`">
                  {{ citation.fileName }}
                </li>
              </ul>
            </div>
          </div>
        </article>
        <article
          v-if="pendingUserMessage !== null"
          class="assistant-message assistant-message--user"
        >
          <div class="assistant-message__avatar" aria-hidden="true">你</div>
          <div class="assistant-message__bubble">
            <header class="assistant-message__meta">
              <strong>你</strong>
              <span class="assistant-message__badge">{{ resolveToolModeLabel(pendingUserMessage.toolMode) }}</span>
              <time :datetime="pendingUserMessage.createdAt">{{ formatTime(pendingUserMessage.createdAt) }}</time>
            </header>
            <MarkdownContent
              class="assistant-message__body"
              :content="pendingUserMessage.content"
              mode="plain"
            />
          </div>
        </article>
        <article
          v-if="shouldShowStreamingMessage"
          class="assistant-message assistant-message--assistant assistant-message--streaming"
        >
          <div class="assistant-message__avatar" aria-hidden="true">AI</div>
          <div class="assistant-message__bubble">
            <header class="assistant-message__meta">
              <strong>助手</strong>
              <span class="assistant-message__badge">{{ resolveToolModeLabel(streamingToolMode) }}</span>
              <span class="assistant-message__live">
                <i class="assistant-message__pulse" />
                生成中
              </span>
            </header>
            <MarkdownContent
              v-if="hasStreamingReply"
              class="assistant-message__body"
              :content="streamingReply"
              mode="markdown"
              streaming
            />
            <p v-else class="assistant-message__thinking">正在组织回答…</p>
          </div>
        </article>
      </section>
      <div v-else class="assistant-chat-empty assistant-chat-empty--compact">
        <strong>会话已就绪</strong>
        <p>还没有消息。在下方输入问题，回复会出现在这里。</p>
      </div>

      <p v-if="isSending" class="assistant-chat-panel__sending">助手正在生成回复…</p>
    </div>
  </section>
</template>
