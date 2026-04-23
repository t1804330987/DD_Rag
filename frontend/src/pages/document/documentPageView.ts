import type { DocumentItem } from '../../api/document'
import type { GroupRelation, VisibleGroup } from '../../stores/app'

const STATUS_META: Record<string, { label: string; tone: string }> = {
  UPLOADED: { label: '已上传', tone: 'queued' },
  PROCESSING: { label: '处理中', tone: 'progress' },
  READY: { label: '已就绪', tone: 'ready' },
  FAILED: { label: '失败', tone: 'failed' },
}

export interface DocumentFilterForm {
  groupId: number | null
  fileName: string
  status: string
  uploadedFrom: string
  uploadedTo: string
}

export interface DocumentStatusSummaryItem {
  key: string
  label: string
  value: string
  description: string
  tone: string
}

export interface DocumentFailureItem {
  documentId: number
  fileName: string
  reason: string
  uploadedAt: string
}

export const DOCUMENT_STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'UPLOADED', label: '已上传' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'READY', label: '已就绪' },
  { value: 'FAILED', label: '失败' },
]

export function createDocumentFilterForm(
  context: { groupId?: number | null; relation?: GroupRelation | null } = {},
): DocumentFilterForm {
  void context
  return {
    groupId: context.groupId ?? null,
    fileName: '',
    status: '',
    uploadedFrom: '',
    uploadedTo: '',
  }
}

export function countReadyDocuments(documents: DocumentItem[]) {
  return documents.filter((item) => item.status === 'READY').length
}

export function countFailedDocuments(documents: DocumentItem[]) {
  return documents.filter((item) => item.status === 'FAILED').length
}

export function calculateTotalDocumentSize(documents: DocumentItem[]) {
  return documents.reduce((sum, item) => sum + (Number.isFinite(item.fileSize) ? item.fileSize : 0), 0)
}

export function formatDocumentFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

export function formatDocumentDateTime(raw: string) {
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) return raw || '未知时间'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(parsed)
}

export function getDocumentStatusMeta(status: string) {
  return STATUS_META[status] ?? { label: status, tone: 'queued' }
}

export function formatGroupRelationLabel(relation: GroupRelation | null | undefined) {
  if (relation === 'OWNER') return '我拥有的组'
  if (relation === 'MEMBER') return '我加入的组'
  return '未绑定群组'
}

export function formatUploaderLabel(item: DocumentItem) {
  if (item.uploaderDisplayName) return item.uploaderDisplayName
  return '未标记用户'
}

export function canPreviewDocument(item: DocumentItem, relation: GroupRelation | null) {
  void relation
  return item.status === 'READY'
}

export function getPreviewButtonLabel(item: DocumentItem, relation: GroupRelation | null) {
  return canPreviewDocument(item, relation) ? '查看' : '待就绪'
}

export function truncatePreviewText(raw: string | null | undefined) {
  const value = typeof raw === 'string' ? raw.trim() : ''
  return value.slice(0, 200)
}

export function createDocumentStatusSummary(
  documents: DocumentItem[],
  totalSize: number,
): DocumentStatusSummaryItem[] {
  const queuedCount = documents.filter((item) => item.status === 'UPLOADED' || item.status === 'PROCESSING').length
  const failedCount = countFailedDocuments(documents)

  return [
    { key: 'ready', label: '已就绪', value: String(countReadyDocuments(documents)), description: '可直接预览与问答使用', tone: 'ready' },
    { key: 'progress', label: '处理中', value: String(queuedCount), description: '仍在切分、向量化或排队中', tone: 'progress' },
    { key: 'failed', label: '异常文件', value: String(failedCount), description: failedCount > 0 ? '建议优先检查失败原因' : '当前没有失败文件', tone: 'failed' },
    { key: 'size', label: '当前体积', value: formatDocumentFileSize(totalSize), description: '按当前筛选结果累计大小', tone: 'neutral' },
  ]
}

export function collectRecentDocumentFailures(
  documents: DocumentItem[],
  limit = 3,
): DocumentFailureItem[] {
  return documents
    .filter((item) => item.status === 'FAILED' && item.failureReason)
    .sort((left, right) => parseDateTime(right.uploadedAt) - parseDateTime(left.uploadedAt))
    .slice(0, limit)
    .map((item) => ({
      documentId: item.documentId,
      fileName: item.fileName,
      reason: item.failureReason ?? '未返回失败原因',
      uploadedAt: formatDocumentDateTime(item.uploadedAt),
    }))
}

export function createDocumentActionItems(options: {
  currentGroup: VisibleGroup | null
  canManageCurrentGroup: boolean
}): string[] {
  if (options.currentGroup === null) {
    return ['先选择知识库空间，再加载文件列表与筛选结果。']
  }

  if (options.canManageCurrentGroup) {
    return ['上传新文件', '查看 READY 文件预览', '删除异常或无效文件', '按状态与时间窗口收窄结果']
  }

  return ['查看当前文件状态', '预览 READY 文件内容', '按状态与时间窗口缩小结果', '上传与删除仅由所有者执行']
}

export function createDocumentFilterContext(
  filters: DocumentFilterForm,
  relation: GroupRelation | null,
): string[] {
  void relation
  const items = [
    filters.fileName.trim() ? `文件名包含 “${filters.fileName.trim()}”` : '文件名：全部',
    filters.status ? `状态：${getDocumentStatusMeta(filters.status).label}` : '状态：全部',
  ]

  items.push(
    filters.uploadedFrom || filters.uploadedTo
      ? `上传时间：${filters.uploadedFrom || '不限'} 至 ${filters.uploadedTo || '不限'}`
      : '上传时间：不限',
  )

  return items
}

export function matchesDocumentFilters(
  item: DocumentItem,
  filters: DocumentFilterForm,
  currentRelation: GroupRelation | null,
) {
  void currentRelation
  if (filters.groupId !== null && item.groupId !== filters.groupId) return false
  if (filters.fileName.trim() && !item.fileName.toLowerCase().includes(filters.fileName.trim().toLowerCase())) return false
  if (filters.status && item.status !== filters.status) return false

  const uploadedAt = parseDate(filtersDateFallback(item.uploadedAt))
  const uploadedFrom = parseDate(filters.uploadedFrom)
  const uploadedTo = parseDate(filters.uploadedTo, true)

  if (uploadedAt !== null && uploadedFrom !== null && uploadedAt < uploadedFrom) return false
  if (uploadedAt !== null && uploadedTo !== null && uploadedAt > uploadedTo) return false

  return true
}

export function parsePositiveInteger(raw: string) {
  const value = raw.trim()
  if (!/^\d+$/.test(value)) return null

  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function parseDate(raw: string, endOfDay = false) {
  const value = raw.trim()
  if (!value) return null

  const parsed = new Date(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function parseDateTime(raw: string) {
  const parsed = new Date(raw)
  return Number.isNaN(parsed.getTime()) ? 0 : parsed.getTime()
}

function filtersDateFallback(raw: string) {
  return raw.includes('T') ? raw.slice(0, 10) : raw
}
