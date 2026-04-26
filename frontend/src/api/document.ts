import http, { type ApiResponse } from './http'

export type DocumentGroupRelation = 'OWNER' | 'MEMBER'

export interface DocumentListQuery {
  groupId?: number
  groupRelation?: DocumentGroupRelation
  fileName?: string
  uploaderUserId?: number
  status?: string
  uploadedFrom?: string
  uploadedTo?: string
}

export interface DocumentItem {
  documentId: number
  groupId: number
  fileName: string
  fileExt: string | null
  contentType: string | null
  fileSize: number
  status: string
  failureReason: string | null
  uploadedAt: string
  uploaderUserId: number | null
  uploaderDisplayName: string | null
  uploaderUserCode: string | null
  previewText: string | null
}

export interface DocumentPreview {
  documentId: number
  groupId: number
  fileName: string
  previewText: string
  status: string | null
}

interface UploadDocumentPayload {
  groupId: number
  file: File
  onProgress?: (loadedBytes: number, totalBytes?: number) => void
}

export interface InitDocumentUploadPayload {
  groupId: number
  fileName: string
  fileSize: number
  contentType: string
  fileHash: string
  chunkSize: number
  chunkCount: number
}

export interface UploadChunkPayload {
  uploadId: string
  chunkIndex: number
  chunkHash: string
  chunk: Blob
}

export interface UploadInitResult {
  instantUpload: boolean
  documentId: number | null
  uploadId: string | null
  uploadedChunks: number[]
  chunkSize: number | null
  chunkCount: number | null
}

export interface UploadStatusResult {
  status: string
  uploadedChunks: number[]
  uploadedChunkCount: number
  chunkCount: number | null
}

type DocumentListPayload =
  | DocumentItem[]
  | ApiResponse<DocumentItem[] | { items?: unknown[]; list?: unknown[]; records?: unknown[] }>

type DocumentPreviewPayload = DocumentPreview | ApiResponse<DocumentPreview | Record<string, unknown>>

export async function fetchDocuments(query: DocumentListQuery = {}): Promise<DocumentItem[]> {
  const { data } = await http.get<DocumentListPayload>('/documents', {
    params: buildDocumentListParams(query),
  })

  return normalizeDocumentListPayload(data)
}

export async function fetchDocumentPreview(
  documentId: number,
  groupId: number,
): Promise<DocumentPreview> {
  const { data } = await http.get<DocumentPreviewPayload>(`/documents/${documentId}/preview`, {
    params: { groupId },
  })

  return normalizeDocumentPreviewPayload(data, documentId, groupId)
}

export async function uploadDocument(payload: UploadDocumentPayload): Promise<number> {
  const formData = new FormData()
  formData.append('groupId', String(payload.groupId))
  formData.append('file', payload.file)

  const { data } = await http.post<ApiResponse<number>>('/documents/upload', formData, {
    onUploadProgress: (event) => {
      payload.onProgress?.(event.loaded, event.total)
    },
  })

  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? '上传文件失败')
  }

  return data.data
}

export async function initDocumentUpload(
  payload: InitDocumentUploadPayload,
): Promise<UploadInitResult> {
  const { data } = await http.post<ApiResponse<UploadInitResult>>('/documents/upload/init', payload)

  if (!data.success || !data.data) {
    throw new Error(data.message ?? '初始化上传失败')
  }

  return normalizeUploadInitResult(data.data)
}

export async function uploadDocumentChunk(
  payload: UploadChunkPayload,
  onProgress?: (loadedBytes: number) => void,
): Promise<UploadStatusResult> {
  const formData = new FormData()
  formData.append('uploadId', payload.uploadId)
  formData.append('chunkIndex', String(payload.chunkIndex))
  formData.append('chunkHash', payload.chunkHash)
  formData.append('chunk', payload.chunk)

  const { data } = await http.post<ApiResponse<UploadStatusResult>>('/documents/upload/chunks', formData, {
    onUploadProgress: (event) => {
      onProgress?.(event.loaded)
    },
  })

  if (!data.success || !data.data) {
    throw new Error(data.message ?? '上传分片失败')
  }

  return normalizeUploadStatusResult(data.data)
}

export async function fetchUploadStatus(uploadId: string): Promise<UploadStatusResult> {
  const { data } = await http.get<ApiResponse<UploadStatusResult>>(`/documents/upload/${uploadId}`)

  if (!data.success || !data.data) {
    throw new Error(data.message ?? '获取上传状态失败')
  }

  return normalizeUploadStatusResult(data.data)
}

export async function completeDocumentUpload(uploadId: string): Promise<number> {
  const { data } = await http.post<ApiResponse<number>>(`/documents/upload/${uploadId}/complete`)

  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? '完成上传失败')
  }

  return data.data
}

export async function deleteDocument(documentId: number, groupId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/documents/${documentId}`, {
    params: { groupId },
  })

  if (!data.success) {
    throw new Error(data.message ?? '删除文档失败')
  }
}

export async function retryDocumentIngestion(documentId: number, groupId: number): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>(`/documents/${documentId}/retry-ingestion`, null, {
    params: { groupId },
  })

  if (!data.success) {
    throw new Error(data.message ?? '重新处理文档失败')
  }
}

function buildDocumentListParams(query: DocumentListQuery) {
  const params: Record<string, number | string> = {}

  assignQueryParam(params, 'groupId', query.groupId)
  assignQueryParam(params, 'groupRelation', query.groupRelation)
  assignQueryParam(params, 'fileName', query.fileName)
  assignQueryParam(params, 'uploaderUserId', query.uploaderUserId)
  assignQueryParam(params, 'status', query.status)
  assignQueryParam(params, 'uploadedFrom', query.uploadedFrom)
  assignQueryParam(params, 'uploadedTo', query.uploadedTo)

  return params
}

function assignQueryParam(
  params: Record<string, number | string>,
  key: string,
  value: number | string | null | undefined,
) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    params[key] = value
    return
  }

  if (typeof value === 'string' && value.trim().length > 0) {
    params[key] = value.trim()
  }
}

function normalizeDocumentListPayload(payload: unknown): DocumentItem[] {
  const unwrapped = unwrapApiResponse(payload)
  const rawItems = resolveListItems(unwrapped)

  return rawItems.map((item) => normalizeDocumentItem(item)).filter(Boolean) as DocumentItem[]
}

function normalizeDocumentPreviewPayload(
  payload: unknown,
  fallbackDocumentId: number,
  fallbackGroupId: number,
): DocumentPreview {
  const unwrapped = unwrapApiResponse(payload)
  const source = isRecord(unwrapped) ? unwrapped : {}
  const previewText = truncatePreviewText(
    readString(source.previewText) ?? readString(source.text) ?? '',
  )

  return {
    documentId: readNumber(source.documentId) ?? fallbackDocumentId,
    groupId: readNumber(source.groupId) ?? fallbackGroupId,
    fileName: readString(source.fileName) ?? `文档 #${fallbackDocumentId}`,
    previewText,
    status: readString(source.status),
  }
}

function normalizeUploadInitResult(payload: unknown): UploadInitResult {
  const source = isRecord(payload) ? payload : {}
  return {
    instantUpload: Boolean(source.instantUpload),
    documentId: readNumber(source.documentId),
    uploadId: readString(source.uploadId),
    uploadedChunks: readNumberArray(source.uploadedChunks),
    chunkSize: readNumber(source.chunkSize),
    chunkCount: readNumber(source.chunkCount),
  }
}

function normalizeUploadStatusResult(payload: unknown): UploadStatusResult {
  const source = isRecord(payload) ? payload : {}
  return {
    status: readString(source.status) ?? 'UNKNOWN',
    uploadedChunks: readNumberArray(source.uploadedChunks),
    uploadedChunkCount: readNumber(source.uploadedChunkCount) ?? 0,
    chunkCount: readNumber(source.chunkCount),
  }
}

function unwrapApiResponse(payload: unknown) {
  if (isRecord(payload) && 'success' in payload && 'data' in payload) {
    return payload.data
  }

  return payload
}

function resolveListItems(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) {
    return payload.filter(isRecord)
  }

  if (!isRecord(payload)) {
    return []
  }

  for (const key of ['items', 'list', 'records']) {
    const value = payload[key]
    if (Array.isArray(value)) {
      return value.filter(isRecord)
    }
  }

  return []
}

function normalizeDocumentItem(source: Record<string, unknown> | null): DocumentItem | null {
  if (source === null) {
    return null
  }

  const documentId = readNumber(source.documentId)

  if (documentId === null) {
    return null
  }

  return {
    documentId,
    groupId: readNumber(source.groupId) ?? 0,
    fileName: readString(source.fileName) ?? `文档 #${documentId}`,
    fileExt: readString(source.fileExt),
    contentType: readString(source.contentType),
    fileSize: readNumber(source.fileSize) ?? 0,
    status: readString(source.status) ?? 'UNKNOWN',
    failureReason: readString(source.failureReason),
    uploadedAt: readString(source.uploadedAt) ?? '',
    uploaderUserId: readNumber(source.uploaderUserId) ?? readNumber(source.uploaderId),
    uploaderDisplayName:
      readString(source.uploaderDisplayName) ?? readString(source.uploaderName),
    uploaderUserCode: readString(source.uploaderUserCode) ?? readString(source.uploaderCode),
    previewText: readString(source.previewText) ?? readString(source.preview),
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function readNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

function readString(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

function readNumberArray(value: unknown): number[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .map((item) => readNumber(item))
    .filter((item): item is number => typeof item === 'number')
}

function truncatePreviewText(value: string) {
  return value.trim().slice(0, 200)
}
