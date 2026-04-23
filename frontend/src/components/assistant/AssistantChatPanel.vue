<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type {
  AssistantChatResult,
  AssistantConversationContext,
  AssistantMessageItem,
  AssistantToolMode,
} from '../../types/assistant'

const props = defineProps<{
  sessionTitle: string
  selectedSessionId: number | null
  conversationContext: AssistantConversationContext | null
  latestChatResult: AssistantChatResult | null
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
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">当前会话</p>
        <h2>{{ selectedSessionId === null ? '等待会话' : sessionTitle }}</h2>
      </div>
      <span class="panel__pill">{{ selectedSessionId === null ? '未选中' : `会话 #${selectedSessionId}` }}</span>
    </div>

    <p v-if="error" class="feedback feedback--error">{{ error }}</p>
    <p v-else-if="selectedSessionId === null" class="placeholder-text">
      先从左侧创建或选择一个会话，再开始连续对话。
    </p>
    <p v-else-if="isLoading" class="placeholder-text">正在恢复会话上下文...</p>
    <div v-else class="assistant-chat-panel">
      <section
        v-if="conversationContext?.recentMessages?.length || shouldShowStreamingMessage"
        ref="messageListRef"
        class="assistant-message-list"
      >
        <article
          v-for="message in conversationContext?.recentMessages ?? []"
          :key="message.messageId"
          class="assistant-message"
          :class="message.role === 'USER' ? 'assistant-message--user' : 'assistant-message--assistant'"
        >
          <header class="assistant-message__meta">
            <strong>{{ resolveRoleLabel(message) }}</strong>
            <span>{{ message.toolMode ?? 'CHAT' }}</span>
            <time :datetime="message.createdAt">{{ formatTime(message.createdAt) }}</time>
          </header>
          <p class="assistant-message__body">{{ message.content }}</p>
          <div v-if="resolveCitations(message).length > 0" class="assistant-message__citations">
            <span>引用文件</span>
            <ul>
              <li v-for="citation in resolveCitations(message)" :key="`${message.messageId}-${citation.fileName}`">
                {{ citation.fileName }}
              </li>
            </ul>
          </div>
        </article>
        <article
          v-if="shouldShowStreamingMessage"
          class="assistant-message assistant-message--assistant assistant-message--streaming"
        >
          <header class="assistant-message__meta">
            <strong>助手</strong>
            <span>{{ streamingToolMode ?? 'CHAT' }}</span>
            <span>生成中</span>
          </header>
          <p class="assistant-message__body">{{ streamingReply }}</p>
        </article>
      </section>
      <p v-else class="placeholder-text">
        当前会话还没有历史消息。发送第一条消息后，这里会持续显示最近上下文。
      </p>

      <p v-if="isSending" class="assistant-chat-panel__sending">助手正在处理中...</p>
    </div>
  </section>
</template>
