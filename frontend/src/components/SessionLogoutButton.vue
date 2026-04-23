<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const isLoggingOut = ref(false)

async function handleLogout() {
  if (isLoggingOut.value) {
    return
  }

  isLoggingOut.value = true
  try {
    await authStore.logout()
    await router.replace('/login')
  } finally {
    isLoggingOut.value = false
  }
}
</script>

<template>
  <button
    class="session-logout-button"
    type="button"
    :disabled="isLoggingOut"
    @click="handleLogout"
  >
    {{ isLoggingOut ? '退出中...' : '退出登录' }}
  </button>
</template>

<style scoped>
.session-logout-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 2.75rem;
  padding: 0.65rem 1rem;
  font: inherit;
  color: #f4f8fb;
  background: linear-gradient(135deg, #274b68, #2f6b95);
  border: 0;
  border-radius: 999px;
  box-shadow: 0 14px 28px rgba(39, 75, 104, 0.22);
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    opacity 180ms ease;
}

.session-logout-button:hover,
.session-logout-button:focus-visible {
  transform: translateY(-1px);
  outline: none;
}

.session-logout-button:disabled {
  opacity: 0.72;
}
</style>
