<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { askQuestion, type AskQuestionResponse } from '../../api/qa'
import { fetchGroups } from '../../api/group'
import { extractApiError } from '../../api/http'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import { useAppStore } from '../../stores/app'
import { useAuthStore } from '../../stores/auth'
import '../../assets/page-shell.css'
import '../../assets/qa-page.css'
import QaConversationPanel from './components/QaConversationPanel.vue'
import QaPromptPanel from './components/QaPromptPanel.vue'

const appStore = useAppStore()
const authStore = useAuthStore()
const question = ref('')
const result = ref<AskQuestionResponse | null>(null)
const askError = ref('')
const groupError = ref('')
const isSubmitting = ref(false)
const isGroupsRefreshing = ref(false)
let qaContextVersion = 0
let latestAskRequestId = 0

const currentGroupId = computed(() => appStore.currentGroupId)
const hasGroups = computed(() => appStore.visibleGroups.length > 0)
const currentGroup = computed(() => appStore.currentGroup)
const ownedGroups = computed(() => appStore.ownedGroups)
const joinedGroups = computed(() => appStore.joinedGroups)
const pendingInvitationCount = computed(() => appStore.pendingInvitations.length)
const canSubmit = computed(
  () => currentGroupId.value !== null && hasGroups.value && !appStore.isGroupsLoading,
)
const questionLength = computed(() => question.value.trim().length)
const currentRoleLabel = computed(() => currentGroup.value?.relation ?? '未选择')
const availableGroupCount = computed(() => ownedGroups.value.length + joinedGroups.value.length)
const currentContextKey = computed(() => `${authStore.currentUser?.userId ?? 'anonymous'}:${currentGroupId.value ?? 'none'}`)
const currentGroupDescription = computed(() => {
  if (currentGroup.value === null) {
    return '先锁定一个知识库空间，再开始组内检索式问答。'
  }
  return currentGroup.value.relation === 'OWNER'
    ? `当前在 OWNER 空间「${currentGroup.value.groupName}」内问答，回答仅使用该组的有效证据。`
    : `当前在 MEMBER 空间「${currentGroup.value.groupName}」内问答，回答范围同样只限制在该组。`
})
const currentRoleHint = computed(() => {
  if (currentGroup.value === null) {
    return 'OWNER 与 MEMBER 都可以问答，但必须先进入一个当前可见组。'
  }
  return currentGroup.value.relation === 'OWNER'
    ? '你当前拥有该组，可继续在文件页执行上传、删除与重建，在此页聚焦问答。'
    : '你当前是 MEMBER，可查看内容并问答，但不能上传文件或管理成员。'
})
const pageHeroDescription = computed(() =>
  currentGroup.value === null
    ? '先选择一个知识库空间，再围绕该组完成提问、回答和证据核对。'
    : `当前聚焦「${currentGroup.value.groupName}」，所有回答都严格约束在该知识库的检索结果内。`,
)

watch(
  () => authStore.currentUser?.userId,
  () => {
    void refreshGroups()
  },
  { immediate: true },
)

watch(
  [currentGroupId, () => appStore.isGroupsLoading],
  () => {
    qaContextVersion += 1
    latestAskRequestId += 1
    result.value = null
    askError.value = ''
    isSubmitting.value = false
  },
)

async function handleAsk() {
  const trimmedQuestion = question.value.trim()
  if (!canSubmit.value || currentGroupId.value === null) {
    askError.value = '请先选择群组后再提问。'
    return
  }
  if (trimmedQuestion.length === 0) {
    askError.value = '请输入问题。'
    return
  }
  const contextVersion = qaContextVersion
  const contextKey = currentContextKey.value
  const requestId = ++latestAskRequestId
  isSubmitting.value = true
  askError.value = ''
  try {
    const nextResult = await askQuestion({
      groupId: currentGroupId.value,
      question: trimmedQuestion,
    })
    if (!isActiveAskRequest(contextVersion, contextKey, requestId)) return
    result.value = nextResult
  } catch (error) {
    if (!isActiveAskRequest(contextVersion, contextKey, requestId)) return
    result.value = null
    askError.value = extractApiError(error, '问答请求失败')
  } finally {
    if (isActiveAskRequest(contextVersion, contextKey, requestId)) {
      isSubmitting.value = false
    }
  }
}

async function refreshGroups() {
  isGroupsRefreshing.value = true
  groupError.value = ''
  try {
    const groupQueryResult = await fetchGroups()
    appStore.applyGroupQueryResult(groupQueryResult)
  } catch (error) {
    appStore.resetGroupContext(false)
    groupError.value = extractApiError(error, '获取问答空间失败')
  } finally {
    isGroupsRefreshing.value = false
  }
}

function selectGroup(groupId: number) {
  if (groupId === appStore.currentGroupId) {
    return
  }
  appStore.setCurrentGroupId(groupId)
}

function isActiveAskRequest(contextVersion: number, contextKey: string, requestId: number) {
  return (
    contextVersion === qaContextVersion &&
    contextKey === currentContextKey.value &&
    requestId === latestAskRequestId &&
    !appStore.isGroupsLoading
  )
}
</script>

<template>
  <WorkbenchShell class="page-shell--qa">
    <template #sidebar>
      <WorkbenchSidebar />
    </template>

    <template #main>
      <main class="qa-page">
        <PageHeaderHero eyebrow="Answer Studio" title="问答工作台" :description="pageHeroDescription">
          <template #actions>
            <div class="qa-page__hero-actions">
              <div class="qa-page__hero-context">
                <span>当前空间</span>
                <strong>{{ currentGroup?.groupName ?? '等待选择知识库' }}</strong>
              </div>
              <div class="qa-page__hero-context">
                <span>当前角色</span>
                <strong>{{ currentRoleLabel }}</strong>
              </div>
              <div class="qa-page__hero-context">
                <span>可见组数</span>
                <strong>{{ availableGroupCount }}</strong>
              </div>
              <div class="qa-page__hero-context">
                <span>待处理邀请</span>
                <strong>{{ pendingInvitationCount }}</strong>
              </div>
            </div>
          </template>
        </PageHeaderHero>

        <section class="qa-page__layout">
          <QaPromptPanel
            :current-group="currentGroup"
            :owned-groups="ownedGroups"
            :joined-groups="joinedGroups"
            :current-group-id="currentGroupId"
            :question="question"
            :question-length="questionLength"
            :has-groups="hasGroups"
            :is-submitting="isSubmitting"
            :can-submit="canSubmit"
            :is-groups-refreshing="isGroupsRefreshing"
            :group-error="groupError"
            :current-group-description="currentGroupDescription"
            :current-role-hint="currentRoleHint"
            @update:question="question = $event"
            @refresh-groups="refreshGroups"
            @select-group="selectGroup"
            @submit="handleAsk"
          />

          <QaConversationPanel
            :current-group-name="currentGroup?.groupName ?? '未选择知识库'"
            :current-question="question"
            :result="result"
            :ask-error="askError"
            :is-groups-loading="appStore.isGroupsLoading"
            :current-group-id="currentGroupId"
            :is-submitting="isSubmitting"
          />
        </section>
      </main>
    </template>
  </WorkbenchShell>
</template>
