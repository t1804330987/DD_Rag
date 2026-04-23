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
      <p class="panel__eyebrow">Workspace</p>
      <h2>当前知识库</h2>
      <strong>{{ workspaceName }}</strong>
      <p>{{ workspaceMeta }}</p>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">Role</p>
      <h2>当前角色</h2>
      <div class="document-context-aside__badge">{{ relationLabel }}</div>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">Actions</p>
      <h2>当前页可执行动作</h2>
      <ul class="document-context-aside__list">
        <li v-for="item in availableActions" :key="item">{{ item }}</li>
      </ul>
    </section>

    <section class="document-context-aside__section">
      <p class="panel__eyebrow">Filters</p>
      <h2>当前筛选上下文</h2>
      <ul class="document-context-aside__list">
        <li v-for="item in filterContext" :key="item">{{ item }}</li>
      </ul>
    </section>
  </section>
</template>
