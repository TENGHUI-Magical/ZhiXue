// 统一请求封装：与接口契约文档 1.1/1.2/1.3 节对齐。
// 响应拦截解包 {code, message, data}，页面只拿 data、只认 code。
import axios from 'axios'
import { ElMessage } from 'element-plus'

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

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

// 请求拦截：自动附加 Sa-Token（避坑清单 A1）；登录页就绪后改为从 Pinia 读取
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('satoken')
  if (token) config.headers.set('satoken', token)
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse
    // 非包装响应（SSE 等）直接放行
    if (body === null || typeof body !== 'object' || !('code' in body)) {
      return response
    }
    if (body.code !== 0) {
      if (body.code === 40100) {
        localStorage.removeItem('satoken')
        // TODO(P1)：登录页就绪后跳转 /login；当前只清 token 并提示
        ElMessage.warning(body.message || '登录已过期，请重新登录')
      } else {
        // 需求规格说明书错误态约束：明确反馈 + 恢复入口
        ElMessage.error(body.message || '请求失败')
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 解包：调用方经 api.get<T>/api.post<T> 直接拿 data（axios 拦截器类型断言的标准写法）
    return body.data as unknown as typeof response
  },
  (error) => {
    // 网络层错误（后端不可达/超时）兜底提示
    ElMessage.error(error?.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请检查服务是否已启动')
    return Promise.reject(error)
  }
)

/** 类型安全入口：promise 直接兑现 data（拦截器已解包） */
export const api = {
  get<T>(url: string, params?: object) {
    return request.get<unknown, T>(url, { params })
  },
  post<T>(url: string, data?: object) {
    return request.post<unknown, T>(url, data)
  }
}

export default request