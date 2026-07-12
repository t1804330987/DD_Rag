import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  gfm: true,
  breaks: true,
})

/**
 * 规范化模型输出，提高 ATX 标题等结构的解析成功率。
 * 中文模型常见：无空格标题、全角 #、CRLF、行首缩进。
 */
function normalizeMarkdownSource(source: string): string {
  let text = source
    .replace(/\uFEFF/g, '')
    .replace(/\r\n?/g, '\n')
    // 全角井号 → 半角
    .replace(/＃/g, '#')

  // 允许行首最多 3 个空格（CommonMark 规则），并给无空格标题补空格：###标题 → ### 标题
  text = text.replace(/^[ \t]{0,3}(#{1,6})(?=[^\s#])/gm, '$1 ')

  // 去掉行尾空白，减少流式拼接时的怪异 token
  text = text.replace(/[ \t]+$/gm, '')

  // 流式未闭合代码围栏：补一个结束 fence，避免整段被吞成半成品
  const fenceCount = (text.match(/^```/gm) ?? []).length
  if (fenceCount % 2 === 1) {
    text = `${text}\n\`\`\``
  }

  return text
}

/**
 * 将模型返回的 Markdown 安全渲染为 HTML。
 * 流式片段可能不完整，仍按 best-effort 解析；XSS 由 DOMPurify 兜底。
 */
export function renderMarkdown(source: string): string {
  const text = normalizeMarkdownSource(source ?? '')
  if (text.trim().length === 0) {
    return ''
  }

  const rawHtml = marked.parse(text, { async: false }) as string
  return DOMPurify.sanitize(rawHtml, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel'],
  })
}
