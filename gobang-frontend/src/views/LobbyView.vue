<template>
  <div class="lobby-page">
    <!-- 古风山水背景图片 -->
    <div class="bg-image"></div>
    <div class="bg-overlay"></div>

    <header>
      <h1>五子棋 游戏大厅</h1>
      <div class="header-right">
        <span class="username">{{ user?.nickname }}</span>
        <!-- 音乐控制 -->
        <div class="music-control">
          <button class="music-btn" @click="toggleLobbyMusic" :title="lobbyMusicPlaying ? '暂停音乐' : '播放音乐'">
            <svg v-if="lobbyMusicPlaying" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="6" y="4" width="4" height="16" rx="1" fill="#e8c4a0"/>
              <rect x="14" y="4" width="4" height="16" rx="1" fill="#e8c4a0"/>
            </svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M6 4L18 12L6 20V4Z" fill="#e8c4a0"/>
            </svg>
          </button>
          <input 
            type="range" 
            class="volume-slider" 
            min="0" 
            max="1" 
            step="0.1" 
            :value="lobbyMusicVolume"
            @input="changeLobbyVolume"
            title="音量"
          />
        </div>
        <button class="btn-outline" @click="goProfile">个人中心</button>
        <button class="btn-text" @click="logout">退出</button>
      </div>
    </header>

    <div class="content">
      <h2>选择难度开始游戏</h2>
      <div class="difficulty-cards">
        <div class="card" :class="{ selected: difficulty === 'EASY' }" @click="difficulty = 'EASY'">
          <div class="card-icon">😊</div>
          <h3>初级</h3>
          <p>随机防守策略<br/>适合新手入门</p>
        </div>
        <div class="card" :class="{ selected: difficulty === 'MEDIUM' }" @click="difficulty = 'MEDIUM'">
          <div class="card-icon">🤔</div>
          <h3>中级</h3>
          <p>评分算法策略<br/>需要一些技巧</p>
        </div>
        <div class="card" :class="{ selected: difficulty === 'HARD' }" @click="difficulty = 'HARD'">
          <div class="card-icon">🔥</div>
          <h3>高级</h3>
          <p>深度搜索算法<br/>高手挑战级别</p>
        </div>
      </div>

      <div class="mode-selection">
        <h3>游戏模式</h3>
        <div class="mode-buttons">
          <button
            class="mode-btn"
            :class="{ selected: gameMode === 'TWO' }"
            @click="gameMode = 'TWO'"
          >双人模式</button>
          <button
            class="mode-btn"
            :class="{ selected: gameMode === 'THREE' }"
            @click="gameMode = 'THREE'"
          >三人混战</button>
          <button
            class="mode-btn obstacle"
            :class="{ selected: gameMode === 'OBSTACLE' }"
            @click="gameMode = 'OBSTACLE'"
          >🏔️ 障碍物模式</button>
          <button
            class="mode-btn three-obstacle"
            :class="{ selected: gameMode === 'THREE_OBSTACLE' }"
            @click="gameMode = 'THREE_OBSTACLE'"
          >🔺 三人障碍物</button>
        </div>
      </div>

      <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
      <button class="btn-start" @click="startGame" :disabled="loading">
        {{ loading ? '创建游戏中...' : '开始游戏' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { gameApi, userApi } from '../api'

const router = useRouter()
const difficulty = ref('EASY')
const gameMode = ref('TWO')  // 新增：游戏模式
const loading = ref(false)
const errorMsg = ref('')

// 音乐控制（与登录界面共用同一个Audio对象）
const lobbyMusic = ref(null)
const lobbyMusicPlaying = ref(false)
const lobbyMusicVolume = ref(0.5)

// 初始化音乐（复用登录界面的音乐）
function initLobbyMusic() {
  // 从全局变量获取音频对象
  if (window.__globalAudio && window.__globalAudioType === 'login') {
    lobbyMusic.value = window.__globalAudio
    lobbyMusicPlaying.value = !lobbyMusic.value.paused
    lobbyMusicVolume.value = lobbyMusic.value.volume
  } else {
    // 如果不存在，创建新的
    lobbyMusic.value = new Audio('/music/login-music.mp3')
    lobbyMusic.value.loop = true
    lobbyMusic.value.volume = lobbyMusicVolume.value
    window.__globalAudio = lobbyMusic.value
    window.__globalAudioType = 'login'
  }
}

// 播放/暂停音乐
function toggleLobbyMusic() {
  if (!lobbyMusic.value) initLobbyMusic()
  
  if (lobbyMusicPlaying.value) {
    lobbyMusic.value.pause()
    lobbyMusicPlaying.value = false
  } else {
    lobbyMusic.value.play().then(() => {
      lobbyMusicPlaying.value = true
    }).catch(err => {
      console.warn('音乐播放失败:', err)
    })
  }
}

// 调整音量
function changeLobbyVolume(event) {
  const vol = parseFloat(event.target.value)
  lobbyMusicVolume.value = vol
  if (lobbyMusic.value) {
    lobbyMusic.value.volume = vol
  }
}

onMounted(() => {
  initLobbyMusic()
  // 确保音乐在播放（从游戏返回时可能需要重新播放）
  if (lobbyMusic.value) {
    if (lobbyMusic.value.paused) {
      lobbyMusic.value.play().then(() => {
        lobbyMusicPlaying.value = true
      }).catch(err => {
        console.warn('大厅音乐播放失败:', err)
      })
    } else {
      lobbyMusicPlaying.value = true
    }
  }
})

onUnmounted(() => {
  // 离开大厅时不停止音乐，让音乐继续在游戏中播放
})

const user = JSON.parse(sessionStorage.getItem('user') || '{}')

function goProfile() { router.push('/profile') }

async function startGame() {
  errorMsg.value = ''
  loading.value = true
  try {
    const res = await gameApi.start(difficulty.value, gameMode.value)
    if (res.code === 200) {
      sessionStorage.setItem('difficulty', difficulty.value)
      sessionStorage.setItem('gameMode', gameMode.value)
      sessionStorage.setItem('gameState', JSON.stringify(res.data))
      router.push('/game')
    } else {
      errorMsg.value = res.message || '开始游戏失败'
    }
  } catch {
    errorMsg.value = '系统维护中'
  } finally {
    loading.value = false
  }
}

async function logout() {
  await userApi.logout()
  sessionStorage.clear()
  router.push('/login')
}
</script>

<style scoped>
.lobby-page {
  min-height: 100vh;
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
}

/* 背景遮罩层 */
.bg-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg,
    rgba(10, 10, 30, 0.65) 0%,
    rgba(20, 20, 50, 0.55) 50%,
    rgba(10, 10, 30, 0.65) 100%
  );
  z-index: 1;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: rgba(255,255,255,0.03);
  position: relative;
  z-index: 10;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
header h1 {
  font-size: 20px;
  color: #e8c4a0;
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  letter-spacing: 2px;
}
.header-right { display: flex; align-items: center; gap: 16px; }
.username { color: #ccc; font-size: 14px; }

/* 音乐控制 */
.music-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
.music-btn {
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  color: #e8c4a0;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.3s;
}
.music-btn:hover {
  border-color: #d4a574;
  color: #d4a574;
  background: rgba(212, 165, 116, 0.1);
}
.volume-slider {
  width: 80px;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}
.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #d4a574;
  cursor: pointer;
}
.volume-slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #d4a574;
  cursor: pointer;
  border: none;
}
.btn-outline {
  padding: 6px 16px;
  border: 1px solid rgba(255,255,255,0.3);
  border-radius: 6px;
  background: transparent;
  color: #ccc;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.3s;
}
.btn-outline:hover { border-color: #d4a574; color: #d4a574; }
.btn-text {
  padding: 6px 8px;
  border: none;
  background: transparent;
  color: #888;
  cursor: pointer;
  font-size: 13px;
}
.btn-text:hover { color: #d4a574; }

.content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  position: relative;
  z-index: 10;
}
.content h2 {
  font-size: 22px;
  color: #ccc;
  margin-bottom: 40px;
}
.difficulty-cards {
  display: flex;
  gap: 24px;
  margin-bottom: 40px;
}
.card {
  width: 200px;
  padding: 30px 20px;
  background: rgba(255,255,255,0.05);
  border: 2px solid rgba(255,255,255,0.1);
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}
.card:hover {
  border-color: rgba(212,165,116,0.5);
  transform: translateY(-4px);
}
.card.selected {
  border-color: #d4a574;
  background: rgba(212,165,116,0.1);
}
.card-icon { font-size: 36px; margin-bottom: 12px; }
.card h3 { font-size: 18px; color: #eee; margin-bottom: 8px; }
.card p { font-size: 13px; color: #888; line-height: 1.6; }

.btn-start {
  padding: 14px 60px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #8B7355, #5C4033);
  border: 1px solid #6B5340;
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  transition: all 0.3s;
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  letter-spacing: 2px;
}
.btn-start:hover { 
  opacity: 0.9; 
  transform: scale(1.05);
  box-shadow: 0 4px 15px rgba(139, 115, 85, 0.4);
}
.btn-start:disabled { opacity: 0.6; transform: none; cursor: not-allowed; }
.error {
  color: #d4a574;
  margin-bottom: 16px;
  font-size: 14px;
}

.mode-selection {
  margin-bottom: 40px;
  text-align: center;
}
.mode-selection h3 {
  font-size: 14px;
  color: #ccc;
  margin-bottom: 16px;
}
.mode-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
}
.mode-btn {
  flex: 1;
  max-width: 180px;
  padding: 10px 0;
  border: 1px solid rgba(255,255,255,0.2);
  border-radius: 8px;
  background: transparent;
  color: #ccc;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}
.mode-btn:hover {
  border-color: rgba(212,165,116,0.5);
}
.mode-btn.selected {
  background: linear-gradient(135deg, #8B7355, #5C4033);
  border-color: #6B5340;
  color: #fff;
}
</style>
