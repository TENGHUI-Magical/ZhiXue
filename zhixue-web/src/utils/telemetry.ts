/**
 * 极简前端可观测出口：开发期落 console，生产期预留上报钩子。
 * 目的：消除静默空 catch，让业务失败至少留下可定位的上下文与 traceId。
 * 不引入新依赖，不采集个人信息。
 */
export function reportError(error: unknown, context: string): void {
  const message = error instanceof Error ? error.message : String(error)
  const traceId = (error as { traceId?: string } | null | undefined)?.traceId
  if (import.meta.env?.DEV) {
    console.error(`[zhixue:${context}]`, message, traceId ? `traceId=${traceId}` : '')
  }
  // 生产环境可在此接入上报 SDK / navigator.sendBeacon；当前先保留单一出口。
}
