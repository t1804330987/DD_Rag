<script setup lang="ts">
import { computed } from 'vue'
import type { GroupItem } from '../../api/group'
import type { AssistantToolMode } from '../../types/assistant'

const props = defineProps<{
  draft: string
  toolMode: AssistantToolMode
  selectedGroupId: number | null
  groups: Array<GroupItem & { relation: 'OWNER' | 'MEMBER' }>
  isSending: boolean
  isStreaming: boolean
  canSend: boolean
}>()

const emit = defineEmits<{
  'update:draft': [value: string]
  'update:toolMode': [value: AssistantToolMode]
  'update:groupId': [value: number | null]
  submit: []
  stop: []
}>()

const ownedGroups = computed(() => props.groups.filter((group) => group.relation === 'OWNER'))
const joinedGroups = computed(() => props.groups.filter((group) => group.relation === 'MEMBER'))

function handleTextareaKeydown(event: KeyboardEvent, canSend: boolean, isSending: boolean) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }
  event.preventDefault()
  if (!canSend || isSending) {
    return
  }
  emit('submit')
}
</script>

<template>
  <section class="assistant-composer">
    <div class="assistant-composer__mode-tabs" role="tablist" aria-label="工具模式">
      <button
        class="assistant-composer__mode-tab"
        :class="{ 'is-active': props.toolMode === 'CHAT' }"
        type="button"
        :disabled="props.isSending"
        @click="emit('update:toolMode', 'CHAT')"
      >
        仅对话
      </button>
      <button
        class="assistant-composer__mode-tab"
        :class="{ 'is-active': props.toolMode === 'KB_SEARCH' }"
        type="button"
        :disabled="props.isSending"
        @click="emit('update:toolMode', 'KB_SEARCH')"
      >
        知识库检索
      </button>
    </div>

    <div v-if="props.toolMode === 'KB_SEARCH'" class="assistant-composer__group-field">
      <label>
        <span>知识库空间</span>
        <select
          :value="props.selectedGroupId ?? ''"
          :disabled="props.isSending"
          @change="emit('update:groupId', Number(($event.target as HTMLSelectElement).value) || null)"
        >
          <option value="">请选择组</option>
          <optgroup v-if="ownedGroups.length > 0" label="我拥有的知识库">
            <option
              v-for="group in ownedGroups"
              :key="`owner-${group.groupId}`"
              :value="group.groupId"
            >
              {{ group.groupName }} · 所有者
            </option>
          </optgroup>
          <optgroup v-if="joinedGroups.length > 0" label="我加入的知识库">
            <option
              v-for="group in joinedGroups"
              :key="`member-${group.groupId}`"
              :value="group.groupId"
            >
              {{ group.groupName }} · 成员
            </option>
          </optgroup>
        </select>
      </label>
      <p class="assistant-composer__group-tip">只会在当前选择的知识库空间内检索，不会跨空间回答。</p>
    </div>

    <label class="assistant-composer__message-field">
      <span>输入内容</span>
      <textarea
        :value="props.draft"
        rows="3"
        maxlength="4000"
        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
        :disabled="props.isSending"
        @input="emit('update:draft', ($event.target as HTMLTextAreaElement).value)"
        @keydown="handleTextareaKeydown($event, props.canSend, props.isSending)"
      />
    </label>

    <div class="assistant-composer__footer">
      <p>
        {{
          props.toolMode === 'CHAT'
            ? '对话模式：自由多轮交流，不检索知识库。'
            : '检索模式：仅在所选知识库内作答，并尽量附带引用。'
        }}
      </p>
      <div class="assistant-composer__actions">
        <button
          v-if="props.isStreaming"
          class="ghost-button ghost-button--danger"
          type="button"
          @click="emit('stop')"
        >
          停止
        </button>
        <button class="primary-button" type="button" :disabled="!props.canSend || props.isSending" @click="emit('submit')">
          {{ props.isSending ? '生成中…' : '发送' }}
        </button>
      </div>
    </div>
  </section>
</template>
