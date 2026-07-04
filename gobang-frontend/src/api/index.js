import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true
})

// 响应拦截器
api.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code === 401) {
      sessionStorage.removeItem('user')
      window.location.href = '/login'
    }
    return data
  },
  error => {
    // 输出真实错误信息到控制台，方便排查
    console.error('[API Error]', error)
    let msg = '系统维护中'
    if (error.code === 'ECONNABORTED') {
      msg = '请求超时，请检查后端是否启动'
    } else if (error.response) {
      msg = `后端错误(${error.response.status}): ${JSON.stringify(error.response.data)}`
    } else if (error.request) {
      msg = '无法连接后端，请确认后端是否已启动（端口8080）'
    }
    return { code: 500, message: msg, data: null }
  }
)

// ===== 用户 API =====
export const userApi = {
  register: (data) => api.post('/user/register', data),
  login: (data) => api.post('/user/login', data),
  logout: () => api.post('/user/logout'),
  getInfo: () => api.get('/user/info')
}

// ===== 游戏 API =====
export const gameApi = {
  start: (difficulty, gameMode) => api.post('/game/start', { difficulty, gameMode }),
  move: (row, col) => api.post('/game/move', { row, col }),
  aiMove: () => api.post('/game/ai-move'),
  undo: () => api.post('/game/undo'),
  pause: () => api.post('/game/pause'),
  resume: () => api.post('/game/resume'),
  getState: () => api.get('/game/state')
}

// ===== 得分 API =====
export const scoreApi = {
  getMyScore: () => api.get('/score/my')
}

export default api
