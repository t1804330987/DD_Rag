<script setup lang="ts">
import { computed } from 'vue'
import type { AssistantAvailableModel } from '../../types/assistant'

const props = defineProps<{
  models: AssistantAvailableModel[]
  connectionId: number | null
  modelId: number | null
  disabled: boolean
  loading: boolean
  error: string
}>()

const emit = defineEmits<{ select: [model: AssistantAvailableModel] }>()

const value = computed(() =>
  props.connectionId === null || props.modelId === null ? '' : `${props.connectionId}:${props.modelId}`,
)

function selectModel(event: Event) {
  const selected = (event.target as HTMLSelectElement).value
  const model = props.models.find((item) => `${item.connectionId}:${item.modelId}` === selected)
  if (model) emit('select', model)
}
</script>

<template>
  <label class="assistant-selector">
    <span>回答模型</span>
    <select :value="value" :disabled="disabled || loading || models.length === 0" @change="selectModel">
      <option value="" disabled>{{ loading ? '正在加载模型...' : models.length === 0 ? '暂无可用模型' : '请选择模型' }}</option>
      <option v-for="model in models" :key="`${model.connectionId}:${model.modelId}`" :value="`${model.connectionId}:${model.modelId}`">
        {{ model.modelName }} · {{ model.providerType }} · {{ model.ownerType === 'PLATFORM' ? '平台' : '个人 BYOK' }}
      </option>
    </select>
    <small v-if="error" class="assistant-selector__error">{{ error }}</small>
    <small v-else class="assistant-selector__meta">{{ value ? '已绑定当前会话' : '\u00a0' }}</small>
  </label>
</template>
