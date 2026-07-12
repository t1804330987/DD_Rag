<script setup lang="ts">
import { computed } from 'vue'
import type { VisibleGroup } from '../../../stores/app'

interface SelectOption {
  value: string
  label: string
}

const emit = defineEmits<{
  'change:groupId': [groupId: number | null]
  'change:fileName': [value: string]
  'change:status': [value: string]
  'refresh-groups': []
  'file-change': [file: File | null]
  'upload': []
}>()

const props = defineProps<{
  groups: VisibleGroup[]
  currentGroupId: number | null
  currentGroup: VisibleGroup | null
  isGroupsLoading: boolean
  fileName: string
  status: string
  statusOptions: ReadonlyArray<SelectOption>
  selectedFileName: string
  fileInputKey: number
  canManageCurrentGroup: boolean
  isUploading: boolean
}>()

const ownedGroups = computed(() => props.groups.filter((group) => group.relation === 'OWNER'))
const joinedGroups = computed(() => props.groups.filter((group) => group.relation === 'MEMBER'))

function handleGroupChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  emit('change:groupId', value ? Number(value) : null)
}

function handleFileChange(event: Event) {
  emit('file-change', (event.target as HTMLInputElement).files?.[0] ?? null)
}
</script>

<template>
  <section class="document-toolbar">
    <div class="document-toolbar__primary">
      <label class="document-toolbar__field document-toolbar__field--group">
        <span>知识库</span>
        <div class="document-toolbar__select-wrap">
          <select :value="props.currentGroupId ?? ''" :disabled="props.isGroupsLoading" @change="handleGroupChange">
            <option value="">请选择知识库</option>
            <optgroup v-if="ownedGroups.length > 0" label="我拥有的">
              <option v-for="group in ownedGroups" :key="`owner-${group.groupId}`" :value="group.groupId">
                {{ group.groupName }} · 所有者
              </option>
            </optgroup>
            <optgroup v-if="joinedGroups.length > 0" label="我加入的">
              <option v-for="group in joinedGroups" :key="`member-${group.groupId}`" :value="group.groupId">
                {{ group.groupName }} · 成员
              </option>
            </optgroup>
          </select>
          <button type="button" class="ghost-button" :disabled="props.isGroupsLoading" @click="emit('refresh-groups')">
            {{ props.isGroupsLoading ? '同步中…' : '刷新' }}
          </button>
        </div>
      </label>

      <label class="document-toolbar__field">
        <span>文件名</span>
        <input
          :value="props.fileName"
          type="search"
          maxlength="128"
          placeholder="搜索当前组文件"
          @input="emit('change:fileName', ($event.target as HTMLInputElement).value)"
        />
      </label>

      <label class="document-toolbar__field">
        <span>状态</span>
        <select :value="props.status" @change="emit('change:status', ($event.target as HTMLSelectElement).value)">
          <option v-for="option in props.statusOptions" :key="`status-${option.value || 'all'}`" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </div>

    <div v-if="props.currentGroup && props.canManageCurrentGroup" class="document-toolbar__upload">
      <div class="document-toolbar__upload-copy">
        <span class="document-toolbar__label">上传</span>
        <strong>{{ props.selectedFileName }}</strong>
        <p>上传到当前知识库，列表会自动同步处理状态。</p>
      </div>

      <div class="document-toolbar__upload-actions">
        <label class="document-toolbar__upload-picker">
          <input :key="fileInputKey" type="file" @change="handleFileChange" />
          <span>选择文件</span>
        </label>
        <button type="button" class="primary-button" :disabled="props.isUploading" @click="emit('upload')">
          {{ props.isUploading ? '上传中…' : '上传' }}
        </button>
      </div>
    </div>

    <div v-else-if="props.currentGroup" class="document-toolbar__readonly">
      <p class="filter-hint">成员身份访问「{{ props.currentGroup.groupName }}」：可筛选与预览，不可上传。</p>
    </div>
  </section>
</template>
