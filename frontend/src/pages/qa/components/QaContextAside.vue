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
      <p class="panel__eyebrow">知识库</p>
      <h2>当前范围</h2>
      <strong>{{ workspaceName }}</strong>
      <p>{{ workspaceMeta }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">角色</p>
      <h2>权限边界</h2>
      <div class="qa-context-aside__badge">{{ relationLabel }}</div>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">问题</p>
      <h2>当前上下文</h2>
      <p>{{ questionContext }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">证据</p>
      <h2>检索摘要</h2>
      <p>{{ evidenceSummary }}</p>
    </section>

    <section class="qa-context-aside__section">
      <p class="panel__eyebrow">提示</p>
      <h2>使用建议</h2>
      <ul class="qa-context-aside__list">
        <li v-for="item in usageHints" :key="item">{{ item }}</li>
      </ul>
    </section>
  </section>
</template>
