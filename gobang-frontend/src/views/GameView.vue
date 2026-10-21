<template>
  <div class="game-page">
    <!-- 古风山水背景图片 -->
    <div class="bg-image"></div>
    <div class="bg-overlay"></div>

    <header>
      <button class="btn-back" @click="goBack">← 返回大厅</button>
      <h1>五子棋 · {{ difficultyLabel }}</h1>
      <div class="header-info">
        <span class="header-turn" :class="statusClass">{{ turnLabel || message }}</span>
        <span>悔棋 {{ undoRemaining }}/{{ undoLimit }}</span>
        <div class="music-control">
          <button class="music-btn" @click="toggleMusic" :title="musicPlaying ? '暂停音乐' : '播放音乐'">
            <svg v-if="musicPlaying" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
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
            :value="musicVolume"
            @input="changeVolume"
            title="音量"
          />
        </div>
      </div>
    </header>

    <div class="game-area">
        <div class="board-wrapper">
        <!-- 棋罐装饰 - 圆形木罐 -->
        <div class="stone-boxes">
          <!-- 双人模式：对角线两个角 -->
          <template v-if="gameMode === 'TWO' || gameMode === 'OBSTACLE'">
            <!-- 左上角 - 黑棋罐 -->
            <div class="stone-jar corner-tl">
              <div class="jar-lid"></div>
              <div class="jar-body">
                <div class="jar-rim"></div>
                <div class="jar-content">
                  <div class="jar-stones">
                    <div class="jar-stone black"></div>
                    <div class="jar-stone black"></div>
                    <div class="jar-stone black"></div>
                    <div class="jar-stone black"></div>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 右下角 - 白棋罐 -->
            <div class="stone-jar corner-br">
              <div class="jar-lid"></div>
              <div class="jar-body">
                <div class="jar-rim"></div>
                <div class="jar-content">
                  <div class="jar-stones">
                    <div class="jar-stone white"></div>
                    <div class="jar-stone white"></div>
                    <div class="jar-stone white"></div>
                    <div class="jar-stone white"></div>
                  </div>
                </div>
              </div>
            </div>
          </template>
          
          <!-- 三人模式：三个角 -->
          <template v-if="gameMode === 'THREE' || gameMode === 'THREE_OBSTACLE'">
            <!-- 左上角 - 黑棋罐 -->
            <div class="stone-jar corner-tl">
              <div class="jar-lid"></div>
              <div class="jar-body">
                <div class="jar-rim"></div>
                <div class="jar-content">
                  <div class="jar-stones">
                    <div class="jar-stone black"></div>
                    <div class="jar-stone black"></div>
                    <div class="jar-stone black"></div>
                    <div class="jar-stone black"></div>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 右上角 - 白棋罐 -->
            <div class="stone-jar corner-tr">
              <div class="jar-lid"></div>
              <div class="jar-body">
                <div class="jar-rim"></div>
                <div class="jar-content">
                  <div class="jar-stones">
                    <div class="jar-stone white"></div>
                    <div class="jar-stone white"></div>
                    <div class="jar-stone white"></div>
                    <div class="jar-stone white"></div>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 左下角 - 灰棋罐 -->
            <div class="stone-jar corner-bl">
              <div class="jar-lid"></div>
              <div class="jar-body">
                <div class="jar-rim"></div>
                <div class="jar-content">
                  <div class="jar-stones">
                    <div class="jar-stone gray"></div>
                    <div class="jar-stone gray"></div>
                    <div class="jar-stone gray"></div>
                    <div class="jar-stone gray"></div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div class="board-container">
          <!-- 对话气泡 -->
          <div v-if="showBubble && currentBubble" class="bubble-wrapper">
            <div class="bubble" :class="currentBubble.sender">
              <span class="bubble-avatar">{{ currentBubble.sender === 'player' ? '🧑' : '🤖' }}</span>
              <div class="bubble-text">{{ currentBubble.text }}</div>
            </div>
          </div>

          <canvas
            ref="canvasRef"
            :width="canvasSize"
            :height="canvasSize"
            @click="handleClick"
            @mousemove="handleMouseMove"
            @mouseleave="hoverPos = null"
            aria-label="五子棋棋盘，点击交叉点落子"
          ></canvas>
        </div>
      </div>

      <div class="side-panel">
        <div class="status-box" :class="statusClass">
          <div class="status-icon">{{ statusIcon }}</div>
          <div class="status-text">{{ message }}</div>
          <div v-if="turnLabel" class="turn-label">{{ turnLabel }}</div>
        </div>

        <div class="controls">
          <button v-if="status === 'PLAYER_TURN'" class="btn-ctrl" @click="doPause">
            ⏸ 暂停
          </button>
          <button v-if="status === 'PAUSED'" class="btn-ctrl resume" @click="doResume">
            ▶ 继续
          </button>
          <button
              v-if="['PLAYER_TURN', 'GRAY_TURN'].includes(status) && undoRemaining > 0"
            class="btn-ctrl undo"
            @click="doUndo"
            :disabled="undoLoading"
          >
            ↩ 悔棋 ({{ undoRemaining }})
          </button>
          <button
            v-if="['PLAYER_WIN', 'AI_WIN', 'WHITE_WIN', 'GRAY_WIN', 'DRAW'].includes(status)"
            class="btn-ctrl new-game"
            @click="goBack"
          >
            🆕 新游戏
          </button>
          <!-- 动态棋盘模式：解锁区块按钮 -->
          <button
            v-if="gameMode === 'DYNAMIC' && unlockTokens > 0 && status === 'PLAYER_TURN'"
            class="btn-ctrl unlock"
            @click="doUnlockBlock"
          >
            🔓 解锁区块 ({{ unlockTokens }})
          </button>
        </div>

        <div class="legend">
          <div class="legend-item"><span class="dot black"></span> 你 (黑)</div>
          <div class="legend-item"><span class="dot white"></span> AI (白)</div>
          <div v-if="gameMode === 'THREE' || gameMode === 'THREE_OBSTACLE'" class="legend-item"><span class="dot gray"></span> AI (灰)</div>
          <div v-if="gameMode === 'OBSTACLE' || gameMode === 'THREE_OBSTACLE'" class="legend-item">
            <span class="dot terrain-obstacle"></span> 假山（可包围消除）
          </div>
          <div v-if="gameMode === 'OBSTACLE' || gameMode === 'THREE_OBSTACLE'" class="legend-item">
            <span class="dot terrain-water"></span> 水潭（落子滑走）
          </div>
        </div>

        <!-- 玩家快捷语回击 -->
        <div class="player-phrases">
          <div class="phrases-title">快捷语回击</div>
          <div class="phrases-grid">
            <button
              v-for="(phrase, idx) in playerPhrases"
              :key="idx"
              class="phrase-btn"
              @click="onPlayerPhrase(phrase)"
            >{{ phrase }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 游戏结束弹窗 -->
    <div v-if="gameOver" class="result-overlay" @click.self="closeResult">
      <div class="result-modal" :class="resultType">
        <div class="result-icon">{{ resultIcon }}</div>
        <div class="result-title">{{ resultTitle }}</div>
        <div class="result-subtitle">{{ resultSubtitle }}</div>
        <div class="result-buttons">
          <button class="result-btn secondary" @click="goBack">🏠 返回大厅</button>
          <button class="result-btn primary" @click="restartGame">🔄 再来一局</button>
        </div>
      </div>
    </div>

    <!-- 获得解锁令牌弹窗 -->
    <div v-if="showUnlockNotify" class="notify-overlay" @click="dismissUnlockNotify">
      <div class="notify-modal">
        <div class="notify-icon">🔓</div>
        <div class="notify-title">获得解锁机会！</div>
        <div class="notify-text">你获得了 1 次解锁区块的机会，当前可用：<strong>{{ unlockNotifyTokens }}</strong> 次</div>
        <div class="notify-hint">点击任意处关闭</div>
      </div>
    </div>

    <!-- AI自动解锁区块弹窗 -->
    <div v-if="showAiUnlockNotify" class="notify-overlay" @click="dismissAiUnlockNotify">
      <div class="notify-modal ai-unlock">
        <div class="notify-icon">🤖</div>
        <div class="notify-title">AI 解锁了区块！</div>
        <div class="notify-text">{{ aiUnlockNotifyMsg }}</div>
        <div class="notify-hint">点击任意处关闭</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { gameApi } from '../api'

const router = useRouter()
const canvasRef = ref(null)

// 背景音乐
const bgMusic = ref(null)
const musicPlaying = ref(false)
const musicVolume = ref(0.2)
const musicLoaded = ref(false)

// 落子音效
let stoneAudioContext = null

function playStoneSound() {
  try {
    // 使用真实的音频文件（如果存在）
    const stoneSound = new Audio('/music/stone-place.mp3')
    stoneSound.volume = 0.5
    stoneSound.play().catch(() => {
      // 如果文件不存在，使用Web Audio生成音效
      playStoneSoundFallback()
    })
  } catch (err) {
    console.warn('落子音效播放失败:', err)
    playStoneSoundFallback()
  }
}

// 备用音效（Web Audio生成）
function playStoneSoundFallback() {
  try {
    if (!stoneAudioContext) {
      stoneAudioContext = new (window.AudioContext || window.webkitAudioContext)()
    }
    const ctx = stoneAudioContext
    
    // 创建更逼真的落子音效
    const oscillator1 = ctx.createOscillator()
    const oscillator2 = ctx.createOscillator()
    const gainNode = ctx.createGain()
    const filter = ctx.createBiquadFilter()
    
    // 配置滤波器（让声音更自然）
    filter.type = 'bandpass'
    filter.frequency.value = 1000
    filter.Q.value = 1
    
    // 第一个振荡器：低频撞击声
    oscillator1.type = 'sine'
    oscillator1.frequency.setValueAtTime(600, ctx.currentTime)
    oscillator1.frequency.exponentialRampToValueAtTime(150, ctx.currentTime + 0.08)
    
    // 第二个振荡器：高频点击声
    oscillator2.type = 'square'
    oscillator2.frequency.setValueAtTime(2000, ctx.currentTime)
    oscillator2.frequency.exponentialRampToValueAtTime(500, ctx.currentTime + 0.03)
    
    // 音量包络（快速衰减）
    gainNode.gain.setValueAtTime(0, ctx.currentTime)
    gainNode.gain.linearRampToValueAtTime(0.4, ctx.currentTime + 0.01)
    gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.12)
    
    // 连接音频节点
    oscillator1.connect(filter)
    oscillator2.connect(filter)
    filter.connect(gainNode)
    gainNode.connect(ctx.destination)
    
    // 播放音效
    oscillator1.start(ctx.currentTime)
    oscillator1.stop(ctx.currentTime + 0.12)
    oscillator2.start(ctx.currentTime)
    oscillator2.stop(ctx.currentTime + 0.05)
  } catch (err) {
    console.warn('落子音效播放失败:', err)
  }
}

// 初始化背景音乐
function initMusic() {
  // 停止并清理之前的音乐
  if (window.__globalAudio) {
    window.__globalAudio.pause()
    if (window.__globalAudioType !== 'game') {
      window.__globalAudio = null
    }
  }
  
  if (bgMusic.value) return
  
  // 创建音频对象（用户可以替换为自己的音乐文件）
  bgMusic.value = new Audio('/music/guzheng1.mp3')
  bgMusic.value.loop = true
  bgMusic.value.volume = musicVolume.value
  
  // 保存到全局变量
  window.__globalAudio = bgMusic.value
  window.__globalAudioType = 'game'
  
  // 预加载音乐文件
  bgMusic.value.addEventListener('canplaythrough', () => {
    musicLoaded.value = true
  })
  
  bgMusic.value.addEventListener('error', () => {
    console.warn('背景音乐加载失败，请确保音乐文件存在于 public/music/ 目录下')
  })
  
  bgMusic.value.addEventListener('play', () => {
    musicPlaying.value = true
  })
  
  bgMusic.value.addEventListener('pause', () => {
    musicPlaying.value = false
  })
}

// 播放/暂停音乐
function toggleMusic() {
  if (!bgMusic.value) initMusic()
  
  if (musicPlaying.value) {
    bgMusic.value.pause()
    musicPlaying.value = false
  } else {
    bgMusic.value.play().then(() => {
      musicPlaying.value = true
    }).catch(err => {
      console.warn('音乐播放失败:', err)
    })
  }
}

// 调整音量
function changeVolume(event) {
  const vol = parseFloat(event.target.value)
  musicVolume.value = vol
  if (bgMusic.value) {
    bgMusic.value.volume = vol
  }
}

// 组件卸载时停止音乐
onUnmounted(() => {
  if (bgMusic.value) {
    bgMusic.value.pause()
    bgMusic.value = null
    window.__globalAudio = null
    window.__globalAudioType = null
  }
})

// 棋盘配置
const PADDING = 30

const gameMode = ref('TWO')
const boardSize = computed(() => gameMode.value === 'THREE' || gameMode.value === 'THREE_OBSTACLE' ? 25 : 15)
const cellSize = computed(() => gameMode.value === 'THREE' || gameMode.value === 'THREE_OBSTACLE' ? 22 : 36)
const stoneRadius = computed(() => gameMode.value === 'THREE' || gameMode.value === 'THREE_OBSTACLE' ? 9 : 15)
const canvasSize = computed(() => PADDING * 2 + cellSize.value * (boardSize.value - 1))
const undoLimit = computed(() => ['OBSTACLE', 'THREE_OBSTACLE'].includes(gameMode.value) ? 3 : 2)

// 动态棋盘模式相关状态
const blockStates = ref(null)  // 区块状态数组（5x5）
const unlockTokens = ref(0)    // 解锁令牌数
const showBlockSelector = ref(false)  // 是否显示区块选择器
const showUnlockNotify = ref(false)   // 获得解锁令牌弹窗
const unlockNotifyTokens = ref(0)     // 弹窗中显示的令牌数
const showAiUnlockNotify = ref(false) // AI自动解锁弹窗
const aiUnlockNotifyMsg = ref('')     // AI解锁弹窗消息
let unlockNotifyTimer = null          // 弹窗自动关闭计时器

// 星位点（动态计算）
const starPoints = computed(() => {
  const size = boardSize.value
  const edge = Math.max(3, Math.floor(size / 5))
  const center = Math.floor(size / 2)
  return [edge, center, size - 1 - edge]
})

function createEmptyBoard(size) {
  return Array.from({ length: size }, () => Array(size).fill(0))
}

// 游戏状态
const board = ref(createEmptyBoard(15))
const terrain = ref(null) // 地形数据（障碍物模式）
const status = ref('PLAYER_TURN')
const difficulty = ref('EASY')
const undoRemaining = ref(2)
const message = ref('请落子')
const hoverPos = ref(null)
const undoLoading = ref(false)
const slideFrom = ref(null) // 水潭滑动动画起点
const slideTo = ref(null)   // 水潭滑动动画终点
let slideAnimTimer = null

// ===== 对话系统 =====
const dialogues = ref([])           // 对话历史记录
const currentBubble = ref(null)      // 当前显示的气泡 { sender, text, avatar }
const showBubble = ref(false)        // 气泡显示控制
let thinkTimer = null                 // 思考计时器
let randomTimer = null                // 随机发言计时器
let bubbleTimer = null                // 气泡自动消失计时器

// 文案池：AI 催促（玩家思考过久）
const aiHurryUpPhrases = [
  '快点儿，我等的花儿都谢了🌸',
  '你是去泡茶了吗？赶紧的！',
  '思考人生呢？该你落子啦～',
  '等你等到天长地久...',
  'speed 点！我棋力已经溢出啦！',
  '再不下子我就当你认输咯😏',
]

// 文案池：AI 嘲讽（AI 落子后占优）
const aiTauntPhrases = [
  '就这水平？呵呵～',
  '你这步棋... 挺有创意的🤭',
  '我让让你吧，可惜你不领情',
  '这局我已经赢定了！',
  '你的防线漏洞百出呀',
  '我赌你撑不过10手',
]

// 文案池：AI 欢呼（AI 形成连珠）
const aiCheerPhrases = [
  '哈哈哈这步妙手！',
  '四连珠！你挡得住吗～',
  '感觉良好，胜利在望！',
  '你是不是慌了？我感觉到了😆',
  '我这棋力，没谁了！',
]

// 文案池：AI 随机评论
const aiRandomPhrases = [
  '这棋盘真好看，可惜你要输了',
  '要不要我教你几招？',
  '认真点嘛，我在认真下呢',
  '你的棋风... 很特别',
  '我在想今天晚饭吃什么🍜',
  '这局挺有意思的，继续！',
]

// 玩家快捷回击语
const playerPhrases = [
  '别得意太早！',
  '你等着，我马上翻盘',
  '哼，让你几步而已',
  '失误失误，下次不会了',
  '你AI也会得意忘形？',
  '放马过来吧！',
]

// 工具函数：从数组随机取一条
function randomPick(arr) {
  return arr[Math.floor(Math.random() * arr.length)]
}

// 显示气泡（3秒后自动消失）
function showBubbleMsg(sender, text, avatar = null) {
  currentBubble.value = { sender, text, avatar }
  showBubble.value = true
  if (bubbleTimer) clearTimeout(bubbleTimer)
  bubbleTimer = setTimeout(() => {
    showBubble.value = false
  }, 3000)
}

// 添加对话记录
function addDialogue(sender, text) {
  dialogues.value.push({
    id: Date.now(),
    sender,
    text,
    time: new Date().toLocaleTimeString(),
  })
  // 保留最近20条
  if (dialogues.value.length > 20) dialogues.value.shift()
}

// ===== 对话系统结束 =====

// ===== 计时器逻辑 =====
// 监听游戏状态变化：玩家回合启动时启动思考计时器
watch(status, (newVal) => {
  // 清除旧计时器
  if (thinkTimer) { clearTimeout(thinkTimer); thinkTimer = null }
  if (newVal === 'PLAYER_TURN') {
    startThinkTimer()
  }
  // 游戏结束时清除随机计时器
  if (['PLAYER_WIN', 'AI_WIN', 'WHITE_WIN', 'GRAY_WIN', 'DRAW'].includes(newVal)) {
    if (randomTimer) { clearTimeout(randomTimer); randomTimer = null }
  }
}, { immediate: true })

function startThinkTimer() {
  if (thinkTimer) clearTimeout(thinkTimer)
  thinkTimer = setTimeout(() => {
    // 10秒思考过久，AI 催促
    const text = randomPick(aiHurryUpPhrases)
    showBubbleMsg('ai', text)
    addDialogue('ai', text)
  }, 10000)
}

function startRandomTimer() {
  if (randomTimer) clearTimeout(randomTimer)
  // 15~30秒随机间隔
  const delay = 15000 + Math.random() * 15000
  randomTimer = setTimeout(() => {
    const text = randomPick(aiRandomPhrases)
    showBubbleMsg('ai', text)
    addDialogue('ai', text)
    // 循环：继续下一个随机计时
    startRandomTimer()
  }, delay)
}

function clearAllTimers() {
  if (thinkTimer) { clearTimeout(thinkTimer); thinkTimer = null }
  if (randomTimer) { clearTimeout(randomTimer); randomTimer = null }
  if (bubbleTimer) { clearTimeout(bubbleTimer); bubbleTimer = null }
  if (unlockNotifyTimer) { clearTimeout(unlockNotifyTimer); unlockNotifyTimer = null }
  clearSlideAnim()
}

// ===== 计时器逻辑结束 =====

// 计算属性
const difficultyLabel = computed(() => {
  const map = { EASY: '初级', MEDIUM: '中级', HARD: '高级' }
  return map[difficulty.value] || '初级'
})
const statusClass = computed(() => {
  const map = {
    PLAYER_WIN: 'win', AI_WIN: 'lose', WHITE_WIN: 'lose', GRAY_WIN: 'lose',
    DRAW: 'draw', AI_TURN: 'thinking', AI_THINKING: 'thinking',
    GRAY_TURN: 'thinking', PAUSED: 'paused'
  }
  return map[status.value] || ''
})
const statusIcon = computed(() => {
  const map = {
    PLAYER_TURN: '🤔', AI_TURN: '💭', AI_THINKING: '💭', GRAY_TURN: '💭',
    PLAYER_WIN: '🎉', AI_WIN: '😢', WHITE_WIN: '🤖', GRAY_WIN: '🤖',
    DRAW: '🤝', PAUSED: '⏸'
  }
  return map[status.value] || '🎯'
})
const turnLabel = computed(() => {
  const map = {
    PLAYER_TURN: '⚫ 你的回合（黑子）',
    AI_TURN: '⚪ 白子AI 回合',
    AI_THINKING: '⚪ 白子AI 回合',
    GRAY_TURN: '🔘 灰子AI 回合',
  }
  return map[status.value] || ''
})

// 游戏结束弹窗相关
const gameOver = computed(() => {
  return ['PLAYER_WIN', 'AI_WIN', 'WHITE_WIN', 'GRAY_WIN', 'DRAW'].includes(status.value)
})
const resultType = computed(() => {
  if (status.value === 'PLAYER_WIN') return 'win'
  if (status.value === 'DRAW') return 'draw'
  return 'lose'
})
const resultIcon = computed(() => {
  const map = {
    PLAYER_WIN: '🎉',
    AI_WIN: '😢',
    WHITE_WIN: '🤖',
    GRAY_WIN: '🤖',
    DRAW: '🤝',
  }
  return map[status.value] || '🎯'
})
const resultTitle = computed(() => {
  const map = {
    PLAYER_WIN: '你赢了！',
    AI_WIN: '你输了',
    WHITE_WIN: '你输了',
    GRAY_WIN: '你输了',
    DRAW: '平局',
  }
  return map[status.value] || ''
})
const resultSubtitle = computed(() => {
  if (status.value === 'PLAYER_WIN') return '恭喜你击败了AI！'
  if (status.value === 'DRAW') return '旗鼓相当，不分胜负'
  return '下次再战，加油！'
})

function closeResult() {
  // 弹窗点击背景关闭，但不改变游戏状态
}
async function restartGame() {
  try {
    const res = await gameApi.start(difficulty.value, gameMode.value)
    if (res.code === 200) {
      const data = res.data
      // 同步 gameMode，防止前端状态不同步导致地形不渲染
      if (data.gameMode) gameMode.value = data.gameMode
      board.value = data.board
      terrain.value = data.terrain || null
      status.value = data.status
      undoRemaining.value = data.undoRemaining
      message.value = data.message || '请落子'
      hoverPos.value = null
      // 动态棋盘模式：重置区块状态和解锁令牌
      if (gameMode.value === 'DYNAMIC') {
        blockStates.value = data.blockStates
        unlockTokens.value = data.unlockTokens || 0
      }
      dialogues.value = []
      showBubble.value = false
      clearAllTimers()
      startRandomTimer()
      startThinkTimer()
      // 等待 Vue 完成 DOM 更新后再绘制，防止 canvas 被 Vue 响应式更新清空
      await nextTick()
      drawBoard()
    }
  } catch {
    message.value = '系统维护中'
  }
}

async function restoreFromServer() {
  try {
    const res = await gameApi.getState()
    if (res.code !== 200) {
      message.value = res.message || '棋局同步失败，请刷新页面'
      return
    }
    const state = res.data
    if (!state || state.status === 'ERROR') {
      router.replace('/lobby')
      return
    }
    if (state.gameMode) gameMode.value = state.gameMode
    board.value = state.board
    terrain.value = state.terrain || null
    status.value = state.status
    difficulty.value = state.difficulty || difficulty.value
    undoRemaining.value = state.undoRemaining ?? undoRemaining.value
    message.value = state.message || (state.status === 'PLAYER_TURN' ? '请落子' : '棋局已恢复')
    blockStates.value = state.blockStates || null
    unlockTokens.value = state.unlockTokens || 0
    await nextTick()
    drawBoard()
    if (canvasRef.value && ['AI_TURN', 'GRAY_TURN'].includes(state.status)) {
      await doAiMove()
    }
  } catch {
    message.value = '棋局同步失败，请刷新页面'
  }
}

onMounted(() => {
  // 先读取模式以确定棋盘大小
  gameMode.value = sessionStorage.getItem('gameMode') || 'TWO'
  const saved = sessionStorage.getItem('gameState')
  if (saved) {
    const state = JSON.parse(saved)
    board.value = state.board || createEmptyBoard(boardSize.value)
    terrain.value = state.terrain || null
    status.value = state.status || 'PLAYER_TURN'
    difficulty.value = state.difficulty || 'EASY'
    undoRemaining.value = state.undoRemaining ?? 2
    message.value = state.message || '请落子'
    // 从保存状态恢复 gameMode
    if (state.gameMode) gameMode.value = state.gameMode
    // 动态棋盘模式：恢复区块状态和解锁令牌数
    if (state.gameMode === 'DYNAMIC') {
      blockStates.value = state.blockStates || null
      unlockTokens.value = state.unlockTokens || 0
    }
    sessionStorage.removeItem('gameState')
  } else {
    difficulty.value = sessionStorage.getItem('difficulty') || 'EASY'
    board.value = createEmptyBoard(boardSize.value)
  }
  drawBoard()
  // 初始化背景音乐并自动播放
  initMusic()
  if (bgMusic.value) {
    bgMusic.value.play().then(() => {
      musicPlaying.value = true
    }).catch(err => {
      console.warn('自动播放失败，需要用户交互:', err)
    })
  }
  // 启动随机对话计时器
  startRandomTimer()
  restoreFromServer()
})

onUnmounted(() => {
  // 清除所有计时器
  clearAllTimers()
  clearSlideAnim()
  // 保存当前状态
  const state = {
    board: board.value,
    terrain: terrain.value,
    status: status.value,
    difficulty: difficulty.value,
    undoRemaining: undoRemaining.value,
    message: message.value,
    gameMode: gameMode.value
  }
  sessionStorage.setItem('gameState', JSON.stringify(state))
})

// 绘制棋盘
function drawBoard() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const cs = canvasSize.value
  const bs = boardSize.value
  const cl = cellSize.value
  const sp = starPoints.value

  // 背景 - 灰色系（适配古风山水背景）
  const bgGradient = ctx.createLinearGradient(0, 0, cs, cs)
  bgGradient.addColorStop(0, '#8A8A8A')
  bgGradient.addColorStop(0.3, '#9A9A9A')
  bgGradient.addColorStop(0.5, '#A0A0A0')
  bgGradient.addColorStop(0.7, '#9A9A9A')
  bgGradient.addColorStop(1, '#8A8A8A')
  ctx.fillStyle = bgGradient
  ctx.fillRect(0, 0, cs, cs)

  // 添加木纹纹理线条
  ctx.strokeStyle = 'rgba(180, 150, 100, 0.15)'
  ctx.lineWidth = 1
  for (let i = 0; i < cs; i += 15) {
    ctx.beginPath()
    ctx.moveTo(0, i + Math.sin(i * 0.05) * 3)
    ctx.lineTo(cs, i + Math.sin(i * 0.05) * 3)
    ctx.stroke()
    ctx.beginPath()
    ctx.moveTo(i + Math.cos(i * 0.03) * 2, 0)
    ctx.lineTo(i + Math.cos(i * 0.03) * 2, cs)
    ctx.stroke()
  }

  // 网格线 - 深色细线（匹配图片风格）
  ctx.strokeStyle = 'rgba(90, 60, 30, 0.6)'
  ctx.lineWidth = bs === 25 ? 0.7 : 1
  for (let i = 0; i < bs; i++) {
    const pos = PADDING + i * cl
    ctx.beginPath()
    ctx.moveTo(PADDING, pos)
    ctx.lineTo(PADDING + cl * (bs - 1), pos)
    ctx.stroke()
    ctx.beginPath()
    ctx.moveTo(pos, PADDING)
    ctx.lineTo(pos, PADDING + cl * (bs - 1))
    ctx.stroke()
  }

  // 动态棋盘模式：绘制区块状态
  if (gameMode.value === 'DYNAMIC' && blockStates.value) {
    // 绘制区块边框（加粗）
    ctx.strokeStyle = 'rgba(90, 60, 30, 0.8)'
    ctx.lineWidth = 3
    for (let i = 0; i <= 5; i++) {
      const pos = PADDING + i * cl * 3
      if (pos <= PADDING + cl * (bs - 1)) {
        ctx.beginPath()
        ctx.moveTo(pos, PADDING)
        ctx.lineTo(pos, PADDING + cl * (bs - 1))
        ctx.stroke()
        ctx.beginPath()
        ctx.moveTo(PADDING, pos)
        ctx.lineTo(PADDING + cl * (bs - 1), pos)
        ctx.stroke()
      }
    }

    // 绘制锁定/封印区块的遮罩
    // 区块边界位置：PADDING + i * 3 * cl （i = 0~4，共5条边界线形成5x5区块）
    for (let br = 0; br < 5; br++) {
      for (let bc = 0; bc < 5; bc++) {
        if (blockStates.value[br][bc] === 0 || blockStates.value[br][bc] === 2) { // 锁定或封印
          // 区块左上角坐标
          const x = PADDING + bc * 3 * cl
          const y = PADDING + br * 3 * cl
          // 计算区块实际大小（考虑棋盘边界）
          const maxPos = PADDING + (bs - 1) * cl  // 棋盘最右/下坐标
          const blockEndX = PADDING + (bc + 1) * 3 * cl  // 区块理论结束位置
          const blockEndY = PADDING + (br + 1) * 3 * cl
          const sizeX = Math.min(blockEndX, maxPos) - x  // 实际宽度
          const sizeY = Math.min(blockEndY, maxPos) - y  // 实际高度
          
          // 设置阴影颜色（半透明，网格线仍然可见）
          ctx.fillStyle = blockStates.value[br][bc] === 0 
            ? 'rgba(80, 80, 80, 0.6)'   // 锁定：深灰色
            : 'rgba(255, 0, 0, 0.4)'   // 封印：红色
          
          // 绘制阴影矩形
          ctx.fillRect(x, y, sizeX, sizeY)
        }
      }
    }
  }

  // 星位 - 深棕色小圆点
  ctx.fillStyle = 'rgba(70, 45, 20, 0.9)'
  sp.forEach(r => {
    sp.forEach(c => {
      ctx.beginPath()
      ctx.arc(PADDING + r * cl, PADDING + c * cl, bs === 25 ? 2.5 : 4, 0, Math.PI * 2)
      ctx.fill()
    })
  })

  // 地形渲染（障碍物模式 / 三人障碍物模式）
  if (terrain.value && (gameMode.value === 'OBSTACLE' || gameMode.value === 'THREE_OBSTACLE')) {
    for (let r = 0; r < Math.min(bs, terrain.value.length); r++) {
      for (let c = 0; c < Math.min(bs, terrain.value[r] ? terrain.value[r].length : 0); c++) {
        const t = terrain.value[r][c]
        if (t === 4) drawObstacle(ctx, r, c)  // 假山
        if (t === 5) drawWater(ctx, r, c)       // 水潭
      }
    }
  }

  // 棋子
  for (let r = 0; r < bs; r++) {
    for (let c = 0; c < bs; c++) {
      if (board.value[r] && board.value[r][c] !== 0) {
        drawStone(ctx, r, c, board.value[r][c])
      }
    }
  }

  // 悬停预览
  if (hoverPos.value && status.value === 'PLAYER_TURN') {
    const hr = hoverPos.value.r, hc = hoverPos.value.c
    if (board.value[hr]?.[hc] === 0) {
      if (terrain.value && terrain.value[hr]?.[hc] === 4) {
        // 假山：红色叉号
        drawStone(ctx, hr, hc, 1, 0.15)
        ctx.save()
        const hx = PADDING + hc * cellSize.value
        const hy = PADDING + hr * cellSize.value
        const r = stoneRadius.value * 0.6
        ctx.strokeStyle = 'rgba(255,0,0,0.5)'
        ctx.lineWidth = 2
        ctx.beginPath()
        ctx.moveTo(hx - r, hy - r)
        ctx.lineTo(hx + r, hy + r)
        ctx.moveTo(hx + r, hy - r)
        ctx.lineTo(hx - r, hy + r)
        ctx.stroke()
        ctx.restore()
      } else if (terrain.value && terrain.value[hr]?.[hc] === 5) {
        // 水潭：蓝色半透明预览
        drawStone(ctx, hr, hc, 1, 0.35)
        ctx.save()
        const hx = PADDING + hc * cellSize.value
        const hy = PADDING + hr * cellSize.value
        ctx.fillStyle = 'rgba(59,142,212,0.4)'
        ctx.font = `${stoneRadius.value * 1.5}px sans-serif`
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        ctx.fillText('💧', hx, hy)
        ctx.restore()
      } else {
        drawStone(ctx, hr, hc, 1, 0.4)
      }
    }
  }
}

function drawStone(ctx, row, col, color, alpha = 1) {
  const cl = cellSize.value
  const x = PADDING + col * cl
  const y = PADDING + row * cl
  const radius = stoneRadius.value
  const hlOffset = Math.max(2, radius * 0.25)
  const hlSize = Math.max(2, radius * 0.33)

  ctx.save()
  ctx.globalAlpha = alpha

  const gradient = ctx.createRadialGradient(x - hlOffset, y - hlOffset, 1, x, y, radius)
  if (color === 1) {
    // 黑子 - 匹配登录界面小棋盘的样式
    gradient.addColorStop(0, '#555')
    gradient.addColorStop(1, '#000')
  } else if (color === 3) {
    // 灰子
    gradient.addColorStop(0, '#aaa')
    gradient.addColorStop(1, '#555')
  } else {
    // 白子 - 匹配登录界面小棋盘的样式
    gradient.addColorStop(0, '#fff')
    gradient.addColorStop(1, '#ddd')
  }

  ctx.beginPath()
  ctx.arc(x, y, radius, 0, Math.PI * 2)
  ctx.fillStyle = gradient
  ctx.fill()

  // 高光
  ctx.beginPath()
  ctx.arc(x - hlOffset, y - hlOffset, hlSize, 0, Math.PI * 2)
  let highlightColor
  if (color === 1) highlightColor = 'rgba(255,255,255,0.15)'
  else if (color === 3) highlightColor = 'rgba(255,255,255,0.3)'
  else highlightColor = 'rgba(255,255,255,0.4)'
  ctx.fillStyle = highlightColor
  ctx.fill()

  ctx.restore()
}

// ===== 地形绘制 =====
function drawObstacle(ctx, row, col) {
  const cl = cellSize.value
  const x = PADDING + col * cl
  const y = PADDING + row * cl
  const r = stoneRadius.value

  ctx.save()
  // 假山：岩石纹理
  const grad = ctx.createLinearGradient(x - r, y - r, x + r, y + r)
  grad.addColorStop(0, '#8B7355')
  grad.addColorStop(0.3, '#6B5340')
  grad.addColorStop(0.7, '#8B6914')
  grad.addColorStop(1, '#5C4033')

  ctx.beginPath()
  // 不规则多边形
  const pts = 7
  for (let i = 0; i < pts; i++) {
    const angle = (i / pts) * Math.PI * 2 - Math.PI / 2
    const rr = r * (0.7 + Math.random() * 0.3)
    const px = x + Math.cos(angle) * rr
    const py = y + Math.sin(angle) * rr
    if (i === 0) ctx.moveTo(px, py)
    else ctx.lineTo(px, py)
  }
  ctx.closePath()
  ctx.fillStyle = grad
  ctx.fill()
  ctx.strokeStyle = '#4a3520'
  ctx.lineWidth = 1
  ctx.stroke()

  // 假山图标 ⛰
  ctx.fillStyle = '#fff'
  ctx.font = `${r * 1.2}px sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('⛰', x, y)
  ctx.restore()
}

function drawWater(ctx, row, col) {
  const cl = cellSize.value
  const x = PADDING + col * cl
  const y = PADDING + row * cl
  const r = stoneRadius.value

  ctx.save()
  // 水潭：碧蓝圆形
  const grad = ctx.createRadialGradient(x - r * 0.2, y - r * 0.2, r * 0.1, x, y, r)
  grad.addColorStop(0, '#7EC8E3')
  grad.addColorStop(0.5, '#3B8ED4')
  grad.addColorStop(1, '#1a5f8a')

  ctx.beginPath()
  ctx.arc(x, y, r * 0.9, 0, Math.PI * 2)
  ctx.fillStyle = grad
  ctx.fill()
  ctx.strokeStyle = '#9fd4f0'
  ctx.lineWidth = 1.5
  ctx.stroke()

  // 水面波纹
  ctx.beginPath()
  ctx.ellipse(x, y - r * 0.25, r * 0.5, r * 0.15, 0, 0, Math.PI * 2)
  ctx.strokeStyle = 'rgba(255,255,255,0.4)'
  ctx.lineWidth = 0.8
  ctx.stroke()

  // 水潭图标 💧
  ctx.fillStyle = '#fff'
  ctx.font = `${r * 1.0}px sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('💧', x, y + r * 0.3)
  ctx.restore()
}

function clearSlideAnim() {
  if (slideAnimTimer) { clearTimeout(slideAnimTimer); slideAnimTimer = null }
  slideFrom.value = null
  slideTo.value = null
}

function getBoardPos(clientX, clientY) {
  const canvas = canvasRef.value
  const rect = canvas.getBoundingClientRect()
  const cs = canvasSize.value
  const bs = boardSize.value
  const cl = cellSize.value
  const scaleX = cs / rect.width
  const scaleY = cs / rect.height
  const x = (clientX - rect.left) * scaleX
  const y = (clientY - rect.top) * scaleY

  const col = Math.round((x - PADDING) / cl)
  const row = Math.round((y - PADDING) / cl)

  if (row >= 0 && row < bs && col >= 0 && col < bs) {
    // 检查是否接近交叉点
    const cx = PADDING + col * cl
    const cy = PADDING + row * cl
    // Match the visible cell on a scaled canvas, including narrow touch screens.
    const threshold = cl / 2
    if (Math.abs(x - cx) < threshold && Math.abs(y - cy) < threshold) {
      return { r: row, c: col }
    }
  }
  return null
}

function handleMouseMove(e) {
  const pos = getBoardPos(e.clientX, e.clientY)
  hoverPos.value = pos
  drawBoard()
}

async function handleClick(e) {
  // 动态棋盘模式：区块选择处理
  if (showBlockSelector.value) {
    const pos = getBoardPos(e.clientX, e.clientY)
    if (!pos) return
    
    const blockRow = Math.floor(pos.r / 3)
    const blockCol = Math.floor(pos.c / 3)
    
    if (blockRow >= 0 && blockRow < 5 && blockCol >= 0 && blockCol < 5) {
      if (blockStates.value && (blockStates.value[blockRow][blockCol] === 0 || blockStates.value[blockRow][blockCol] === 2)) {
        await handleBlockClick(blockRow, blockCol)
      } else {
        message.value = '请选择锁定或已封印的区块'
      }
    }
    return
  }
  
  if (status.value !== 'PLAYER_TURN') return
  const pos = getBoardPos(e.clientX, e.clientY)
  if (!pos) return
  
  // 动态棋盘模式：检查区块状态
  if (gameMode.value === 'DYNAMIC') {
    const blockRow = Math.floor(pos.r / 3)
    const blockCol = Math.floor(pos.c / 3)
    if (blockStates.value && blockStates.value[blockRow][blockCol] !== 1) {
      message.value = '此区块未解锁或已被封印'
      return
    }
  }
  
  // 假山处不能点击落子
  if (terrain.value && terrain.value[pos.r]?.[pos.c] === 4) return
  // 已有棋子的位置不能落子
  if (board.value[pos.r][pos.c] !== 0) return
  
  // 播放落子音效
  playStoneSound()
  
  // 记住原始点击位置（用于水潭滑动动画）
  const clickRow = pos.r, clickCol = pos.c

  // 清除思考计时器
  if (thinkTimer) { clearTimeout(thinkTimer); thinkTimer = null }
  // 不直接改状态，等后端返回后再更新（避免玩家点击后立即显示"思考中"）
  drawBoard()

  try {
    const res = await gameApi.move(clickRow, clickCol)
    if (res.code === 200) {
      const data = res.data

      // 更新棋盘和地形
      board.value = data.board
      terrain.value = data.terrain || null
      undoRemaining.value = data.undoRemaining
      
      // 动态棋盘模式：更新区块状态（令牌在AI回合发放）
      if (gameMode.value === 'DYNAMIC') {
        blockStates.value = data.blockStates
        unlockTokens.value = data.unlockTokens || 0
      }

      // 双人/障碍物模式：玩家落子后返回 AI_TURN，前端自动请求 AI 落子
      // 三人/三人障碍物模式：玩家落子后返回 GRAY_TURN，前端自动请求灰AI落子
      if (data.status === 'AI_TURN') {
        status.value = 'AI_TURN'
        message.value = '白子AI 思考中...'
        drawBoard()
        // 延迟 400ms 再请求 AI 落子，让玩家感知到 AI 在"思考"
        await new Promise(r => setTimeout(r, 400))
        await doAiMove()
      } else {
        status.value = data.status
        message.value = data.message || ''
      }

      // 水潭滑动动画：检测落子位置是否变化
      if ((gameMode.value === 'OBSTACLE' || gameMode.value === 'THREE_OBSTACLE') && data.status !== 'PLAYER_WIN') {
        // 找到实际落子位置（与点击位置不同的最近黑子）
        for (let r = 0; r < board.value.length; r++) {
          for (let c = 0; c < board.value[r].length; c++) {
            if (board.value[r][c] === 1 && !(r === clickRow && c === clickCol)) {
              // 检查是否是新落子（附近没有旧黑子逻辑...简化：检查是否在点击位置附近）
              const dist = Math.abs(r - clickRow) + Math.abs(c - clickCol)
              if (dist === 1 && data.message && data.message.includes('滑入')) {
                slideFrom.value = { r: clickRow, c: clickCol }
                slideTo.value = { r, c }
                drawBoard()
                // 延迟播放滑动动画
                if (slideAnimTimer) clearTimeout(slideAnimTimer)
                slideAnimTimer = setTimeout(() => {
                  drawBoard()
                  clearSlideAnim()
                }, 600)
              }
            }
          }
        }
      }

      // AI 落子后对话
      if (data.status === 'PLAYER_TURN') {
        if (Math.random() < 0.5) {
          const text = randomPick(aiTauntPhrases)
          showBubbleMsg('ai', text)
          addDialogue('ai', text)
        }
      } else if (data.status.includes('WIN')) {
        const text = randomPick(aiCheerPhrases)
        showBubbleMsg('ai', text)
        addDialogue('ai', text)
      }

      // 三人/三人障碍物模式：白子落完后自动触发灰子回合
      if (data.status === 'GRAY_TURN') {
        status.value = 'GRAY_TURN'
        message.value = '灰子AI 思考中...'
        drawBoard()
        // 白AI落子后延迟 500ms 再触发灰AI，让玩家感知到"回合流转"
        await new Promise(r => setTimeout(r, 500))
        await doAiMove()
      }
    } else {
      status.value = 'PLAYER_TURN'
      message.value = res.message || '操作失败'
    }
    drawBoard()
  } catch {
    status.value = 'PLAYER_TURN'
    message.value = '系统维护中'
    drawBoard()
  }
}

async function doAiMove() {
  // 记录AI响应前的状态（用于检测变化）
  const oldBlockStates = blockStates.value ? JSON.parse(JSON.stringify(blockStates.value)) : null
  const oldUnlockTokens = unlockTokens.value

  try {
    const res = await gameApi.aiMove()
    if (res.code === 200) {
      const data = res.data
      board.value = data.board
      terrain.value = data.terrain || null
      status.value = data.status
      undoRemaining.value = data.undoRemaining
      message.value = data.message || ''

      // 动态棋盘模式：每2回合（4步）发放令牌，在AI回合触发
      if (gameMode.value === 'DYNAMIC') {
        blockStates.value = data.blockStates
        const newTokens = data.unlockTokens || 0
        unlockTokens.value = newTokens

        // 检测是否玩家获得了新令牌
        if (newTokens > oldUnlockTokens && oldUnlockTokens >= 0) {
          unlockNotifyTokens.value = newTokens
          showUnlockNotify.value = true
          if (unlockNotifyTimer) clearTimeout(unlockNotifyTimer)
          unlockNotifyTimer = setTimeout(() => {
            showUnlockNotify.value = false
          }, 3500)
        }

        // 检测AI是否自动解锁了区块
        if (oldBlockStates && data.blockStates) {
          let aiUnlocked = false
          let unlockedRow = -1, unlockedCol = -1
          for (let i = 0; i < 5; i++) {
            for (let j = 0; j < 5; j++) {
              if ((oldBlockStates[i][j] === 0 || oldBlockStates[i][j] === 2) && data.blockStates[i][j] === 1) {
                aiUnlocked = true
                unlockedRow = i
                unlockedCol = j
              }
            }
          }
          if (aiUnlocked) {
            aiUnlockNotifyMsg.value = 'AI 自动解锁了区块(' + unlockedRow + ',' + unlockedCol + ')，现在可以在此区块落子了'
            showAiUnlockNotify.value = true
            if (unlockNotifyTimer) clearTimeout(unlockNotifyTimer)
            unlockNotifyTimer = setTimeout(() => {
              showAiUnlockNotify.value = false
            }, 4000)
          }
        }
      }

      // AI 落子后对话
      if (data.status === 'PLAYER_TURN') {
        if (Math.random() < 0.5) {
          const text = randomPick(aiTauntPhrases)
          showBubbleMsg('ai', text)
          addDialogue('ai', text)
        }
      } else if (data.status.includes('WIN')) {
        const text = randomPick(aiCheerPhrases)
        showBubbleMsg('ai', text)
        addDialogue('ai', text)
      }

      // 三人/三人障碍物模式：白子落完返回 GRAY_TURN，自动触发灰子AI落子
      if (data.status === 'GRAY_TURN') {
        status.value = 'GRAY_TURN'
        message.value = '灰子AI 思考中...'
        drawBoard()
        // 延迟 500ms 让玩家感知到回合流转
        await new Promise(r => setTimeout(r, 500))
        await doAiMove()
      }
    } else {
      status.value = 'PLAYER_TURN'
      message.value = res.message || '操作失败'
    }
    drawBoard()
  } catch {
    status.value = 'PLAYER_TURN'
    message.value = '系统维护中'
    drawBoard()
  }
}

async function doUndo() {
  undoLoading.value = true
  try {
    const res = await gameApi.undo()
    if (res.code === 200) {
      board.value = res.data.board
      terrain.value = res.data.terrain || null
      status.value = res.data.status
      undoRemaining.value = res.data.undoRemaining
      message.value = res.data.message || ''
      clearSlideAnim()
      drawBoard()
    }
  } catch {
    message.value = '系统维护中'
  } finally {
    undoLoading.value = false
  }
}

async function doPause() {
  const res = await gameApi.pause()
  if (res.code === 200) {
    status.value = res.data.status
    message.value = res.data.message
  }
}

async function doResume() {
  const res = await gameApi.resume()
  if (res.code === 200) {
    board.value = res.data.board
    terrain.value = res.data.terrain || null
    status.value = res.data.status
    message.value = res.data.message
    drawBoard()
  }
}

// 动态棋盘模式：关闭解锁通知弹窗
function dismissUnlockNotify() {
  showUnlockNotify.value = false
  if (unlockNotifyTimer) clearTimeout(unlockNotifyTimer)
}

// 动态棋盘模式：关闭AI解锁通知弹窗
function dismissAiUnlockNotify() {
  showAiUnlockNotify.value = false
  if (unlockNotifyTimer) clearTimeout(unlockNotifyTimer)
}

// 动态棋盘模式：解锁区块
async function doUnlockBlock() {
  showBlockSelector.value = true
  message.value = '请点击要解锁的区块'
  drawBoard()
}

// 动态棋盘模式：处理区块点击（解锁）
async function handleBlockClick(blockRow, blockCol) {
  if (!showBlockSelector.value) return
  
  try {
    const res = await gameApi.unlockBlock(blockRow, blockCol)
    if (res.code === 200) {
      blockStates.value = res.data.blockStates
      unlockTokens.value = res.data.unlockTokens
      showBlockSelector.value = false
      message.value = res.data.message || '区块解锁成功'
      drawBoard()
    } else {
      message.value = res.message || '解锁失败'
    }
  } catch {
    message.value = '系统维护中'
  }
}

// 玩家发送快捷语
function onPlayerPhrase(phrase) {
  showBubbleMsg('player', phrase)
  addDialogue('player', phrase)
}

function goBack() {
  router.push('/lobby')
}
</script>

<style scoped>
.game-page {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
}

/* 古风山水背景图片 */
.bg-image {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: url('/ink-landscape-v2.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  z-index: 0;
}

/* 游戏棋盘容器 - 匹配登录界面小棋盘样式 */
.board-wrapper {
  padding: 15px;
  background: linear-gradient(145deg, #6a6a6a 0%, #8a8a8a 20%, #9a9a9a 40%, #8a8a8a 60%, #6a6a6a 100%);
  border-radius: 12px;
  border: 3px solid #4a4a4a;
  box-shadow:
    inset 0 0 20px rgba(100, 100, 100, 0.3),
    0 8px 32px rgba(0, 0, 0, 0.5),
    0 2px 10px rgba(80, 80, 80, 0.3);
  width: fit-content;
  position: relative;
  animation: floatBoard 3s ease-in-out infinite;
}

/* 边框装饰角落 */
.board-wrapper::before {
  content: '';
  position: absolute;
  top: -3px; left: -3px; right: -3px; bottom: -3px;
  border-radius: 14px;
  border: 2px solid transparent;
  background: linear-gradient(135deg, #aaa, #888, #aaa) border-box;
  -webkit-mask: linear-gradient(#fff 0 0) padding-box, linear-gradient(#fff 0 0);
  mask-composite: exclude;
  pointer-events: none;
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
  padding: 12px 24px;
  background: rgba(255,255,255,0.03);
  border-bottom: 1px solid rgba(255,255,255,0.08);
  position: relative;
  z-index: 10;
}
header h1 {
  font-size: 18px;
  color: #e8c4a0;
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  letter-spacing: 2px;
}
.btn-back {
  padding: 6px 14px;
  border: 1px solid rgba(255,255,255,0.3);
  border-radius: 6px;
  background: transparent;
  color: #ccc;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.3s;
}
.btn-back:hover { border-color: #d4a574; color: #d4a574; }
.header-info {
  color: #e0d9d7;
  font-size: 13px; 
  display: flex;
  align-items: center;
  gap: 15px;
}
.header-turn {
  max-width: 220px;
  padding: 7px 10px;
  border: 1px solid rgba(232, 196, 160, 0.3);
  border-radius: 999px;
  color: #f5dec0;
  background: rgba(30, 25, 34, 0.55);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 音乐控制 */
.music-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
.music-btn {
  padding: 6px 10px;
  border: 1px solid rgba(255,255,255,0.2);
  border-radius: 6px;
  background: rgba(255,255,255,0.05);
  color: #ccc;
  cursor: pointer;
  font-size: 16px;
  transition: all 0.3s;
}
.music-btn:hover {
  border-color: #d4a574;
  color: #d4a574;
  background: rgba(212,165,116,0.1);
}
.volume-slider {
  width: 80px;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: rgba(255,255,255,0.2);
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

.game-area {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  gap: 32px;
  padding: 30px 20px;
  position: relative;
  z-index: 10;
  width: min(100%, 1040px);
  margin: 0 auto;
}

.board-container {
  position: relative;
  border: 2px solid rgba(139, 90, 43, 0.6);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0,0,0,0.4);
  padding: 5px;
  background: rgba(139, 90, 43, 0.15);
  max-width: 100%;
}
canvas {
  display: block;
  width: 100%;
  max-width: 100%;
  height: auto;
  cursor: pointer;
  touch-action: manipulation;
}

.side-panel {
  width: 220px;
}

.status-box {
  padding: 20px;
  background: rgba(17, 22, 37, 0.8);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 12px;
  text-align: center;
  margin-bottom: 20px;
  transition: all 0.3s;
}
.status-box.win { border-color: #4ecca3; background: rgba(78,204,163,0.1); }
.status-box.lose { border-color: #d4a574; background: rgba(212,165,116,0.1); }
.status-box.draw { border-color: #f0a500; background: rgba(240,165,0,0.1); }
.status-box.thinking { border-color: #0f3460; animation: pulse 1.5s infinite; }
.status-icon { font-size: 36px; margin-bottom: 8px; }
.status-text { font-size: 14px; color: #ccc; line-height: 1.5; }
.turn-label {
  margin-top: 10px;
  padding: 8px 12px;
  background: rgba(255,255,255,0.08);
  border-radius: 6px;
  font-size: 13px;
  color: #f0a500;
  animation: fadeIn 0.3s ease;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.controls {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}
.btn-ctrl {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  color: #fff;
}
.btn-ctrl { 
  background: linear-gradient(135deg, #8B7355, #5C4033);
  border: 1px solid #6B5340;
}
.btn-ctrl:hover { opacity: 0.85; }
.btn-ctrl.resume { background: #4ecca3; }
.btn-ctrl.undo { 
  background: linear-gradient(135deg, #8B7355, #5C4033);
  border: 1px solid #6B5340;
}
.btn-ctrl.new-game { background: #d4a574; }
.btn-ctrl:disabled { opacity: 0.5; cursor: not-allowed; }

.legend {
  padding: 16px;
  background: rgba(17, 22, 37, 0.75);
  border-radius: 8px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  color: #e3dcda;
  font-size: 13px;
}
.dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
}
.dot.black { background: radial-gradient(circle at 35% 35%, #555, #111); }
.dot.white { background: radial-gradient(circle at 35% 35%, #fff, #bbb); border: 1px solid #999; }
.dot.gray { background: radial-gradient(circle at 35% 35%, #aaa, #555); }
.dot.terrain-obstacle {
  background: linear-gradient(135deg, #8B7355, #5C4033);
  border: 1px solid #4a3520;
}
.dot.terrain-water {
  background: radial-gradient(circle at 35% 35%, #7EC8E3, #1a5f8a);
  border: 1px solid #3B8ED4;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-5px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 棋盘上下悬浮动画 */
@keyframes floatBoard {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-4px); }
}

/* 对话气泡 */
.bubble-wrapper {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  animation: bubbleIn 0.4s ease;
}
@keyframes bubbleIn {
  0% { opacity: 0; transform: translateX(-50%) translateY(-10px) scale(0.8); }
  100% { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
}
.bubble {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 12px;
  max-width: 280px;
  font-size: 13px;
  line-height: 1.4;
  box-shadow: 0 4px 16px rgba(0,0,0,0.4);
}
.bubble.ai {
  background: rgba(30,30,50,0.95);
  color: #e0e0e0;
  border: 1px solid rgba(255,255,255,0.15);
}
.bubble.player {
  background: rgba(15,52,96,0.95);
  color: #e0e0e0;
  border: 1px solid rgba(233,69,96,0.4);
}
.bubble-avatar {
  font-size: 18px;
  flex-shrink: 0;
}
.bubble-text {
  word-break: break-all;
}

/* 棋罐装饰 - 圆形木罐 */
.stone-boxes {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 5;
}
.stone-jar {
  position: absolute;
  width: 90px;
  height: 90px;
}
/* 角落定位 */
.stone-jar.corner-tl {
  top: -35px;
  left: -35px;
}
.stone-jar.corner-tr {
  top: -35px;
  right: -35px;
}
.stone-jar.corner-bl {
  bottom: -35px;
  left: -35px;
}
.stone-jar.corner-br {
  bottom: -35px;
  right: -35px;
}

/* 棋罐主体 - 展示更多侧面 */
.jar-body {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  width: 70px;
  height: 60px;
  background: linear-gradient(135deg, #3d2817 0%, #5C4033 35%, #6B4F2A 70%, #4a2f1f 100%);
  border-radius: 50% / 25%;
  box-shadow: 
    inset 0 4px 8px rgba(255,255,255,0.15),
    inset 0 -5px 10px rgba(0,0,0,0.55),
    4px 5px 12px rgba(0,0,0,0.65);
  overflow: hidden;
}

/* 罐口边缘 */
.jar-rim {
  position: absolute;
  top: 0;
  left: -3px;
  right: -3px;
  height: 12px;
  background: linear-gradient(180deg, #1a0f08 0%, #3d2817 50%, #5C4033 100%);
  border-radius: 50% / 50%;
  box-shadow: 
    inset 0 2px 4px rgba(0,0,0,0.85),
    0 1px 2px rgba(255,255,255,0.1);
  z-index: 2;
}

/* 罐内容物（棋子） */
.jar-content {
  position: absolute;
  top: 8px;
  left: 8px;
  right: 8px;
  bottom: 8px;
  background: radial-gradient(ellipse at center 30%, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0.85) 100%);
  border-radius: 50% / 30%;
  overflow: hidden;
}

/* 棋子堆叠效果 - 2x2 排列 */
.jar-stones {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
  width: 40px;
  height: 28px;
}

.jar-stone {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  box-shadow: 
    inset 0 1px 2px rgba(255,255,255,0.35),
    0 1px 2px rgba(0,0,0,0.4);
}
.jar-stone.black {
  background: radial-gradient(circle at 30% 30%, #666, #000);
}
.jar-stone.white {
  background: radial-gradient(circle at 30% 30%, #fff, #aaa);
  box-shadow: 
    inset 0 1px 2px rgba(255,255,255,0.6), 
    inset 0 -1px 2px rgba(0,0,0,0.25);
}
.jar-stone.gray {
  background: radial-gradient(circle at 30% 30%, #bbb, #555);
}

/* 棋罐盖子 - 斜靠在旁边 */
.jar-lid {
  position: absolute;
  top: -12px;
  right: -22px;
  width: 42px;
  height: 25px;
  background: linear-gradient(180deg, #5C4033 0%, #3d2817 100%);
  border-radius: 50% / 30%;
  transform: rotate(-40deg);
  box-shadow: 
    inset 0 1px 3px rgba(255,255,255,0.2),
    2px 3px 6px rgba(0,0,0,0.55);
}
.jar-lid::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 6px;
  background: linear-gradient(180deg, rgba(255,255,255,0.25), transparent);
  border-radius: 50% / 50%;
}

/* 玩家快捷语 */
.player-phrases {
  margin-top: 16px;
  padding: 12px;
  background: rgba(17, 22, 37, 0.75);
  border-radius: 8px;
}
.phrases-title {
  font-size: 13px;
  color: #d5ced0;
  margin-bottom: 8px;
}
.phrases-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}
.phrase-btn {
  min-width: 0;
  min-height: 36px;
  padding: 6px 4px;
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 6px;
  background: rgba(8, 12, 24, 0.5);
  color: #eee6e4;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: normal;
  overflow-wrap: anywhere;
}
.phrase-btn:hover {
  background: rgba(212,165,116,0.2);
  border-color: #d4a574;
  color: #fff;
}

/* 游戏结束弹窗 */
.result-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(7, 13, 27, 0.72);
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: overlayIn 0.3s ease;
}
@keyframes overlayIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.result-modal {
  background: #c0c0c0;
  border-radius: 16px;
  padding: 40px 48px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  border: 2px solid #a0a0a0;
  animation: modalIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  min-width: 300px;
  max-width: 100%;
}
.result-modal.win {
  border-color: #a0a0a0;
}
.result-modal.lose {
  border-color: #a0a0a0;
}
.result-modal.draw {
  border-color: #a0a0a0;
}
@keyframes modalIn {
  from { opacity: 0; transform: scale(0.8) translateY(20px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
.result-icon {
  font-size: 64px;
  margin-bottom: 16px;
  animation: iconBounce 0.6s ease 0.3s both;
}
@keyframes iconBounce {
  0% { transform: scale(0); }
  50% { transform: scale(1.2); }
  100% { transform: scale(1); }
}
.result-title {
  font-size: 28px;
  font-weight: bold;
  color: #222;
  margin-bottom: 8px;
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  letter-spacing: 4px;
  text-shadow: none;
}
.result-modal.win .result-title {
  color: #222;
}
.result-modal.lose .result-title {
  color: #222;
}
.result-modal.draw .result-title {
  color: #222;
}
.result-subtitle {
  font-size: 14px;
  color: #444;
  margin-bottom: 32px;
}
.result-buttons {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.result-btn {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
  color: #fff;
}
.result-btn.primary {
  background: linear-gradient(135deg, #8B7355, #5C4033);
  border: 1px solid #6B5340;
}
.result-btn.primary:hover {
  opacity: 0.85;
  transform: translateY(-1px);
}
.result-btn.secondary {
  background: transparent;
  border: 1px solid rgba(0, 0, 0, 0.3);
  color: #444;
}
.result-btn.secondary:hover {
  background: rgba(0, 0, 0, 0.05);
  border-color: #666;
  color: #222;
  transform: translateY(-1px);
}

/* 解锁令牌通知弹窗 */
.notify-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  animation: overlayIn 0.25s ease;
}
.notify-modal {
  background: linear-gradient(135deg, #1a1a2e, #16213e);
  border: 2px solid #4ecca3;
  border-radius: 16px;
  padding: 32px 40px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(78, 204, 163, 0.2);
  animation: notifyIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  min-width: 280px;
}
@keyframes notifyIn {
  from { opacity: 0; transform: scale(0.8) translateY(30px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
.notify-icon {
  font-size: 48px;
  margin-bottom: 12px;
  animation: iconBounce 0.5s ease 0.2s both;
}
.notify-title {
  font-size: 22px;
  font-weight: bold;
  color: #4ecca3;
  margin-bottom: 12px;
  letter-spacing: 2px;
}
.notify-text {
  font-size: 14px;
  color: #ccc;
  line-height: 1.6;
  margin-bottom: 16px;
}
.notify-text strong {
  color: #4ecca3;
  font-size: 18px;
}
.notify-hint {
  font-size: 12px;
  color: #666;
}

/* AI解锁弹窗（橙色主题） */
.notify-modal.ai-unlock {
  border-color: #ff9800;
  box-shadow: 0 20px 60px rgba(255, 152, 0, 0.2);
}
.notify-modal.ai-unlock .notify-title {
  color: #ff9800;
}

@media (max-width: 920px) {
  header { flex-wrap: wrap; gap: 10px; padding: 12px 16px; }
  header h1 { text-align: center; flex: 1; }
  .header-info { width: 100%; justify-content: space-between; flex-wrap: wrap; }
  .header-turn { max-width: min(52vw, 260px); }
  .game-area { flex-direction: column; align-items: center; gap: 18px; padding: 18px 12px 32px; }
  .board-wrapper { width: fit-content; max-width: 100%; }
  .stone-boxes { display: none; }
  .side-panel { width: min(100%, 590px); }
  .status-box { margin-bottom: 12px; }
  .controls { flex-direction: row; flex-wrap: wrap; }
  .btn-ctrl { flex: 1 1 140px; min-height: 44px; }
  .legend { display: flex; flex-wrap: wrap; gap: 8px 18px; }
  .legend-item { margin: 0; }
}

@media (max-width: 560px) {
  header h1 { width: 100%; order: -1; flex: auto; font-size: 17px; }
  .btn-back { min-height: 40px; }
  .header-info { width: auto; flex: 1; justify-content: flex-end; gap: 8px; }
  .header-turn { max-width: 100%; order: 2; width: 100%; text-align: center; }
  .volume-slider { width: 56px; }
  .game-area { padding: 14px 10px 24px; }
  .board-wrapper { padding: 8px; animation: none; }
  .board-container { padding: 3px; }
  .status-box { padding: 14px; }
  .status-icon { font-size: 27px; margin-bottom: 4px; }
  .result-modal, .notify-modal { width: 100%; min-width: 0; padding: 28px 20px; }
  .result-buttons { flex-direction: column-reverse; }
  .result-btn { min-height: 44px; }
  .bubble { max-width: min(280px, 78vw); }
}

@media (prefers-reduced-motion: reduce) {
  .board-wrapper, .status-box.thinking, .bubble-wrapper, .result-overlay,
  .result-modal, .result-icon, .notify-modal, .notify-icon { animation: none !important; }
}
</style>
