<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { applyTheme, watchSystemTheme } from '@/utils/settings'
import { syncAppTheme } from '@/utils/theme'

onLaunch(() => {
  // #ifdef H5
  // 微信内置浏览器自带标题栏：隐藏页面自己的标题栏（见样式 html.in-wechat），并固定字号不随微信字体设置放大
  if (/MicroMessenger/i.test(navigator.userAgent)) {
    document.documentElement.classList.add('in-wechat')
    const lock = () => (window as unknown as { WeixinJSBridge?: { invoke: (...a: unknown[]) => void } })
      .WeixinJSBridge?.invoke('setFontSizeCallback', { fontSize: 0 })
    lock()
    document.addEventListener('WeixinJSBridgeReady', lock, false)
  }
  // #endif
  // 先用本地缓存的配色，再拉取后台最新设定
  applyTheme()
  watchSystemTheme()
  syncAppTheme(applyTheme)
})

// 底部 Tab 在启动时可能还没渲染，回到前台时再设置一次颜色
onShow(() => applyTheme())
</script>

<style lang="scss">
/* 主题色：暖纸色背景 + 赭红强调色，深色模式跟随系统 */
@mixin light-vars {
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
}

@mixin dark-vars {
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

page {
  @include light-vars;

  background: var(--bg);
  color: var(--text);
  font-size: 30rpx;
  line-height: 1.7;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  -webkit-font-smoothing: antialiased;
  -webkit-tap-highlight-color: transparent;
  /* 不随系统 / 微信的字体设置自动放大文字（否则文字变大、留白不变，比例失调） */
  -webkit-text-size-adjust: 100%;
  text-size-adjust: 100%;
}

@media (prefers-color-scheme: dark) {
  page {
    @include dark-vars;
  }
}

/* #ifdef H5 */
/* 微信里已有原生标题栏：隐藏页面自带的标题栏，页面整体上移 */
html.in-wechat {
  --window-top: 0px !important;
}
html.in-wechat uni-page-head {
  display: none !important;
}
html,
body {
  -webkit-text-size-adjust: 100% !important;
  text-size-adjust: 100% !important;
}

/* 设置里手动选了主题：覆盖跟随系统（body 和每页的 uni-page-body 上都有变量） */
body.theme-light,
body.theme-light uni-page-body {
  @include light-vars;
  background: var(--bg);
}
body.theme-dark,
body.theme-dark uni-page-body {
  @include dark-vars;
  background: var(--bg);
}
body.theme-light .uni-page-head {
  background-color: #f6f1e8 !important;
  color: #000 !important;
}
body.theme-dark .uni-page-head {
  background-color: #171412 !important;
  color: #fff !important;
}
body.theme-light .uni-page-head .uni-btn-icon {
  color: #000 !important;
}
body.theme-dark .uni-page-head .uni-btn-icon {
  color: #fff !important;
}
/* 返回键是 SVG，填充色按系统主题写死在 path 上，单改 color 不够 */
body.theme-light .uni-page-head svg path {
  fill: #000 !important;
}
body.theme-dark .uni-page-head svg path {
  fill: #fff !important;
}
body.theme-light .uni-tabbar {
  background-color: #fffdf8 !important;
}
body.theme-dark .uni-tabbar {
  background-color: #221e1b !important;
}
body.theme-dark .uni-tabbar-border {
  background-color: #36302b !important;
}
/* #endif */

/*
 * 排版规范（750rpx 设计宽度，手机上 1rpx ≈ 0.5px）
 * - 页面左右留白 32rpx：卡片外边缘、页面大数字区、卡片外小标题都从这里开始
 * - 卡片内边距 28rpx，卡片之间 20rpx
 * - 字号：正文 30 / 列表标题 30 / 次要 26 / 辅助 24 / 小标签 22
 */
/* 卡片：浅色描边 + 柔和投影 */
.card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: $lemon-radius;
  box-shadow: var(--shadow);
  padding: 28rpx;
  margin: 20rpx 32rpx;
}

.muted {
  color: var(--muted);
  font-size: 26rpx;
}

/* 卡片外的小标题，如“选择试卷” */
.section-label {
  padding: 28rpx 32rpx 0;
  color: var(--muted);
  font-size: 26rpx;
}

/* 大数字头部：如“今日已刷 12 题” */
.hero {
  padding: 28rpx 32rpx 4rpx;
}
.hero-label {
  color: var(--muted);
  font-size: 26rpx;
}
.hero-num {
  font-size: 80rpx;
  font-weight: 700;
  line-height: 1.15;
  color: var(--accent);
  font-variant-numeric: tabular-nums;
}
.hero-unit {
  font-size: 30rpx;
  margin-left: 8rpx;
  color: var(--text);
  font-weight: 500;
}
.hero-sub {
  color: var(--muted);
  font-size: 24rpx;
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
  padding: 4rpx 28rpx;
}
.list-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 26rpx 0;
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
/* 可输入的弹窗（如修改昵称） */
uni-modal .uni-modal__textarea {
  display: block;
  width: calc(100% - 40px) !important;
  margin: 8px 20px 0 !important;
  box-sizing: border-box;
  background: var(--bg) !important;
  color: var(--text) !important;
  border: 1px solid var(--line) !important;
  border-radius: 10px;
  padding: 10px 12px !important;
  font-size: 16px !important;
}
/* #endif */

.empty {
  text-align: center;
  padding: 120rpx 0;
  color: var(--muted);
}
</style>
