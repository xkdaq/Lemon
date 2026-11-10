<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // TODO M3：恢复登录态、拉取“继续上次练习”
})
</script>

<style lang="scss">
page {
  background: $lemon-bg;
  color: $lemon-text;
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin: 24rpx;
}

.muted {
  color: $lemon-muted;
  font-size: 24rpx;
}
</style>
