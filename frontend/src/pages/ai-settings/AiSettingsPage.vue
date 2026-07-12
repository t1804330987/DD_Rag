<script setup lang="ts">
import { ref } from 'vue'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import '../../assets/ai-settings-page.css'
import ModelConnectionsTab from './ModelConnectionsTab.vue'
import InstructionProfilesTab from './InstructionProfilesTab.vue'
import ModelUsageTab from './ModelUsageTab.vue'

type TabKey = 'connections' | 'instructions' | 'usage'

const activeTab = ref<TabKey>('connections')

const tabs: Array<{ key: TabKey; label: string; caption: string }> = [
  { key: 'connections', label: '模型连接', caption: '管理个人 BYOK 连接与可用模型' },
  { key: 'instructions', label: '个人指令', caption: '维护每轮对话注入的人格提示词' },
  { key: 'usage', label: '我的用量', caption: '查看个人模型调用的汇总记录' },
]
</script>

<template>
  <WorkbenchShell class="page-shell--ai-settings">
    <template #sidebar><WorkbenchSidebar /></template>
    <template #main>
      <main class="ai-settings-page">
        <PageHeaderHero
          eyebrow="AI Settings"
          title="个人 AI 设置"
          description="集中管理个人模型连接、对话指令和调用用量。密钥仅在提交时发送，不会写入浏览器存储。"
        />

        <nav class="ai-settings-tabs" aria-label="AI 设置标签">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            :class="{ 'is-active': activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            <strong>{{ tab.label }}</strong>
            <span>{{ tab.caption }}</span>
          </button>
        </nav>

        <ModelConnectionsTab v-if="activeTab === 'connections'" />
        <InstructionProfilesTab v-else-if="activeTab === 'instructions'" />
        <ModelUsageTab v-else />
      </main>
    </template>
  </WorkbenchShell>
</template>
