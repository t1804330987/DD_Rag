<script setup lang="ts">
import type { InstructionProfile } from '../../types/model-platform'

const props = defineProps<{
  profiles: InstructionProfile[]
  profileId: number | null
  disabled: boolean
  loading: boolean
  error: string
}>()

const emit = defineEmits<{ select: [profileId: number | null] }>()

function selectProfile(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  emit('select', value === '' ? null : Number(value))
}
</script>

<template>
  <label class="assistant-selector">
    <span>个人助手指令</span>
    <select :value="profileId ?? ''" :disabled="disabled || loading" @change="selectProfile">
      <option value="">平台默认指令</option>
      <option v-for="profile in profiles.filter((item) => item.enabled)" :key="profile.profileId" :value="profile.profileId">
        {{ profile.name }}{{ profile.isDefault ? ' · 默认' : '' }}
      </option>
    </select>
    <small v-if="error" class="assistant-selector__error">{{ error }}</small>
    <small v-else class="assistant-selector__meta">{{ loading ? '正在加载个人指令...' : '\u00a0' }}</small>
  </label>
</template>
