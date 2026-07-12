<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { fetchMyModelUsage } from '../../api/model-platform'
import { extractApiError } from '../../api/http'
import type { ModelUsageReport, UsageFilter } from '../../types/model-platform'

const report = ref<ModelUsageReport | null>(null)
const isLoading = ref(false)
const error = ref('')
const filter = reactive<UsageFilter>({ logicalStatus: '', transportStatus: '' })

onMounted(() => { void loadUsage() })

async function loadUsage() {
  isLoading.value = true
  error.value = ''
  try {
    report.value = await fetchMyModelUsage(cleanFilter())
  } catch (cause) {
    report.value = null
    error.value = extractApiError(cause, '加载个人用量失败')
  } finally {
    isLoading.value = false
  }
}

function cleanFilter(): UsageFilter {
  return Object.fromEntries(Object.entries(filter).filter(([, value]) => typeof value === 'string' && value.trim() !== ''))
}

function formatNumber(value: number) { return new Intl.NumberFormat('zh-CN').format(value) }
</script>

<template>
  <section class="ai-settings-panel ai-settings-usage">
    <div class="ai-settings-panel__head"><div><span>My Usage</span><h2>个人调用记录</h2></div><button class="ghost-button" type="button" :disabled="isLoading" @click="loadUsage">刷新</button></div>
    <form class="ai-settings-usage-filter" @submit.prevent="loadUsage"><label>Provider<input v-model="filter.providerType" placeholder="例如 OPENAI" /></label><label>模型<input v-model="filter.modelName" placeholder="模型名" /></label><label>场景<input v-model="filter.scenario" placeholder="例如 ASSISTANT" /></label><label>逻辑状态<select v-model="filter.logicalStatus"><option value="">全部</option><option value="SUCCEEDED">成功</option><option value="FAILED">失败</option><option value="CANCELLED">已取消</option></select></label><button class="primary-button" type="submit" :disabled="isLoading">筛选</button></form>
    <p v-if="error" class="feedback feedback--error">{{ error }}</p><p v-else-if="isLoading" class="placeholder-text">正在汇总当前用户的调用记录…</p>
    <template v-else-if="report"><div class="ai-settings-usage-summary"><div><span>调用次数</span><strong>{{ formatNumber(report.invocationCount) }}</strong></div><div><span>总 Token</span><strong>{{ formatNumber(report.totalTokens) }}</strong></div><div><span>输入 / 输出</span><strong>{{ formatNumber(report.inputTokens) }} / {{ formatNumber(report.outputTokens) }}</strong></div><div><span>累计时长</span><strong>{{ formatNumber(report.durationMs) }} ms</strong></div></div>
      <p v-if="report.groups.length === 0" class="placeholder-text">当前筛选条件下没有调用记录。</p>
      <div v-else class="ai-settings-usage-table-wrap"><table><thead><tr><th>Provider / 模型</th><th>场景</th><th>状态</th><th>调用</th><th>Token</th><th>时长</th></tr></thead><tbody><tr v-for="group in report.groups" :key="[group.providerType, group.modelName, group.scenario, group.logicalStatus, group.transportStatus].join('-')"><td><strong>{{ group.providerType }}</strong><span>{{ group.modelName }}</span></td><td>{{ group.scenario }}</td><td><span class="ai-settings-status-pair">{{ group.logicalStatus }} / {{ group.transportStatus }}</span></td><td>{{ formatNumber(group.invocationCount) }}</td><td>{{ formatNumber(group.totalTokens) }}</td><td>{{ formatNumber(group.durationMs) }} ms</td></tr></tbody></table></div></template>
  </section>
</template>
