<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '@/api/operation'
import { THEME_PRESETS, contrastWithWhite, customPreset, isCustomColor, type PreviewPalette } from '@/utils/appTheme'

/** 用户端主题配色：选预设，或自定义主色。保存后用户下次打开（或刷新）网页即生效 */
const props = defineProps<{ value: string }>()
const emit = defineEmits<{ saved: [] }>()

const KEY = 'app.theme'
const selected = ref(props.value)
const customColor = ref(isCustomColor(props.value) ? props.value : '#2563eb')
const saving = ref(false)

watch(
  () => props.value,
  (v) => {
    selected.value = v
    if (isCustomColor(v)) customColor.value = v
  },
)

const isCustom = computed(() => isCustomColor(selected.value))
const current = computed(() => (isCustom.value ? customPreset(selected.value) : THEME_PRESETS.find((p) => p.key === selected.value) ?? THEME_PRESETS[0]))
const lowContrast = computed(() => isCustom.value && contrastWithWhite(selected.value) < 3)
const changed = computed(() => selected.value !== props.value)

function pickCustom(v: string | null) {
  if (!v) return
  customColor.value = v.toLowerCase()
  selected.value = customColor.value
}

async function save() {
  saving.value = true
  try {
    await api.updateConfig(KEY, selected.value)
    ElMessage.success('已保存，用户刷新网页后生效')
    emit('saved')
  } finally {
    saving.value = false
  }
}

const previewStyle = (p: PreviewPalette) => ({
  '--bg': p.bg,
  '--surface': p.surface,
  '--text': p.text,
  '--muted': p.muted,
  '--line': p.line,
  '--accent': p.accent,
  '--accent-soft': p.accentSoft,
})
</script>

<template>
  <div class="theme-card">
    <div class="head">
      <div>
        <div class="title">用户端主题配色</div>
        <div class="sub">网页端（H5）生效；用户仍可在「设置」里选择跟随系统 / 浅色 / 深色。小程序暂时固定使用默认配色。</div>
      </div>
      <el-button type="primary" :disabled="!changed" :loading="saving" @click="save">保存</el-button>
    </div>

    <div class="body">
      <div class="choices">
        <div
          v-for="p in THEME_PRESETS"
          :key="p.key"
          class="choice"
          :class="{ on: selected === p.key }"
          @click="selected = p.key"
        >
          <span class="swatch" :style="{ background: p.light.bg }"><i :style="{ background: p.light.accent }" /></span>
          <span class="swatch" :style="{ background: p.dark.bg }"><i :style="{ background: p.dark.accent }" /></span>
          <span class="name">{{ p.name }}</span>
          <span v-if="props.value === p.key" class="using">使用中</span>
        </div>
        <div class="choice" :class="{ on: isCustom }" @click="selected = customColor">
          <el-color-picker :model-value="customColor" :predefine="['#2563eb', '#7c3aed', '#be123c', '#0e7490', '#4d7c0f', '#c2410c']" @change="pickCustom" @click.stop />
          <span class="name">自定义主色</span>
          <span v-if="isCustomColor(props.value)" class="using">使用中</span>
        </div>
        <el-alert v-if="lowContrast" type="warning" :closable="false" show-icon class="warn">
          这个颜色偏浅，按钮上的白字会看不清，建议选深一点的颜色
        </el-alert>
      </div>

      <div class="previews">
        <div v-for="mode in (['light', 'dark'] as const)" :key="mode" class="preview" :style="previewStyle(current[mode])">
          <div class="pv-label">{{ mode === 'light' ? '浅色' : '深色' }}</div>
          <div class="pv-hero"><b>12</b> / 340 题</div>
          <div class="pv-card">
            <div class="pv-name">333模考8套卷（一）</div>
            <div class="pv-muted">42 题 · 180 分钟</div>
            <div class="pv-bar"><span /></div>
            <div class="pv-actions">
              <span class="pv-btn">逐题练习</span>
              <span class="pv-btn primary">模拟考试</span>
            </div>
          </div>
          <div class="pv-chips">
            <span class="pv-chip on">冲刺刷题</span>
            <span class="pv-chip">强化刷题</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.theme-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.title {
  font-weight: 600;
  font-size: 15px;
}
.sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}
.body {
  display: flex;
  gap: 24px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.choices {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-content: start;
  gap: 8px;
  width: 440px;
}
.choice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
}
.choice.on {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.swatch {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
}
.swatch i {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
.name {
  flex: 1;
}
.using {
  font-size: 12px;
  color: var(--el-color-success);
}
.warn {
  grid-column: 1 / -1;
}
.previews {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.preview {
  width: 240px;
  background: var(--bg);
  color: var(--text);
  border-radius: 12px;
  padding: 14px;
  font-size: 12px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}
.pv-label {
  color: var(--muted);
}
.pv-hero {
  margin: 4px 0 10px;
}
.pv-hero b {
  font-size: 30px;
  color: var(--accent);
  margin-right: 4px;
}
.pv-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px;
}
.pv-name {
  font-weight: 600;
  font-size: 13px;
}
.pv-muted {
  color: var(--muted);
}
.pv-bar {
  height: 6px;
  background: var(--line);
  border-radius: 3px;
  margin: 8px 0;
  overflow: hidden;
}
.pv-bar span {
  display: block;
  width: 40%;
  height: 100%;
  background: var(--accent);
}
.pv-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}
.pv-btn {
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 6px;
  padding: 3px 8px;
}
.pv-btn.primary {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.pv-chips {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}
.pv-chip {
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--muted);
  border-radius: 99px;
  padding: 1px 10px;
}
.pv-chip.on {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
}
</style>
