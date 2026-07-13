const CHINA_TIME_ZONE = 'Asia/Shanghai'

export function formatChinaDateTime(value: string | null, emptyValue = '—') {
  if (!value) return emptyValue
  const parsed = new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString('zh-CN', { timeZone: CHINA_TIME_ZONE })
}
