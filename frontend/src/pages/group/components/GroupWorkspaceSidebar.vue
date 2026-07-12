<script setup lang="ts">
import type { GroupItem, PendingInvitationItem } from '../../../api/group'
import type { WorkspaceSelection } from '../groupWorkspaceView'

defineProps<{
  pendingInvitations: PendingInvitationItem[]
  ownedGroups: GroupItem[]
  joinedGroups: GroupItem[]
  selectedNode: WorkspaceSelection | null
  isLoading: boolean
}>()

const emit = defineEmits<{
  select: [selection: WorkspaceSelection]
}>()

function handleSelect(selection: WorkspaceSelection) {
  emit('select', selection)
}
</script>

<template>
  <aside class="workspace-sidebar">
    <section id="groups-sidebar-invitation" class="workspace-sidebar__section workspace-sidebar__section--pending">
      <div class="workspace-sidebar__header">
        <div class="workspace-sidebar__heading">
          <p class="workspace-sidebar__eyebrow">待处理</p>
          <h3>待处理邀请</h3>
        </div>
        <span class="workspace-sidebar__count">{{ pendingInvitations.length }}</span>
      </div>

      <div v-if="pendingInvitations.length > 0" class="workspace-sidebar__list workspace-sidebar__list--pending">
        <button
          v-for="invitation in pendingInvitations"
          :key="`invitation-${invitation.invitationId}`"
          class="workspace-item workspace-item--pending"
          :class="{ 'is-selected': selectedNode?.type === 'invitation' && selectedNode.id === invitation.invitationId }"
          type="button"
          @click="handleSelect({ type: 'invitation', id: invitation.invitationId })"
        >
          <div class="workspace-item__title-row">
            <strong>{{ invitation.groupName }}</strong>
            <span class="workspace-item__badge">待处理</span>
          </div>
          <p>来自 {{ invitation.inviterDisplayName }}</p>
        </button>
      </div>
      <p v-else class="workspace-sidebar__empty">暂无待处理邀请。</p>
    </section>

    <section id="groups-sidebar-owned" class="workspace-sidebar__section">
      <div class="workspace-sidebar__header">
        <div class="workspace-sidebar__heading">
          <p class="workspace-sidebar__eyebrow">我拥有的组</p>
          <h3>我拥有的组</h3>
        </div>
        <span class="workspace-sidebar__count">{{ ownedGroups.length }}</span>
      </div>

      <div v-if="ownedGroups.length > 0" class="workspace-sidebar__list">
        <article
          v-for="group in ownedGroups"
          :key="`owned-${group.groupId}`"
          class="workspace-item"
          :class="{ 'is-selected': selectedNode?.type === 'ownedGroup' && selectedNode.id === group.groupId }"
        >
          <div class="workspace-item__title-row">
            <strong>{{ group.groupName }}</strong>
            <span class="workspace-item__role">所有者</span>
          </div>
          <p class="workspace-item__code" :title="group.groupCode">组织 ID：{{ group.groupCode }}</p>
          <div class="workspace-item__actions">
            <button
              class="workspace-item__action-button"
              type="button"
              @click="handleSelect({ type: 'ownedGroup', id: group.groupId })"
            >
              管理
            </button>
          </div>
        </article>
      </div>
      <p v-else class="workspace-sidebar__empty">当前没有你拥有的组。</p>
    </section>

    <section id="groups-sidebar-joined" class="workspace-sidebar__section">
      <div class="workspace-sidebar__header">
        <div class="workspace-sidebar__heading">
          <p class="workspace-sidebar__eyebrow">我加入的组</p>
          <h3>我加入的组</h3>
        </div>
        <span class="workspace-sidebar__count">{{ joinedGroups.length }}</span>
      </div>

      <div v-if="joinedGroups.length > 0" class="workspace-sidebar__list">
        <article
          v-for="group in joinedGroups"
          :key="`joined-${group.groupId}`"
          class="workspace-item"
          :class="{ 'is-selected': selectedNode?.type === 'joinedGroup' && selectedNode.id === group.groupId }"
        >
          <div class="workspace-item__title-row">
            <strong>{{ group.groupName }}</strong>
            <span class="workspace-item__role workspace-item__role--member">成员</span>
          </div>
          <p class="workspace-item__code" :title="group.groupCode">组织 ID：{{ group.groupCode }}</p>
          <div class="workspace-item__actions">
            <button
              class="workspace-item__action-button workspace-item__action-button--member"
              type="button"
              @click="handleSelect({ type: 'joinedGroup', id: group.groupId })"
            >
              查看详情
            </button>
          </div>
        </article>
      </div>
      <p v-else class="workspace-sidebar__empty">当前没有你加入的组。</p>
    </section>

    <p v-if="isLoading" class="workspace-sidebar__hint">正在同步当前用户的组数据。</p>
  </aside>
</template>

<style scoped>
.workspace-sidebar {
  display: grid;
  gap: 0.85rem;
  max-height: calc(100dvh - 5.5rem);
  overflow-x: hidden;
  overflow-y: auto;
  padding: 0.95rem;
  border: 1px solid rgba(16, 42, 59, 0.1);
  border-radius: 0.8rem;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(243, 249, 252, 0.94));
  box-shadow: 0 12px 28px rgba(13, 40, 58, 0.06);
}

.workspace-sidebar__section {
  display: grid;
  gap: 0.7rem;
  min-width: 0;
}

.workspace-sidebar__section--pending {
  padding-bottom: 0.75rem;
  border-bottom: 1px solid rgba(25, 90, 118, 0.12);
}

.workspace-sidebar__header {
  display: flex;
  justify-content: space-between;
  gap: 0.65rem;
  align-items: flex-start;
  min-width: 0;
}

.workspace-sidebar__heading {
  display: grid;
  gap: 0.18rem;
  min-width: 0;
  flex: 1 1 auto;
}

.workspace-sidebar__eyebrow {
  margin: 0;
  padding: 0;
  max-width: 100%;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  line-height: 1.4;
  text-transform: none;
  color: #5f7686;
  overflow: visible;
  white-space: normal;
}

.workspace-sidebar__header h3 {
  margin: 0;
  font-size: 0.98rem;
  line-height: 1.35;
  color: #102a3b;
  overflow-wrap: anywhere;
}

.workspace-sidebar__count {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.7rem;
  min-height: 1.55rem;
  padding: 0.2rem 0.5rem;
  border-radius: 0.45rem;
  background: rgba(16, 42, 59, 0.07);
  font-size: 0.76rem;
  font-weight: 700;
  line-height: 1.2;
  color: #355466;
}

.workspace-sidebar__list {
  display: grid;
  gap: 0.5rem;
  min-width: 0;
}

.workspace-item {
  position: relative;
  display: grid;
  gap: 0.32rem;
  width: 100%;
  min-width: 0;
  padding: 0.78rem 0.82rem 0.78rem 0.95rem;
  border: 1px solid rgba(16, 42, 59, 0.08);
  border-radius: 0.65rem;
  background: rgba(255, 255, 255, 0.88);
  text-align: left;
  color: #102a3b;
  overflow: visible;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.workspace-item::before {
  content: '';
  position: absolute;
  top: 0.55rem;
  bottom: 0.55rem;
  left: 0;
  width: 3px;
  border-radius: 999px;
  background: transparent;
}

.workspace-item:hover {
  transform: translateY(-1px);
  border-color: rgba(25, 90, 118, 0.2);
}

.workspace-item.is-selected {
  border-color: rgba(25, 90, 118, 0.26);
  background: linear-gradient(145deg, rgba(243, 250, 255, 0.98), rgba(232, 243, 249, 0.96));
  box-shadow: 0 10px 22px rgba(16, 42, 59, 0.08);
}

.workspace-item.is-selected::before {
  background: #195a76;
}

.workspace-item--pending {
  background: linear-gradient(145deg, rgba(255, 249, 242, 0.98), rgba(255, 243, 234, 0.94));
}

.workspace-item--pending.is-selected::before {
  background: #c05f30;
}

.workspace-item__title-row {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  align-items: flex-start;
  min-width: 0;
}

.workspace-item strong {
  min-width: 0;
  flex: 1 1 auto;
  font-size: 0.92rem;
  line-height: 1.4;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.workspace-item p {
  margin: 0;
  color: #607684;
  line-height: 1.5;
  font-size: 0.82rem;
  overflow-wrap: anywhere;
}

.workspace-item__code {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-item__badge,
.workspace-item__role {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  min-height: 1.35rem;
  padding: 0.12rem 0.42rem;
  border-radius: 0.4rem;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1.25;
  white-space: nowrap;
}

.workspace-item__actions {
  display: flex;
  justify-content: flex-start;
  margin-top: 0.2rem;
}

.workspace-item__action-button {
  border: 1px solid rgba(25, 90, 118, 0.16);
  border-radius: 0.45rem;
  background: rgba(25, 90, 118, 0.1);
  color: #195a76;
  padding: 0.38rem 0.72rem;
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1.3;
  cursor: pointer;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease;
}

.workspace-item__action-button:hover {
  background: rgba(25, 90, 118, 0.14);
  border-color: rgba(25, 90, 118, 0.24);
  transform: translateY(-1px);
}

.workspace-item__action-button--member {
  background: rgba(60, 96, 141, 0.08);
  border-color: rgba(60, 96, 141, 0.14);
  color: #30577f;
}

.workspace-item__action-button--member:hover {
  background: rgba(60, 96, 141, 0.12);
  border-color: rgba(60, 96, 141, 0.22);
}

.workspace-item__badge {
  background: rgba(226, 111, 60, 0.14);
  color: #9b3f1b;
}

.workspace-item__role {
  background: rgba(25, 90, 118, 0.12);
  color: #195a76;
}

.workspace-item__role--member {
  background: rgba(60, 96, 141, 0.1);
  color: #30577f;
}

.workspace-sidebar__empty,
.workspace-sidebar__hint {
  margin: 0;
  padding: 0.7rem 0.75rem;
  border-radius: 0.55rem;
  border: 1px dashed rgba(16, 42, 59, 0.12);
  background: rgba(248, 251, 253, 0.9);
  color: #607684;
  font-size: 0.84rem;
  line-height: 1.5;
}

@media (max-width: 1080px) {
  .workspace-sidebar {
    max-height: none;
    overflow: visible;
  }
}
</style>
