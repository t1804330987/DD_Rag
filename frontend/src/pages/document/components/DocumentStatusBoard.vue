<script setup lang="ts">
import type { DocumentFailureItem, DocumentStatusSummaryItem } from '../documentPageView'

defineProps<{
  summaryItems: DocumentStatusSummaryItem[]
  recentFailures: DocumentFailureItem[]
  resultsSummary: string
  matchedCount: number
}>()
</script>

<template>
  <section class="document-status-board">
    <div class="document-status-board__summary">
      <article
        v-for="item in summaryItems"
        :key="item.key"
        class="document-status-board__card"
        :data-tone="item.tone"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.description }}</p>
      </article>
    </div>

    <div class="document-status-board__details">
      <article class="document-status-board__panel">
        <div class="document-status-board__panel-header">
          <div>
            <p class="panel__eyebrow">Matches</p>
            <h2>当前筛选命中</h2>
          </div>
          <strong class="document-status-board__highlight">{{ matchedCount }}</strong>
        </div>
        <p class="document-status-board__summary-text">{{ resultsSummary }}</p>
      </article>

      <article class="document-status-board__panel">
        <div class="document-status-board__panel-header">
          <div>
            <p class="panel__eyebrow">Recent Failures</p>
            <h2>最近异常</h2>
          </div>
        </div>

        <ul v-if="recentFailures.length > 0" class="document-status-board__failure-list">
          <li v-for="item in recentFailures" :key="item.documentId">
            <strong>{{ item.fileName }}</strong>
            <span>{{ item.reason }}</span>
            <small>{{ item.uploadedAt }}</small>
          </li>
        </ul>
        <p v-else class="document-status-board__summary-text">当前筛选结果里没有新的失败文件。</p>
      </article>
    </div>
  </section>
</template>
