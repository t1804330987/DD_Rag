<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'default' | 'inline'
    workspaceName: string
    workspaceMeta: string
    relationLabel: string
    questionContext: string
    evidenceSummary: string
    usageHints: string[]
  }>(),
  {
    variant: 'default',
  },
)
</script>

<template>
  <section class="qa-context-aside" :class="`qa-context-aside--${variant}`">
    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">Workspace</p>
      <h2>当前知识库</h2>
      <strong>{{ workspaceName }}</strong>
      <p>{{ workspaceMeta }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">Role</p>
      <h2>当前角色边界</h2>
      <div class="qa-context-aside__badge">{{ relationLabel }}</div>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">Question Context</p>
      <h2>当前问题上下文</h2>
      <p>{{ questionContext }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">Evidence</p>
      <h2>证据摘要</h2>
      <p>{{ evidenceSummary }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">Tips</p>
      <h2>使用提示</h2>
      <ul class="qa-context-aside__list">
        <li v-for="item in usageHints" :key="item">{{ item }}</li>
      </ul>
    </section>
  </section>
</template>
