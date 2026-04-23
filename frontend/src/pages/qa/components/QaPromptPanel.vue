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
        <p class="panel__eyebrow">问答流程</p>
        <h2>先选知识库，再发起问题</h2>
      </div>
      <button class="ghost-button" type="button" :disabled="isGroupsRefreshing" @click="emit('refresh-groups')">
        {{ isGroupsRefreshing ? '同步中...' : '刷新知识库' }}
      </button>
    </div>

    <section class="qa-prompt-panel__workspace">
      <div class="qa-prompt-panel__workspace-copy">
        <span class="qa-prompt-panel__workspace-label">当前知识库</span>
        <strong>{{ currentGroup?.groupName ?? '未选择问答空间' }}</strong>
        <p>{{ currentGroupDescription }}</p>
        <small>{{ currentRoleHint }}</small>
      </div>
      <div class="qa-prompt-panel__workspace-status">
        <span class="panel__pill">{{ hasGroups ? '组内证据问答' : '等待群组' }}</span>
        <span class="qa-prompt-panel__workspace-tip">
          {{ currentGroupId !== null ? '已锁定当前问答范围' : '请选择一个组开始' }}
        </span>
      </div>
    </section>

    <section class="qa-prompt-panel__selection-board">
      <div class="qa-prompt-panel__selection-copy">
        <p class="qa-prompt-panel__section-label">步骤 1</p>
        <h3>选择问答范围</h3>
        <p>所有问题都只会在你当前选中的知识库空间中检索证据，不会跨组回答。</p>
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
        <p>问题越具体，越容易在当前知识库里命中有效证据并得到稳定回答。</p>
      </div>

      <label class="qa-prompt-panel__composer">
        <span>问题输入</span>
        <textarea
          :value="question"
          class="qa-question-box"
          maxlength="2000"
          placeholder="例如：这个知识库最近一次版本演进解决了什么问题？相关证据分别来自哪些文件？"
          @input="emit('update:question', ($event.target as HTMLTextAreaElement).value)"
        />
      </label>

      <div class="qa-prompt-panel__footer">
        <div class="qa-prompt-panel__footer-copy">
          <span>{{ questionLength }}/2000</span>
          <p>回答只会使用当前组选中的可见、有效、可引用证据片段。</p>
        </div>
        <button class="primary-button" type="button" :disabled="isSubmitting || !canSubmit" @click="emit('submit')">
          {{ isSubmitting ? '检索与生成中...' : '开始问答' }}
        </button>
      </div>
    </section>

    <section class="qa-prompt-panel__rules">
      <div class="qa-prompt-panel__selection-copy">
        <p class="qa-prompt-panel__section-label">规则说明</p>
        <h3>当前问答规则</h3>
      </div>
      <ul class="qa-prompt-panel__rules-list">
        <li>问答只在当前选中知识库内检索，不做跨组拼接。</li>
        <li>OWNER 和 MEMBER 都可以提问，但权限边界仍按组关系执行。</li>
        <li>回答后需要结合右侧证据区一起核对，不建议只看模型正文。</li>
      </ul>
    </section>

    <p v-if="groupError" class="feedback feedback--error">{{ groupError }}</p>
  </article>
</template>
