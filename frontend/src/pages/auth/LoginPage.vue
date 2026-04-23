<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { extractApiError } from '../../api/http'
import '../../assets/login-page.css'
import AuthSplitShell from '../../components/auth/AuthSplitShell.vue'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({
  loginId: '',
  password: '',
})

const pageError = ref('')
const pageNotice = ref('')
const isBootstrapping = ref(false)

onMounted(() => {
  pageNotice.value = route.query.registered === '1' ? '注册成功，请使用新账号登录。' : ''
  void redirectAuthenticatedUser()
})

async function redirectAuthenticatedUser() {
  isBootstrapping.value = true
  try {
    const currentUser = await authStore.bootstrap()
    if (currentUser !== null) {
      await router.replace(authStore.resolveLandingPath(readRedirectQuery()))
    }
  } finally {
    isBootstrapping.value = false
  }
}

async function handleSubmit() {
  pageError.value = ''

  if (form.loginId.trim().length === 0 || form.password.length === 0) {
    pageError.value = '请输入账号和密码'
    return
  }

  try {
    await authStore.login({
      loginId: form.loginId,
      password: form.password,
    })
    await router.replace(authStore.resolveLandingPath(readRedirectQuery()))
  } catch (error) {
    pageError.value = extractApiError(error, '登录失败，请检查账号或密码')
  }
}

function readRedirectQuery(): string | null {
  const redirect = route.query.redirect
  return Array.isArray(redirect) ? (redirect[0] ?? null) : (redirect ?? null)
}
</script>

<template>
  <AuthSplitShell
    class="login-page auth-page--login"
    eyebrow="产品品牌"
    title="DD RAG"
    description="进入知识协作工作台。同一套身份体系会把管理员送往系统控制区，把普通用户送往群组、文档与问答工作区。首次登录需要先完成密码更新。"
  >
    <template #brand>
      <div class="auth-brand-stack">
        <div class="auth-brand-chip-row" aria-label="认证能力">
          <span>JWT access token</span>
          <span>HttpOnly refresh cookie</span>
          <span>Role aware routing</span>
        </div>

        <div class="auth-brand-stat-grid">
          <article class="auth-brand-stat">
            <strong>01</strong>
            <span>登录后按系统角色进入不同工作区，减少错误入口。</span>
          </article>
          <article class="auth-brand-stat">
            <strong>02</strong>
            <span>刷新会话后仍保持安全 cookie，降低重复登录摩擦。</span>
          </article>
          <article class="auth-brand-stat">
            <strong>03</strong>
            <span>强制改密流程保留，避免初始口令直接流入业务区。</span>
          </article>
        </div>
      </div>
    </template>

    <section class="auth-panel" aria-labelledby="login-title">
      <div class="auth-panel__header">
        <p class="auth-panel__eyebrow">Secure sign in</p>
        <h2 id="login-title" class="auth-panel__title">登录</h2>
        <p class="auth-panel__hint">支持用户名或邮箱登录，成功后会按当前角色自动跳转。</p>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="auth-form__field">
          <span>账号</span>
          <input
            v-model="form.loginId"
            type="text"
            autocomplete="username"
            maxlength="100"
            placeholder="用户名或邮箱"
            :disabled="authStore.isAuthenticating || isBootstrapping"
          />
        </label>

        <label class="auth-form__field">
          <span>密码</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            maxlength="128"
            placeholder="输入密码"
            :disabled="authStore.isAuthenticating || isBootstrapping"
          />
        </label>

        <p v-if="pageError" class="auth-form__error" role="alert">
          {{ pageError }}
        </p>
        <p v-if="pageNotice" class="auth-form__notice">
          {{ pageNotice }}
        </p>

        <button
          class="auth-form__submit"
          type="submit"
          :disabled="authStore.isAuthenticating || isBootstrapping"
        >
          {{ authStore.isAuthenticating ? '登录中...' : '进入系统' }}
        </button>
      </form>

      <div class="auth-panel__footer">
        <span>还没有账号？</span>
        <RouterLink class="auth-page-link" to="/register">创建新账号</RouterLink>
      </div>
    </section>
  </AuthSplitShell>
</template>
