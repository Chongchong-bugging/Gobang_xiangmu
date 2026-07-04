<template>
  <div class="profile-page">
    <!-- 古风山水背景图片 -->
    <div class="bg-image"></div>
    <div class="bg-overlay"></div>

    <header>
      <h1>五子棋</h1>
      <div class="header-right">
        <button class="btn-outline" @click="$router.push('/lobby')">游戏大厅</button>
        <button class="btn-text" @click="logout">退出</button>
      </div>
    </header>

    <div class="content">
      <div class="profile-card" v-if="score">
        <h2>个人中心</h2>
        <div class="user-name">{{ score.nickname }}</div>

        <div class="score-grid">
          <div class="score-item total">
            <div class="score-value">{{ score.totalScore }}</div>
            <div class="score-label">总得分</div>
          </div>
          <div class="score-item">
            <div class="score-value win">{{ score.wins }}</div>
            <div class="score-label">胜利</div>
          </div>
          <div class="score-item">
            <div class="score-value loss">{{ score.losses }}</div>
            <div class="score-label">失败</div>
          </div>
          <div class="score-item">
            <div class="score-value draw">{{ score.draws }}</div>
            <div class="score-label">平局</div>
          </div>
        </div>

        <div class="win-rate">
          <div class="rate-label">胜率</div>
          <div class="rate-bar">
            <div class="rate-fill" :style="{ width: score.winRate + '%' }"></div>
          </div>
          <div class="rate-text">{{ score.winRate.toFixed(1) }}%</div>
        </div>
      </div>

      <div v-else class="loading">加载中...</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { scoreApi, userApi } from '../api'

const router = useRouter()
const score = ref(null)

onMounted(async () => {
  const res = await scoreApi.getMyScore()
  if (res.code === 200) score.value = res.data
})

async function logout() {
  await userApi.logout()
  sessionStorage.clear()
  router.push('/login')
}
</script>

<style scoped>
.profile-page {
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
.header-right { display: flex; gap: 16px; }
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
  justify-content: center;
  padding: 60px 20px;
  position: relative;
  z-index: 10;
}
.profile-card {
  background: rgba(255,255,255,0.05);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 16px;
  padding: 40px;
  width: 480px;
  text-align: center;
}
.profile-card h2 {
  font-size: 22px;
  color: #d4a574;
  margin-bottom: 12px;
}
.user-name {
  font-size: 18px;
  color: #eee;
  margin-bottom: 32px;
}
.score-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}
.score-item {
  padding: 16px 8px;
  background: rgba(255,255,255,0.05);
  border-radius: 10px;
}
.score-item.total { grid-column: span 4; }
.score-value {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 4px;
}
.score-item.total .score-value { color: #d4a574; }
.score-value.win { color: #4ecca3; }
.score-value.loss { color: #d4a574; }
.score-value.draw { color: #f0a500; }
.score-label {
  font-size: 13px;
  color: #888;
}
.win-rate {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rate-label { color: #888; font-size: 14px; white-space: nowrap; }
.rate-bar {
  flex: 1;
  height: 10px;
  background: rgba(255,255,255,0.1);
  border-radius: 5px;
  overflow: hidden;
}
.rate-fill {
  height: 100%;
  background: linear-gradient(90deg, #d4a574, #4ecca3);
  border-radius: 5px;
  transition: width 0.5s;
}
.rate-text {
  color: #ccc;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}
.loading { color: #888; font-size: 16px; padding: 40px; }
</style>
