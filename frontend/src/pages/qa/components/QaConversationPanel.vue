<script setup lang="ts">
import { computed } from 'vue'
import type { AskQuestionResponse } from '../../../api/qa'
import CitationList from '../../../components/CitationList.vue'
import MarkdownContent from '../../../components/MarkdownContent.vue'
import { humanizeQaRefusalMessage } from '../../../utils/model-error-message'

const props = defineProps<{
  currentGroupId: number | null
  currentGroupName: string
  currentQuestion: string
  result: AskQuestionResponse | null
  askError: string
  isGroupsLoading: boolean
  isSubmitting: boolean
}>()

const refusalMessage = computed(() =>
  humanizeQaRefusalMessage(props.result?.reasonCode, props.result?.reasonMessage),
)

const refusalTitle = computed(() => {
  const code = props.result?.reasonCode
  if (code === 'INSUFFICIENT_EVIDENCE') return '证据不足'
  if (code === 'ANSWER_FORMAT_ERROR') return '模型输出异常'
  if (code && code.startsWith('MODEL_')) return '模型暂不可用'
  if (code === 'PROVIDER_ERROR' || code === 'PROVIDER_RATE_LIMITED') return '模型服务异常'
  return '本次未能回答'
})
</script>

<template>
  <article class="panel panel--wide qa-conversation-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">结果</p>
        <h2>回答与证据</h2>
      </div>
      <span v-if="result" class="panel__pill">
        {{ result.answered ? '已回答' : '拒答' }}
      </span>
    </div>

    <section class="qa-conversation-panel__question-card">
      <div class="qa-conversation-panel__question-meta">
        <span>最近问题</span>
        <strong>{{ currentGroupName }}</strong>
      </div>
      <p v-if="currentQuestion">{{ currentQuestion }}</p>
      <p v-else class="placeholder-text">提问后，这里会显示最近一次问题。</p>
    </section>

    <p v-if="askError" class="feedback feedback--error">{{ askError }}</p>
    <p v-if="isGroupsLoading" class="placeholder-text">正在同步可用知识库…</p>
    <p v-else-if="currentGroupId === null" class="placeholder-text">请先选择知识库，再提问。</p>
    <p v-else-if="isSubmitting" class="placeholder-text">正在检索证据并生成回答…</p>
    <p v-else-if="result === null" class="placeholder-text">输入问题后，这里会显示回答或拒答原因。</p>

    <template v-else>
      <section v-if="result.answered" class="qa-answer-card">
        <div class="qa-answer-card__header">
          <div>
            <p class="qa-answer-card__eyebrow">基于证据</p>
            <h3>模型回答</h3>
          </div>
          <span class="qa-answer-card__badge">可核对</span>
        </div>
        <MarkdownContent
          class="qa-answer-card__body"
          :content="result.answer ?? ''"
          mode="markdown"
          show-copy
        />
        <p class="qa-answer-card__source">回答仅基于当前知识库检索结果，请结合下方证据核对。</p>
      </section>

      <section v-else class="qa-refusal-card">
        <div class="qa-refusal-card__head">
          <strong>{{ refusalTitle }}</strong>
          <span v-if="result.reasonCode">{{ result.reasonCode }}</span>
        </div>
        <p>{{ refusalMessage }}</p>
      </section>

      <CitationList
        :citations="result.citations"
        title="证据"
        :empty-text="result.answered ? '已生成回答，但暂无可用证据片段。' : '拒答场景下暂无证据片段。'"
      />
    </template>
  </article>
</template>
