<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { extractApiError } from '../../api/http'
import { register } from '../../api/auth'
import '../../assets/login-page.css'
import AuthSplitShell from '../../components/auth/AuthSplitShell.vue'

const router = useRouter()

const form = reactive({
  username: '',
  email: '',
  displayName: '',
  password: '',
  confirmPassword: '',
})

const pageError = ref('')
const isSubmitting = ref(false)

async function handleSubmit() {
  pageError.value = ''

  if (
    form.username.trim().length === 0 ||
    form.email.trim().length === 0 ||
    form.displayName.trim().length === 0 ||
    form.password.length === 0
  ) {
    pageError.value = '请填写完整注册信息'
    return
  }

  if (form.password !== form.confirmPassword) {
    pageError.value = '两次输入的密码不一致'
    return
  }

  isSubmitting.value = true
  try {
    await register({
      username: form.username.trim(),
      email: form.email.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
    })
    await router.replace({ path: '/login', query: { registered: '1' } })
  } catch (error) {
    pageError.value = extractApiError(error, '注册失败')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <AuthSplitShell
    class="login-page auth-page--register"
    eyebrow="Create business account"
    title="创建可加入知识库的业务账号"
    description="注册只完成身份创建，不会自动获得任何组权限。登录后，你仍需要按组织 ID 申请加入空间，或自行创建协作组。"
  >
    <template #brand>
      <div class="auth-brand-stack">
        <ul class="auth-brand-list" aria-label="注册规则">
          <li>
            <strong>默认角色是 USER</strong>
            <span>注册后只拥有基础业务身份，不会直接接触管理员入口。</span>
          </li>
          <li>
            <strong>不会自动加入任何知识库</strong>
            <span>群组权限仍由 OWNER 创建、邀请或审批加入。</span>
          </li>
          <li>
            <strong>密码规则在首次注册时生效</strong>
            <span>建议直接设置满足规则的强密码，减少后续改密成本。</span>
          </li>
        </ul>
      </div>
    </template>

    <section class="auth-panel" aria-labelledby="register-title">
      <div class="auth-panel__header">
        <p class="auth-panel__eyebrow">Create account</p>
        <h2 id="register-title" class="auth-panel__title">注册账号</h2>
        <p class="auth-panel__hint">填写基础资料后创建账号，成功后会自动返回登录页。</p>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="auth-form__field">
          <span>用户名</span>
          <input v-model="form.username" type="text" autocomplete="username" maxlength="64" placeholder="例如：user001" />
        </label>

        <label class="auth-form__field">
          <span>邮箱</span>
          <input v-model="form.email" type="email" autocomplete="email" maxlength="128" placeholder="user001@example.com" />
        </label>

        <label class="auth-form__field">
          <span>显示名称</span>
          <input v-model="form.displayName" type="text" maxlength="128" placeholder="例如：张三" />
        </label>

        <label class="auth-form__field">
          <span>密码</span>
          <input v-model="form.password" type="password" autocomplete="new-password" maxlength="128" placeholder="至少 8 位，包含字母和数字" />
        </label>

        <label class="auth-form__field">
          <span>确认密码</span>
          <input v-model="form.confirmPassword" type="password" autocomplete="new-password" maxlength="128" placeholder="再次输入密码" />
        </label>

        <p v-if="pageError" class="auth-form__error" role="alert">
          {{ pageError }}
        </p>

        <button class="auth-form__submit" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? '注册中...' : '创建账号' }}
        </button>
      </form>

      <div class="auth-panel__footer">
        <span>已有账号？</span>
        <RouterLink class="auth-page-link" to="/login">返回登录</RouterLink>
      </div>
    </section>
  </AuthSplitShell>
</template>
