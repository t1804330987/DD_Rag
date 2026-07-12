<script setup lang="ts">
import { computed } from 'vue'
import type { GroupItem } from '../../../api/group'

const props = defineProps<{
  currentGroup: GroupItem | null
  currentGroupId: number | null
  ownedGroups: GroupItem[]
  joinedGroups: GroupItem[]
  question: string
  questionLength: number
  hasGroups: boolean
  canSubmit: boolean
  isGroupsRefreshing: boolean
  isSubmitting: boolean
  groupError: string
  currentGroupDescription: string
  currentRoleHint: string
}>()

const emit = defineEmits<{
  'update:question': [value: string]
  'refresh-groups': []
  'select-group': [groupId: number]
  submit: []
}>()

const selectableGroups = computed(() => props.ownedGroups.length + props.joinedGroups.length)

function handleGroupChange(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value)
  if (Number.isInteger(value) && value > 0) {
    emit('select-group', value)
  }
}
</script>

<template>
  <article class="panel qa-prompt-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">提问</p>
        <h2>选择知识库并提问</h2>
      </div>
      <button class="ghost-button" type="button" :disabled="isGroupsRefreshing" @click="emit('refresh-groups')">
        {{ isGroupsRefreshing ? '同步中…' : '刷新列表' }}
      </button>
    </div>

    <section class="qa-prompt-panel__workspace">
      <div class="qa-prompt-panel__workspace-copy">
        <span class="qa-prompt-panel__workspace-label">当前知识库</span>
        <strong>{{ currentGroup?.groupName ?? '未选择' }}</strong>
        <p>{{ currentGroupDescription }}</p>
        <small>{{ currentRoleHint }}</small>
      </div>
      <div class="qa-prompt-panel__workspace-status">
        <span class="panel__pill">{{ hasGroups ? '组内检索' : '无可用组' }}</span>
        <span class="qa-prompt-panel__workspace-tip">
          {{ currentGroupId !== null ? '范围已锁定' : '请先选择知识库' }}
        </span>
      </div>
    </section>

    <section class="qa-prompt-panel__selection-board">
      <div class="qa-prompt-panel__selection-copy">
        <p class="qa-prompt-panel__section-label">步骤 1</p>
        <h3>选择知识库</h3>
        <p>只在当前选中的组内检索，不会跨组作答。</p>
      </div>

      <label class="qa-prompt-panel__scope-field">
        <span>知识库空间</span>
        <div class="qa-prompt-panel__scope-select-wrap">
          <select class="qa-prompt-panel__scope-select" :value="currentGroupId ?? ''" :disabled="!hasGroups" @change="handleGroupChange">
            <option value="">{{ hasGroups ? '请选择当前问答范围' : '当前没有可用知识库' }}</option>
            <optgroup v-if="ownedGroups.length > 0" label="我拥有的知识库">
              <option
                v-for="group in ownedGroups"
                :key="`qa-owned-${group.groupId}`"
                :value="group.groupId"
              >
                {{ group.groupName }} · 所有者
              </option>
            </optgroup>
            <optgroup v-if="joinedGroups.length > 0" label="我加入的知识库">
              <option
                v-for="group in joinedGroups"
                :key="`qa-joined-${group.groupId}`"
                :value="group.groupId"
              >
                {{ group.groupName }} · 成员
              </option>
            </optgroup>
          </select>
        </div>
        <p class="qa-prompt-panel__scope-hint">
          {{ currentGroup ? `当前已锁定：「${currentGroup.groupName}」` : `当前共有 ${selectableGroups} 个可选知识库` }}
        </p>
      </label>
    </section>

    <section class="qa-prompt-panel__composer-card">
      <div class="qa-prompt-panel__selection-copy">
        <p class="qa-prompt-panel__section-label">步骤 2</p>
        <h3>输入问题</h3>
        <p>问题越具体，越容易命中有效证据。</p>
      </div>

      <label class="qa-prompt-panel__composer">
        <span>问题</span>
        <textarea
          :value="question"
          class="qa-question-box"
          maxlength="2000"
          placeholder="例如：本组文档里关于部署步骤的说明有哪些？分别出自哪些文件？"
          @input="emit('update:question', ($event.target as HTMLTextAreaElement).value)"
        />
      </label>

      <div class="qa-prompt-panel__footer">
        <div class="qa-prompt-panel__footer-copy">
          <span>{{ questionLength }}/2000</span>
          <p>仅使用当前组内可引用的证据片段。</p>
        </div>
        <button class="primary-button" type="button" :disabled="isSubmitting || !canSubmit" @click="emit('submit')">
          {{ isSubmitting ? '检索中…' : '提问' }}
        </button>
      </div>
    </section>

    <section class="qa-prompt-panel__rules">
      <div class="qa-prompt-panel__selection-copy">
        <p class="qa-prompt-panel__section-label">规则</p>
        <h3>回答边界</h3>
      </div>
      <ul class="qa-prompt-panel__rules-list">
        <li>只在当前知识库内检索，不跨组。</li>
        <li>所有者与成员都可提问，权限仍按组角色执行。</li>
        <li>请结合证据区核对，不要只看模型正文。</li>
      </ul>
    </section>

    <p v-if="groupError" class="feedback feedback--error">{{ groupError }}</p>
  </article>
</template>
