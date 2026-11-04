// 统一请求封装：与接口契约 V2（docs/openapi.yaml）对齐。
// 约定：响应体恒为 {code, message, data, timestamp}；成功由 unwrap 取 data，失败抛 ApiError 并携带业务 code。
import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 视图已用内联错误块呈现时置 true，跳过全局 toast，避免重复提示 */
    silent?: boolean
  }
}

/** 契约 1.1 统一响应包装 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

/** 契约 1.3 分页标准格式 */
export interface PageResult<T> {
  page: number
  size: number
  total: number
  records: T[]
}

/** 业务错误：保留 code，页面可按 40900/42900/60004 分支处理 */
export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
    /** 后端 x-trace-id：便于把一次用户报错对应到服务端日志 */
    public readonly traceId?: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 兼容 AxiosHeaders 实例与普通对象两种响应头形态 */
function traceIdOf(headers: unknown): string | undefined {
  if (!headers || typeof headers !== 'object') return undefined
  const get = (headers as { get?: (name: string) => unknown }).get
  const viaGet = typeof get === 'function' ? get.call(headers, 'x-trace-id') : undefined
  if (typeof viaGet === 'string' && viaGet) return viaGet
  const record = headers as Record<string, unknown>
  const raw = record['x-trace-id'] ?? record['traceId'] ?? record['trace-id']
  return typeof raw === 'string' && raw ? raw : undefined
}

/** 优先 SPA 软导航：保留路由上下文与未提交表单；动态引入避免与 router/index.ts 循环依赖 */
function navigate(path: string) {
  import('../router')
    .then(({ default: router }) => router.replace(path))
    .catch(() => window.location.assign(path))
}

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

// 请求拦截：自动附加 Sa-Token（避坑清单 A1）
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('satoken')
  if (token) config.headers.set('satoken', token)
  return config
})

// 401 统一处理：清 token、保留当前站内地址、跳登录一次（避免各处复制）
// 首屏并发多个请求同时 401 时只跳一次、只提示一次
let lastRedirectAt = 0
function redirectToLogin(message: string) {
  localStorage.removeItem('satoken')
  // 登录页的 40100 是“用户名或密码错误”，由表单内联提示；不能再弹“登录已过期”
  if (window.location.pathname.startsWith('/login')) return
  const now = Date.now()
  if (now - lastRedirectAt < 3000) return
  lastRedirectAt = now
  ElMessage.warning(message)
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const redirect = current.startsWith('/') ? current : ''
  navigate(redirect ? `/login?redirect=${encodeURIComponent(redirect)}` : '/login')
}

// 响应拦截只处理 HTTP/网络层错误，业务 code 交给 unwrap 统一判定，避免两处逻辑漂移
request.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse>) => {
    const body = error.response?.data
    const code = body && typeof body === 'object' && 'code' in body ? body.code : error.response?.status ?? -1
    const message =
      (body && typeof body === 'object' && body.message) ||
      (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请检查服务是否已启动')
    const traceId = traceIdOf(error.response?.headers)
    // 视图已用内联错误块呈现时传 { silent: true }，避免 toast + 内联重复提示
    const silent = Boolean(error.config?.silent)

    if (code === 40100) {
      redirectToLogin(message)
    } else if (!silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new ApiError(code, message, traceId))
  }
)

/** 解包业务响应：code!==0 抛 ApiError；非包装响应（SSE/二进制）原样返回 */
async function unwrap<T>(promise: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> {
  const res = await promise
  const body = res.data
  if (body === null || typeof body !== 'object' || !('code' in body)) {
    return body as unknown as T
  }
  if (body.code === 40100) {
    redirectToLogin(body.message || '登录已过期，请重新登录')
  }
  if (body.code !== 0) {
    throw new ApiError(body.code, body.message || '请求失败', traceIdOf(res.headers))
  }
  return body.data
}

/** 类型安全入口：直接兑现 data */
export const api = {
  get<T>(url: string, params?: object, config?: AxiosRequestConfig) {
    return unwrap<T>(request.get<ApiResponse<T>>(url, { ...config, params }))
  },
  post<T>(url: string, data?: object, config?: AxiosRequestConfig) {
    return unwrap<T>(request.post<ApiResponse<T>>(url, data, config))
  },
  put<T>(url: string, data?: object, config?: AxiosRequestConfig) {
    return unwrap<T>(request.put<ApiResponse<T>>(url, data, config))
  },
  upload<T>(url: string, form: FormData, config?: AxiosRequestConfig) {
    return unwrap<T>(request.post<ApiResponse<T>>(url, form, config))
  }
}

/**
 * CSV 文件下载（教师端导出）：后端业务错误仍 HTTP 200 + JSON envelope，
 * 需按 content-type 分流——JSON 说明是业务错误（读 code 抛 ApiError），
 * text/csv 才触发浏览器保存（后端已带 BOM，Excel 中文不乱码）。
 */
export async function downloadCsv(url: string, params: object, filename: string): Promise<void> {
  const res = await request.get(url, { params, responseType: 'blob' })
  const contentType = String(res.headers?.['content-type'] || '')
  if (contentType.includes('application/json')) {
    const text = await (res.data as Blob).text()
    try {
      const body = JSON.parse(text) as ApiResponse
      if (body.code === 40100) {
        redirectToLogin(body.message || '登录已过期，请重新登录')
      } else {
        ElMessage.error(body.message || '导出失败')
      }
      throw new ApiError(body.code, body.message || '导出失败')
    } catch (e) {
      if (e instanceof ApiError) throw e
      throw new ApiError(-1, '导出失败')
    }
  }
  const blob = new Blob([res.data as Blob])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.rel = 'noopener'
  link.style.display = 'none'
  // Firefox/Safari 在点击后立刻回收 blob URL 会中断下载（Bugzilla 1282407），延迟回收更稳
  document.body.appendChild(link)
  link.click()
  window.setTimeout(() => {
    link.remove()
    URL.revokeObjectURL(link.href)
  }, 1000)
}

export default request
