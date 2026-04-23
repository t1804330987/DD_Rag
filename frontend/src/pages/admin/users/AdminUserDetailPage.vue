<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  fetchAdminUserDetail,
  resetAdminUserPassword,
  updateAdminUserStatus,
  type AdminUserItem,
  type UserStatus,
} from '../../../api/admin-user'
import { extractApiError } from '../../../api/http'

const route = useRoute()
const router = useRouter()

const user = ref<AdminUserItem | null>(null)
const pageError = ref('')
const pageFeedback = ref('')
const isLoading = ref(false)
const isActing = ref(false)

const userId = computed(() => Number(route.params.userId))
const detailRows = computed(() => {
  if (user.value === null) {
    return []
  }
  return [
    { label: '用户 ID', value: String(user.value.userId) },
    { label: '账号编码', value: user.value.userCode },
    { label: '用户名', value: user.value.username },
    { label: '邮箱', value: user.value.email },
    { label: '显示名称', value: user.value.displayName },
    { label: '系统角色', value: user.value.systemRole },
    { label: '账号状态', value: user.value.status },
    { label: '密码状态', value: user.value.mustChangePassword ? '待改密' : '正常' },
    { label: '最近登录', value: formatLastLogin(user.value.lastLoginAt) },
  ]
})

onMounted(() => {
  void loadUser()
})

async function loadUser() {
  if (!Number.isInteger(userId.value) || userId.value <= 0) {
    pageError.value = '用户 ID 非法'
    return
  }
  isLoading.value = true
  pageError.value = ''
  try {
    user.value = await fetchAdminUserDetail(userId.value)
  } catch (error) {
    pageError.value = extractApiError(error, '加载用户详情失败')
  } finally {
    isLoading.value = false
  }
}

function formatLastLogin(value: string | null) {
  if (!value) {
    return '从未登录'
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}

async function handleStatusChange() {
  if (user.value === null) {
    return
  }
  const nextStatus: UserStatus = user.value.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionLabel = nextStatus === 'DISABLED' ? '禁用' : '启用'
  if (!window.confirm(`确认${actionLabel}用户「${user.value.username}」吗？`)) {
    return
  }

  await runDetailAction(`${actionLabel}用户失败`, async () => {
    await updateAdminUserStatus(user.value!.userId, nextStatus)
    pageFeedback.value = `已${actionLabel}用户「${user.value!.username}」。`
  })
}

async function handleResetPassword() {
  if (user.value === null) {
    return
  }
  const newPassword = window.prompt(`请输入「${user.value.username}」的新密码`)
  if (newPassword === null) {
    return
  }

  await runDetailAction('重置密码失败', async () => {
    await resetAdminUserPassword(user.value!.userId, newPassword)
    pageFeedback.value = `已重置用户「${user.value!.username}」的密码。`
  })
}

async function runDetailAction(fallbackMessage: string, action: () => Promise<void>) {
  isActing.value = true
  pageError.value = ''
  pageFeedback.value = ''
  try {
    await action()
    await loadUser()
  } catch (error) {
    pageError.value = extractApiError(error, fallbackMessage)
  } finally {
    isActing.value = false
  }
}
</script>

<template>
  <section class="admin-page-section admin-user-detail-page">
    <article class="admin-panel admin-panel--detail">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">用户详情</p>
          <h2>{{ user?.displayName ?? '查看账号详情' }}</h2>
          <p class="admin-panel__description">
            这里集中查看单个账号的身份、状态与登录信息，避免在列表页里堆过多字段。
          </p>
        </div>
        <div class="admin-panel__header-actions">
          <button class="ghost-button" type="button" @click="void loadUser()">刷新详情</button>
          <button class="primary-button" type="button" @click="router.push('/admin/users')">返回列表</button>
        </div>
      </div>

      <p v-if="pageError" class="feedback feedback--error">{{ pageError }}</p>
      <p v-if="pageFeedback" class="feedback feedback--success">{{ pageFeedback }}</p>
      <p v-else-if="isLoading" class="placeholder-text">正在加载用户详情...</p>
      <div v-else-if="user === null" class="admin-empty-state">
        <p class="panel__eyebrow">空数据</p>
        <h3>当前没有可展示的用户详情</h3>
        <p>请返回列表后重新选择账号。</p>
      </div>
      <div v-else class="admin-detail-grid">
        <article class="admin-detail-hero">
          <span>账号身份</span>
          <strong>{{ user.displayName }}</strong>
          <small>{{ user.email }}</small>
          <div class="admin-detail-hero__badges">
            <span class="admin-role-pill" :data-role="user.systemRole">{{ user.systemRole }}</span>
            <span class="admin-status" :data-status="user.status">{{ user.status }}</span>
            <span class="admin-security-pill" :data-tone="user.mustChangePassword ? 'warning' : 'normal'">
              {{ user.mustChangePassword ? '待改密' : '正常' }}
            </span>
          </div>
          <div class="admin-detail-hero__actions">
            <button class="ghost-button" type="button" :disabled="isActing" @click="handleStatusChange">
              {{ user.status === 'ACTIVE' ? '禁用账号' : '启用账号' }}
            </button>
            <button class="primary-button" type="button" :disabled="isActing" @click="handleResetPassword">
              {{ isActing ? '处理中...' : '重置密码' }}
            </button>
          </div>
        </article>

        <article class="admin-detail-card">
          <div class="admin-detail-list">
            <div v-for="item in detailRows" :key="item.label" class="admin-detail-list__row">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>
      </div>
    </article>
  </section>
</template>
