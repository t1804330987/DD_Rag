<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AccountPasswordForm from '../../components/AccountPasswordForm.vue'
import AuthSplitShell from '../../components/auth/AuthSplitShell.vue'
import SessionLogoutButton from '../../components/SessionLogoutButton.vue'
import '../../assets/account-security-page.css'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const currentUser = computed(() => authStore.currentUser)
const mustChangePassword = computed(() => currentUser.value?.mustChangePassword === true)
const returnPath = computed(() => authStore.resolveLandingPath())

onMounted(() => {
  void ensureAuthenticated()
})

async function ensureAuthenticated() {
  const user = await authStore.bootstrap()
  if (user === null) {
    await router.replace('/login?redirect=/account/security')
  }
}

async function handlePasswordChanged(payload: { wasMandatory: boolean }) {
  if (payload.wasMandatory) {
    await router.replace(authStore.resolveLandingPath())
  }
}
</script>

<template>
  <AuthSplitShell
    class="account-security-page"
    eyebrow="Account security"
    title="收紧账户边界并更新口令"
    description="修改密码后，系统会刷新当前用户状态。若这是首次登录的强制修改，完成后会按你的角色进入对应区域。"
  >
    <template #brand>
      <div class="security-brand-stack">
        <div class="security-brand-points">
          <article class="security-brand-point">
            <strong>强制改密流程保留</strong>
            <span>首次登录仍必须完成新密码设置，避免默认口令直接进入业务区。</span>
          </article>
          <article class="security-brand-point">
            <strong>角色路由保持不变</strong>
            <span>修改完成后继续沿用现有 landing path 解析，不改守卫语义。</span>
          </article>
        </div>

        <div v-if="currentUser" class="security-brand-summary">
          <span class="security-brand-summary__eyebrow">当前身份</span>
          <strong>{{ currentUser.displayName }}</strong>
          <span>{{ currentUser.userCode }} · {{ currentUser.systemRole }}</span>
        </div>
      </div>
    </template>

    <template #actions>
      <div class="security-shell-actions">
        <RouterLink v-if="!mustChangePassword" class="security-shell-back" :to="returnPath">
          返回主页面
        </RouterLink>
        <SessionLogoutButton class="security-shell-logout" />
      </div>
    </template>

    <AccountPasswordForm show-user-summary @completed="handlePasswordChanged" />
  </AuthSplitShell>
</template>
