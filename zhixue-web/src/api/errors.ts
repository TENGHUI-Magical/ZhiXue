/**
 * 业务错误码单一事实源（docs/03-错误码与降级.md §2 / §4）。
 *
 * 约定：后端业务错误恒为 HTTP 200 + code≠0，视图只消费这里给出的文案与动作，
 * 不再各自写 `if (code === 40100) ... else if (code === 40900) ...`。
 * 新增错误码必须先改 docs/03 与 openapi.yaml，再补这里的映射（契约纪律 §5）。
 */

export const ErrorCode = {
  OK: 0,
  /** 参数校验失败：缺参、越界、枚举非法 */
  PARAM: 40000,
  /** 未登录 / token 过期：清凭证跳登录，不可原地重试 */
  UNAUTHORIZED: 40100,
  /** 无权限：跨用户或角色不符 */
  FORBIDDEN: 40300,
  /** 资源不存在 */
  NOT_FOUND: 40400,
  /** 重复提交 / 状态冲突：刷新列表，禁止自动重试 */
  CONFLICT: 40900,
  /** 限流：带 Retry-After */
  RATE_LIMITED: 42900,
  /** 业务兜底失败 */
  SERVER: 50000,
  /** AI 兜底码（常规降级走 L2/L3，不以该码替代） */
  AI_FAILED: 60000,
  /** AI 超时 / L4 无法生成 */
  AI_TIMEOUT: 60001,
  /** OCR 识别失败 */
  OCR_FAILED: 60002,
  /** 检索无匹配（topK 最高分 < 0.35） */
  RETRIEVAL_EMPTY: 60003,
  /** 合规拦截（学术诚信词表命中） */
  COMPLIANCE_BLOCKED: 60004,
  /** 前端本地：网络不可达 / 超时（非服务端码） */
  NETWORK: -1
} as const

export interface ErrorAdvice {
  /** 说人话的下一步（视图可直接放在错误块里） */
  hint: string
  /** 同一请求原样重试是否可能成功 */
  retryable: boolean
  /** 写操作状态冲突：应刷新当前列表，不能自动重试 */
  refresh: boolean
  /** 该错误通常由视图内联呈现（讲解区 / 上传区），全局 toast 会重复打扰 */
  inline: boolean
}

const ADVICE: Record<number, ErrorAdvice> = {
  [ErrorCode.PARAM]: {
    hint: "请检查填写内容后重试",
    retryable: false,
    refresh: false,
    inline: false
  },
  [ErrorCode.UNAUTHORIZED]: {
    hint: "请重新登录",
    retryable: false,
    refresh: false,
    inline: false
  },
  [ErrorCode.FORBIDDEN]: {
    hint: "当前账号没有该操作权限，如需开通请联系课程教师",
    retryable: false,
    refresh: false,
    inline: false
  },
  [ErrorCode.NOT_FOUND]: {
    hint: "该内容可能已被删除，返回列表刷新后再试",
    retryable: false,
    refresh: true,
    inline: false
  },
  [ErrorCode.CONFLICT]: {
    hint: "该操作已提交过，无需重复操作；请刷新列表确认最新状态",
    retryable: false,
    refresh: true,
    inline: false
  },
  [ErrorCode.RATE_LIMITED]: {
    hint: "请求过于频繁，请稍后再试",
    retryable: true,
    refresh: false,
    inline: false
  },
  [ErrorCode.SERVER]: {
    hint: "服务暂时不可用，请稍后重试；若持续失败请联系管理员",
    retryable: true,
    refresh: false,
    inline: false
  },
  [ErrorCode.AI_FAILED]: {
    hint: "AI 服务暂时不可用，可稍后重新生成",
    retryable: true,
    refresh: false,
    inline: true
  },
  [ErrorCode.AI_TIMEOUT]: {
    hint: "生成超时，请重新生成；也可先看教材对应页",
    retryable: true,
    refresh: false,
    inline: true
  },
  [ErrorCode.OCR_FAILED]: {
    hint: "图片不够清晰，请重拍或改为手动输入题干",
    retryable: true,
    refresh: false,
    inline: true
  },
  [ErrorCode.RETRIEVAL_EMPTY]: {
    hint: "教材里没检索到对应内容，可换一种表述或手动选择知识点",
    retryable: true,
    refresh: false,
    inline: true
  },
  [ErrorCode.COMPLIANCE_BLOCKED]: {
    hint: "这更像在索取答案，系统不提供直接答案；请先写下你的思路再提问",
    retryable: false,
    refresh: false,
    inline: true
  },
  [ErrorCode.NETWORK]: {
    hint: "请检查网络或确认服务已启动，然后重试",
    retryable: true,
    refresh: false,
    inline: false
  }
}

const FALLBACK: ErrorAdvice = {
  hint: "请稍后重试；若持续失败请联系管理员",
  retryable: true,
  refresh: false,
  inline: false
}

/**
 * 哪些业务码由请求层统一 toast：
 * 40300 / 40900 / 42900 / 50000 与网络错误，视图通常没有内联错误位，
 * 不提示就只剩一个静默失败的按钮；40000 / 40400 / 600xx 由视图内联呈现，
 * 请求层再弹一次会重复打扰（这也是 config.silent 的适用场景）。
 *
 * 视图在 catch 里用 isGloballyNotified 判断，避免“全局弹一次 + 自己再弹一次”。
 */
const NOTIFY_CODES = new Set<number>([
  ErrorCode.FORBIDDEN,
  ErrorCode.CONFLICT,
  ErrorCode.RATE_LIMITED,
  ErrorCode.SERVER,
  ErrorCode.NETWORK
])

export const isGloballyNotified = (code: number): boolean => NOTIFY_CODES.has(code)

export const adviceFor = (code: number): ErrorAdvice => ADVICE[code] ?? FALLBACK

export const isUnauthorized = (error: unknown): boolean =>
  (error as { code?: number } | null | undefined)?.code === ErrorCode.UNAUTHORIZED

export const isConflict = (error: unknown): boolean =>
  (error as { code?: number } | null | undefined)?.code === ErrorCode.CONFLICT

export const isRateLimited = (error: unknown): boolean =>
  (error as { code?: number } | null | undefined)?.code === ErrorCode.RATE_LIMITED

/** 统一取错误文案：ApiError.message 优先，其次 Error.message，最后兜底句 */
export const messageOf = (error: unknown, fallback: string): string => {
  if (error instanceof Error && error.message) return error.message
  if (typeof error === "string" && error) return error
  return fallback
}

/**
 * 给用户看的完整一句：服务端文案 + 下一步。
 * 服务端文案已包含下一步时（如“请先…”）不追加，避免两句话打架。
 */
export const describeError = (error: unknown, fallback: string): string => {
  const code = (error as { code?: number } | null | undefined)?.code
  const message = messageOf(error, fallback)
  if (typeof code !== "number") return message
  const { hint } = adviceFor(code)
  if (!hint || message.includes(hint)) return message
  return `${message}；${hint}`
}
