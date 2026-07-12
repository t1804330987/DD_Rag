<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  copyInstructionProfile,
  createInstructionProfile,
  deleteInstructionProfile,
  fetchInstructionProfiles,
  setInstructionProfileDefault,
  setInstructionProfileEnabled,
  updateInstructionProfile,
} from '../../api/model-platform'
import { extractApiError } from '../../api/http'
import type { InstructionProfile } from '../../types/model-platform'

const profiles = ref<InstructionProfile[]>([])
const isLoading = ref(false)
const isSaving = ref(false)
const feedback = ref('')
const error = ref('')
const editingId = ref<number | null>(null)
const form = reactive({ name: '', content: '', makeDefault: false })
const editing = computed(() => editingId.value !== null)

onMounted(() => { void loadProfiles() })

async function loadProfiles() {
  isLoading.value = true
  try { profiles.value = await fetchInstructionProfiles() }
  catch (cause) { error.value = extractApiError(cause, '加载个人指令失败') }
  finally { isLoading.value = false }
}

function resetForm() { editingId.value = null; Object.assign(form, { name: '', content: '', makeDefault: false }); error.value = '' }
function beginEdit(profile: InstructionProfile) { editingId.value = profile.profileId; Object.assign(form, { name: profile.name, content: profile.content, makeDefault: profile.isDefault }); feedback.value = ''; error.value = '' }

async function save() {
  isSaving.value = true; error.value = ''; feedback.value = ''
  try {
    if (editingId.value === null) await createInstructionProfile(form)
    else {
      await updateInstructionProfile(editingId.value, form)
    }
    resetForm(); feedback.value = '个人指令已保存，新内容已形成不可变版本。'; await loadProfiles()
  } catch (cause) { error.value = extractApiError(cause, '保存个人指令失败') }
  finally { isSaving.value = false }
}

async function run(operation: () => Promise<void>, success: string) {
  error.value = ''; feedback.value = ''
  try { await operation(); feedback.value = success; await loadProfiles() }
  catch (cause) { error.value = extractApiError(cause, '操作个人指令失败') }
}

function copy(profile: InstructionProfile) { void run(async () => { await copyInstructionProfile(profile.profileId, `${profile.name} 副本`) }, '个人指令已复制。') }
function toggle(profile: InstructionProfile) { void run(() => setInstructionProfileEnabled(profile.profileId, !profile.enabled), profile.enabled ? '个人指令已停用。' : '个人指令已启用。') }
function makeDefault(profile: InstructionProfile) { void run(() => setInstructionProfileDefault(profile.profileId), '已设置为默认个人指令。') }
function remove(profile: InstructionProfile) { if (window.confirm(`确认删除个人指令「${profile.name}」吗？`)) void run(() => deleteInstructionProfile(profile.profileId), '个人指令已删除。') }
</script>

<template>
  <section class="ai-settings-grid ai-settings-grid--instructions">
    <form class="ai-settings-panel ai-settings-instruction-form" @submit.prevent="save">
      <div class="ai-settings-panel__head"><div><span>Personal Instructions</span><h2>{{ editing ? '编辑个人指令' : '新建个人指令' }}</h2></div><button v-if="editing" class="ai-settings-icon-button" type="button" title="取消编辑" @click="resetForm">×</button></div>
      <label>名称<input v-model="form.name" required maxlength="128" placeholder="例如：严谨技术顾问" /></label>
      <label>指令内容<textarea v-model="form.content" required maxlength="8000" rows="9" placeholder="每轮对话都会注入此内容" /></label>
      <label class="ai-settings-checkbox"><input v-model="form.makeDefault" type="checkbox" />设为默认指令</label>
      <button class="primary-button" type="submit" :disabled="isSaving">{{ isSaving ? '正在保存' : editing ? '保存修改' : '创建指令' }}</button>
    </form>
    <section class="ai-settings-panel ai-settings-instruction-list">
      <div class="ai-settings-panel__head"><div><span>Profiles</span><h2>我的个人指令</h2></div><button class="ghost-button" type="button" :disabled="isLoading" @click="loadProfiles">刷新</button></div>
      <p v-if="error" class="feedback feedback--error">{{ error }}</p><p v-else-if="feedback" class="feedback feedback--success">{{ feedback }}</p>
      <p v-if="isLoading" class="placeholder-text">正在加载个人指令…</p><p v-else-if="profiles.length === 0" class="placeholder-text">尚未创建个人指令。</p>
      <article v-for="profile in profiles" :key="profile.profileId" class="ai-settings-connection-card">
        <header><div><strong>{{ profile.name }}</strong><span>版本 {{ profile.version }} · {{ profile.enabled ? '已启用' : '已停用' }}</span></div><b v-if="profile.isDefault" class="status-active">默认</b></header>
        <p class="ai-settings-instruction-preview">{{ profile.content }}</p>
        <div class="ai-settings-card-actions"><button class="ghost-button" type="button" @click="beginEdit(profile)">编辑</button><button class="ghost-button" type="button" @click="copy(profile)">复制</button><button v-if="profile.enabled && !profile.isDefault" class="ghost-button" type="button" @click="makeDefault(profile)">设为默认</button><button class="ghost-button" type="button" @click="toggle(profile)">{{ profile.enabled ? '停用' : '启用' }}</button><button class="ai-settings-danger-button" type="button" @click="remove(profile)">删除</button></div>
      </article>
    </section>
  </section>
</template>
