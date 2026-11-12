<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 轻量折线图（纯 SVG，不引第三方图表库）：多条序列共用一个 Y 轴，鼠标悬停显示当天各序列数值。
 */
export interface Series {
  name: string
  color: string
  data: (number | null)[]
}

const props = withDefaults(defineProps<{ labels: string[]; series: Series[]; height?: number; unit?: string }>(), {
  height: 240,
  unit: '',
})

/** 按容器实际宽度画，避免缩放把文字拉变形 */
const width = ref(800)
const W = computed(() => width.value)
const PAD = { left: 44, right: 16, top: 16, bottom: 28 }
const innerW = computed(() => W.value - PAD.left - PAD.right)
const plotRef = ref<HTMLDivElement>()
let observer: ResizeObserver | undefined
onMounted(() => {
  observer = new ResizeObserver(([e]) => {
    if (e.contentRect.width > 0) width.value = Math.round(e.contentRect.width)
  })
  if (plotRef.value) observer.observe(plotRef.value)
})
onBeforeUnmount(() => observer?.disconnect())
const innerH = computed(() => props.height - PAD.top - PAD.bottom)

/** Y 轴刻度：步长取 1 / 2 / 5 × 10^n（整数），大约 4~5 格 */
const tickStep = computed(() => {
  const max = Math.max(1, ...props.series.flatMap((s) => s.data.map((v) => v ?? 0)))
  const raw = max / 4
  const p = Math.pow(10, Math.floor(Math.log10(raw)))
  return Math.max(1, [1, 2, 5, 10].map((m) => m * p).find((v) => v >= raw) ?? 10 * p)
})
const yMax = computed(() => {
  const max = Math.max(1, ...props.series.flatMap((s) => s.data.map((v) => v ?? 0)))
  return Math.ceil(max / tickStep.value) * tickStep.value
})
const ticks = computed(() => {
  const list: number[] = []
  for (let v = 0; v <= yMax.value; v += tickStep.value) list.push(v)
  return list
})

const x = (i: number) => PAD.left + (props.labels.length <= 1 ? innerW.value / 2 : (innerW.value * i) / (props.labels.length - 1))
const y = (v: number) => PAD.top + innerH.value - (innerH.value * v) / yMax.value

function path(data: (number | null)[]) {
  let d = ''
  data.forEach((v, i) => {
    if (v == null) return
    d += `${d ? 'L' : 'M'}${x(i).toFixed(1)},${y(v).toFixed(1)}`
  })
  return d
}

/** X 轴最多显示约 10 个日期 */
const labelStep = computed(() => Math.max(1, Math.ceil(props.labels.length / Math.max(2, Math.floor(innerW.value / 60)))))
const shortLabel = (s: string) => s.slice(5)

const hover = ref<number | null>(null)
const svgRef = ref<SVGSVGElement>()
function onMove(e: MouseEvent) {
  const rect = svgRef.value?.getBoundingClientRect()
  if (!rect || !props.labels.length) return
  const px = e.clientX - rect.left
  const i = props.labels.length <= 1 ? 0 : Math.round(((px - PAD.left) / innerW.value) * (props.labels.length - 1))
  hover.value = Math.min(props.labels.length - 1, Math.max(0, i))
}
const tipLeft = computed(() => (hover.value == null ? 0 : (x(hover.value) / W.value) * 100))
</script>

<template>
  <div class="chart">
    <div class="legend">
      <span v-for="s in series" :key="s.name" class="legend-item"><i :style="{ background: s.color }" />{{ s.name }}</span>
    </div>
    <div ref="plotRef" class="plot">
      <svg ref="svgRef" :viewBox="`0 0 ${W} ${height}`" :style="{ height: `${height}px` }"
        @mousemove="onMove" @mouseleave="hover = null">
        <g v-for="t in ticks" :key="t">
          <line :x1="PAD.left" :x2="W - PAD.right" :y1="y(t)" :y2="y(t)" class="grid" />
          <text :x="PAD.left - 8" :y="y(t) + 4" class="axis" text-anchor="end">{{ t }}</text>
        </g>
        <template v-for="(l, i) in labels" :key="l">
          <text v-if="i % labelStep === 0 || i === labels.length - 1" :x="x(i)" :y="height - 8" class="axis" text-anchor="middle">
            {{ shortLabel(l) }}
          </text>
        </template>
        <line v-if="hover != null" :x1="x(hover)" :x2="x(hover)" :y1="PAD.top" :y2="PAD.top + innerH" class="cursor" />
        <path v-for="s in series" :key="s.name" :d="path(s.data)" :stroke="s.color" class="line" />
        <template v-for="s in series" :key="s.name + '-dot'">
          <circle v-if="hover != null && s.data[hover] != null" :cx="x(hover)" :cy="y(s.data[hover]!)" r="4" :fill="s.color" />
        </template>
      </svg>
      <div v-if="hover != null" class="tip" :style="{ left: `${tipLeft}%` }" :class="{ flip: tipLeft > 70 }">
        <div class="tip-date">{{ labels[hover] }}</div>
        <div v-for="s in series" :key="s.name" class="tip-row">
          <i :style="{ background: s.color }" />{{ s.name }}<b>{{ s.data[hover] ?? '—' }}{{ s.data[hover] != null ? unit : '' }}</b>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
}
.legend-item i,
.tip-row i {
  display: inline-block;
  width: 10px;
  height: 3px;
  border-radius: 2px;
  margin-right: 6px;
  vertical-align: middle;
}
.plot {
  position: relative;
}
svg {
  width: 100%;
  display: block;
}
.grid {
  stroke: var(--el-border-color-lighter);
  stroke-width: 1;
}
.axis {
  font-size: 11px;
  fill: var(--el-text-color-secondary);
}
.line {
  fill: none;
  stroke-width: 2;
  vector-effect: non-scaling-stroke;
}
.cursor {
  stroke: var(--el-border-color);
  stroke-dasharray: 3 3;
}
.tip {
  position: absolute;
  top: 8px;
  transform: translateX(12px);
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
  padding: 8px 10px;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
  z-index: 2;
}
.tip.flip {
  transform: translateX(calc(-100% - 12px));
}
.tip-date {
  font-weight: 600;
  margin-bottom: 4px;
}
.tip-row b {
  margin-left: 12px;
  float: right;
}
</style>
