<script setup lang="ts">
import { RouterLink } from 'vue-router'

withDefaults(
  defineProps<{
    variant?: 'aside' | 'inline'
    title: string
    subtitle: string
    badgeLabel: string
    badgeTone: 'pending' | 'owner' | 'member' | 'neutral'
    permissionLines: string[]
    pendingLabel: string
    pendingCount: number
    showPendingFocus: boolean
    showInvitationActions: boolean
    invitationActionDisabled: boolean
    showLeaveAction: boolean
    leaveActionDisabled: boolean
  }>(),
  {
    variant: 'aside',
  },
)

const emit = defineEmits<{
  openCreate: []
  openSecurity: []
  focusPending: []
  acceptInvitation: []
  rejectInvitation: []
  leaveGroup: []
}>()
</script>

<template>
  <aside class="group-context-aside" :class="[`group-context-aside--${variant}`, `is-${badgeTone}`]">
    <header class="group-context-aside__header">
      <p class="group-context-aside__eyebrow">{{ variant === 'aside' ? 'Workspace Context' : '当前上下文' }}</p>
      <div class="group-context-aside__title-row">
        <h2>{{ title }}</h2>
        <span class="group-context-aside__badge">{{ badgeLabel }}</span>
      </div>
      <p>{{ subtitle }}</p>
    </header>

    <section class="group-context-aside__section">
      <h3>权限边界</h3>
      <ul class="group-context-aside__list">
        <li v-for="line in permissionLines" :key="line">{{ line }}</li>
      </ul>
    </section>

    <section class="group-context-aside__section">
      <div class="group-context-aside__section-header">
        <h3>待办摘要</h3>
        <span v-if="pendingCount > 0" class="group-context-aside__count">{{ pendingCount }}</span>
      </div>
      <p>{{ pendingLabel }}</p>
      <button v-if="showPendingFocus" class="ghost-button" type="button" @click="emit('focusPending')">查看左栏待处理</button>
    </section>

    <section class="group-context-aside__section">
      <h3>快捷动作</h3>
      <div class="group-context-aside__actions">
        <button class="primary-button" type="button" @click="emit('openCreate')">创建组</button>
        <button class="ghost-button" type="button" @click="emit('openSecurity')">账户安全</button>
        <RouterLink class="ghost-button group-context-aside__link" to="/documents">去文档</RouterLink>
        <RouterLink class="ghost-button group-context-aside__link" to="/qa">去问答</RouterLink>
        <button
          v-if="showInvitationActions"
          class="primary-button"
          :disabled="invitationActionDisabled"
          type="button"
          @click="emit('acceptInvitation')"
        >
          接受邀请
        </button>
        <button
          v-if="showInvitationActions"
          class="ghost-button"
          :disabled="invitationActionDisabled"
          type="button"
          @click="emit('rejectInvitation')"
        >
          拒绝邀请
        </button>
        <button
          v-if="showLeaveAction"
          class="ghost-button"
          :disabled="leaveActionDisabled"
          type="button"
          @click="emit('leaveGroup')"
        >
          退出当前组
        </button>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.group-context-aside {
  display: grid;
  gap: 1rem;
  padding: 1.1rem;
  border-radius: 28px;
  border: 1px solid rgba(16, 42, 59, 0.08);
  background:
    radial-gradient(circle at top right, rgba(110, 176, 197, 0.18), transparent 16rem),
    linear-gradient(165deg, rgba(252, 254, 255, 0.98), rgba(237, 245, 248, 0.94));
  box-shadow: 0 24px 64px rgba(13, 40, 58, 0.1);
}

.group-context-aside--inline {
  padding: 1rem;
  border-radius: 24px;
}

.group-context-aside__header h2,
.group-context-aside__section h3 {
  margin: 0;
  color: #102a3b;
}

.group-context-aside__eyebrow {
  margin: 0 0 0.4rem;
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #6b8795;
}

.group-context-aside__title-row,
.group-context-aside__section-header {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: start;
}

.group-context-aside__header p:last-child,
.group-context-aside__section p,
.group-context-aside__list {
  margin: 0;
  color: #607684;
  line-height: 1.65;
}

.group-context-aside__badge,
.group-context-aside__count {
  flex-shrink: 0;
  padding: 0.24rem 0.58rem;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 700;
}

.group-context-aside.is-owner .group-context-aside__badge {
  background: rgba(25, 90, 118, 0.12);
  color: #195a76;
}

.group-context-aside.is-member .group-context-aside__badge {
  background: rgba(60, 96, 141, 0.12);
  color: #30577f;
}

.group-context-aside.is-pending .group-context-aside__badge,
.group-context-aside__count {
  background: rgba(226, 111, 60, 0.14);
  color: #9b3f1b;
}

.group-context-aside.is-neutral .group-context-aside__badge {
  background: rgba(16, 42, 59, 0.06);
  color: #4f6472;
}

.group-context-aside__section {
  display: grid;
  gap: 0.75rem;
  padding-top: 0.95rem;
  border-top: 1px solid rgba(16, 42, 59, 0.08);
}

.group-context-aside__list {
  display: grid;
  gap: 0.55rem;
  padding-left: 1rem;
}

.group-context-aside__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
}

.group-context-aside__link {
  text-decoration: none;
  text-align: center;
}

@media (max-width: 720px) {
  .group-context-aside__actions {
    grid-template-columns: 1fr;
  }
}
</style>
