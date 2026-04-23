<script setup lang="ts">
import { computed, reactive, ref, useId } from 'vue'
import { extractApiError } from '../api/http'
import { useAuthStore } from '../stores/auth'

const MIN_PASSWORD_LENGTH = 8

const props = withDefaults(
  defineProps<{
  showUserSummary?: boolean
  inline?: boolean
}>(),
  {
    showUserSummary: false,
    inline: false,
  },
)

const emit = defineEmits<{
  completed: [payload: { wasMandatory: boolean }]
}>()

const authStore = useAuthStore()
const titleId = useId()

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pageError = ref('')
const successMessage = ref('')
const isSubmitting = ref(false)

const currentUser = computed(() => authStore.currentUser)
const mustChangePassword = computed(() => currentUser.value?.mustChangePassword === true)
const passwordRuleText = '至少 8 位，并同时包含字母和数字'
const securitySummary = computed(() =>
  mustChangePassword.value
    ? '首次登录后需要先更新密码，完成后系统会根据角色放行到对应工作区。'
    : '建议定期更新密码，并避免复用与当前密码过于接近的口令。',
)

async function handleSubmit() {
  pageError.value = ''
  successMessage.value = ''

  const validationError = validateForm()
  if (validationError !== null) {
    pageError.value = validationError
    return
  }

  isSubmitting.value = true
  const shouldLeaveSecurityPage = mustChangePassword.value

  try {
    await authStore.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword,
    })
    resetForm()
    successMessage.value = '密码已更新'
    emit('completed', { wasMandatory: shouldLeaveSecurityPage })
  } catch (error) {
    pageError.value = extractApiError(error, '修改密码失败')
  } finally {
    isSubmitting.value = false
  }
}

function validateForm(): string | null {
  if (form.currentPassword.length === 0 || form.newPassword.length === 0) {
    return '请输入当前密码和新密码'
  }

  if (form.newPassword.length < MIN_PASSWORD_LENGTH) {
    return `新密码${passwordRuleText}`
  }

  if (!/[A-Za-z]/.test(form.newPassword) || !/\d/.test(form.newPassword)) {
    return `新密码${passwordRuleText}`
  }

  if (form.currentPassword === form.newPassword) {
    return '新密码不能与当前密码相同'
  }

  if (form.newPassword !== form.confirmPassword) {
    return '两次输入的新密码不一致'
  }

  return null
}

function resetForm() {
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
}
</script>

<template>
  <section
    class="account-password-form"
    :class="{ 'account-password-form--inline': props.inline }"
    :aria-labelledby="titleId"
  >
    <div class="security-card__header">
      <div class="security-card__title-block">
        <p class="auth-panel__eyebrow security-card__kicker">
          {{ mustChangePassword ? '必须修改密码' : '修改登录密码' }}
        </p>
        <h2 :id="titleId" class="auth-panel__title">设置新密码</h2>
        <p class="auth-panel__hint security-card__summary">
          {{ securitySummary }}
        </p>
      </div>
      <span v-if="currentUser" class="security-card__role">
        {{ currentUser.systemRole }}
      </span>
    </div>

    <div v-if="props.showUserSummary && currentUser" class="security-card__user-strip">
      <span class="security-card__user-eyebrow">当前账号</span>
      <strong>{{ currentUser.displayName }}</strong>
      <span>{{ currentUser.userCode }}</span>
    </div>

    <form class="auth-form security-form" @submit.prevent="handleSubmit">
      <label class="auth-form__field security-form__field">
        <span>当前密码</span>
        <input
          v-model="form.currentPassword"
          type="password"
          autocomplete="current-password"
          maxlength="128"
          placeholder="输入当前登录密码"
          :disabled="isSubmitting"
        />
      </label>

      <label class="auth-form__field security-form__field">
        <span>新密码</span>
        <input
          v-model="form.newPassword"
          type="password"
          autocomplete="new-password"
          maxlength="128"
          placeholder="输入新的登录密码"
          :disabled="isSubmitting"
        />
        <small class="auth-form__help">{{ passwordRuleText }}</small>
      </label>

      <label class="auth-form__field security-form__field">
        <span>确认新密码</span>
        <input
          v-model="form.confirmPassword"
          type="password"
          autocomplete="new-password"
          maxlength="128"
          placeholder="再次输入新密码"
          :disabled="isSubmitting"
        />
      </label>

      <p v-if="pageError" class="auth-form__error security-form__error" role="alert">
        {{ pageError }}
      </p>
      <p v-if="successMessage" class="auth-form__success security-form__success" role="status">
        {{ successMessage }}
      </p>

      <button class="auth-form__submit security-form__submit" type="submit" :disabled="isSubmitting">
        {{ isSubmitting ? '保存中...' : '保存新密码' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.account-password-form {
  --auth-panel-border: rgba(96, 117, 140, 0.16);
  --auth-panel-bg: rgba(248, 251, 253, 0.95);
  --auth-panel-shadow: 0 28px 60px rgba(17, 37, 58, 0.14);
  --auth-accent: #2f6b95;
  --auth-accent-strong: #214f70;
  --auth-highlight: #b67d50;
  --auth-text: #163047;
  --auth-text-muted: #60758c;
  width: min(100%, 30rem);
  display: grid;
  gap: 1.5rem;
  padding: clamp(1.5rem, 3vw, 2.25rem);
  border: 1px solid var(--auth-panel-border);
  border-radius: 1.8rem;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(244, 248, 251, 0.92)),
    var(--auth-panel-bg);
  box-shadow: var(--auth-panel-shadow);
  backdrop-filter: blur(18px);
}

.account-password-form--inline {
  width: 100%;
  box-shadow: none;
}

.auth-panel__eyebrow {
  margin: 0;
  color: var(--auth-highlight);
  font-size: 0.76rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.auth-panel__title {
  margin: 0;
  color: var(--auth-text);
  font-family: var(--wb-font-display);
  font-size: clamp(1.95rem, 3vw, 2.45rem);
  line-height: 1;
  letter-spacing: -0.05em;
}

.auth-panel__hint {
  margin: 0;
  color: var(--auth-text-muted);
  font-size: 0.96rem;
  line-height: 1.7;
}

.auth-form {
  display: grid;
  gap: 1rem;
}

.auth-form__field {
  display: grid;
  gap: 0.45rem;
  color: var(--auth-text);
  font-weight: 700;
}

.auth-form__field span {
  font-size: 0.92rem;
}

.auth-form__field input {
  width: 100%;
  min-height: 3.35rem;
  padding: 0.9rem 1rem;
  border: 1px solid rgba(96, 117, 140, 0.18);
  border-radius: 1rem;
  color: var(--auth-text);
  background: rgba(255, 255, 255, 0.92);
  outline: none;
  transition:
    border-color 180ms ease,
    box-shadow 180ms ease,
    transform 180ms ease,
    background-color 180ms ease;
}

.auth-form__field input::placeholder {
  color: #8ea1b3;
}

.auth-form__field input:focus {
  border-color: rgba(47, 107, 149, 0.46);
  box-shadow: 0 0 0 0.28rem rgba(47, 107, 149, 0.12);
  transform: translateY(-1px);
  background: #fff;
}

.auth-form__help {
  color: var(--auth-text-muted);
  font-size: 0.82rem;
  line-height: 1.5;
}

.auth-form__error,
.auth-form__success {
  margin: 0;
  padding: 0.8rem 0.9rem;
  border-radius: 1rem;
  font-size: 0.92rem;
  line-height: 1.6;
}

.auth-form__error {
  color: #8f2d2d;
  background: rgba(255, 232, 226, 0.92);
}

.auth-form__success {
  color: #1f5e52;
  background: rgba(226, 247, 239, 0.92);
}

.auth-form__submit {
  min-height: 3.35rem;
  border: 0;
  border-radius: 1rem;
  padding: 0.95rem 1rem;
  color: #f4f8fb;
  font-weight: 800;
  background: linear-gradient(135deg, #203246, #b67d50);
  box-shadow: 0 18px 32px rgba(32, 50, 70, 0.2);
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    opacity 180ms ease;
}

.auth-form__submit:hover:not(:disabled),
.auth-form__submit:focus-visible {
  transform: translateY(-1px);
  box-shadow: 0 22px 36px rgba(32, 50, 70, 0.26);
  outline: none;
}

.auth-form__submit:disabled {
  opacity: 0.68;
}

.security-card__header {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
  justify-content: space-between;
}

.security-card__title-block {
  display: grid;
  gap: 0.45rem;
}

.security-card__summary {
  max-width: 24rem;
}

.security-card__role {
  display: inline-flex;
  align-items: center;
  min-height: 2rem;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
  color: var(--auth-accent-strong);
  font-size: 0.78rem;
  font-weight: 800;
  background: rgba(47, 107, 149, 0.12);
}

.security-card__user-strip {
  display: grid;
  gap: 0.2rem;
  padding: 0.9rem 1rem;
  border-radius: 1rem;
  background: rgba(235, 242, 247, 0.86);
}

.security-card__user-eyebrow {
  color: #b67d50;
  font-size: 0.74rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.security-card__user-strip strong {
  color: var(--wb-color-text);
  font-size: 1.05rem;
}

@media (max-width: 960px) {
  .account-password-form {
    width: 100%;
  }

  .security-card__header {
    flex-direction: column;
  }
}
</style>
