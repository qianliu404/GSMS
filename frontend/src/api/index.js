import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'gsms_token'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 60000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    // 文件下载直接返回原始响应
    if (response.config.responseType === 'blob') {
      return response
    }
    const body = response.data
    if (body && body.code === 0) {
      return body.data
    }
    if (body && body.code === 40101) {
      localStorage.removeItem(TOKEN_KEY)
      if (!window.location.pathname.startsWith('/login')) {
        ElMessage.error('登录已过期，请重新登录')
        window.location.href = '/login'
      }
      return Promise.reject(body)
    }
    ElMessage.error((body && body.message) || '请求失败')
    return Promise.reject(body)
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      window.location.href = '/login'
    } else if (status === 413) {
      ElMessage.error('文件超出上传大小限制')
    } else {
      ElMessage.error(error.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export { TOKEN_KEY }
export default http
