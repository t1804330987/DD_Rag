/**
 * Map model-platform / QA technical error codes to user-facing Chinese hints.
 * Used when platform routes or provider connections fail for end users.
 */
const MODEL_ERROR_HINTS: Record<string, string> = {
  MODEL_NOT_CONFIGURED:
    '平台问答模型暂不可用（未配置或已失效）。请联系管理员在「模型治理」中检查场景路由，并确保模型测试通过且已启用。',
  MODEL_NOT_AVAILABLE:
    '平台问答模型当前不可用。请联系管理员检查模型是否仍启用、测试是否通过。',
  MODEL_NOT_AUTHORIZED:
    '你暂无该平台模型的使用权限。请联系管理员在「模型治理」中为你授权。',
  MODEL_CONNECTION_NOT_ACTIVE:
    '平台模型连接未处于可用状态。请联系管理员检查连接是否已启用并测试通过。',
  MODEL_ROUTE_MODEL_UNAVAILABLE:
    '场景路由绑定的模型已不可用。请联系管理员重新绑定可用模型。',
  MODEL_ROUTE_CONNECTION_NOT_ACTIVE:
    '场景路由绑定的连接未激活。请联系管理员修复模型连接。',
  MODEL_CONFIGURATION_CHANGED:
    '模型配置已变更，当前配置失效。请联系管理员重新测试并启用模型。',
  PROVIDER_ERROR:
    '模型服务暂时异常（上游调用失败）。请稍后重试；若持续失败，请联系管理员检查 API Key、地址与服务商状态。',
  PROVIDER_RATE_LIMITED:
    '模型服务触发限流，请稍后重试。若频繁出现，请联系管理员调整配额或更换模型。',
  MODEL_PROVIDER_INVALID:
    '模型服务商配置无效。请联系管理员检查连接配置。',
  GLOBAL_BUSY:
    '系统当前繁忙，请稍后重试。',
  USER_BUSY:
    '你有进行中的模型调用，请稍后再试。',
  CONNECTION_BUSY:
    '该模型连接并发已满，请稍后重试。',
  SESSION_BUSY:
    '当前会话忙，请稍后再试。',
  CALL_TIMEOUT:
    '模型调用超时，请稍后重试。若持续超时，请联系管理员。',
  FIRST_TOKEN_TIMEOUT:
    '模型响应超时，请稍后重试。',
  STREAM_IDLE_TIMEOUT:
    '模型输出中断超时，请稍后重试。',
  ANSWER_FORMAT_ERROR:
    '模型返回内容无法解析。请稍后重试；若持续出现，请联系管理员检查问答模型与提示配置。',
  INSUFFICIENT_EVIDENCE:
    '检索到的有效证据不足，暂不回答。可尝试换一种问法，或确认知识库中已有相关文档。',
}

const REASON_CODE_HINTS: Record<string, string> = {
  ANSWER_FORMAT_ERROR: MODEL_ERROR_HINTS.ANSWER_FORMAT_ERROR,
  INSUFFICIENT_EVIDENCE: MODEL_ERROR_HINTS.INSUFFICIENT_EVIDENCE,
}

/** Map API / exception message (often a code) to a friendly Chinese hint. */
export function humanizeModelErrorMessage(raw: string | null | undefined, fallback = '请求失败'): string {
  if (raw == null) return fallback
  const text = raw.trim()
  if (!text) return fallback

  if (MODEL_ERROR_HINTS[text]) {
    return MODEL_ERROR_HINTS[text]
  }

  // Some gateways may embed the code in a longer string.
  for (const [code, hint] of Object.entries(MODEL_ERROR_HINTS)) {
    if (text === code || text.includes(code)) {
      return hint
    }
  }

  return text
}

/** Map QA refusal reasonCode / reasonMessage for display. */
export function humanizeQaRefusalMessage(
  reasonCode: string | null | undefined,
  reasonMessage: string | null | undefined,
): string {
  if (reasonCode && REASON_CODE_HINTS[reasonCode]) {
    return REASON_CODE_HINTS[reasonCode]
  }
  if (reasonCode && MODEL_ERROR_HINTS[reasonCode]) {
    return MODEL_ERROR_HINTS[reasonCode]
  }
  if (reasonMessage && reasonMessage.trim()) {
    return humanizeModelErrorMessage(reasonMessage, reasonMessage)
  }
  return '当前证据不足，无法给出可靠回答。'
}
