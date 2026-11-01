// 统一请求封装：与接口契约 V2（docs/openapi.yaml）对齐。
// 约定：响应体恒为 {code, message, data, timestamp}；成功由 unwrap 取 data，失败抛 ApiError 并携带业务 code。
import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'

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
    message: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
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
  const now = Date.now()
  if (now - lastRedirectAt < 3000) return
  lastRedirectAt = now
  localStorage.removeItem('satoken')
  ElMessage.warning(message)
  if (window.location.pathname.startsWith('/login')) return
  const current = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const redirect = current.startsWith('/') ? current : ''
  window.location.assign(redirect ? `/login?redirect=${encodeURIComponent(redirect)}` : '/login')
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

    if (code === 40100) {
      redirectToLogin(message)
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(new ApiError(code, message))
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
    throw new ApiError(body.code, body.message || '请求失败')
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

export default request
