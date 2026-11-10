<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // TODO M3：恢复登录态、拉取“继续上次练习”
})
</script>

<style lang="scss">
/* 主题色：暖纸色背景 + 赭红强调色，深色模式跟随系统 */
page {
  --bg: #f6f1e8;
  --surface: #fffdf8;
  --text: #2b2520;
  --muted: #8a7f73;
  --line: #e7dfd2;
  --accent: #b4432f;
  --accent-soft: #f6e3dc;
  --key: #9a3412;
  --g0: #c2410c;
  --g1: #b7791f;
  --g2: #2f855a;
  /* 进度条“已刷”段：介于底色和强调色之间 */
  --done-color: #ebc3b5;
  /* 答对 / 答错的浅底色 */
  --g2-soft: #e3f1e8;
  --g0-soft: #fbe6dc;
  --shadow: 0 1px 2px rgba(60, 40, 20, 0.06), 0 4px 16px rgba(60, 40, 20, 0.05);

  background: var(--bg);
  color: var(--text);
  font-size: 30rpx;
  line-height: 1.7;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;
}

@media (prefers-color-scheme: dark) {
  page {
    --bg: #171412;
    --surface: #221e1b;
    --text: #ece5dc;
    --muted: #9b9086;
    --line: #36302b;
    --accent: #e0785f;
    --accent-soft: #3a2620;
    --key: #f0a07f;
    --g0: #f08a5d;
    --g1: #e0b04d;
    --g2: #5fbf8a;
    --done-color: #6b4236;
    --g2-soft: #1f3328;
    --g0-soft: #3a2620;
    --shadow: none;
  }
}

/* 卡片：浅色描边 + 柔和投影 */
.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: $lemon-radius;
  box-shadow: var(--shadow);
  padding: 32rpx;
  margin: 24rpx 32rpx;
}

.muted {
  color: var(--muted);
  font-size: 26rpx;
}

/* 卡片外的小标题，如“选择试卷” */
.section-label {
  padding: 32rpx 40rpx 0;
  color: var(--muted);
  font-size: 26rpx;
}

/* 大数字头部：如“今日已刷 12 题” */
.hero {
  padding: 32rpx 40rpx 8rpx;
}
.hero-label {
  color: var(--muted);
  font-size: 28rpx;
}
.hero-num {
  font-size: 112rpx;
  font-weight: 700;
  line-height: 1.1;
  color: var(--accent);
  font-variant-numeric: tabular-nums;
}
.hero-unit {
  font-size: 36rpx;
  margin-left: 8rpx;
  color: var(--text);
  font-weight: 500;
}
.hero-sub {
  color: var(--muted);
  font-size: 26rpx;
}

/* 按钮：默认描边，primary 实心赭红，ghost 浅赭红 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--text);
  padding: 16rpx 32rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
  font-weight: 500;
  line-height: 1.4;
}
.btn:active {
  transform: scale(0.98);
}
.btn-primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.btn-ghost {
  background: var(--accent-soft);
  border-color: transparent;
  color: var(--accent);
}
.btn-small {
  padding: 8rpx 24rpx;
  font-size: 26rpx;
}
.btn-block {
  display: flex;
  width: 100%;
  box-sizing: border-box;
  padding: 22rpx 32rpx;
  font-size: 30rpx;
}

.tag {
  display: inline-block;
  font-size: 22rpx;
  padding: 2rpx 16rpx;
  border-radius: 99rpx;
  background: var(--accent-soft);
  color: var(--accent);
  white-space: nowrap;
  line-height: 1.6;
}

/* 列表卡片：行与行之间细线分隔 */
.list-card {
  padding: 8rpx 32rpx;
}
.list-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx 0;
  border-bottom: 1px solid var(--line);
}
.list-row:last-child {
  border-bottom: none;
}
.row-arrow {
  color: var(--muted);
  font-size: 36rpx;
  line-height: 1;
}

/* #ifdef H5 */
/* 系统弹窗（uni.showModal）换成主题样式：纸色卡片 + 圆角按钮 */
uni-modal .uni-mask {
  background: rgba(23, 20, 18, 0.45) !important;
  backdrop-filter: blur(2px);
}
uni-modal .uni-modal {
  width: 82% !important;
  max-width: 320px !important;
  background: var(--surface) !important;
  border: 1px solid var(--line);
  border-radius: 16px !important;
  box-shadow: 0 12px 40px rgba(60, 40, 20, 0.18);
  overflow: hidden;
}
uni-modal .uni-modal__hd {
  padding: 24px 20px 4px !important;
}
uni-modal .uni-modal__title {
  color: var(--text) !important;
  font-size: 18px !important;
  font-weight: 600 !important;
}
uni-modal .uni-modal__bd {
  padding: 8px 24px 4px !important;
  min-height: 0 !important;
  color: var(--muted) !important;
  font-size: 15px !important;
  line-height: 1.6 !important;
}
uni-modal .uni-modal__ft {
  display: flex !important;
  gap: 10px;
  padding: 20px !important;
  line-height: normal !important;
  font-size: 16px !important;
}
uni-modal .uni-modal__ft::after,
uni-modal .uni-modal__btn::after {
  display: none !important;
}
uni-modal .uni-modal__btn {
  flex: 1;
  height: 44px;
  line-height: 44px !important;
  border-radius: 10px;
  font-weight: 500;
}
uni-modal .uni-modal__btn_default {
  background: var(--bg);
  border: 1px solid var(--line);
  color: var(--text) !important;
}
uni-modal .uni-modal__btn_primary {
  background: var(--accent);
  color: #fff !important;
}
uni-modal .uni-modal__btn:active {
  opacity: 0.85;
}
/* #endif */

.empty {
  text-align: center;
  padding: 120rpx 0;
  color: var(--muted);
}
</style>
