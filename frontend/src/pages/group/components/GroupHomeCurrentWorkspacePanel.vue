<script setup lang="ts">
import type {
  GroupItem,
  GroupMemberItem,
  OwnerJoinRequestItem,
  PendingInvitationItem,
} from '../../../api/group'

defineProps<{
  isCreateComposerOpen: boolean
  isCreatingGroup: boolean
  createGroupName: string
  createGroupDescription: string
  selectedInvitation: PendingInvitationItem | null
  selectedOwnedGroup: GroupItem | null
  selectedJoinedGroup: GroupItem | null
  selectedOwnerMemberCount: string
  selectedMemberMessage: string
  groupMembers: GroupMemberItem[]
  ownerJoinRequests: OwnerJoinRequestItem[]
  isMembersLoading: boolean
  isOwnerRequestsLoading: boolean
  isInviting: boolean
  inviteeUserId: string
  invitationActionIds: Set<number>
  joinRequestActionIds: Set<number>
  removingMemberKeys: Set<string>
  leavingGroupIds: Set<number>
}>()

const emit = defineEmits<{
  closeCreate: []
  createGroup: []
  inviteMember: []
  invitationDecision: [invitationId: number, action: 'accept' | 'reject']
  joinRequestDecision: [requestId: number, action: 'approve' | 'reject']
  removeMember: [userId: number]
  leaveGroup: [groupId: number]
  'update:createGroupName': [value: string]
  'update:createGroupDescription': [value: string]
  'update:inviteeUserId': [value: string]
}>()

function handleCreateGroupNameInput(event: Event) {
  emit('update:createGroupName', (event.target as HTMLInputElement).value)
}

function handleCreateGroupDescriptionInput(event: Event) {
  emit('update:createGroupDescription', (event.target as HTMLTextAreaElement).value)
}

function handleInviteeUserIdInput(event: Event) {
  emit('update:inviteeUserId', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <section class="group-home-current">
    <div class="group-home-current__header">
      <div>
        <p class="panel__eyebrow">{{ isCreateComposerOpen ? '创建新组' : '组详情' }}</p>
        <h2>{{ isCreateComposerOpen ? '创建新组' : '组详情' }}</h2>
        <p>
          {{
            isCreateComposerOpen
              ? '创建动作独立展示，不再混进当前组选中详情里。'
              : '左侧负责进入组详情，这里只保留当前对象的直接信息和操作。'
          }}
        </p>
      </div>
    </div>

    <section v-if="isCreateComposerOpen" class="detail-card detail-card--composer">
      <div class="detail-card__header">
        <div>
          <p class="panel__eyebrow">创建入口</p>
          <h2>创建新组</h2>
        </div>
        <button class="ghost-button" type="button" @click="emit('closeCreate')">取消创建</button>
      </div>

      <div class="detail-card__stack">
        <label class="groups-form-field">
          <span>组名称</span>
          <input
            :value="createGroupName"
            type="text"
            maxlength="128"
            placeholder="例如：设计资料库"
            @input="handleCreateGroupNameInput"
          />
        </label>
        <label class="groups-form-field">
          <span>组描述</span>
          <textarea
            :value="createGroupDescription"
            maxlength="512"
            rows="4"
            placeholder="描述这个知识库空间的用途与边界。"
            @input="handleCreateGroupDescriptionInput"
          />
        </label>
      </div>

      <div class="detail-card__actions">
        <button class="primary-button" :disabled="isCreatingGroup" type="button" @click="emit('createGroup')">
          {{ isCreatingGroup ? '创建中...' : '创建组' }}
        </button>
      </div>
    </section>

    <section v-else-if="selectedInvitation" class="detail-card">
      <div class="detail-card__header">
        <div>
          <p class="panel__eyebrow">待处理邀请</p>
          <h2>{{ selectedInvitation.groupName }}</h2>
        </div>
      </div>

      <div class="detail-card__stack">
        <div class="detail-meta">
          <div>
            <span>邀请人</span>
            <strong>{{ selectedInvitation.inviterDisplayName }}</strong>
          </div>
          <div>
            <span>目标组</span>
            <strong>#{{ selectedInvitation.groupId }}</strong>
          </div>
          <div>
            <span>状态</span>
            <strong>{{ selectedInvitation.status }}</strong>
          </div>
        </div>
        <p class="detail-note">接受后该组会进入“我加入的组”，拒绝后会从当前列表中移除。</p>
      </div>

      <div class="detail-card__actions">
        <button
          class="primary-button"
          :disabled="invitationActionIds.has(selectedInvitation.invitationId)"
          type="button"
          @click="emit('invitationDecision', selectedInvitation.invitationId, 'accept')"
        >
          接受邀请
        </button>
        <button
          class="ghost-button"
          :disabled="invitationActionIds.has(selectedInvitation.invitationId)"
          type="button"
          @click="emit('invitationDecision', selectedInvitation.invitationId, 'reject')"
        >
          拒绝邀请
        </button>
      </div>

    </section>

    <section v-else-if="selectedOwnedGroup" class="detail-card detail-card--owner">
      <div class="detail-card__header">
        <div>
          <p class="panel__eyebrow">我拥有的组</p>
          <h2>{{ selectedOwnedGroup.groupName }}</h2>
        </div>
        <span class="panel__pill">所有者</span>
      </div>

      <div class="detail-card__stack detail-card__stack--split">
        <section class="detail-subsection">
          <h3>基础信息</h3>
          <div class="detail-meta">
            <div>
              <span>组织 ID</span>
              <strong :title="selectedOwnedGroup.groupCode">{{ selectedOwnedGroup.groupCode }}</strong>
            </div>
            <div>
              <span>内部记录 ID</span>
              <strong>#{{ selectedOwnedGroup.groupId }}</strong>
            </div>
            <div>
              <span>当前角色</span>
              <strong>所有者</strong>
            </div>
            <div>
              <span>成员数</span>
              <strong>{{ selectedOwnerMemberCount }}</strong>
            </div>
          </div>
          <p class="detail-note">你可以邀请成员、审批加入申请，并移除非所有者成员。</p>
        </section>

        <section class="detail-subsection">
          <div class="detail-subsection__header">
            <h3>成员管理</h3>
            <span class="panel__pill panel__pill--soft">当前选中组</span>
          </div>

          <label class="groups-form-field">
            <span>邀请用户 ID</span>
            <div class="groups-inline-form">
              <input
                :value="inviteeUserId"
                type="number"
                min="1"
                placeholder="例如：1003"
                @input="handleInviteeUserIdInput"
              />
              <button class="primary-button" :disabled="isInviting" type="button" @click="emit('inviteMember')">
                {{ isInviting ? '邀请中...' : '发起邀请' }}
              </button>
            </div>
          </label>
          <p class="detail-note">可让对方在“我的组”页右上角查看自己的用户 ID，再把该编号发给你。</p>

          <p v-if="isMembersLoading" class="placeholder-text">正在加载成员列表...</p>
          <ul v-else class="groups-member-list">
            <li v-for="member in groupMembers" :key="`member-${member.userId}`" class="groups-member-list__item">
              <div class="groups-member-list__profile">
                <strong>{{ member.displayName }}</strong>
                <span>
                  用户 ID：{{ member.userId }} · 用户编码：{{ member.userCode }} · 角色：{{
                    member.role === 'OWNER' ? '所有者' : member.role
                  }}
                </span>
              </div>
              <button
                v-if="member.role !== 'OWNER'"
                class="ghost-button"
                :disabled="removingMemberKeys.has(`${selectedOwnedGroup.groupId}:${member.userId}`)"
                type="button"
                @click="emit('removeMember', member.userId)"
              >
                {{ removingMemberKeys.has(`${selectedOwnedGroup.groupId}:${member.userId}`) ? '移除中...' : '移除成员' }}
              </button>
            </li>
          </ul>
        </section>

        <section class="detail-subsection">
          <div class="detail-subsection__header">
            <h3>待审批申请</h3>
            <span class="panel__pill panel__pill--pending">{{ ownerJoinRequests.length }}</span>
          </div>

          <p v-if="isOwnerRequestsLoading" class="placeholder-text">正在加载待审批申请...</p>
          <p v-else-if="ownerJoinRequests.length === 0" class="placeholder-text">当前组暂无待审批申请。</p>
          <ul v-else class="groups-member-list">
            <li
              v-for="request in ownerJoinRequests"
              :key="`owner-request-${request.requestId}`"
              class="groups-member-list__item"
            >
              <div>
                <strong>{{ request.applicantDisplayName }}</strong>
                <span>{{ new Date(request.createdAt).toLocaleString() }}</span>
              </div>
              <div class="detail-card__actions">
                <button
                  class="primary-button"
                  :disabled="joinRequestActionIds.has(request.requestId)"
                  type="button"
                  @click="emit('joinRequestDecision', request.requestId, 'approve')"
                >
                  通过
                </button>
                <button
                  class="ghost-button"
                  :disabled="joinRequestActionIds.has(request.requestId)"
                  type="button"
                  @click="emit('joinRequestDecision', request.requestId, 'reject')"
                >
                  拒绝
                </button>
              </div>
            </li>
          </ul>
        </section>

      </div>
    </section>

    <section v-else-if="selectedJoinedGroup" class="detail-card detail-card--member">
      <div class="detail-card__header">
        <div>
          <p class="panel__eyebrow">我加入的组</p>
          <h2>{{ selectedJoinedGroup.groupName }}</h2>
        </div>
        <span class="panel__pill panel__pill--member">成员</span>
      </div>

      <div class="detail-card__stack detail-card__stack--split">
        <section class="detail-subsection">
          <h3>基础信息</h3>
          <div class="detail-meta">
            <div>
              <span>组织 ID</span>
              <strong :title="selectedJoinedGroup.groupCode">{{ selectedJoinedGroup.groupCode }}</strong>
            </div>
            <div>
              <span>内部记录 ID</span>
              <strong>#{{ selectedJoinedGroup.groupId }}</strong>
            </div>
            <div>
              <span>当前角色</span>
              <strong>成员</strong>
            </div>
          </div>
        </section>

        <section class="detail-subsection">
          <h3>权限边界</h3>
          <ul class="permissions-list">
            <li>{{ selectedMemberMessage }}</li>
            <li>你不能邀请成员或移除成员。</li>
            <li>如需管理权限，请联系该组的所有者。</li>
          </ul>
        </section>

      </div>

      <div class="detail-card__actions">
        <button
          class="ghost-button"
          :disabled="leavingGroupIds.has(selectedJoinedGroup.groupId)"
          type="button"
          @click="emit('leaveGroup', selectedJoinedGroup.groupId)"
        >
          {{ leavingGroupIds.has(selectedJoinedGroup.groupId) ? '退出中...' : '退出该组' }}
        </button>
      </div>
    </section>

    <section v-else class="detail-empty">
      <p class="detail-empty__eyebrow">尚未选择</p>
      <h2>请选择左侧一个组或邀请</h2>
      <p>选择左侧对象后，这里会显示该组或邀请的详情与可执行操作。</p>
    </section>
  </section>
</template>

<style scoped>
.group-home-current {
  display: grid;
  gap: 1rem;
}

.group-home-current__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
}

.group-home-current__header h2 {
  margin: 0;
  color: #102a3b;
}

.group-home-current__header p:last-child {
  margin: 0.45rem 0 0;
  color: #607684;
  line-height: 1.65;
  max-width: 38rem;
}

.group-home-current__request-list {
  display: grid;
  gap: 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.group-home-current__request-list li {
  display: grid;
  gap: 0.18rem;
  padding: 0.8rem 0.9rem;
  border-radius: 18px;
  border: 1px solid rgba(16, 42, 59, 0.08);
  background: rgba(255, 255, 255, 0.84);
}

.group-home-current__request-list strong {
  color: #102a3b;
}

.group-home-current__request-list span {
  color: #607684;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .group-home-current__header {
    flex-direction: column;
  }
}
</style>
