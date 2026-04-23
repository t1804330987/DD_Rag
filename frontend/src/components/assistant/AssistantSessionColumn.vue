<script setup lang="ts">
import { ref, watch } from 'vue'
import type { AssistantSessionListItem } from '../../types/assistant'

const props = defineProps<{
  sessions: AssistantSessionListItem[]
  selectedSessionId: number | null
  isLoading: boolean
  error: string
  editingSessionId: number | null
  renamingSessionId: number | null
  deletingSessionId: number | null
}>()

const emit = defineEmits<{
  create: []
  select: [sessionId: number]
  startRename: [sessionId: number]
  rename: [sessionId: number, title: string]
  cancelRename: []
  remove: [sessionId: number]
}>()

const editingTitle = ref('')

watch(
  () => props.editingSessionId,
  (sessionId) => {
    if (sessionId === null) {
      editingTitle.value = ''
      return
    }
    const session = props.sessions.find((item) => item.sessionId === sessionId)
    editingTitle.value = session?.title ?? ''
  },
  { immediate: true },
)

function formatTime(value: string | null) {
  if (value === null) {
    return '尚未开始对话'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '时间未知'
    : new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date)
}

function startRename(event: MouseEvent, session: AssistantSessionListItem) {
  event.stopPropagation()
  editingTitle.value = session.title
  emit('startRename', session.sessionId)
}

function submitRename(event: Event, sessionId: number) {
  event.stopPropagation()
  const nextTitle = editingTitle.value.trim()
  if (!nextTitle) {
    editingTitle.value = props.sessions.find((item) => item.sessionId === sessionId)?.title ?? ''
    emit('cancelRename')
    return
  }
  emit('rename', sessionId, nextTitle)
}

function cancelRename(event: Event) {
  event.stopPropagation()
  emit('cancelRename')
}

function triggerDelete(event: MouseEvent, sessionId: number) {
  event.stopPropagation()
  emit('remove', sessionId)
}
</script>

<template>
  <section class="assistant-page__column assistant-page__column--sessions">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">会话列表</p>
        <h2>你的聊天记录</h2>
      </div>
      <button class="primary-button" type="button" @click="emit('create')">新建会话</button>
    </div>

    <p v-if="error" class="feedback feedback--error">{{ error }}</p>
    <p v-else-if="isLoading" class="placeholder-text">正在同步会话列表...</p>
    <p v-else-if="sessions.length === 0" class="placeholder-text">
      这里会保留你的最近会话。现在还没有任何记录，先创建一个新会话开始。
    </p>

    <div v-else class="assistant-session-list">
      <article
        v-for="session in sessions"
        :key="session.sessionId"
        class="assistant-session-card"
        :class="{ 'is-active': selectedSessionId === session.sessionId }"
      >
        <div class="assistant-session-card__title-row">
          <input
            v-if="editingSessionId === session.sessionId"
            v-model="editingTitle"
            class="assistant-session-card__title-input"
            maxlength="255"
            @click.stop
            @keydown.enter.prevent="submitRename($event, session.sessionId)"
            @keydown.esc.prevent="cancelRename($event)"
            @blur="submitRename($event, session.sessionId)"
          />
          <strong v-else>{{ session.title }}</strong>

          <div class="assistant-session-card__actions">
            <button
              class="assistant-session-card__action ghost-button"
              type="button"
              :disabled="renamingSessionId === session.sessionId"
              @click="startRename($event, session)"
            >
              {{ renamingSessionId === session.sessionId ? '保存中...' : '重命名' }}
            </button>
            <button
              class="assistant-session-card__action ghost-button ghost-button--danger"
              type="button"
              :disabled="deletingSessionId === session.sessionId"
              @click="triggerDelete($event, session.sessionId)"
            >
              {{ deletingSessionId === session.sessionId ? '删除中...' : '删除' }}
            </button>
          </div>
        </div>
        <button class="assistant-session-card__main" type="button" @click="emit('select', session.sessionId)">
          <span>会话 #{{ session.sessionId }}</span>
          <small>{{ formatTime(session.lastMessageAt) }}</small>
        </button>
      </article>
    </div>
  </section>
</template>
