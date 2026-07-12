<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'default' | 'inline'
  workspaceName: string
  workspaceMeta: string
  relationLabel: string
  availableActions: string[]
  filterContext: string[]
}>(), {
  variant: 'default',
})
</script>

<template>
  <section class="document-context-aside" :class="`document-context-aside--${variant}`">
    <section class="document-context-aside__section">
      <p class="panel__eyebrow">知识库</p>
      <h2>当前范围</h2>
      <strong>{{ workspaceName }}</strong>
      <p>{{ workspaceMeta }}</p>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">角色</p>
      <h2>当前身份</h2>
      <div class="document-context-aside__badge">{{ relationLabel }}</div>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">动作</p>
      <h2>本页可执行</h2>
      <ul class="document-context-aside__list">
        <li v-for="item in availableActions" :key="item">{{ item }}</li>
      </ul>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">筛选</p>
      <h2>当前条件</h2>
      <ul class="document-context-aside__list">
        <li v-for="item in filterContext" :key="item">{{ item }}</li>
      </ul>
    </section>
  </section>
</template>
