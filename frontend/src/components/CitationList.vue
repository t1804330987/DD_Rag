<script setup lang="ts">
import type { CitationItem } from '../api/qa'

withDefaults(
  defineProps<{
    citations: CitationItem[]
    title?: string
    emptyText?: string
  }>(),
  {
    title: '参考文件',
    emptyText: '当前回答没有可展示的参考文件。',
  },
)
</script>

<template>
  <section class="citation-list">
    <header class="citation-list__header">
      <p class="citation-list__eyebrow">Evidence Trail</p>
      <h2>{{ title }}</h2>
      <p class="citation-list__summary">
        将后端返回的文件、片段和相关性分数按证据区形式展开，便于核对回答是否可信。
      </p>
    </header>

    <ul v-if="citations.length > 0" class="citation-list__items">
      <li v-for="citation in citations" :key="citation.fileName">
        <article class="citation-card">
          <div class="citation-card__topline">
            <span class="citation-card__tag">Evidence</span>
            <span class="citation-card__score">Score {{ citation.score.toFixed(3) }}</span>
          </div>

          <div class="citation-card__meta">
            <strong>{{ citation.fileName }}</strong>
            <span v-if="citation.documentId !== null">文档 #{{ citation.documentId }}</span>
            <span v-if="citation.chunkIndex !== null">片段 {{ citation.chunkIndex }}</span>
          </div>

          <p v-if="citation.snippet" class="citation-card__snippet">
            {{ citation.snippet }}
          </p>

          <div class="citation-card__footer">
            <span>{{ citation.chunkId !== null ? `Chunk ID ${citation.chunkId}` : '未返回 Chunk ID' }}</span>
            <span>{{ citation.snippet ? '已携带片段摘要' : '当前仅返回文件级引用' }}</span>
          </div>
        </article>
      </li>
    </ul>

    <p v-else class="citation-list__empty">{{ emptyText }}</p>
  </section>
</template>

<style scoped>
.citation-list {
  display: grid;
  gap: 0.9rem;
}

.citation-list__header {
  display: grid;
  gap: 0.28rem;
}

.citation-list__header h2 {
  margin: 0;
  font-size: 1.02rem;
  color: #163047;
}

.citation-list__eyebrow {
  margin: 0;
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #617189;
}

.citation-list__summary {
  margin: 0;
  color: #60758c;
  font-size: 0.88rem;
  line-height: 1.65;
}

.citation-list__items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}

.citation-card {
  display: grid;
  gap: 0.78rem;
  padding: 1rem 1.05rem;
  border: 1px solid rgba(98, 128, 158, 0.16);
  border-radius: 20px;
  background:
    linear-gradient(145deg, rgba(252, 254, 255, 0.98), rgba(241, 247, 252, 0.92)),
    rgba(246, 249, 253, 0.88);
  box-shadow: 0 14px 34px rgba(16, 32, 51, 0.06);
}

.citation-card__topline,
.citation-card__footer {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
  align-items: center;
}

.citation-card__tag,
.citation-card__score {
  display: inline-flex;
  align-items: center;
  min-height: 1.9rem;
  padding: 0.2rem 0.62rem;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 700;
}

.citation-card__tag {
  background: rgba(47, 107, 149, 0.12);
  color: #244965;
}

.citation-card__score {
  background: rgba(24, 88, 68, 0.1);
  color: #1f6e5b;
}

.citation-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  align-items: center;
}

.citation-card__meta strong {
  color: #163047;
  min-width: 100%;
}

.citation-card__meta span,
.citation-card__footer span {
  color: #60758c;
  font-size: 0.83rem;
  line-height: 1.6;
}

.citation-card__snippet {
  margin: 0;
  padding: 0.85rem 0.9rem;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(98, 128, 158, 0.12);
  color: #22384c;
  line-height: 1.7;
  white-space: pre-wrap;
}

.citation-list__empty {
  margin: 0;
  padding: 1rem 1.05rem;
  border-radius: 20px;
  background: rgba(245, 247, 250, 0.92);
  color: #667085;
  line-height: 1.65;
}
</style>
