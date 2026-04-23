<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  acceptInvitation,
  approveJoinRequest,
  createGroup,
  createInvitation,
  fetchMyJoinRequests,
  fetchGroupMembers,
  fetchGroups,
  fetchOwnerJoinRequests,
  leaveGroup,
  rejectInvitation,
  rejectJoinRequest,
  removeGroupMember,
  submitJoinRequest,
  type GroupMemberItem,
  type JoinRequestItem,
  type OwnerJoinRequestItem,
} from '../../api/group'
import { extractApiError } from '../../api/http'
import AccountPasswordForm from '../../components/AccountPasswordForm.vue'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import { useAppStore } from '../../stores/app'
import { useAuthStore } from '../../stores/auth'
import GroupHomeCurrentWorkspacePanel from './components/GroupHomeCurrentWorkspacePanel.vue'
import GroupHomeOnboardingPanel from './components/GroupHomeOnboardingPanel.vue'
import GroupHomeOverviewPanel from './components/GroupHomeOverviewPanel.vue'
import GroupWorkspaceSidebar from './components/GroupWorkspaceSidebar.vue'
import {
  hasWorkspaceItems,
  isSameSelection,
  resolveWorkspaceSelection,
  selectFirstByFocus,
  type WorkspaceNodeType,
  type WorkspaceSelection,
} from './groupWorkspaceView'
import '../../assets/page-shell.css'
import '../../assets/groups-page.css'

const appStore = useAppStore()
const authStore = useAuthStore()

const ownedGroups = computed(() => appStore.ownedGroups)
const joinedGroups = computed(() => appStore.joinedGroups)
const pendingInvitations = computed(() => appStore.pendingInvitations)
const selectedNode = ref<WorkspaceSelection | null>(null)
const groupMembers = ref<GroupMemberItem[]>([])
const myJoinRequests = ref<JoinRequestItem[]>([])
const ownerJoinRequests = ref<OwnerJoinRequestItem[]>([])
const isWorkspaceLoading = ref(false)
const isMembersLoading = ref(false)
const isMyRequestsLoading = ref(false)
const isOwnerRequestsLoading = ref(false)
const isSecurityPanelOpen = ref(false)
const isCreatingGroup = ref(false)
const isCreateComposerOpen = ref(false)
const isInviting = ref(false)
const isSubmittingJoinRequest = ref(false)
const invitationActionIds = ref<Set<number>>(new Set())
const joinRequestActionIds = ref<Set<number>>(new Set())
const leavingGroupIds = ref<Set<number>>(new Set())
const removingMemberKeys = ref<Set<string>>(new Set())
const createGroupName = ref('')
const createGroupDescription = ref('')
const inviteeUserId = ref('')
const joinGroupCode = ref('')
const pageFeedback = ref('')
const pageError = ref('')
const mainDetailAnchor = ref<HTMLElement | null>(null)
const securityPanelAnchor = ref<HTMLElement | null>(null)

const workspaceCollections = computed(() => ({
  pendingInvitations: pendingInvitations.value,
  ownedGroups: ownedGroups.value,
  joinedGroups: joinedGroups.value,
}))
const activeSection = computed<WorkspaceNodeType | ''>(() => selectedNode.value?.type ?? '')
const hasAnyWorkspaceItem = computed(() => hasWorkspaceItems(workspaceCollections.value))
const currentUserLabel = computed(() => authStore.currentUser?.displayName ?? '未识别用户')
const shouldShowOnboarding = computed(
  () => !isCreateComposerOpen.value && (!hasAnyWorkspaceItem.value || selectedNode.value === null),
)
const selectedInvitation = computed(() =>
  selectedNode.value?.type === 'invitation'
    ? pendingInvitations.value.find((item) => item.invitationId === selectedNode.value?.id) ?? null
    : null,
)
const selectedOwnedGroup = computed(() =>
  selectedNode.value?.type === 'ownedGroup'
    ? ownedGroups.value.find((item) => item.groupId === selectedNode.value?.id) ?? null
    : null,
)
const selectedJoinedGroup = computed(() =>
  selectedNode.value?.type === 'joinedGroup'
    ? joinedGroups.value.find((item) => item.groupId === selectedNode.value?.id) ?? null
    : null,
)
const selectedOwnerMemberCount = computed(() =>
  selectedOwnedGroup.value === null
    ? '未选中'
    : isMembersLoading.value
      ? '成员同步中'
      : `${groupMembers.value.length} 名成员`,
)
const selectedMemberMessage = computed(() => {
  if (selectedJoinedGroup.value === null) {
    return ''
  }
  return `你当前是成员，仅可查看「${selectedJoinedGroup.value.groupName}」中的内容。`
})
const totalPendingCount = computed(
  () => pendingInvitations.value.length + myJoinRequests.value.length + ownerJoinRequests.value.length,
)
const currentUserIdLabel = computed(() => authStore.currentUser?.userId?.toString() ?? '--')
const pageHeroDescription = computed(() =>
  shouldShowOnboarding.value
    ? '首页先回答“当前在哪”和“下一步做什么”，再进入具体知识库动作。'
    : '左侧选择组对象，中间直接进入组详情与成员动作，不再展示额外上下文栏。',
)

watch(
  () => authStore.currentUser?.userId,
  () => {
    resetTransientState()
    void refreshWorkspace(null)
  },
  { immediate: true },
)

async function refreshWorkspace(preferredSelection: WorkspaceSelection | null = selectedNode.value) {
  isWorkspaceLoading.value = true
  isMyRequestsLoading.value = true
  pageError.value = ''
  try {
    const [result, requests] = await Promise.all([fetchGroups(), fetchMyJoinRequests()])
    appStore.applyGroupQueryResult(result)
    myJoinRequests.value = requests
    const nextSelection = resolveWorkspaceSelection(result, preferredSelection)
    await setSelectedNode(nextSelection)
  } catch (error) {
    selectedNode.value = null
    groupMembers.value = []
    myJoinRequests.value = []
    ownerJoinRequests.value = []
    pageError.value = extractApiError(error, '同步组工作台失败')
  } finally {
    isWorkspaceLoading.value = false
    isMyRequestsLoading.value = false
  }
}

async function setSelectedNode(nextSelection: WorkspaceSelection | null) {
  if (isSameSelection(selectedNode.value, nextSelection)) {
    if (nextSelection?.type === 'ownedGroup') {
      await loadOwnerGroupDetails(nextSelection.id)
    }
    return
  }

  selectedNode.value = nextSelection
  isCreateComposerOpen.value = false

  if (nextSelection?.type === 'ownedGroup') {
    appStore.setCurrentGroupId(nextSelection.id)
    await loadOwnerGroupDetails(nextSelection.id)
    return
  }

  groupMembers.value = []
  isMembersLoading.value = false
  ownerJoinRequests.value = []
  isOwnerRequestsLoading.value = false

  if (nextSelection?.type === 'joinedGroup') {
    appStore.setCurrentGroupId(nextSelection.id)
  }
}

function resetTransientState() {
  selectedNode.value = null
  groupMembers.value = []
  myJoinRequests.value = []
  ownerJoinRequests.value = []
  isMembersLoading.value = false
  isMyRequestsLoading.value = false
  isOwnerRequestsLoading.value = false
  isCreateComposerOpen.value = false
  isInviting.value = false
  isSubmittingJoinRequest.value = false
  createGroupName.value = ''
  createGroupDescription.value = ''
  inviteeUserId.value = ''
  joinGroupCode.value = ''
  invitationActionIds.value = new Set()
  joinRequestActionIds.value = new Set()
  leavingGroupIds.value = new Set()
  removingMemberKeys.value = new Set()
  pageFeedback.value = ''
  pageError.value = ''
}

async function loadOwnerGroupDetails(groupId: number) {
  await Promise.all([loadMembers(groupId), loadOwnerJoinRequests(groupId)])
}

async function loadMembers(groupId: number) {
  isMembersLoading.value = true
  pageError.value = ''
  try {
    groupMembers.value = await fetchGroupMembers(groupId)
  } catch (error) {
    groupMembers.value = []
    pageError.value = extractApiError(error, '加载成员列表失败')
  } finally {
    isMembersLoading.value = false
  }
}

async function loadOwnerJoinRequests(groupId: number) {
  isOwnerRequestsLoading.value = true
  pageError.value = ''
  try {
    ownerJoinRequests.value = await fetchOwnerJoinRequests(groupId)
  } catch (error) {
    ownerJoinRequests.value = []
    pageError.value = extractApiError(error, '加载待审批申请失败')
  } finally {
    isOwnerRequestsLoading.value = false
  }
}

async function handleSelectNode(nextSelection: WorkspaceSelection) {
  pageError.value = ''
  pageFeedback.value = ''
  await setSelectedNode(nextSelection)
  await scrollToMainDetail()
}

async function handleSummaryFocus(section: WorkspaceNodeType) {
  const nextSelection = selectFirstByFocus(workspaceCollections.value, section)
  if (nextSelection === null) {
    pageFeedback.value =
      section === 'invitation'
        ? '当前没有待处理邀请。'
        : section === 'ownedGroup'
          ? '当前没有你拥有的组。'
          : '当前没有你加入的组。'
    scrollSection(section)
    return
  }

  pageFeedback.value = ''
  pageError.value = ''
  scrollSection(section)
  await setSelectedNode(nextSelection)
  await scrollToMainDetail()
}

function scrollSection(section: WorkspaceNodeType) {
  const targetId =
    section === 'invitation'
      ? 'groups-sidebar-invitation'
      : section === 'ownedGroup'
        ? 'groups-sidebar-owned'
        : 'groups-sidebar-joined'
  document.getElementById(targetId)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

async function scrollToMainDetail() {
  await nextTick()
  mainDetailAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function scrollToSecurityPanel() {
  await nextTick()
  securityPanelAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function openCreateComposer() {
  isCreateComposerOpen.value = true
  pageError.value = ''
  pageFeedback.value = ''
  await scrollToMainDetail()
}

function closeCreateComposer() {
  isCreateComposerOpen.value = false
}

async function openSecurityPanel() {
  isSecurityPanelOpen.value = true
  pageError.value = ''
  pageFeedback.value = ''
  await scrollToSecurityPanel()
}

function closeSecurityPanel() {
  isSecurityPanelOpen.value = false
}

async function handleCreateGroup() {
  if (createGroupName.value.trim().length === 0) {
    pageError.value = '请输入组名称。'
    return
  }

  isCreatingGroup.value = true
  pageError.value = ''
  pageFeedback.value = ''
  try {
    const groupId = await createGroup({
      name: createGroupName.value.trim(),
      description: createGroupDescription.value.trim() || undefined,
    })
    createGroupName.value = ''
    createGroupDescription.value = ''
    isCreateComposerOpen.value = false
    await refreshWorkspace({ type: 'ownedGroup', id: groupId })
    pageFeedback.value = `已创建新组 #${groupId}。`
  } catch (error) {
    pageError.value = extractApiError(error, '创建组失败')
  } finally {
    isCreatingGroup.value = false
  }
}

async function handleInvitationDecision(invitationId: number, action: 'accept' | 'reject') {
  const invitation = pendingInvitations.value.find((item) => item.invitationId === invitationId) ?? null
  const nextActionIds = new Set(invitationActionIds.value)
  nextActionIds.add(invitationId)
  invitationActionIds.value = nextActionIds
  pageError.value = ''
  pageFeedback.value = ''

  try {
    if (action === 'accept') {
      await acceptInvitation(invitationId)
      await refreshWorkspace(invitation ? { type: 'joinedGroup', id: invitation.groupId } : null)
      pageFeedback.value = '邀请已接受。'
    } else {
      await rejectInvitation(invitationId)
      await refreshWorkspace(selectedNode.value?.id === invitationId ? null : selectedNode.value)
      pageFeedback.value = '邀请已拒绝。'
    }
  } catch (error) {
    pageError.value = extractApiError(error, action === 'accept' ? '接受邀请失败' : '拒绝邀请失败')
  } finally {
    const finalActionIds = new Set(invitationActionIds.value)
    finalActionIds.delete(invitationId)
    invitationActionIds.value = finalActionIds
  }
}

async function handleInviteMember() {
  if (selectedOwnedGroup.value === null) {
    pageError.value = '请先选择一个你拥有的组。'
    return
  }

  const parsedUserId = Number(inviteeUserId.value)
  if (!Number.isInteger(parsedUserId) || parsedUserId <= 0) {
    pageError.value = '请输入合法的用户 ID。'
    return
  }

  isInviting.value = true
  pageError.value = ''
  pageFeedback.value = ''
  try {
    const invitationId = await createInvitation(selectedOwnedGroup.value.groupId, parsedUserId)
    inviteeUserId.value = ''
    await refreshWorkspace(selectedNode.value)
    pageFeedback.value = `已发出邀请 #${invitationId}。`
  } catch (error) {
    pageError.value = extractApiError(error, '发起邀请失败')
  } finally {
    isInviting.value = false
  }
}

async function handleSubmitJoinRequest() {
  const groupCode = joinGroupCode.value.trim()
  if (groupCode.length === 0) {
    pageError.value = '请输入组织 ID。'
    return
  }

  isSubmittingJoinRequest.value = true
  pageError.value = ''
  pageFeedback.value = ''
  try {
    const requestId = await submitJoinRequest(groupCode)
    joinGroupCode.value = ''
    myJoinRequests.value = await fetchMyJoinRequests()
    pageFeedback.value = `已提交加入申请 #${requestId}，等待所有者审批。`
  } catch (error) {
    pageError.value = extractApiError(error, '提交加入申请失败')
  } finally {
    isSubmittingJoinRequest.value = false
  }
}

async function handleJoinRequestDecision(requestId: number, action: 'approve' | 'reject') {
  if (selectedOwnedGroup.value === null) {
    return
  }

  const groupId = selectedOwnedGroup.value.groupId
  const nextActionIds = new Set(joinRequestActionIds.value)
  nextActionIds.add(requestId)
  joinRequestActionIds.value = nextActionIds
  pageError.value = ''
  pageFeedback.value = ''

  try {
    if (action === 'approve') {
      await approveJoinRequest(groupId, requestId)
      pageFeedback.value = '已通过加入申请。'
    } else {
      await rejectJoinRequest(groupId, requestId)
      pageFeedback.value = '已拒绝加入申请。'
    }
    await loadOwnerGroupDetails(groupId)
  } catch (error) {
    pageError.value = extractApiError(error, action === 'approve' ? '通过申请失败' : '拒绝申请失败')
  } finally {
    const finalActionIds = new Set(joinRequestActionIds.value)
    finalActionIds.delete(requestId)
    joinRequestActionIds.value = finalActionIds
  }
}

async function handleRemoveMember(userId: number) {
  if (selectedOwnedGroup.value === null) {
    return
  }

  const memberKey = `${selectedOwnedGroup.value.groupId}:${userId}`
  const nextRemovingKeys = new Set(removingMemberKeys.value)
  nextRemovingKeys.add(memberKey)
  removingMemberKeys.value = nextRemovingKeys
  pageError.value = ''
  pageFeedback.value = ''

  try {
    await removeGroupMember(selectedOwnedGroup.value.groupId, userId)
    await loadMembers(selectedOwnedGroup.value.groupId)
    await refreshWorkspace(selectedNode.value)
    pageFeedback.value = `已移除成员 #${userId}。`
  } catch (error) {
    pageError.value = extractApiError(error, '移除成员失败')
  } finally {
    const finalRemovingKeys = new Set(removingMemberKeys.value)
    finalRemovingKeys.delete(memberKey)
    removingMemberKeys.value = finalRemovingKeys
  }
}

async function handleLeaveGroup(groupId: number) {
  const nextLeavingIds = new Set(leavingGroupIds.value)
  nextLeavingIds.add(groupId)
  leavingGroupIds.value = nextLeavingIds
  pageError.value = ''
  pageFeedback.value = ''

  try {
    await leaveGroup(groupId)
    await refreshWorkspace(selectedNode.value?.type === 'joinedGroup' && selectedNode.value.id === groupId ? null : selectedNode.value)
    pageFeedback.value = `已退出群组 #${groupId}。`
  } catch (error) {
    pageError.value = extractApiError(error, '退出群组失败')
  } finally {
    const finalLeavingIds = new Set(leavingGroupIds.value)
    finalLeavingIds.delete(groupId)
    leavingGroupIds.value = finalLeavingIds
  }
}
</script>

<template>
  <WorkbenchShell class="page-shell--groups">
    <template #sidebar>
      <WorkbenchSidebar />
    </template>

    <template #main>
      <main class="groups-page">
        <PageHeaderHero eyebrow="协作小组" title="我的组" :description="pageHeroDescription">
          <template #actions>
            <div class="groups-page__hero-actions">
              <div class="groups-page__identity">
                <span>当前用户</span>
                <strong>{{ currentUserLabel }}</strong>
              </div>
              <div class="groups-page__identity">
                <span>我的用户 ID</span>
                <strong>{{ currentUserIdLabel }}</strong>
              </div>
            </div>
          </template>
        </PageHeaderHero>

        <section
          v-if="isSecurityPanelOpen"
          ref="securityPanelAnchor"
          class="inline-security-panel"
        >
          <div class="inline-security-panel__header">
            <div>
              <p class="panel__eyebrow">账户安全</p>
              <h2>账户安全</h2>
            </div>
            <button class="ghost-button" type="button" @click="closeSecurityPanel">返回我的组</button>
          </div>
          <AccountPasswordForm inline @completed="closeSecurityPanel" />
        </section>

        <div class="groups-detail__feedback">
          <p v-if="pageFeedback" class="feedback feedback--success">{{ pageFeedback }}</p>
          <p v-if="pageError" class="feedback feedback--error">{{ pageError }}</p>
        </div>

        <section class="groups-page__workspace">
          <div class="groups-page__sidebar-panel">
            <GroupWorkspaceSidebar
              :pending-invitations="pendingInvitations"
              :owned-groups="ownedGroups"
              :joined-groups="joinedGroups"
              :selected-node="selectedNode"
              :is-loading="isWorkspaceLoading"
              @select="handleSelectNode"
            />
          </div>

          <div class="groups-page__main-panel">
            <div
              v-if="isCreateComposerOpen"
              id="group-detail-panel"
              ref="mainDetailAnchor"
            >
              <GroupHomeCurrentWorkspacePanel
                :is-create-composer-open="isCreateComposerOpen"
                :is-creating-group="isCreatingGroup"
                :create-group-name="createGroupName"
                :create-group-description="createGroupDescription"
                :selected-invitation="selectedInvitation"
                :selected-owned-group="selectedOwnedGroup"
                :selected-joined-group="selectedJoinedGroup"
                :selected-owner-member-count="selectedOwnerMemberCount"
                :selected-member-message="selectedMemberMessage"
                :group-members="groupMembers"
                :owner-join-requests="ownerJoinRequests"
                :is-members-loading="isMembersLoading"
                :is-owner-requests-loading="isOwnerRequestsLoading"
                :is-inviting="isInviting"
                :invitee-user-id="inviteeUserId"
                :invitation-action-ids="invitationActionIds"
                :join-request-action-ids="joinRequestActionIds"
                :removing-member-keys="removingMemberKeys"
                :leaving-group-ids="leavingGroupIds"
                @close-create="closeCreateComposer"
                @create-group="handleCreateGroup"
                @invite-member="handleInviteMember"
                @invitation-decision="handleInvitationDecision"
                @join-request-decision="handleJoinRequestDecision"
                @remove-member="handleRemoveMember"
                @leave-group="handleLeaveGroup"
                @update:create-group-name="createGroupName = $event"
                @update:create-group-description="createGroupDescription = $event"
                @update:invitee-user-id="inviteeUserId = $event"
              />
            </div>

            <GroupHomeOnboardingPanel
              v-if="shouldShowOnboarding"
              :current-user-label="currentUserLabel"
              :has-any-workspace-item="hasAnyWorkspaceItem"
              :has-selection="selectedNode !== null"
              :owned-count="ownedGroups.length"
              :joined-count="joinedGroups.length"
              :invitation-count="pendingInvitations.length"
              :my-join-request-count="myJoinRequests.length"
              :join-group-code="joinGroupCode"
              :is-submitting-join-request="isSubmittingJoinRequest"
              :my-join-requests="myJoinRequests"
              :is-my-requests-loading="isMyRequestsLoading"
              @open-create="openCreateComposer"
              @open-security="openSecurityPanel"
              @submit-join-request="handleSubmitJoinRequest"
              @focus="handleSummaryFocus"
              @update:join-group-code="joinGroupCode = $event"
            />

            <template v-else>
              <GroupHomeOverviewPanel
                v-if="hasAnyWorkspaceItem"
                :owned-count="ownedGroups.length"
                :joined-count="joinedGroups.length"
                :invitation-count="pendingInvitations.length"
                :total-pending-count="totalPendingCount"
                :active-section="activeSection"
                :pending-invitations="pendingInvitations"
                :my-join-requests="myJoinRequests"
                :is-my-requests-loading="isMyRequestsLoading"
                :join-group-code="joinGroupCode"
                :is-submitting-join-request="isSubmittingJoinRequest"
                @focus="handleSummaryFocus"
                @create="openCreateComposer"
                @submit-join-request="handleSubmitJoinRequest"
                @update:join-group-code="joinGroupCode = $event"
              />

              <div
                v-if="!isCreateComposerOpen"
                id="group-detail-panel"
                ref="mainDetailAnchor"
              >
                <GroupHomeCurrentWorkspacePanel
                  :is-create-composer-open="isCreateComposerOpen"
                  :is-creating-group="isCreatingGroup"
                  :create-group-name="createGroupName"
                  :create-group-description="createGroupDescription"
                  :selected-invitation="selectedInvitation"
                  :selected-owned-group="selectedOwnedGroup"
                  :selected-joined-group="selectedJoinedGroup"
                  :selected-owner-member-count="selectedOwnerMemberCount"
                  :selected-member-message="selectedMemberMessage"
                  :group-members="groupMembers"
                  :owner-join-requests="ownerJoinRequests"
                  :is-members-loading="isMembersLoading"
                  :is-owner-requests-loading="isOwnerRequestsLoading"
                  :is-inviting="isInviting"
                  :invitee-user-id="inviteeUserId"
                  :invitation-action-ids="invitationActionIds"
                  :join-request-action-ids="joinRequestActionIds"
                  :removing-member-keys="removingMemberKeys"
                  :leaving-group-ids="leavingGroupIds"
                  @create-group="handleCreateGroup"
                  @invite-member="handleInviteMember"
                  @invitation-decision="handleInvitationDecision"
                  @join-request-decision="handleJoinRequestDecision"
                  @remove-member="handleRemoveMember"
                  @leave-group="handleLeaveGroup"
                  @update:create-group-name="createGroupName = $event"
                  @update:create-group-description="createGroupDescription = $event"
                  @update:invitee-user-id="inviteeUserId = $event"
                />
              </div>
            </template>
          </div>
        </section>
      </main>
    </template>
  </WorkbenchShell>
</template>
