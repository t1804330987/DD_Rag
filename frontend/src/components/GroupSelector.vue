<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { fetchGroups } from '../api/group'
import { extractApiError } from '../api/http'
import { useAppStore } from '../stores/app'
import { useAuthStore } from '../stores/auth'

const appStore = useAppStore()
const authStore = useAuthStore()
const errorMessage = ref('')
let requestToken = 0

const groups = computed(() => appStore.visibleGroups)
const isLoading = computed(() => appStore.isGroupsLoading)
const currentGroupValue = computed(() =>
  appStore.currentGroupId === null ? '' : String(appStore.currentGroupId),
)

async function refreshGroups() {
  const currentToken = ++requestToken
  appStore.setGroupsLoading(true)
  errorMessage.value = ''

  try {
    const groupQueryResult = await fetchGroups()

    if (currentToken !== requestToken) {
      return
    }

    appStore.applyGroupQueryResult(groupQueryResult)
  } catch (error) {
    if (currentToken !== requestToken) {
      return
    }

    appStore.resetGroupContext(false)
    errorMessage.value = extractApiError(error, '获取群组失败')
  } finally {
    if (currentToken === requestToken) {
      appStore.setGroupsLoading(false)
    }
  }
}

function handleGroupChange(event: Event) {
  const nextValue = (event.target as HTMLSelectElement).value
  appStore.setCurrentGroupId(nextValue.length === 0 ? null : Number(nextValue))
}

watch(
  () => authStore.currentUser?.userId,
  () => {
    void refreshGroups()
  },
  { immediate: true },
)

defineExpose({
  refresh: refreshGroups,
})
</script>

<template>
  <section class="group-card">
    <div class="group-card__header">
      <div>
        <p class="group-card__eyebrow">Group Scope</p>
        <h2>选择当前群组</h2>
      </div>
      <button type="button" class="group-card__refresh" :disabled="isLoading" @click="refreshGroups">
        {{ isLoading ? '刷新中...' : '刷新群组' }}
      </button>
    </div>

    <div class="group-card__controls">
      <label class="group-card__field">
        <span>当前可见群组</span>
        <select
          :value="currentGroupValue"
          :disabled="isLoading || groups.length === 0"
          @change="handleGroupChange"
        >
          <option value="" disabled>请选择群组</option>
          <option v-for="group in groups" :key="group.groupId" :value="group.groupId">
            {{ group.groupName }} · {{ group.relation === 'OWNER' ? '我拥有的组' : '我加入的组' }}
          </option>
        </select>
      </label>
    </div>

    <p v-if="errorMessage" class="group-card__message group-card__message--error">
      {{ errorMessage }}
    </p>
    <p v-else-if="isLoading" class="group-card__message">正在同步当前登录用户的群组列表。</p>
    <p v-else-if="groups.length === 0" class="group-card__message">
      当前登录用户暂无可见群组。
    </p>
    <p v-else class="group-card__message">
      已同步 {{ groups.length }} 个群组和 {{ appStore.pendingInvitations.length }} 条待处理邀请。
    </p>
  </section>
</template>

<style scoped>
.group-card {
  display: grid;
  gap: 0.9rem;
  padding: 1rem 1.1rem;
  border: 1px solid rgba(29, 35, 53, 0.1);
  border-radius: 22px;
  background:
    linear-gradient(145deg, rgba(248, 250, 255, 0.98), rgba(240, 248, 246, 0.92));
  box-shadow: 0 18px 50px rgba(18, 40, 30, 0.08);
}

.group-card__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
}

.group-card__header h2 {
  margin: 0;
  font-size: 1.05rem;
  color: #1f2432;
}

.group-card__eyebrow {
  margin: 0 0 0.2rem;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #1f7665;
}

.group-card__refresh {
  border: 1px solid rgba(31, 118, 101, 0.2);
  border-radius: 999px;
  padding: 0.55rem 0.95rem;
  background: rgba(255, 255, 255, 0.8);
  color: #1f544a;
}

.group-card__field {
  display: grid;
  gap: 0.45rem;
}

.group-card__field span {
  font-size: 0.82rem;
  font-weight: 600;
  color: #475166;
}

.group-card__field select {
  width: 100%;
  min-height: 2.9rem;
  border: 1px solid rgba(31, 118, 101, 0.16);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  padding: 0 0.9rem;
  color: #1f2432;
}

.group-card__message {
  margin: 0;
  font-size: 0.82rem;
  color: #5d6678;
}

.group-card__message--error {
  color: #b03b2a;
}

@media (max-width: 720px) {
  .group-card__header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
