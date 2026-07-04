<template>
  <div class="game-page">
    <!-- 古风山水背景图片 -->
    <div class="bg-image"></div>
    <div class="bg-overlay"></div>

    <header>
      <button class="btn-back" @click="goBack">← 返回大厅</button>
      <h1>五子棋 · {{ difficultyLabel }}</h1>
      <div class="header-info">
        <span>悔棋: {{ undoRemaining }}/2</span>
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
            v-if="['PLAYER_TURN', 'GRAY_TURN', 'AI_WIN', 'WHITE_WIN', 'GRAY_WIN'].includes(status) && undoRemaining > 0"
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
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
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
    DRAW: 'draw', AI_THINKING: 'thinking', GRAY_TURN: 'thinking', PAUSED: 'paused'
  }
  return map[status.value] || ''
})
const statusIcon = computed(() => {
  const map = {
    PLAYER_TURN: '🤔', AI_THINKING: '💭', GRAY_TURN: '💭',
    PLAYER_WIN: '🎉', AI_WIN: '😢', WHITE_WIN: '🤖', GRAY_WIN: '🤖',
    DRAW: '🤝', PAUSED: '⏸'
  }
  return map[status.value] || '🎯'
})
const turnLabel = computed(() => {
  const map = {
    PLAYER_TURN: '⚫ 你的回合（黑子）',
    AI_THINKING: '⚪ 白子AI 回合',
    GRAY_TURN: '🔘 灰子AI 回合',
  }
  return map[status.value] || ''
})

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

  // 背景 - 使用不透明颜色避免重影
  ctx.fillStyle = '#d2b48c'
  ctx.fillRect(0, 0, cs, cs)

  // 网格线 - 匹配登录界面小棋盘的木质色调
  ctx.strokeStyle = 'rgba(139, 90, 43, 0.4)'
  ctx.lineWidth = bs === 25 ? 0.6 : 1
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

  // 星位
  ctx.fillStyle = bs === 25 ? '#444' : '#333'
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
    const threshold = stoneRadius.value + 1
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
  if (status.value !== 'PLAYER_TURN') return
  const pos = getBoardPos(e.clientX, e.clientY)
  if (!pos) return
  
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
  try {
    const res = await gameApi.aiMove()
    if (res.code === 200) {
      const data = res.data
      board.value = data.board
      terrain.value = data.terrain || null
      status.value = data.status
      undoRemaining.value = data.undoRemaining
      message.value = data.message || ''

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

/* 游戏棋盘容器 - 匹配登录界面小棋盘样式 */
.board-wrapper {
  padding: 10px;
  background: rgba(139, 90, 43, 0.15);
  border-radius: 8px;
  border: 1px solid rgba(139, 90, 43, 0.25);
  backdrop-filter: blur(5px);
  width: fit-content;
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
  color: #888; 
  font-size: 13px; 
  display: flex;
  align-items: center;
  gap: 15px;
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
}

.board-container {
  position: relative;
  border: 2px solid rgba(139, 90, 43, 0.6);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0,0,0,0.4);
  padding: 5px;
  background: rgba(139, 90, 43, 0.15);
}
canvas { display: block; cursor: pointer; }

.side-panel {
  width: 220px;
}

.status-box {
  padding: 20px;
  background: rgba(255,255,255,0.05);
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
.btn-ctrl { background: #0f3460; }
.btn-ctrl:hover { opacity: 0.85; }
.btn-ctrl.resume { background: #4ecca3; }
.btn-ctrl.undo { background: #f0a500; }
.btn-ctrl.new-game { background: #d4a574; }
.btn-ctrl:disabled { opacity: 0.5; cursor: not-allowed; }

.legend {
  padding: 16px;
  background: rgba(255,255,255,0.03);
  border-radius: 8px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  color: #aaa;
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

/* 玩家快捷语 */
.player-phrases {
  margin-top: 16px;
  padding: 12px;
  background: rgba(255,255,255,0.03);
  border-radius: 8px;
}
.phrases-title {
  font-size: 13px;
  color: #888;
  margin-bottom: 8px;
}
.phrases-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.phrase-btn {
  padding: 6px 4px;
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 6px;
  background: rgba(255,255,255,0.05);
  color: #ccc;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.phrase-btn:hover {
  background: rgba(212,165,116,0.2);
  border-color: #d4a574;
  color: #fff;
}
</style>
