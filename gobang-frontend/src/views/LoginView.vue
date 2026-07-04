<template>
  <div class="login-page">
    <!-- 古风山水背景图片 -->
    <div class="bg-image"></div>

    <!-- 背景遮罩层 -->
    <div class="bg-overlay"></div>

    <!-- 音乐控制按钮 -->
    <div class="music-control-login">
      <button class="music-btn-login" @click="toggleBgMusic" :title="bgMusicPlaying ? '暂停音乐' : '播放音乐'">
        <svg v-if="bgMusicPlaying" width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="6" y="4" width="4" height="16" rx="1" fill="#e8c4a0"/>
          <rect x="14" y="4" width="4" height="16" rx="1" fill="#e8c4a0"/>
        </svg>
        <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M6 4L18 12L6 20V4Z" fill="#e8c4a0"/>
        </svg>
      </button>
    </div>

    <!-- 左侧装饰区域 -->
    <div class="left-decoration">
      <!-- 竹子装饰 -->
      <div class="bamboo bamboo-1">
        <div class="bamboo-stalk"></div>
        <div class="bamboo-leaves">
          <div class="leaf leaf-1"></div>
          <div class="leaf leaf-2"></div>
          <div class="leaf leaf-3"></div>
        </div>
      </div>

      <div class="bamboo bamboo-2">
        <div class="bamboo-stalk"></div>
        <div class="bamboo-leaves">
          <div class="leaf leaf-4"></div>
          <div class="leaf leaf-5"></div>
        </div>
      </div>

      <!-- 花朵装饰 -->
      <div class="flower flower-1">❀</div>
      <div class="flower flower-2">✿</div>
      <div class="flower flower-3">❁</div>

      <!-- 问候语和诗词 -->
      <div class="greeting-section">
        <div class="greeting">{{ greeting }}</div>
        <div class="poem">
          <p>棋盘纵横十九路</p>
          <p>黑白交错定乾坤</p>
        </div>
        <div class="welcome-text">
          <p>欢迎来到五子棋世界</p>
          <p>与AI对弈，感受智慧之美</p>
        </div>
      </div>

      <!-- 漂浮的棋子 -->
      <div class="floating-pieces">
        <div class="piece piece-1">●</div>
        <div class="piece piece-2">○</div>
        <div class="piece piece-3">●</div>
      </div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="login-card">
      <div class="logo-section">
        <h1>五子棋 AI 对战</h1>
      </div>
      <p class="subtitle">经典五子棋 · 人机对战 · 三种难度</p>

      <!-- 装饰性小棋盘 -->
      <div class="mini-board">
        <div class="board-grid">
          <div class="board-row" v-for="(row, i) in miniBoard" :key="i">
            <div
              v-for="(cell, j) in row"
              :key="j"
              :class="['board-cell', { 'has-piece': cell !== 0, 'black': cell === 1, 'white': cell === 2 }]"
            >
              <div v-if="cell !== 0" class="piece-on-board"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="tabs">
        <button :class="{ active: isLogin }" @click="isLogin = true">登录</button>
        <button :class="{ active: !isLogin }" @click="isLogin = false">注册</button>
      </div>

      <form @submit.prevent="handleSubmit" class="form">
        <div class="input-group">
          <label>昵称</label>
          <input v-model="nickname" placeholder="请输入昵称" required maxlength="50" />
        </div>
        <div class="input-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" required />
        </div>
        <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
        <div v-if="successMsg" class="success">{{ successMsg }}</div>
        <button type="submit" class="btn-primary" :disabled="loading">
          {{ loading ? '处理中...' : (isLogin ? '登录' : '注册') }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '../api'

const router = useRouter()
const isLogin = ref(true)
const nickname = ref('')
const password = ref('')
const errorMsg = ref('')
const successMsg = ref('')
const loading = ref(false)
const currentHour = ref(new Date().getHours())

// 全局背景音乐（登录界面、大厅共用）
const bgMusic = ref(null)
const bgMusicPlaying = ref(false)
const bgMusicVolume = ref(0.5)

// 初始化全局背景音乐
function initBgMusic() {
  // 检查是否已存在全局音频对象
  if (window.__globalAudio) {
    bgMusic.value = window.__globalAudio
    bgMusicPlaying.value = !bgMusic.value.paused
    bgMusicVolume.value = bgMusic.value.volume
    return
  }
  
  bgMusic.value = new Audio('/music/login-music.mp3')
  bgMusic.value.loop = true
  bgMusic.value.volume = bgMusicVolume.value
  
  // 保存到全局变量
  window.__globalAudio = bgMusic.value
  window.__globalAudioType = 'login'
  
  bgMusic.value.addEventListener('error', () => {
    console.warn('背景音乐加载失败')
  })
  
  bgMusic.value.addEventListener('play', () => {
    bgMusicPlaying.value = true
  })
  
  bgMusic.value.addEventListener('pause', () => {
    bgMusicPlaying.value = false
  })
}

// 播放/暂停背景音乐
function toggleBgMusic() {
  if (!bgMusic.value) initBgMusic()
  
  if (bgMusicPlaying.value) {
    bgMusic.value.pause()
    bgMusicPlaying.value = false
  } else {
    bgMusic.value.play().then(() => {
      bgMusicPlaying.value = true
    }).catch(err => {
      console.warn('音乐播放失败:', err)
    })
  }
}

// 调整音量
function changeBgMusicVolume(vol) {
  bgMusicVolume.value = vol
  if (bgMusic.value) {
    bgMusic.value.volume = vol
  }
}

// 装饰性小棋盘数据 (9x9)
const miniBoard = ref([
  [0, 0, 0, 0, 2, 0, 0, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, 0, 1, 0, 0, 0, 2, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 0, 0],
  [2, 0, 0, 0, 0, 0, 0, 0, 1],
  [0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, 0, 2, 0, 0, 0, 1, 0, 0],
  [0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, 0, 0, 0, 1, 0, 0, 0, 0],
])

// 更新时间的定时器
let timer = null

onMounted(() => {
  timer = setInterval(() => {
    currentHour.value = new Date().getHours()
  }, 60000) // 每分钟更新一次
  
  // 自动初始化并播放登录界面音乐
  initBgMusic()
  // 尝试自动播放
  bgMusic.value.play().then(() => {
    bgMusicPlaying.value = true
  }).catch(() => {
    // 如果自动播放失败，等待用户交互
    console.log('需要用户交互才能播放音乐')
  })
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  // 不停止音乐，让音乐继续在大厅播放
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// 根据时间计算问候语
const greeting = computed(() => {
  const hour = currentHour.value
  if (hour >= 5 && hour < 12) {
    return '早上好，棋友'
  } else if (hour >= 12 && hour < 14) {
    return '午安，棋友'
  } else if (hour >= 14 && hour < 18) {
    return '下午好，棋友'
  } else if (hour >= 18 && hour < 22) {
    return '晚上好，棋友'
  } else {
    return '夜深了，棋友'
  }
})

async function handleSubmit() {
  errorMsg.value = ''
  successMsg.value = ''
  loading.value = true

  if (!isLogin.value) {
    // 注册流程：先注册，弹窗提示成功后再自动登录
    try {
      const res = await userApi.register({ nickname: nickname.value, password: password.value })
      if (res.code === 200) {
        successMsg.value = '注册成功！正在自动登录...'
        // 延迟 1.5 秒后自动登录
        await new Promise(r => setTimeout(r, 1500))
        try {
          const loginRes = await userApi.login({ nickname: nickname.value, password: password.value })
          if (loginRes.code === 200) {
            successMsg.value = '登录成功！正在跳转...'
            sessionStorage.setItem('user', JSON.stringify(loginRes.data))
            await new Promise(r => setTimeout(r, 500))
            router.push('/lobby')
          } else {
            errorMsg.value = loginRes.message || '自动登录失败，请手动登录'
            isLogin.value = true
          }
        } catch {
          errorMsg.value = '自动登录失败，请手动登录'
          isLogin.value = true
        }
      } else {
        errorMsg.value = res.message || '注册失败'
      }
    } catch {
      errorMsg.value = '系统维护中'
    } finally {
      loading.value = false
    }
    return
  }

  // 登录流程
  try {
    const res = await userApi.login({ nickname: nickname.value, password: password.value })
    if (res.code === 200) {
      sessionStorage.setItem('user', JSON.stringify(res.data))
      router.push('/lobby')
    } else {
      errorMsg.value = res.message || '操作失败'
    }
  } catch {
    errorMsg.value = '系统维护中'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 10%;
  position: relative;
  overflow: hidden;
}

/* 古风山水背景图片 */
.bg-image {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: url('/ancient-bg.webp');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  z-index: 0;
  animation: bgSlowZoom 60s ease-in-out infinite alternate;
}

@keyframes bgSlowZoom {
  0% { transform: scale(1); }
  100% { transform: scale(1.05); }
}

/* 背景遮罩层 - 确保文字可读性 */
.bg-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg,
    rgba(10, 10, 30, 0.6) 0%,
    rgba(20, 20, 50, 0.5) 50%,
    rgba(10, 10, 30, 0.6) 100%
  );
  z-index: 1;
}

/* 左侧装饰区域 */
.left-decoration {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 50%;
  z-index: 5;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 40px;
}

/* 竹子装饰 */
.bamboo {
  position: absolute;
  bottom: 0;
}

.bamboo-1 {
  left: 8%;
  height: 70%;
}

.bamboo-2 {
  left: 15%;
  height: 60%;
}

.bamboo-stalk {
  width: 8px;
  height: 100%;
  background: linear-gradient(90deg, #2d5a27 0%, #4a7c3f 50%, #2d5a27 100%);
  border-radius: 4px;
  position: relative;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.3);
}

.bamboo-stalk::before,
.bamboo-stalk::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: rgba(0, 0, 0, 0.2);
}

.bamboo-stalk::before {
  top: 30%;
}

.bamboo-stalk::after {
  top: 60%;
}

.bamboo-leaves {
  position: absolute;
  top: 0;
  left: 50%;
}

.leaf {
  position: absolute;
  width: 40px;
  height: 120px;
  background: linear-gradient(135deg, #3a6b32, #5a8c4a);
  border-radius: 0 100% 0 100%;
  transform-origin: bottom center;
  opacity: 0.9;
}

.leaf-1 {
  top: -50px;
  left: 10px;
  transform: rotate(-30deg);
  animation: leafSway 4s ease-in-out infinite;
}

.leaf-2 {
  top: -80px;
  left: -30px;
  transform: rotate(-60deg);
  animation: leafSway 4.5s ease-in-out infinite;
  animation-delay: 0.5s;
}

.leaf-3 {
  top: -60px;
  left: 20px;
  transform: rotate(-45deg);
  animation: leafSway 3.5s ease-in-out infinite;
  animation-delay: 1s;
}

.leaf-4 {
  top: -40px;
  left: 5px;
  transform: rotate(-25deg);
  animation: leafSway 4.2s ease-in-out infinite;
}

.leaf-5 {
  top: -70px;
  left: -25px;
  transform: rotate(-55deg);
  animation: leafSway 3.8s ease-in-out infinite;
  animation-delay: 0.8s;
}

@keyframes leafSway {
  0%, 100% { transform: rotate(-30deg) translateY(0); }
  50% { transform: rotate(-35deg) translateY(-5px); }
}

/* 花朵装饰 */
.flower {
  position: absolute;
  font-size: 36px;
  opacity: 0.6;
  animation: flowerFloat 6s ease-in-out infinite;
}

.flower-1 {
  top: 25%;
  left: 20%;
  color: #e8b4b8;
  animation-delay: 0s;
}

.flower-2 {
  top: 45%;
  left: 12%;
  color: #f0c0c0;
  font-size: 28px;
  animation-delay: 2s;
}

.flower-3 {
  bottom: 30%;
  left: 18%;
  color: #d4a574;
  font-size: 32px;
  animation-delay: 4s;
}

@keyframes flowerFloat {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-10px) rotate(5deg); }
}

/* 问候语和诗词区域 */
.greeting-section {
  text-align: left;
  animation: fadeInLeft 1s ease-out;
  padding-left: 20px;
}

@keyframes fadeInLeft {
  from {
    opacity: 0;
    transform: translateX(-30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.greeting {
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  font-size: 72px;
  color: rgba(232, 196, 160, 0.95);
  margin-bottom: 25px;
  letter-spacing: 12px;
  text-shadow:
    0 2px 20px rgba(212, 165, 116, 0.5),
    0 4px 40px rgba(0, 0, 0, 0.6);
  animation: greetingGlow 3s ease-in-out infinite;
  transform: rotate(-2deg);
  display: inline-block;
}

@keyframes greetingGlow {
  0%, 100% { text-shadow: 0 2px 20px rgba(212, 165, 116, 0.5), 0 4px 40px rgba(0, 0, 0, 0.6); }
  50% { text-shadow: 0 2px 30px rgba(212, 165, 116, 0.7), 0 4px 50px rgba(0, 0, 0, 0.7); }
}

.poem {
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  color: rgba(220, 220, 240, 0.9);
  font-size: 36px;
  line-height: 1.6;
  margin-bottom: 30px;
  letter-spacing: 8px;
  text-shadow: 0 2px 15px rgba(0, 0, 0, 0.5);
}

.poem p {
  margin: 15px 0;
}

.poem p:first-child {
  transform: rotate(-1deg);
  margin-left: 30px;
}

.poem p:last-child {
  transform: rotate(1deg);
  margin-left: 60px;
}

.welcome-text {
  font-family: 'ZCOOL XiaoWei', serif;
  color: rgba(200, 200, 220, 0.7);
  font-size: 22px;
  line-height: 1.8;
  letter-spacing: 4px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  margin-left: 90px;
  transform: rotate(-0.5deg);
}

.welcome-text p {
  margin: 5px 0;
}

/* 漂浮的棋子 */
.floating-pieces {
  position: absolute;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.piece {
  position: absolute;
  font-size: 40px;
  opacity: 0.08;
  color: #fff;
  font-family: serif;
  animation: pieceFloat 25s infinite ease-in-out;
}

.piece-1 {
  top: 15%;
  left: 25%;
  animation-delay: 0s;
}

.piece-2 {
  top: 60%;
  left: 10%;
  animation-delay: 8s;
}

.piece-3 {
  bottom: 20%;
  left: 30%;
  animation-delay: 16s;
}

@keyframes pieceFloat {
  0%, 100% { transform: translateY(0px) rotate(0deg); }
  25% { transform: translateY(-15px) rotate(3deg); }
  50% { transform: translateY(8px) rotate(-2deg); }
  75% { transform: translateY(-10px) rotate(2deg); }
}

/* 登录卡片 */
.login-card {
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(25px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  padding: 40px 40px 45px;
  width: 420px;
  text-align: center;
  position: relative;
  z-index: 10;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.4),
    0 0 100px rgba(100, 100, 200, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  animation: cardFadeIn 0.8s ease-out;
}

@keyframes cardFadeIn {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.logo-section {
  margin-bottom: 10px;
}

.logo-icon {
  font-size: 56px;
  margin-bottom: 8px;
  animation: logoFloat 3s ease-in-out infinite;
  filter: drop-shadow(0 4px 10px rgba(212, 165, 116, 0.3));
}

@keyframes logoFloat {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-8px); }
}

h1 {
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  font-size: 32px;
  margin-bottom: 8px;
  background: linear-gradient(135deg, #e8c4a0, #d4a574, #c49060);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-weight: normal;
  letter-spacing: 4px;
  text-shadow: none;
}

.subtitle {
  font-family: 'ZCOOL XiaoWei', serif;
  color: rgba(200, 200, 220, 0.6);
  font-size: 14px;
  margin-bottom: 28px;
  letter-spacing: 3px;
}

.tabs {
  display: flex;
  gap: 0;
  margin-bottom: 28px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.03);
}

.tabs button {
  flex: 1;
  padding: 11px;
  border: none;
  background: transparent;
  color: rgba(200, 200, 220, 0.6);
  cursor: pointer;
  font-size: 15px;
  font-family: 'ZCOOL XiaoWei', serif;
  font-weight: normal;
  letter-spacing: 2px;
  transition: all 0.3s ease;
}

.tabs button.active {
  background: linear-gradient(135deg, rgba(232, 196, 160, 0.3), rgba(212, 165, 116, 0.3));
  color: #e8c4a0;
  box-shadow: 0 2px 10px rgba(232, 196, 160, 0.15);
  border: 1px solid rgba(232, 196, 160, 0.2);
}

.tabs button:hover:not(.active) {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(200, 200, 220, 0.8);
}

.form { text-align: left; }

.input-group { margin-bottom: 20px; }

.input-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-family: 'ZCOOL XiaoWei', serif;
  color: rgba(200, 200, 220, 0.7);
  letter-spacing: 2px;
}

.input-group input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  color: #eee;
  font-size: 14px;
  font-family: 'ZCOOL XiaoWei', serif;
  outline: none;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.input-group input:focus {
  border-color: rgba(212, 165, 116, 0.5);
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 3px rgba(212, 165, 116, 0.08);
}

.input-group input::placeholder {
  color: rgba(200, 200, 220, 0.3);
  font-family: 'ZCOOL XiaoWei', serif;
}

.error {
  color: #d4a574;
  font-size: 13px;
  margin-bottom: 16px;
  text-align: center;
  padding: 8px 12px;
  background: rgba(212, 165, 116, 0.08);
  border-radius: 8px;
  border: 1px solid rgba(212, 165, 116, 0.15);
  font-family: 'ZCOOL XiaoWei', serif;
}

.success {
  color: #4ecca3;
  font-size: 14px;
  margin-bottom: 16px;
  text-align: center;
  padding: 8px 12px;
  background: rgba(78, 204, 163, 0.08);
  border-radius: 8px;
  border: 1px solid rgba(78, 204, 163, 0.15);
  font-family: 'ZCOOL XiaoWei', serif;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

.btn-primary {
  width: 100%;
  padding: 13px;
  border: 1px solid rgba(232, 196, 160, 0.3);
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(232, 196, 160, 0.2), rgba(212, 165, 116, 0.2));
  color: #e8c4a0;
  font-size: 16px;
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  letter-spacing: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(232, 196, 160, 0.1);
  margin-top: 5px;
}

.btn-primary:hover {
  transform: translateY(-2px);
  background: linear-gradient(135deg, rgba(232, 196, 160, 0.3), rgba(212, 165, 116, 0.3));
  box-shadow: 0 6px 20px rgba(232, 196, 160, 0.15);
  border-color: rgba(232, 196, 160, 0.5);
}

.btn-primary:active {
  transform: translateY(0px);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

/* 装饰性小棋盘 */
.mini-board {
  display: flex;
  justify-content: center;
  margin: 15px auto 20px;
  padding: 10px;
  background: rgba(139, 90, 43, 0.15);
  border-radius: 8px;
  border: 1px solid rgba(139, 90, 43, 0.25);
  backdrop-filter: blur(5px);
  width: fit-content;
  animation: boardFloat 4s ease-in-out infinite;
}

/* 登录界面音乐控制按钮 */
.music-control-login {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 100;
}

.music-btn-login {
  padding: 10px 14px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(10px);
  color: #e8c4a0;
  cursor: pointer;
  font-size: 20px;
  transition: all 0.3s;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.music-btn-login span {
  color: #e8c4a0;
  filter: none;
}

.music-btn-login:hover {
  border-color: #d4a574;
  background: rgba(212, 165, 116, 0.2);
  transform: scale(1.1);
}

@keyframes boardFloat {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
}

.board-grid {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 5px;
  background: rgba(210, 180, 140, 0.25);
  border-radius: 4px;
  border: 1px solid rgba(139, 90, 43, 0.3);
}

.board-row {
  display: flex;
  gap: 0;
}

.board-cell {
  width: 20px;
  height: 20px;
  position: relative;
  border-right: 1px solid rgba(139, 90, 43, 0.4);
  border-bottom: 1px solid rgba(139, 90, 43, 0.4);
  box-sizing: border-box;
}

.board-row:last-child .board-cell {
  border-bottom: none;
}

.board-cell:last-child {
  border-right: none;
}

.piece-on-board {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 14px;
  height: 14px;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  animation: pieceAppear 0.5s ease-out backwards;
}

.board-cell.black .piece-on-board {
  background: radial-gradient(circle at 35% 35%, #555, #000);
  animation-delay: 0.1s;
}

.board-cell.white .piece-on-board {
  background: radial-gradient(circle at 35% 35%, #fff, #ddd);
  animation-delay: 0.2s;
}

@keyframes pieceAppear {
  from {
    transform: translate(-50%, -50%) scale(0);
    opacity: 0;
  }
  to {
    transform: translate(-50%, -50%) scale(1);
    opacity: 1;
  }
}
</style>
