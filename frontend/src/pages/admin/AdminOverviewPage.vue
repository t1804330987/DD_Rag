<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAdminUsers, type AdminUserItem } from '../../api/admin-user'
import { extractApiError } from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const users = ref<AdminUserItem[]>([])
const pageError = ref('')
const isLoading = ref(false)

const totalUsers = computed(() => users.value.length)
const activeUsers = computed(() => users.value.filter((item) => item.status === 'ACTIVE').length)
const disabledUsers = computed(() => users.value.filter((item) => item.status === 'DISABLED').length)
const mustChangePasswordUsers = computed(() =>
  users.value.filter((item) => item.mustChangePassword).length,
)
const adminUsers = computed(() => users.value.filter((item) => item.systemRole === 'ADMIN').length)
const recentUsers = computed(() => users.value.slice(0, 5))
const currentAdminName = computed(() => authStore.currentUser?.displayName ?? '系统管理员')

onMounted(() => {
  void loadUsers()
})

async function loadUsers() {
  isLoading.value = true
  pageError.value = ''
  try {
    users.value = await fetchAdminUsers()
  } catch (error) {
    pageError.value = extractApiError(error, '加载后台概览失败')
  } finally {
    isLoading.value = false
  }
}

function goToUsers() {
  void router.push('/admin/users')
}

function formatLastLogin(value: string | null) {
  if (!value) {
    return '从未登录'
  }
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString('zh-CN')
}
</script>

<template>
  <section class="admin-page-section admin-overview-page">
    <article class="admin-panel admin-panel--hero">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">总览</p>
          <h2>后台首页</h2>
          <p class="admin-panel__description">
            先看账号全局状态，再进入用户列表做具体治理，避免一上来就陷入明细操作。
          </p>
        </div>
        <div class="admin-panel__header-actions">
          <button class="ghost-button" type="button" @click="void loadUsers()">刷新数据</button>
          <button class="primary-button" type="button" @click="goToUsers">进入用户管理</button>
        </div>
      </div>

      <div class="admin-stats">
        <article>
          <span>账号总数</span>
          <strong>{{ totalUsers }}</strong>
          <small>当前系统中已存在的全部账号数量。</small>
        </article>
        <article>
          <span>活跃账号</span>
          <strong>{{ activeUsers }}</strong>
          <small>可正常登录并进入授权区域的账号。</small>
        </article>
        <article>
          <span>禁用账号</span>
          <strong>{{ disabledUsers }}</strong>
          <small>保留身份信息，但应被阻断访问的账号。</small>
        </article>
      </div>
    </article>

    <div class="admin-overview-grid">
      <article class="admin-panel">
        <div class="admin-panel__header">
          <div>
            <p class="panel__eyebrow">治理重点</p>
            <h2>当前关注项</h2>
          </div>
        </div>

        <div class="admin-overview-highlights">
          <article>
            <span>待改密账号</span>
            <strong>{{ mustChangePasswordUsers }}</strong>
            <small>新开通或刚重置密码的账号应尽快完成首次改密。</small>
          </article>
          <article>
            <span>管理员账号</span>
            <strong>{{ adminUsers }}</strong>
            <small>系统级管理权限应保持克制，避免过度扩散。</small>
          </article>
          <article>
            <span>当前值守人</span>
            <strong>{{ currentAdminName }}</strong>
            <small>当前登录账号拥有后台治理入口，但不自动拥有业务组权限。</small>
          </article>
        </div>
      </article>

      <article class="admin-panel">
        <div class="admin-panel__header">
          <div>
            <p class="panel__eyebrow">快捷入口</p>
            <h2>常用操作</h2>
          </div>
        </div>

        <div class="admin-overview-actions">
          <button class="admin-overview-action" type="button" @click="goToUsers">
            <strong>查看用户列表</strong>
            <span>进入账号列表，统一处理状态切换、密码重置与筛选排查。</span>
          </button>
          <button class="admin-overview-action" type="button" @click="router.push('/account/security')">
            <strong>检查账号安全</strong>
            <span>返回个人改密页，确保当前管理员自身凭据也处于安全状态。</span>
          </button>
          <article class="admin-overview-highlights__note">
            <strong>治理边界</strong>
            <span>后台当前只负责账号治理，不提供前端手工创建用户入口。</span>
          </article>
        </div>
      </article>
    </div>

    <article class="admin-panel admin-panel--table">
      <div class="admin-panel__header">
        <div>
          <p class="panel__eyebrow">最近账号</p>
          <h2>最近加载的用户视图</h2>
          <p class="admin-panel__description">这里保留一个轻量摘要，详细操作仍在用户管理页完成。</p>
        </div>
      </div>

      <p v-if="pageError" class="feedback feedback--error">{{ pageError }}</p>
      <p v-else-if="isLoading" class="placeholder-text">正在加载后台概览...</p>
      <div v-else-if="recentUsers.length === 0" class="admin-empty-state">
        <p class="panel__eyebrow">空数据</p>
        <h3>当前还没有可展示的账号</h3>
        <p>可以先开通第一个用户，再回来查看后台概览。</p>
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
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in recentUsers" :key="user.userId">
              <td>
                <div class="admin-user-cell">
                  <strong>{{ user.displayName }}</strong>
                  <span>{{ user.username }}</span>
                  <small>{{ user.email }}</small>
                </div>
              </td>
              <td>
                <span class="admin-role-pill" :data-role="user.systemRole">{{ user.systemRole }}</span>
              </td>
              <td>
                <span class="admin-status" :data-status="user.status">{{ user.status }}</span>
              </td>
              <td>
                <span class="admin-security-pill" :data-tone="user.mustChangePassword ? 'warning' : 'normal'">
                  {{ user.mustChangePassword ? '待改密' : '正常' }}
                </span>
              </td>
              <td>{{ formatLastLogin(user.lastLoginAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
