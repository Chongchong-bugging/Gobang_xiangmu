import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN'
})

function handleUnauthorized() {
  sessionStorage.removeItem('user')
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

// 响应拦截器
api.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code === 401) {
      handleUnauthorized()
    }
    return data
  },
  error => {
    // 输出真实错误信息到控制台，方便排查
    console.error('[API Error]', error)
    const backendData = error.response?.data
    if (backendData && typeof backendData.code === 'number') {
      if (backendData.code === 401) handleUnauthorized()
      return backendData
    }

    let msg = '系统维护中'
    if (error.code === 'ECONNABORTED') {
      msg = '请求超时，请检查应用服务是否正常'
    } else if (error.response) {
      msg = `后端错误(${error.response.status}): ${JSON.stringify(error.response.data)}`
    } else if (error.request) {
      msg = '无法连接服务，请确认 Docker 应用是否已启动'
    }
    return { code: 500, message: msg, data: null }
  }
)

// ===== 用户 API =====
export const userApi = {
  getCsrf: () => api.get('/user/csrf'),
  register: (data) => api.post('/user/register', data),
  login: async (data) => {
    const result = await api.post('/user/login', data)
    if (result.code === 200) await api.get('/user/csrf')
    return result
  },
  logout: async () => {
    const result = await api.post('/user/logout')
    await api.get('/user/csrf')
    return result
  },
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
  getState: () => api.get('/game/state'),
  unlockBlock: (blockRow, blockCol) => api.post('/game/unlock-block', { blockRow, blockCol })
}

// ===== 得分 API =====
export const scoreApi = {
  getMyScore: () => api.get('/score/my')
}

export default api
