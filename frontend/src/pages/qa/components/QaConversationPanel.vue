<script setup lang="ts">
import type { AskQuestionResponse } from '../../../api/qa'
import CitationList from '../../../components/CitationList.vue'

defineProps<{
  currentGroupId: number | null
  currentGroupName: string
  currentQuestion: string
  result: AskQuestionResponse | null
  askError: string
  isGroupsLoading: boolean
  isSubmitting: boolean
}>()
</script>

<template>
  <article class="panel panel--wide qa-conversation-panel">
    <div class="panel__header">
      <div>
        <p class="panel__eyebrow">Answer Result</p>
        <h2>回答结果与证据核对</h2>
      </div>
      <span v-if="result" class="panel__pill">
        {{ result.answered ? '已回答' : '拒答' }}
      </span>
    </div>

    <section class="qa-conversation-panel__question-card">
      <div class="qa-conversation-panel__question-meta">
        <span>Current Question</span>
        <strong>{{ currentGroupName }}</strong>
      </div>
      <p v-if="currentQuestion">{{ currentQuestion }}</p>
      <p v-else class="placeholder-text">发起问答后，这里会记录最近一次问题。</p>
    </section>

    <p v-if="askError" class="feedback feedback--error">{{ askError }}</p>
    <p v-if="isGroupsLoading" class="placeholder-text">正在同步当前登录用户的群组上下文...</p>
    <p v-else-if="currentGroupId === null" class="placeholder-text">请先选择可见群组，然后再发起问答。</p>
    <p v-else-if="isSubmitting" class="placeholder-text">正在检索群组内证据并生成回答...</p>
    <p v-else-if="result === null" class="placeholder-text">输入问题后即可查看回答或拒答原因。</p>

    <template v-else>
      <section v-if="result.answered" class="qa-answer-card">
        <div class="qa-answer-card__header">
          <div>
            <p class="qa-answer-card__eyebrow">Grounded Answer</p>
            <h3>模型回答</h3>
          </div>
          <span class="qa-answer-card__badge">Evidence Bound</span>
        </div>
        <p class="qa-answer-card__body">{{ result.answer }}</p>
        <p class="qa-answer-card__source">回答正文只基于当前组选中的检索证据生成，建议结合下方证据区交叉核对。</p>
      </section>

      <section v-else class="qa-refusal-card">
        <div class="qa-refusal-card__head">
          <strong>本次拒答</strong>
          <span>{{ result.reasonCode ?? 'NO_REASON_CODE' }}</span>
        </div>
        <p>{{ result.reasonMessage ?? '当前证据不足，无法给出可靠回答。' }}</p>
      </section>

      <CitationList
        :citations="result.citations"
        title="证据区"
        :empty-text="result.answered ? '回答已生成，但后端当前没有返回可展示的证据片段。' : '拒答场景当前没有可展示的证据片段。'"
      />
    </template>
  </article>
</template>
