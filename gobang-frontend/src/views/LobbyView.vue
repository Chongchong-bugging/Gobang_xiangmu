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
              <rect x="6" y="4" width="4" height="16" rx="1" fill="#5c4033"/>
              <rect x="14" y="4" width="4" height="16" rx="1" fill="#5c4033"/>
            </svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M6 4L18 12L6 20V4Z" fill="#5c4033"/>
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
      <div class="lobby-intro">
        <h2>选一局棋</h2>
        <p>挑好难度与玩法，落子开局。</p>
      </div>
      <div class="difficulty-cards">
        <button type="button" class="card" :class="{ selected: difficulty === 'EASY' }" :aria-pressed="difficulty === 'EASY'" @click="difficulty = 'EASY'">
          <h3>初级</h3>
          <p>随机防守，适合新手</p>
        </button>
        <button type="button" class="card" :class="{ selected: difficulty === 'MEDIUM' }" :aria-pressed="difficulty === 'MEDIUM'" @click="difficulty = 'MEDIUM'">
          <h3>中级</h3>
          <p>评分策略，需要一些技巧</p>
        </button>
        <button type="button" class="card" :class="{ selected: difficulty === 'HARD' }" :aria-pressed="difficulty === 'HARD'" @click="difficulty = 'HARD'">
          <h3>高级</h3>
          <p>深度搜索，适合挑战</p>
        </button>
      </div>

      <div class="mode-selection">
        <div class="mode-heading">
          <h3>玩法</h3>
          <span>选定后查看规则</span>
        </div>
        <div class="mode-buttons">
          <button
            type="button"
            class="mode-btn"
            :class="{ selected: gameMode === 'TWO' }"
            :aria-pressed="gameMode === 'TWO'"
            @click="gameMode = 'TWO'"
          ><span class="mode-symbol" aria-hidden="true">● ○</span><strong>经典对弈</strong><small>黑白交锋，先成五子</small></button>
          <button
            type="button"
            class="mode-btn"
            :class="{ selected: gameMode === 'THREE' }"
            :aria-pressed="gameMode === 'THREE'"
            @click="gameMode = 'THREE'"
          ><span class="mode-symbol" aria-hidden="true">● ○ ◐</span><strong>三人混战</strong><small>双 AI 入局，多方博弈</small></button>
          <button
            type="button"
            class="mode-btn obstacle"
            :class="{ selected: gameMode === 'OBSTACLE' }"
            :aria-pressed="gameMode === 'OBSTACLE'"
            @click="gameMode = 'OBSTACLE'"
          ><span class="mode-symbol" aria-hidden="true">◆</span><strong>障碍棋局</strong><small>假山水潭，借势落子</small></button>
          <button
            type="button"
            class="mode-btn three-obstacle"
            :class="{ selected: gameMode === 'THREE_OBSTACLE' }"
            :aria-pressed="gameMode === 'THREE_OBSTACLE'"
            @click="gameMode = 'THREE_OBSTACLE'"
          ><span class="mode-symbol" aria-hidden="true">◇ ◐</span><strong>三人障碍</strong><small>三方逐鹿，地形生变</small></button>
          <button
            type="button"
            class="mode-btn dynamic"
            :class="{ selected: gameMode === 'DYNAMIC' }"
            :aria-pressed="gameMode === 'DYNAMIC'"
            @click="gameMode = 'DYNAMIC'"
          ><span class="mode-symbol" aria-hidden="true">▦</span><strong>动态棋盘</strong><small>解锁区块，步步开疆</small></button>
        </div>
      </div>

      <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
      <button class="btn-start" @click="showRules" :disabled="loading">
        {{ loading ? '创建游戏中...' : '查看规则，开始对局' }}
      </button>
    </div>
  </div>

  <!-- 游戏规则说明弹窗 -->
  <div v-if="showRulesModal" class="rules-overlay" @click.self="closeRules">
    <section class="rules-modal" role="dialog" aria-modal="true" aria-labelledby="rules-title">
      <button type="button" class="rules-close" aria-label="关闭规则说明" @click="closeRules">×</button>
      <div class="rules-header">
        <div class="rules-icon" aria-hidden="true">{{ currentRule.icon }}</div>
        <div>
          <p class="rules-kicker">对局须知</p>
          <h2 id="rules-title" class="rules-title">{{ currentRule.title }}</h2>
          <p class="rules-subtitle">{{ ruleSubtitle }}</p>
        </div>
      </div>
      <div class="rules-meta">
        <span>{{ difficultyLabel }}难度</span>
        <span>{{ boardSize }} × {{ boardSize }} 棋盘</span>
      </div>
      <ol class="rules-content">
        <li v-for="(line, idx) in currentRule.rules" :key="idx" class="rule-line">
          <span class="rule-index">{{ String(idx + 1).padStart(2, '0') }}</span>
          <span>{{ line.replace('• ', '') }}</span>
        </li>
      </ol>
      <div class="rules-buttons">
        <button type="button" class="rules-btn secondary" @click="closeRules">再看看</button>
        <button type="button" class="rules-btn primary" @click="confirmStart" :disabled="loading">进入对局 <span aria-hidden="true">↗</span></button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { gameApi, userApi } from '../api'

const router = useRouter()
const difficulty = ref('EASY')
const gameMode = ref('TWO')  // 新增：游戏模式
const difficultyLabel = computed(() => ({ EASY: '初级', MEDIUM: '中级', HARD: '高级' }[difficulty.value] || '初级'))
const boardSize = computed(() => ['THREE', 'THREE_OBSTACLE'].includes(gameMode.value) ? 25 : 15)
const ruleSubtitle = computed(() => ({
  TWO: '一人执黑，一方执白，回到五子棋最纯粹的较量。',
  THREE: '黑、白、灰三方入局，一步牵动两位对手。',
  OBSTACLE: '山水入棋，落子还需看清地势。',
  THREE_OBSTACLE: '三方交锋，加上变化的山水地形。',
  DYNAMIC: '从中央起步，让棋盘随对局逐渐展开。'
}[gameMode.value] || '落子无悔，愿君尽兴。'))
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
  document.addEventListener('keydown', handleRulesKeydown)
  
  // 确保音乐在播放（从游戏返回时可能需要重新播放）
  if (lobbyMusic.value) {
    if (lobbyMusic.value.paused) {
      lobbyMusic.value.play().then(() => {
        lobbyMusicPlaying.value = true
      }).catch(err => {
        console.warn('大厅音乐播放失败:', err)
        // 如果自动播放被阻止，添加用户交互监听器
        addLobbyInteractionListener()
      })
    } else {
      lobbyMusicPlaying.value = true
    }
  }
})

// 处理用户交互后播放音乐
function handleLobbyInteraction() {
  if (lobbyMusic.value && lobbyMusic.value.paused) {
    lobbyMusic.value.play().then(() => {
      lobbyMusicPlaying.value = true
    }).catch(err => {
      console.warn('大厅音乐播放失败:', err)
    })
  }
  // 只执行一次
  document.removeEventListener('click', handleLobbyInteraction)
}

// 添加用户交互监听器
function addLobbyInteractionListener() {
  setTimeout(() => {
    document.addEventListener('click', handleLobbyInteraction, { once: true })
  }, 100)
}

onUnmounted(() => {
  // 离开大厅时不停止音乐，让音乐继续在游戏中播放
  document.removeEventListener('keydown', handleRulesKeydown)
})

const user = JSON.parse(sessionStorage.getItem('user') || '{}')

// 游戏规则弹窗
const showRulesModal = ref(false)
const currentRule = ref({})

// 游戏规则说明
const rulesMap = {
  TWO: {
    icon: '● ○',
    title: '经典对弈',
    rules: [
      '• 传统五子棋玩法',
      '• 黑子（你） vs 白子（AI）',
      '• 先连成五子者获胜',
      '• 支持悔棋功能（最多2次）'
    ]
  },
  THREE: {
    icon: '● ○ ◐',
    title: '三人混战',
    rules: [
      '• 三方对战：黑子（你）、白子（AI）、灰子（AI）',
      '• 棋盘大小为 25×25',
      '• 任意一方连成五子即获胜',
      '• 策略性更强，需注意多方威胁'
    ]
  },
  OBSTACLE: {
    icon: '◆',
    title: '障碍棋局',
    rules: [
      '• 棋盘上会随机生成障碍物',
      '• ⛰ 假山：棋子不能落在上面，可被棋子包围消除',
      '• 💧 水潭：棋子落下后会滑到相邻空位',
      '• 利用地形优势来击败AI'
    ]
  },
  THREE_OBSTACLE: {
    icon: '◇ ◐',
    title: '三人障碍',
    rules: [
      '• 三人混战 + 障碍物地形',
      '• 棋盘大小为 25×25',
      '• 黑子（你）、白子（AI）、灰子（AI）',
      '• 利用假山和水潭来取得优势'
    ]
  },
  DYNAMIC: {
    icon: '▦',
    title: '动态棋盘',
    rules: [
      '• 棋盘被分为 5×5 个区块（每个区块3×3）',
      '• 初始只有中心区块解锁，其他区块处于锁定状态',
      '• 落子后随机解锁周围区块',
      '• 可使用解锁令牌手动解锁区块（按钮在右侧）',
      '• 被封印的区块无法落子'
    ]
  }
}

function showRules() {
  currentRule.value = rulesMap[gameMode.value] || rulesMap.TWO
  showRulesModal.value = true
}

function closeRules() {
  showRulesModal.value = false
}

function handleRulesKeydown(event) {
  if (event.key === 'Escape' && showRulesModal.value) closeRules()
}

async function confirmStart() {
  showRulesModal.value = false
  await startGame()
}

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

/* 背景遮罩层 */
.bg-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(180deg, rgba(247, 242, 232, 0.18), rgba(242, 235, 222, 0.3));
  z-index: 2;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: rgba(249, 245, 237, 0.82);
  position: relative;
  z-index: 10;
  border-bottom: 1px solid rgba(63, 51, 42, 0.16);
  box-shadow: 0 3px 20px rgba(54, 45, 37, 0.08);
}
header h1 {
  font-size: 20px;
  color: #3b332d;
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  letter-spacing: 2px;
}
.header-right { display: flex; align-items: center; gap: 16px; }
.username { color: #554b42; font-size: 14px; }

/* 音乐控制 */
.music-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
.music-btn {
  padding: 6px 10px;
  border: 1px solid rgba(63, 51, 42, 0.3);
  border-radius: 6px;
  background: rgba(255, 252, 247, 0.7);
  color: #5c4033;
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
  background: rgba(63, 51, 42, 0.24);
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
  border: 1px solid rgba(63, 51, 42, 0.32);
  border-radius: 6px;
  background: rgba(255, 252, 247, 0.62);
  color: #493b31;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.3s;
}
.btn-outline:hover { border-color: #d4a574; color: #d4a574; }
.btn-text {
  padding: 6px 8px;
  border: none;
  background: transparent;
  color: #5f5349;
  cursor: pointer;
  font-size: 13px;
}
.btn-text:hover { color: #d4a574; }

.content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 20px 56px;
  position: relative;
  z-index: 10;
}
.lobby-intro { width: min(100%, 900px); margin-bottom: 24px; }
.content h2 {
  font-size: 30px;
  color: #2f2b28;
  margin: 0 0 5px;
}
.lobby-intro p { color: #65574c; font-size: 14px; }
.difficulty-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: min(100%, 900px);
  gap: 1px;
  padding: 1px;
  margin-bottom: 30px;
  border-radius: 8px;
  background: #b8a995;
  overflow: hidden;
  box-shadow: 0 5px 18px rgba(42, 34, 27, 0.08);
}
.card {
  position: relative;
  min-width: 0;
  min-height: 98px;
  padding: 18px 22px;
  background: #eee8dd;
  border: 0;
  border-radius: 0;
  text-align: left;
  cursor: pointer;
  color: #3d342e;
  font: inherit;
  transition: background 0.2s;
}
.card:hover {
  background: #e3d8ca;
}
.card.selected {
  background: #d8c8b6;
  color: #342d28;
}
.card.selected::before { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px; background: #755941; }
.card h3 { font-size: 18px; color: inherit; margin: 0 0 7px; }
.card p { font-size: 13px; color: #675d54; line-height: 1.45; margin: 0; }
.card:focus-visible, .mode-btn:focus-visible, .btn-start:focus-visible,
.rules-btn:focus-visible, .rules-close:focus-visible {
  outline: 3px solid #a36d45;
  outline-offset: 3px;
}

.btn-start {
  min-height: 54px;
  padding: 12px 34px;
  border-radius: 6px;
  background: #583f32;
  border: 1px solid #583f32;
  color: #fff9f1;
  font-size: 17px;
  cursor: pointer;
  transition: background 0.2s;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  letter-spacing: 0.04em;
}
.btn-start:hover {
  background: #443127;
}
.btn-start:disabled { opacity: 0.6; cursor: not-allowed; }
.error {
  color: #8d2929;
  margin-bottom: 16px;
  font-size: 14px;
}

.mode-selection {
  width: min(100%, 900px);
  margin-bottom: 26px;
}
.mode-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 16px;
  margin-bottom: 12px;
}
.mode-heading h3 { margin: 0; color: #2f2b28; font-size: 21px; }
.mode-heading > span { color: #5d5148; font-size: 13px; }
.mode-buttons {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  padding: 1px;
  border-radius: 8px;
  background: #b8a995;
  overflow: hidden;
  box-shadow: 0 5px 18px rgba(42, 34, 27, 0.08);
}
.mode-btn {
  position: relative;
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  align-content: center;
  align-items: center;
  column-gap: 12px;
  min-width: 0;
  min-height: 74px;
  padding: 12px 18px;
  border: 0;
  border-radius: 0;
  background: #eee8dd;
  color: #332e2b;
  text-align: left;
  font: inherit;
  cursor: pointer;
  transition: background 0.2s;
}
.mode-btn:last-child { grid-column: 1 / -1; }
.mode-btn:hover {
  background: #e3d8ca;
}
.mode-btn.selected {
  background: #d8c8b6;
  color: #342d28;
}
.mode-btn.selected::before { content: ''; position: absolute; top: 0; bottom: 0; left: 0; width: 3px; background: #755941; }
.mode-symbol { grid-row: span 2; color: #806249; font-size: 18px; letter-spacing: 1px; line-height: 1; white-space: nowrap; }
.mode-btn strong { font-size: 15px; line-height: 1.25; }
.mode-btn small { font-size: 12px; line-height: 1.4; margin-top: 3px; color: #685c51; }

/* ===== 古风装饰元素 ===== */

/* 响应式：小屏幕隐藏装饰 */
@media (max-width: 1100px) {
  .deco-left, .deco-right {
    display: none;
  }
}

/* 游戏规则说明弹窗 */
.rules-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(28, 25, 22, 0.62);
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: overlayFadeIn 0.2s ease;
}
@keyframes overlayFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.rules-modal {
  position: relative;
  background: #f8f4ec;
  border-radius: 18px;
  padding: 32px;
  box-shadow: 0 26px 70px rgba(20, 17, 15, 0.35);
  border: 1px solid #d4c5b3;
  animation: modalSlideIn 0.24s ease-out;
  width: min(100%, 560px);
  min-width: 0;
  max-height: calc(100dvh - 32px);
  overflow-y: auto;
}
@keyframes modalSlideIn {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
.rules-close {
  position: absolute;
  top: 18px;
  right: 18px;
  width: 36px;
  height: 36px;
  border: 1px solid #d9cbb9;
  border-radius: 50%;
  background: #fffaf3;
  color: #5b493a;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}
.rules-header { display: flex; align-items: center; gap: 18px; padding-right: 32px; }
.rules-icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 74px;
  height: 74px;
  border-radius: 14px;
  background: #332e2b;
  color: #e5c99f;
  font-size: 27px;
  letter-spacing: 1px;
}
.rules-kicker { margin: 0 0 6px; color: #8a684d; font: 700 11px Arial, sans-serif; letter-spacing: 0.12em; }
.rules-title {
  font-size: 27px;
  color: #2d2926;
  margin: 0 0 5px;
  font-family: 'Ma Shan Zheng', 'ZCOOL XiaoWei', serif;
  letter-spacing: 2px;
}
.rules-subtitle { margin: 0; color: #675d53; font-size: 13px; line-height: 1.5; }
.rules-meta { display: flex; gap: 8px; flex-wrap: wrap; margin: 24px 0 10px; }
.rules-meta span { padding: 6px 10px; border-radius: 6px; background: #eee5d8; color: #634b39; font-size: 12px; }
.rules-content {
  list-style: none;
  margin: 0 0 24px;
  padding: 0;
}
.rule-line {
  display: flex;
  gap: 15px;
  align-items: start;
  padding: 12px 2px;
  border-bottom: 1px solid #e6dcd0;
  font-size: 14px;
  color: #3c342e;
  line-height: 1.55;
}
.rule-index { flex: none; color: #9b714f; font: 700 12px/1.8 Arial, sans-serif; }
.rules-buttons {
  display: flex;
  gap: 10px;
}
.rules-btn {
  min-height: 46px;
  padding: 10px 20px;
  border-radius: 9px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s, transform 0.2s;
  font-family: inherit;
}
.rules-btn.primary {
  flex: 1;
  background: #583f32;
  border: 1px solid #583f32;
  color: #fff9f1;
  font-weight: 700;
}
.rules-btn.primary span { margin-left: 8px; }
.rules-btn.primary:hover { background: #443127; transform: translateY(-1px); }
.rules-btn.primary:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }
.rules-btn.secondary {
  background: #fffaf3;
  border: 1px solid #cfc0ad;
  color: #54463a;
}
.rules-btn.secondary:hover { background: #f0e6da; }

@media (max-width: 900px) {
  header { padding: 14px 20px; gap: 12px; }
  .content { padding: 34px 20px 48px; }
}

@media (max-width: 680px) {
  header { flex-wrap: wrap; }
  header h1 { width: 100%; text-align: center; font-size: 21px; }
  .header-right { width: 100%; justify-content: center; flex-wrap: wrap; gap: 10px; }
  .username { max-width: 100%; overflow-wrap: anywhere; }
  .content { padding: 20px 16px 24px; }
  .lobby-intro { margin-bottom: 14px; }
  .content h2 { font-size: 25px; }
  .difficulty-cards { grid-template-columns: 1fr; margin-bottom: 18px; }
  .card {
    min-height: 64px;
    padding: 9px 16px;
    text-align: left;
  }
  .card h3 { margin-bottom: 3px; }
  .mode-selection { width: 100%; margin-bottom: 16px; }
  .mode-heading { align-items: end; margin-bottom: 8px; }
  .mode-heading > span { font-size: 11px; }
  .mode-buttons { grid-template-columns: 1fr; }
  .mode-btn { min-height: 60px; padding: 8px 14px; }
  .mode-btn:last-child { grid-column: auto; }
  .btn-start { width: 100%; max-width: 360px; min-height: 48px; }
  .rules-modal { padding: 24px 20px; }
  .rules-header { gap: 12px; }
  .rules-icon { width: 58px; height: 58px; font-size: 19px; }
  .rules-title { font-size: 23px; }
  .rules-buttons { flex-direction: column-reverse; }
  .rules-btn { min-height: 44px; }
}

@media (prefers-reduced-motion: reduce) {
  .rules-overlay, .rules-modal, .rules-icon { animation: none !important; }
}
</style>
