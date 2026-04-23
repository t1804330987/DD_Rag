<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import {
  deleteDocument,
  fetchDocumentPreview,
  fetchDocuments,
  uploadDocument,
  type DocumentItem,
} from '../../api/document'
import { fetchGroups } from '../../api/group'
import { extractApiError } from '../../api/http'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import { useAppStore } from '../../stores/app'
import { useAuthStore } from '../../stores/auth'
import {
  DOCUMENT_STATUS_OPTIONS,
  calculateTotalDocumentSize,
  canPreviewDocument,
  collectRecentDocumentFailures,
  createDocumentFilterForm,
  createDocumentStatusSummary,
  formatDocumentDateTime,
  formatDocumentFileSize,
  formatGroupRelationLabel,
  formatUploaderLabel,
  getDocumentStatusMeta,
  getPreviewButtonLabel,
  matchesDocumentFilters,
  truncatePreviewText,
} from './documentPageView'
import '../../assets/page-shell.css'
import '../../assets/document-page.css'
import DocumentPageToolbar from './components/DocumentPageToolbar.vue'
import DocumentStatusBoard from './components/DocumentStatusBoard.vue'

const appStore = useAppStore()
const authStore = useAuthStore()

const documents = ref<DocumentItem[]>([])
const filters = reactive(createDocumentFilterForm())
const selectedFile = ref<File | null>(null)
const selectedFileName = ref('未选择文件')
const fileInputKey = ref(0)
const groupLoadError = ref('')
const documentsError = ref('')
const uploadFeedback = ref('')
const uploadError = ref('')
const isLoading = ref(false)
const isUploading = ref(false)
const deletingDocumentIds = ref<Set<number>>(new Set())
const isPreviewOpen = ref(false)
const isPreviewLoading = ref(false)
const previewDocumentId = ref<number | null>(null)
const previewFileName = ref('')
const previewStatus = ref('')
const previewText = ref('')
const previewMessage = ref('')
const previewMessageTone = ref<'note' | 'error'>('note')

let documentContextVersion = 0
let latestLoadRequestId = 0
let latestPreviewRequestId = 0
let latestGroupRequestToken = 0

const visibleGroups = computed(() => appStore.visibleGroups)
const currentGroup = computed(() => appStore.currentGroup)
const currentGroupId = computed(() => currentGroup.value?.groupId ?? null)
const currentGroupRelation = computed(() => currentGroup.value?.relation ?? null)
const canLoadDocuments = computed(() => currentGroupId.value !== null && !appStore.isGroupsLoading)
const canManageCurrentGroup = computed(() => canLoadDocuments.value && appStore.canManageCurrentGroup)
const visibleDocuments = computed(() =>
  documents.value.filter((item) => matchesDocumentFilters(item, filters, currentGroupRelation.value)),
)
const totalSize = computed(() => calculateTotalDocumentSize(visibleDocuments.value))
const statusSummaryItems = computed(() =>
  createDocumentStatusSummary(visibleDocuments.value, totalSize.value),
)
const recentFailures = computed(() => collectRecentDocumentFailures(visibleDocuments.value))
const currentContextKey = computed(
  () => `${authStore.currentUser?.userId ?? 'anonymous'}:${currentGroupId.value ?? 'none'}`,
)
const pageHeroDescription = computed(() =>
  currentGroup.value === null
    ? '先锁定知识库空间，再按文件名、状态和时间窗口读取当前结果。'
    : `当前聚焦「${currentGroup.value.groupName}」，主区优先展示筛选结果与异常文件。`,
)
const groupScopeSummary = computed(() => {
  if (currentGroup.value === null) {
    return `当前登录用户拥有 ${appStore.ownedGroups.length} 个 OWNER 空间，加入 ${appStore.joinedGroups.length} 个 MEMBER 空间。`
  }

  return `当前空间 ID #${currentGroup.value.groupId}，所属关系为 ${formatGroupRelationLabel(currentGroup.value.relation)}。`
})
const filterHint = computed(() => {
  if (currentGroup.value === null) {
    return '请先选择知识库空间，再对当前组内文件做筛选。'
  }

  return '当前可按文件名、状态与上传时间窗口收窄结果。'
})
const resultsSummary = computed(() => {
  if (documents.value.length === visibleDocuments.value.length) {
    return `当前共展示 ${visibleDocuments.value.length} 个文件。`
  }

  return `服务端返回 ${documents.value.length} 个文件，当前筛选命中 ${visibleDocuments.value.length} 个。`
})
const emptyStateMessage = computed(() => {
  if (documents.value.length === 0) {
    return canManageCurrentGroup.value
      ? '当前组还没有文件，可先上传一个样例文件。'
      : '当前组还没有可查看的文件。'
  }

  return '没有匹配当前筛选条件的文件。'
})

watch(
  () => authStore.currentUser?.userId,
  () => {
    void refreshGroups()
  },
  { immediate: true },
)

watch(
  [() => authStore.currentUser?.userId, currentGroupId, () => appStore.isGroupsLoading],
  () => {
    documentContextVersion += 1
    latestLoadRequestId += 1
    latestPreviewRequestId += 1
    resetPageForContextChange()
    syncDefaultFilters()
    if (canLoadDocuments.value) {
      void loadDocuments()
    }
  },
  { immediate: true },
)

async function refreshGroups() {
  const currentToken = ++latestGroupRequestToken
  appStore.setGroupsLoading(true)
  groupLoadError.value = ''

  try {
    const result = await fetchGroups()
    if (currentToken !== latestGroupRequestToken) {
      return
    }
    appStore.applyGroupQueryResult(result)
  } catch (error) {
    if (currentToken !== latestGroupRequestToken) {
      return
    }
    appStore.resetGroupContext(false)
    groupLoadError.value = extractApiError(error, '获取群组失败')
  } finally {
    if (currentToken === latestGroupRequestToken) {
      appStore.setGroupsLoading(false)
    }
  }
}

function resetPageForContextChange() {
  documents.value = []
  isLoading.value = false
  isUploading.value = false
  deletingDocumentIds.value = new Set()
  documentsError.value = ''
  uploadFeedback.value = ''
  uploadError.value = ''
  resetSelectedFile()
  closePreview()
}

function syncDefaultFilters() {
  Object.assign(
    filters,
    createDocumentFilterForm({
      groupId: currentGroupId.value,
      relation: currentGroupRelation.value,
    }),
  )
}

async function loadDocuments() {
  if (!canLoadDocuments.value || currentGroupId.value === null) {
    documents.value = []
    return
  }

  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  const requestId = ++latestLoadRequestId
  isLoading.value = true
  documentsError.value = ''

  try {
    const nextDocuments = await fetchDocuments({
      groupId: filters.groupId ?? currentGroupId.value ?? undefined,
      fileName: filters.fileName.trim() || undefined,
      status: filters.status || undefined,
      uploadedFrom: filters.uploadedFrom || undefined,
      uploadedTo: filters.uploadedTo || undefined,
    })
    if (isActiveDocumentRequest(contextVersion, contextKey, requestId)) {
      documents.value = nextDocuments
    }
  } catch (error) {
    if (isActiveDocumentRequest(contextVersion, contextKey, requestId)) {
      documents.value = []
      documentsError.value = extractApiError(error, '加载文档列表失败')
    }
  } finally {
    if (isActiveDocumentRequest(contextVersion, contextKey, requestId)) {
      isLoading.value = false
    }
  }
}

function handleGroupChange(groupId: number | null) {
  appStore.setCurrentGroupId(groupId)
}

function handleFileChange(file: File | null) {
  selectedFile.value = file
  selectedFileName.value = file?.name ?? '未选择文件'
  uploadFeedback.value = ''
  uploadError.value = ''
}

function resetSelectedFile() {
  selectedFile.value = null
  selectedFileName.value = '未选择文件'
  fileInputKey.value += 1
}

async function handleApplyFilters() {
  if (!canLoadDocuments.value) {
    return
  }

  if (filters.uploadedFrom && filters.uploadedTo && filters.uploadedFrom > filters.uploadedTo) {
    documentsError.value = '上传时间范围不合法，开始时间不能晚于结束时间。'
    return
  }

  await loadDocuments()
}

function handleResetFilters() {
  syncDefaultFilters()
  documentsError.value = ''
  if (canLoadDocuments.value) {
    void loadDocuments()
  }
}

async function handleUpload() {
  if (!canManageCurrentGroup.value || currentGroupId.value === null) {
    uploadError.value = '当前组为只读模式，只有 OWNER 可以上传文件。'
    return
  }

  if (selectedFile.value === null) {
    uploadError.value = '请选择待上传文件。'
    return
  }

  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  isUploading.value = true
  uploadFeedback.value = ''
  uploadError.value = ''

  try {
    const documentId = await uploadDocument({
      groupId: currentGroupId.value,
      file: selectedFile.value,
    })
    if (!isCurrentDocumentContext(contextVersion, contextKey)) {
      return
    }
    uploadFeedback.value = `文件已提交，文档 ID #${documentId}。若仍在处理中，可稍后刷新。`
    resetSelectedFile()
    await loadDocuments()
  } catch (error) {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      uploadError.value = extractApiError(error, '上传文档失败')
    }
  } finally {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      isUploading.value = false
    }
  }
}

async function handleDelete(documentId: number, fileName: string) {
  if (!canManageCurrentGroup.value || currentGroupId.value === null) {
    return
  }

  if (!window.confirm(`确认删除文档「${fileName}」吗？`)) {
    return
  }

  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  deletingDocumentIds.value = new Set(deletingDocumentIds.value).add(documentId)
  documentsError.value = ''

  try {
    await deleteDocument(documentId, currentGroupId.value)
    if (!isCurrentDocumentContext(contextVersion, contextKey)) {
      return
    }
    uploadFeedback.value = `文档「${fileName}」已删除。`
    await loadDocuments()
  } catch (error) {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      documentsError.value = extractApiError(error, '删除文档失败')
    }
  } finally {
    if (isCurrentDocumentContext(contextVersion, contextKey)) {
      const nextDeletingIds = new Set(deletingDocumentIds.value)
      nextDeletingIds.delete(documentId)
      deletingDocumentIds.value = nextDeletingIds
    }
  }
}

async function handlePreview(item: DocumentItem) {
  if (currentGroupId.value === null || !canPreviewDocument(item, currentGroupRelation.value)) {
    return
  }

  const cachedPreview = truncatePreviewText(item.previewText)
  const contextVersion = documentContextVersion
  const contextKey = currentContextKey.value
  const requestId = ++latestPreviewRequestId

  previewDocumentId.value = item.documentId
  previewFileName.value = item.fileName
  previewStatus.value = item.status
  previewText.value = cachedPreview
  previewMessage.value = cachedPreview ? '正在同步最新预览...' : ''
  previewMessageTone.value = 'note'
  isPreviewOpen.value = true
  isPreviewLoading.value = true

  try {
    const preview = await fetchDocumentPreview(item.documentId, currentGroupId.value)
    if (!isActivePreviewRequest(contextVersion, contextKey, requestId)) {
      return
    }
    const nextPreview = truncatePreviewText(preview.previewText) || cachedPreview
    previewFileName.value = preview.fileName || item.fileName
    previewStatus.value = preview.status || item.status
    previewText.value = nextPreview
    previewMessage.value = nextPreview ? '' : '当前文件暂无可展示的前 200 字预览。'
    previewMessageTone.value = 'note'
  } catch (error) {
    if (!isActivePreviewRequest(contextVersion, contextKey, requestId)) {
      return
    }
    if (cachedPreview) {
      previewText.value = cachedPreview
      previewMessage.value = '预览接口暂不可用，已显示列表缓存片段。'
      previewMessageTone.value = 'note'
      return
    }
    previewText.value = ''
    previewMessage.value = extractApiError(error, '加载预览失败')
    previewMessageTone.value = 'error'
  } finally {
    if (isActivePreviewRequest(contextVersion, contextKey, requestId)) {
      isPreviewLoading.value = false
    }
  }
}

function closePreview() {
  latestPreviewRequestId += 1
  isPreviewOpen.value = false
  isPreviewLoading.value = false
  previewDocumentId.value = null
  previewFileName.value = ''
  previewStatus.value = ''
  previewText.value = ''
  previewMessage.value = ''
  previewMessageTone.value = 'note'
}

function isCurrentDocumentContext(contextVersion: number, contextKey: string) {
  return (
    contextVersion === documentContextVersion &&
    contextKey === currentContextKey.value &&
    !appStore.isGroupsLoading
  )
}

function isActiveDocumentRequest(contextVersion: number, contextKey: string, requestId: number) {
  return isCurrentDocumentContext(contextVersion, contextKey) && requestId === latestLoadRequestId
}

function isActivePreviewRequest(contextVersion: number, contextKey: string, requestId: number) {
  return (
    isCurrentDocumentContext(contextVersion, contextKey) &&
    requestId === latestPreviewRequestId &&
    isPreviewOpen.value
  )
}

function describeDocumentRow(item: DocumentItem) {
  return item.contentType ?? item.fileExt ?? '未知类型'
}
</script>

<template>
  <WorkbenchShell class="page-shell--documents">
    <template #sidebar>
      <WorkbenchSidebar />
    </template>

    <template #main>
      <main class="documents-page">
        <PageHeaderHero eyebrow="文档中心" title="文件管理" :description="pageHeroDescription">
        </PageHeaderHero>

        <div class="documents-page__feedback">
          <p v-if="groupLoadError" class="feedback feedback--error">{{ groupLoadError }}</p>
          <p v-if="uploadFeedback" class="feedback feedback--success">{{ uploadFeedback }}</p>
          <p v-if="uploadError" class="feedback feedback--error">{{ uploadError }}</p>
          <p v-if="documentsError" class="feedback feedback--error">{{ documentsError }}</p>
        </div>

        <DocumentPageToolbar
          :groups="visibleGroups"
          :current-group-id="currentGroupId"
          :current-group="currentGroup"
          :is-groups-loading="appStore.isGroupsLoading"
          :file-name="filters.fileName"
          :status="filters.status"
          :status-options="DOCUMENT_STATUS_OPTIONS"
          :selected-file-name="selectedFileName"
          :file-input-key="fileInputKey"
          :can-manage-current-group="canManageCurrentGroup"
          :is-uploading="isUploading"
          @change:group-id="handleGroupChange"
          @change:file-name="filters.fileName = $event"
          @change:status="filters.status = $event"
          @refresh-groups="refreshGroups"
          @file-change="handleFileChange"
          @upload="handleUpload"
        />

        <DocumentStatusBoard
          :summary-items="statusSummaryItems"
          :recent-failures="recentFailures"
          :results-summary="resultsSummary"
          :matched-count="visibleDocuments.length"
        />

        <article class="panel panel--wide documents-page__results">
          <div class="panel__header">
            <div>
              <p class="panel__eyebrow">筛选面板</p>
              <h2>筛选与结果</h2>
            </div>
            <span class="panel__pill">{{ currentGroup ? currentGroup.groupName : '未选择知识库' }}</span>
          </div>

          <form class="document-filter-form" @submit.prevent="handleApplyFilters">
            <label class="document-filter-form__field">
              <span>上传时间从</span>
              <input v-model="filters.uploadedFrom" type="date" />
            </label>

            <label class="document-filter-form__field">
              <span>上传时间到</span>
              <input v-model="filters.uploadedTo" type="date" />
            </label>

            <div class="document-filter-form__actions">
              <div class="document-filter-form__meta">
                <p class="filter-hint">{{ filterHint }}</p>
                <p class="filter-hint">{{ groupScopeSummary }}</p>
              </div>
              <div class="document-filter-form__buttons">
                <button type="button" class="ghost-button" @click="handleResetFilters">重置</button>
                <button type="submit" class="primary-button" :disabled="isLoading || !canLoadDocuments">
                  {{ isLoading ? '筛选中...' : '应用筛选' }}
                </button>
              </div>
            </div>
          </form>

          <p v-if="appStore.isGroupsLoading" class="placeholder-text">正在同步当前登录用户的群组上下文...</p>
          <p v-else-if="currentGroup === null" class="placeholder-text">请先选择知识库空间。</p>
          <p v-else-if="isLoading" class="placeholder-text">正在同步当前组的文件状态...</p>
          <p v-else-if="visibleDocuments.length === 0" class="placeholder-text">{{ emptyStateMessage }}</p>

          <div v-else class="document-table-wrap">
            <table class="document-table">
              <thead>
                <tr>
                  <th>文件</th>
                  <th>类型</th>
                  <th>大小</th>
                  <th>上传用户</th>
                  <th>状态与异常</th>
                  <th>上传时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in visibleDocuments" :key="item.documentId">
                  <td>
                    <strong>{{ item.fileName }}</strong>
                  </td>
                  <td><span>{{ describeDocumentRow(item) }}</span></td>
                  <td><span>{{ formatDocumentFileSize(item.fileSize) }}</span></td>
                  <td>
                    <strong>{{ formatUploaderLabel(item) }}</strong>
                  </td>
                  <td>
                    <span class="status-chip" :data-tone="getDocumentStatusMeta(item.status).tone">
                      {{ getDocumentStatusMeta(item.status).label }}
                    </span>
                    <span v-if="item.failureReason" class="table-note table-note--danger">
                      {{ item.failureReason }}
                    </span>
                    <span v-else class="table-note">
                      {{ item.previewText ? '已带缓存预览片段' : '预览将按需调用接口' }}
                    </span>
                  </td>
                  <td>{{ formatDocumentDateTime(item.uploadedAt) }}</td>
                  <td>
                    <div class="table-actions">
                      <button
                        class="ghost-button"
                        :class="{ 'preview-trigger--disabled': !canPreviewDocument(item, currentGroupRelation) }"
                        :disabled="!canPreviewDocument(item, currentGroupRelation)"
                        @click="handlePreview(item)"
                      >
                        {{ getPreviewButtonLabel(item, currentGroupRelation) }}
                      </button>
                      <button
                        v-if="canManageCurrentGroup"
                        class="ghost-button ghost-button--danger"
                        :disabled="deletingDocumentIds.has(item.documentId)"
                        @click="handleDelete(item.documentId, item.fileName)"
                      >
                        {{ deletingDocumentIds.has(item.documentId) ? '删除中...' : '删除' }}
                      </button>
                    </div>
                    <span v-if="!canPreviewDocument(item, currentGroupRelation)" class="table-note">
                      当前仅可查看已就绪文件预览
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </main>
    </template>
  </WorkbenchShell>

  <Teleport to="body">
    <div v-if="isPreviewOpen" class="document-preview-backdrop" @click.self="closePreview">
      <section class="document-preview-panel" role="dialog" aria-modal="true" aria-labelledby="document-preview-title">
        <header>
          <div>
            <p class="panel__eyebrow">文档预览</p>
            <h2 id="document-preview-title">{{ previewFileName }}</h2>
            <p class="document-preview-meta">
              文档 #{{ previewDocumentId }} · {{ getDocumentStatusMeta(previewStatus).label }} · 最多展示前 200 字
            </p>
          </div>
          <button class="ghost-button" @click="closePreview">关闭</button>
        </header>

        <p v-if="previewMessage" :class="previewMessageTone === 'error' ? 'feedback feedback--error' : 'document-preview-note'">
          {{ previewMessage }}
        </p>

        <p v-if="isPreviewLoading && previewText.length === 0" class="placeholder-text">正在加载预览内容...</p>
        <div v-else class="document-preview-text">
          {{ previewText || '当前文件暂无可展示的前 200 字预览。' }}
        </div>

        <div class="document-preview-actions">
          <button class="primary-button" @click="closePreview">完成</button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
