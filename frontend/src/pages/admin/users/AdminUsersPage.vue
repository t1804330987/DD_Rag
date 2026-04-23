<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchAdminUsers,
  resetAdminUserPassword,
  updateAdminUserStatus,
  type AdminUserItem,
  type UserStatus,
} from '../../../api/admin-user'
import { extractApiError } from '../../../api/http'

const router = useRouter()
const users = ref<AdminUserItem[]>([])
const pageError = ref('')
const pageFeedback = ref('')
const isLoading = ref(false)
const actionUserIds = ref<Set<number>>(new Set())
const statusFilter = ref<'ALL' | UserStatus>('ALL')
const searchKeyword = ref('')

const activeUsers = computed(() => users.value.filter((item) => item.status === 'ACTIVE').length)
const disabledUsers = computed(() => users.value.filter((item) => item.status === 'DISABLED').length)
const mustChangePasswordUsers = computed(() => users.value.filter((item) => item.mustChangePassword).length)
const filteredUsers = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return users.value.filter((item) => {
    const matchesStatus = statusFilter.value === 'ALL' || item.status === statusFilter.value
    if (!matchesStatus) {
      return false
    }
    if (!keyword) {
      return true
    }
    const haystack = [item.displayName, item.username, item.email, item.userCode]
      .join(' ')
      .toLowerCase()
    return haystack.includes(keyword)
  })
})
const resultSummary = computed(() => {
  if (filteredUsers.value.length === users.value.length) {
    return `当前共展示 ${filteredUsers.value.length} 个用户。`
  }
  return `总计 ${users.value.length} 个用户，当前筛选命中 ${filteredUsers.value.length} 个。`
})

onMounted(() => {
  void loadUsers()
})

async function loadUsers() {
  isLoading.value = true
  pageError.value = ''
  try {
    users.value = await fetchAdminUsers()
  } catch (error) {
    pageError.value = extractApiError(error, '加载用户列表失败')
  } finally {
    isLoading.value = false
  }
}

async function handleStatusChange(user: AdminUserItem) {
  const nextStatus: UserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionLabel = nextStatus === 'DISABLED' ? '禁用' : '启用'
  if (!window.confirm(`确认${actionLabel}用户「${user.username}」吗？`)) return

  await runUserAction(user.userId, `${actionLabel}用户失败`, async () => {
    await updateAdminUserStatus(user.userId, nextStatus)
    pageFeedback.value = `已${actionLabel}用户「${user.username}」。`
  })
}

async function handleResetPassword(user: AdminUserItem) {
  const newPassword = window.prompt(`请输入「${user.username}」的新密码`)
  if (newPassword === null) return

  await runUserAction(user.userId, '重置密码失败', async () => {
    await resetAdminUserPassword(user.userId, newPassword)
    pageFeedback.value = `已重置用户「${user.username}」的密码。`
  })
}

async function runUserAction(userId: number, fallbackMessage: string, action: () => Promise<void>) {
  const nextActionUserIds = new Set(actionUserIds.value)
  nextActionUserIds.add(userId)
  actionUserIds.value = nextActionUserIds
  pageError.value = ''
  pageFeedback.value = ''

  try {
    await action()
    await loadUsers()
  } catch (error) {
    pageError.value = extractApiError(error, fallbackMessage)
  } finally {
    const finalActionUserIds = new Set(actionUserIds.value)
    finalActionUserIds.delete(userId)
    actionUserIds.value = finalActionUserIds
  }
}

function formatLastLogin(value: string | null) {
  if (!value) return '从未登录'
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}

function updateStatusFilter(value: 'ALL' | UserStatus) {
  statusFilter.value = value
}
</script>

<template>
  <section class="admin-page-section admin-users-page">
    <article class="admin-panel admin-panel--hero">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">用户总览</p>
          <h2>用户管理</h2>
          <p class="admin-panel__description">
            先看账号状态，再决定禁用、重置密码或进入详情页，避免在同一表格里混淆治理动作。
          </p>
        </div>
        <div class="admin-panel__header-actions">
          <button class="ghost-button" type="button" @click="void loadUsers()">刷新列表</button>
        </div>
      </div>

      <div class="admin-stats">
        <article>
          <span>活跃用户</span>
          <strong>{{ activeUsers }}</strong>
          <small>可正常登录并进入授权区域</small>
        </article>
        <article>
          <span>禁用用户</span>
          <strong>{{ disabledUsers }}</strong>
          <small>仍保留账号信息，但当前会话应被阻断</small>
        </article>
        <article>
          <span>待改密用户</span>
          <strong>{{ mustChangePasswordUsers }}</strong>
          <small>首次开通或重置密码后需立即更新口令</small>
        </article>
      </div>
    </article>

    <article class="admin-panel admin-panel--table">
      <div class="admin-panel__header admin-panel__header--stacked">
        <div>
          <p class="panel__eyebrow">筛选条件</p>
          <h2>列表筛选</h2>
          <p class="admin-panel__description">{{ resultSummary }}</p>
        </div>
        <div class="admin-filter-bar" aria-label="用户筛选">
          <label class="admin-filter-field">
            <span>状态</span>
            <div class="admin-filter-pills">
              <button
                class="admin-filter-pill"
                :class="{ 'is-active': statusFilter === 'ALL' }"
                type="button"
                @click="updateStatusFilter('ALL')"
              >
                全部
              </button>
              <button
                class="admin-filter-pill"
                :class="{ 'is-active': statusFilter === 'ACTIVE' }"
                type="button"
                @click="updateStatusFilter('ACTIVE')"
              >
                启用中
              </button>
              <button
                class="admin-filter-pill"
                :class="{ 'is-active': statusFilter === 'DISABLED' }"
                type="button"
                @click="updateStatusFilter('DISABLED')"
              >
                已禁用
              </button>
            </div>
          </label>

          <label class="admin-filter-field admin-filter-field--search">
            <span>检索</span>
            <input
              v-model="searchKeyword"
              type="search"
              maxlength="128"
              placeholder="按姓名、用户名、邮箱或账号编码筛选"
            />
          </label>
        </div>
      </div>

      <p v-if="pageFeedback" class="feedback feedback--success">{{ pageFeedback }}</p>
      <p v-if="pageError" class="feedback feedback--error">{{ pageError }}</p>
      <p v-if="isLoading" class="placeholder-text">正在加载用户列表...</p>

      <div v-else-if="filteredUsers.length === 0" class="admin-empty-state">
        <p class="panel__eyebrow">暂无结果</p>
        <h3>当前筛选没有命中用户</h3>
        <p>可以先清空状态筛选或搜索关键字，再重新查看全部账号。</p>
      </div>

      <div v-else class="admin-table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户</th>
              <th>角色</th>
              <th>账号状态</th>
              <th>密码状态</th>
              <th>最近登录</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in filteredUsers" :key="user.userId">
              <td>
                <div class="admin-user-cell">
                  <strong>{{ user.displayName }}</strong>
                  <span>{{ user.username }}</span>
                  <small>{{ user.email }}</small>
                  <small>账号编码：{{ user.userCode }}</small>
                  <button
                    class="admin-user-cell__link"
                    type="button"
                    @click="router.push(`/admin/users/${user.userId}`)"
                  >
                    查看详情
                  </button>
                </div>
              </td>
              <td>
                <span class="admin-role-pill" :data-role="user.systemRole">{{ user.systemRole }}</span>
              </td>
              <td>
                <span class="admin-status" :data-status="user.status">{{ user.status }}</span>
              </td>
              <td>
                <span
                  class="admin-security-pill"
                  :data-tone="user.mustChangePassword ? 'warning' : 'normal'"
                >
                  {{ user.mustChangePassword ? '待改密' : '正常' }}
                </span>
              </td>
              <td>{{ formatLastLogin(user.lastLoginAt) }}</td>
              <td>
                <div class="admin-actions">
                  <button
                    class="ghost-button"
                    type="button"
                    :disabled="actionUserIds.has(user.userId)"
                    @click="handleStatusChange(user)"
                  >
                    {{ user.status === 'ACTIVE' ? '禁用' : '启用' }}
                  </button>
                  <button
                    class="ghost-button"
                    type="button"
                    :disabled="actionUserIds.has(user.userId)"
                    @click="handleResetPassword(user)"
                  >
                    重置密码
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
