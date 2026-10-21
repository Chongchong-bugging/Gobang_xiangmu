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
      <section class="profile-panel" v-if="score">
        <div class="panel-topline"><span>PLAYER ARCHIVE</span><span>五子棋 · 对局档案</span></div>
        <div class="profile-hero">
          <div class="player-identity">
            <div class="avatar" aria-hidden="true">棋</div>
            <div>
              <p class="welcome">欢迎回来</p>
              <h2>{{ score.nickname }}</h2>
              <p class="hero-note">每一步，都算数。</p>
            </div>
          </div>
          <button type="button" class="continue-btn" @click="$router.push('/lobby')">继续对弈 <span aria-hidden="true">↗</span></button>
        </div>
        <div class="overview-grid">
          <div class="overview-card primary-stat"><span>累计积分</span><strong>{{ score.totalScore }}</strong><small>来自已完成的棋局</small></div>
          <div class="overview-card"><span>累计对局</span><strong>{{ totalGames }}</strong><small>胜、负、平总场次</small></div>
          <div class="overview-card"><span>当前胜率</span><strong>{{ winRate.toFixed(1) }}<em>%</em></strong><small>胜场 / 总对局</small></div>
        </div>
        <div class="section-heading"><h3>战绩分布</h3><span>胜 / 负 / 平</span></div>
        <div class="result-track" role="img" :aria-label="`胜利 ${score.wins} 局，失败 ${score.losses} 局，平局 ${score.draws} 局`">
          <span class="segment win" :style="{ width: share(score.wins) + '%' }"></span>
          <span class="segment loss" :style="{ width: share(score.losses) + '%' }"></span>
          <span class="segment draw" :style="{ width: share(score.draws) + '%' }"></span>
        </div>
        <div class="result-grid">
          <div class="result-item"><span class="result-label"><i class="result-dot win"></i>胜利</span><strong>{{ score.wins }}</strong><small>{{ share(score.wins).toFixed(0) }}%</small></div>
          <div class="result-item"><span class="result-label"><i class="result-dot loss"></i>失败</span><strong>{{ score.losses }}</strong><small>{{ share(score.losses).toFixed(0) }}%</small></div>
          <div class="result-item"><span class="result-label"><i class="result-dot draw"></i>平局</span><strong>{{ score.draws }}</strong><small>{{ share(score.draws).toFixed(0) }}%</small></div>
        </div>
        <div class="panel-footnote">战绩以已完成的对局为准</div>
      </section>

      <div v-else class="loading">{{ loadError || '加载中...' }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { scoreApi, userApi } from '../api'

const router = useRouter()
const score = ref(null)
const loadError = ref('')
const totalGames = computed(() => ['wins', 'losses', 'draws'].reduce((sum, key) => sum + Number(score.value?.[key] || 0), 0))
const winRate = computed(() => Math.max(0, Math.min(100, Number(score.value?.winRate || 0))))
function share(value) { return totalGames.value ? Number(value || 0) / totalGames.value * 100 : 0 }

onMounted(async () => {
  try {
    const res = await scoreApi.getMyScore()
    if (res.code === 200) score.value = res.data
    else loadError.value = res.message || '暂时无法加载积分，请稍后重试'
  } catch {
    loadError.value = '暂时无法加载积分，请稍后重试'
  }
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
  background: linear-gradient(180deg, rgba(247, 242, 232, 0.14), rgba(242, 235, 222, 0.3));
  z-index: 1;
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
.header-right { display: flex; gap: 16px; }
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
  justify-content: center;
  padding: 54px 20px 72px;
  position: relative;
  z-index: 10;
}
.profile-panel {
  width: min(100%, 860px);
  padding: 30px 36px 26px;
  border: 1px solid #d7cab8;
  border-radius: 18px;
  background: rgba(250, 247, 240, 0.96);
  box-shadow: 0 20px 60px rgba(44, 36, 29, 0.2);
  color: #332d28;
}
.panel-topline { display: flex; justify-content: space-between; gap: 12px; padding-bottom: 17px; border-bottom: 1px solid #ded3c4; color: #8a6d55; font-size: 11px; letter-spacing: 0.12em; }
.panel-topline span:first-child { font: 700 11px Arial, sans-serif; letter-spacing: 0.2em; }
.profile-hero { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 26px 0 30px; }
.player-identity { display: flex; align-items: center; gap: 18px; min-width: 0; }
.avatar { display: grid; place-items: center; flex: none; width: 70px; height: 70px; border-radius: 16px; background: #332e2b; color: #e7c89c; font-family: 'Ma Shan Zheng', serif; font-size: 33px; box-shadow: inset 0 0 0 1px #715944; }
.welcome { margin: 0 0 3px; color: #8a6d55; font-size: 12px; }
.profile-hero h2 { margin: 0; font-size: 27px; line-height: 1.25; color: #302923; overflow-wrap: anywhere; }
.hero-note { margin: 5px 0 0; color: #74695e; font-size: 13px; }
.continue-btn { flex: none; min-height: 42px; padding: 9px 16px; border: 1px solid #583f32; border-radius: 8px; background: #583f32; color: #fff9f1; font: inherit; font-size: 13px; font-weight: 700; cursor: pointer; }
.continue-btn:hover { background: #443127; }
.continue-btn:focus-visible { outline: 3px solid #a36d45; outline-offset: 3px; }
.continue-btn span { margin-left: 8px; }
.overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.overview-card { display: flex; flex-direction: column; align-items: flex-start; min-height: 146px; padding: 20px; border: 1px solid #e3d8c9; border-radius: 13px; background: #fffdf9; }
.overview-card span { color: #6b5b4d; font-size: 13px; }
.overview-card strong { margin: 14px 0 7px; color: #342b25; font: 700 34px/1 Arial, sans-serif; }
.overview-card strong em { margin-left: 2px; font-size: 18px; font-style: normal; }
.overview-card small { margin-top: auto; color: #827569; font-size: 11px; }
.overview-card.primary-stat { border-color: #332e2b; background: #332e2b; }
.overview-card.primary-stat span, .overview-card.primary-stat small { color: #dfcdb8; }
.overview-card.primary-stat strong { color: #e7c89c; }
.section-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin: 33px 0 17px; }
.section-heading h3 { margin: 0; font-size: 19px; color: #352e28; }
.section-heading span { color: #827569; font-size: 12px; }
.result-track { display: flex; width: 100%; height: 10px; border-radius: 999px; background: #e9e1d6; overflow: hidden; }
.segment { display: block; height: 100%; }
.segment.win, .result-dot.win { background: #597761; }
.segment.loss, .result-dot.loss { background: #a9755a; }
.segment.draw, .result-dot.draw { background: #c8a45c; }
.result-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 15px; }
.result-item { display: grid; grid-template-columns: 1fr auto; align-items: baseline; gap: 7px; padding: 16px 18px; border: 1px solid #e6dbcd; border-radius: 10px; background: rgba(255,255,255,0.62); }
.result-label { display: flex; align-items: center; gap: 8px; color: #5c5148; font-size: 13px; }
.result-dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%; }
.result-item strong { grid-column: 1; grid-row: 2; font: 700 25px/1 Arial, sans-serif; color: #342b25; }
.result-item small { grid-column: 2; grid-row: 2; color: #81746a; font-size: 12px; }
.panel-footnote { margin-top: 22px; padding-top: 17px; border-top: 1px solid #e6dbcd; color: #827569; font-size: 12px; }
.loading { padding: 28px; border-radius: 12px; background: rgba(250, 247, 240, 0.94); color: #493b31; font-size: 16px; }

@media (max-width: 600px) {
  header { padding: 14px 16px; gap: 10px; }
  .header-right { gap: 8px; }
  .content { padding: 28px 16px 40px; }
  .profile-panel { padding: 22px 18px; }
  .profile-hero { align-items: flex-start; flex-direction: column; padding: 22px 0; }
  .avatar { width: 60px; height: 60px; }
  .profile-hero h2 { font-size: 24px; }
  .continue-btn { align-self: stretch; }
  .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
  .overview-card { min-height: 123px; padding: 15px; }
  .overview-card.primary-stat { grid-column: 1 / -1; }
  .overview-card strong { font-size: 29px; }
  .result-grid { gap: 7px; }
  .result-item { padding: 11px 9px; }
  .result-item strong { font-size: 21px; }
  .result-label { gap: 4px; font-size: 12px; }
  .result-item small { font-size: 11px; }
}
</style>
