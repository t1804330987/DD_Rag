<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import SessionLogoutButton from '../SessionLogoutButton.vue'
import { useAuthStore } from '../../stores/auth'

interface NavItem {
  to: string
  label: string
  caption: string
}

const route = useRoute()
const authStore = useAuthStore()

const businessNavItems: NavItem[] = [
  { to: '/groups', label: '我的组', caption: '建组、成员与申请' },
  { to: '/documents', label: '文档中心', caption: '上传、索引与预览' },
  { to: '/qa', label: '知识问答', caption: '单轮提问与证据核对' },
  { to: '/assistant', label: '个人助手', caption: '多轮对话与检索' },
  { to: '/ai-settings', label: 'AI 设置', caption: '模型、指令与用量' },
]

const adminNavItems: NavItem[] = [
  { to: '/admin/overview', label: '后台首页', caption: '总览与快捷入口' },
  { to: '/admin/users', label: '用户管理', caption: '账号与角色' },
  { to: '/admin/model-governance', label: '模型治理', caption: '连接、授权与用量' },
]

const navItems = computed(() => (authStore.isAdmin ? adminNavItems : businessNavItems))
const currentUser = computed(() => authStore.currentUser)
const roleLabel = computed(() => (authStore.isAdmin ? '系统管理员' : '业务用户'))
const accountSummary = computed(() => currentUser.value?.userCode ?? '未加载账号')
const statusLabel = computed(() => {
  if (currentUser.value === null) {
    return '正在同步当前账号'
  }
  return currentUser.value.mustChangePassword ? '需尽快修改密码' : '当前会话正常'
})

function isActive(targetPath: string) {
  return route.path === targetPath || route.path.startsWith(`${targetPath}/`)
}
</script>

<template>
  <div class="workbench-sidebar">
    <RouterLink class="workbench-sidebar__brand" :to="authStore.homePath">
      <span class="workbench-sidebar__eyebrow">DD RAG</span>
      <strong>知识工作台</strong>
      <span class="workbench-sidebar__brand-subtitle">组内知识协作</span>
      <span class="workbench-sidebar__brand-description">管理组与文档，用问答和助手基于证据回答问题。</span>
      <span class="workbench-sidebar__brand-tag">组 · 文档 · 问答 · 助手</span>
    </RouterLink>

    <nav class="workbench-sidebar__nav" aria-label="系统导航">
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        class="workbench-sidebar__nav-item"
        :class="{ 'is-active': isActive(item.to) }"
      >
        <strong>{{ item.label }}</strong>
        <span>{{ item.caption }}</span>
      </RouterLink>
    </nav>

    <div class="workbench-sidebar__user">
      <div class="workbench-sidebar__user-copy">
        <span class="workbench-sidebar__user-label">{{ roleLabel }}</span>
        <strong>{{ currentUser?.displayName ?? '未登录用户' }}</strong>
        <span>{{ accountSummary }}</span>
      </div>

      <div class="workbench-sidebar__status">
        <span class="workbench-sidebar__status-dot" aria-hidden="true" />
        <span>{{ statusLabel }}</span>
      </div>

      <div class="workbench-sidebar__actions">
        <RouterLink class="workbench-sidebar__security-link" to="/account/security">
          账号安全
        </RouterLink>
        <SessionLogoutButton class="workbench-sidebar__logout" />
      </div>
    </div>
  </div>
</template>
