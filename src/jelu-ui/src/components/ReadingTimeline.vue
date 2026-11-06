<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import dataService from '../services/DataService'

const router = useRouter()
const emit = defineEmits<{ (e: 'refresh'): void }>()

const props = defineProps<{ books: any[], loading: boolean }>()

const PX_PER_DAY_OPTIONS = [6, 12, 24, 48, 96]
const zoomIndex = ref(2)
const pxPerDay = computed(() => PX_PER_DAY_OPTIONS[zoomIndex.value])
const today = dayjs()
const FUTURE_DAYS = 20
const TOTAL_HISTORY_DAYS = 365 * 3
const ROW_HEIGHT = 90
const ROW_PAD = 16
const COVER_W = 38
const COVER_H = 52
const HEADER_H = 36

const scrollRef = ref<HTMLElement | null>(null)
const showGoToday = ref(false)

// ── Layout ────────────────────────────────────────────────────────────────────
const layoutRows = computed(() => {
  if (!props.books.length) return []
  const sorted = [...props.books].sort((a, b) =>
    dayjs(a.startDate).valueOf() - dayjs(b.startDate).valueOf())
  const rows: any[][] = []
  const rowEnds: number[] = []
  for (const book of sorted) {
    const start = dayjs(book.startDate).valueOf()
    const end = book.finishDate ? dayjs(book.finishDate).valueOf() : today.valueOf()
    let placed = false
    for (let r = 0; r < rows.length; r++) {
      if (rowEnds[r] < start - 86400000 * 3) {
        rows[r].push({ ...book, row: r })
        rowEnds[r] = end
        placed = true; break
      }
    }
    if (!placed) {
      rows.push([{ ...book, row: rows.length }])
      rowEnds.push(end)
    }
  }
  return rows.flat()
})

const numRows = computed(() =>
  layoutRows.value.length ? Math.max(...layoutRows.value.map((b: any) => b.row)) + 1 : 1)
const svgHeight = computed(() => HEADER_H + numRows.value * (ROW_HEIGHT + ROW_PAD) + 20)
const totalWidth = computed(() => (TOTAL_HISTORY_DAYS + FUTURE_DAYS) * pxPerDay.value)
const todayX = computed(() => TOTAL_HISTORY_DAYS * pxPerDay.value)

function dateToX(date: string | dayjs.Dayjs) {
  const d = typeof date === 'string' ? dayjs(date) : date
  return todayX.value + d.diff(today, 'day') * pxPerDay.value
}

// ── Ticks ─────────────────────────────────────────────────────────────────────
const ticks = computed(() => {
  const result: { x: number, label: string, major: boolean }[] = []
  let cur = today.subtract(TOTAL_HISTORY_DAYS, 'day').startOf('month')
  while (cur.isBefore(today.add(FUTURE_DAYS, 'day'))) {
    result.push({ x: dateToX(cur), label: cur.format('MMM YYYY'), major: true })
    if (pxPerDay.value >= 24) {
      let w = cur.add(7, 'day')
      while (w.isBefore(cur.add(1, 'month'))) {
        result.push({ x: dateToX(w), label: w.format('D'), major: false })
        w = w.add(7, 'day')
      }
    }
    cur = cur.add(1, 'month')
  }
  return result
})

// ── Bar colors ────────────────────────────────────────────────────────────────
function barColor(status: string) {
  if (status === 'FINISHED') return '#34d399'
  if (status === 'CURRENTLY_READING') return '#38bdf8'
  if (status === 'PAUSED') return '#fbbf24'
  if (status === 'DROPPED') return '#f87171'
  return '#94a3b8'
}
function barGlow(status: string) {
  if (status === 'FINISHED') return 'drop-shadow(0 0 4px rgba(52,211,153,0.5))'
  if (status === 'CURRENTLY_READING') return 'drop-shadow(0 0 4px rgba(56,189,248,0.5))'
  return 'none'
}

// ── Tooltip ───────────────────────────────────────────────────────────────────
const tooltip = ref<{ text: string, x: number, y: number } | null>(null)
function showTip(e: MouseEvent, text: string) {
  tooltip.value = { text, x: e.clientX, y: e.clientY }
}
function hideTip() { tooltip.value = null }

// ── Scroll ────────────────────────────────────────────────────────────────────
function scrollToDefault() {
  if (!scrollRef.value) return
  const el = scrollRef.value
  el.scrollLeft = todayX.value - el.clientWidth * 0.65
}

function goToToday() {
  if (!scrollRef.value) return
  scrollRef.value.scrollLeft = todayX.value - scrollRef.value.clientWidth * 0.65
}

function onWheel(e: WheelEvent) {
  if (!scrollRef.value) return
  e.preventDefault()
  scrollRef.value.scrollLeft += e.deltaY + e.deltaX
}

function onScroll() {
  if (!scrollRef.value) return
  const el = scrollRef.value
  const targetLeft = todayX.value - el.clientWidth * 0.65
  showGoToday.value = Math.abs(el.scrollLeft - targetLeft) > el.clientWidth * 0.5
}

onMounted(() => setTimeout(scrollToDefault, 150))

// ── Zoom ──────────────────────────────────────────────────────────────────────
function zoom(dir: number) {
  const el = scrollRef.value
  const before = pxPerDay.value
  const newIdx = Math.max(0, Math.min(PX_PER_DAY_OPTIONS.length - 1, zoomIndex.value + dir))
  if (newIdx === zoomIndex.value) return
  const ratio = PX_PER_DAY_OPTIONS[newIdx] / before
  const scrollCenter = el ? el.scrollLeft + el.clientWidth / 2 : 0
  zoomIndex.value = newIdx
  if (el) setTimeout(() => { el.scrollLeft = scrollCenter * ratio - el.clientWidth / 2 }, 0)
}

const zoomLabel = computed(() => {
  const p = pxPerDay.value
  if (p >= 96) return 'Day view'
  if (p >= 48) return 'Week view'
  if (p >= 24) return 'Month view'
  if (p >= 12) return '2 Months'
  return 'Quarter'
})

// Max dot radius per book
function maxPages(book: any) {
  return Math.max(...(book.days || []).map((d: any) => d.pagesRead), 1)
}
function dotR(pages: number, book: any) {
  return Math.max(2, Math.min(7, 2 + (pages / maxPages(book)) * 5))
}
</script>

<template>
  <div class="relative">
    <!-- Controls -->
    <div class="flex items-center justify-between mb-3">
      <h2 class="text-lg font-bold flex items-center gap-2 opacity-90">
        <i class="mdi mdi-chart-gantt text-violet-300" /> Reading Timeline
      </h2>
      <div class="flex items-center gap-2">
        <span class="text-xs opacity-40">{{ zoomLabel }}</span>
        <div class="join">
          <button class="btn btn-xs btn-outline join-item" @click="zoom(-1)" :disabled="zoomIndex === 0">
            <i class="mdi mdi-minus" />
          </button>
          <button class="btn btn-xs btn-outline join-item" @click="zoom(1)" :disabled="zoomIndex === PX_PER_DAY_OPTIONS.length - 1">
            <i class="mdi mdi-plus" />
          </button>
        </div>
        <button v-if="showGoToday" class="btn btn-xs btn-primary" @click="goToToday">
          Go to today
        </button>
        <button class="btn btn-xs btn-ghost" @click="emit('refresh')" title="Refresh timeline">
          <i class="mdi mdi-refresh" />
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-md" />
    </div>

    <div v-else-if="!books.length" class="text-center py-10 opacity-40 text-sm">
      No reading activity yet — log some progress to see your timeline.
    </div>

    <!-- Timeline -->
    <div
      v-else
      ref="scrollRef"
      class="overflow-x-auto rounded-xl border border-base-300 bg-base-300/30"
      style="cursor: grab;"
      @wheel.passive="false"
      @wheel="onWheel"
      @scroll="onScroll"
    >
      <svg :width="totalWidth" :height="svgHeight" xmlns="http://www.w3.org/2000/svg">
        <!-- Month grid lines -->
        <g>
          <line
            v-for="tick in ticks.filter(t => t.major)" :key="'g'+tick.x"
            :x1="tick.x" :x2="tick.x" y1="0" :y2="svgHeight"
            stroke="rgba(255,255,255,0.05)" stroke-width="1"
          />
          <line
            v-for="tick in ticks.filter(t => !t.major)" :key="'gm'+tick.x"
            :x1="tick.x" :x2="tick.x" y1="0" :y2="svgHeight"
            stroke="rgba(255,255,255,0.025)" stroke-width="1"
          />
        </g>

        <!-- Tick labels -->
        <g>
          <text
            v-for="tick in ticks.filter(t => t.major)" :key="'l'+tick.x"
            :x="tick.x + 5" :y="HEADER_H - 8"
            font-size="11" fill="rgba(255,255,255,0.35)" font-family="sans-serif"
          >{{ tick.label }}</text>
          <text
            v-for="tick in ticks.filter(t => !t.major && pxPerDay >= 24)" :key="'lm'+tick.x"
            :x="tick.x + 3" :y="HEADER_H - 8"
            font-size="9" fill="rgba(255,255,255,0.2)" font-family="sans-serif"
          >{{ tick.label }}</text>
        </g>

        <!-- Header separator -->
        <line :x1="0" :x2="totalWidth" :y1="HEADER_H" :y2="HEADER_H" stroke="rgba(255,255,255,0.07)" stroke-width="1" />

        <!-- Today line -->
        <line
          :x1="todayX" :x2="todayX" :y1="0" :y2="svgHeight"
          stroke="oklch(var(--p,0.65 0.25 264))" stroke-width="1.5" stroke-dasharray="5,4" opacity="0.6"
        />
        <rect :x="todayX - 18" y="2" width="36" height="16" rx="8" fill="oklch(var(--p,0.65 0.25 264))" opacity="0.15" />
        <text :x="todayX" y="14" text-anchor="middle" font-size="9" fill="oklch(var(--p,0.65 0.25 264))" opacity="0.8" font-family="sans-serif">TODAY</text>

        <!-- Row alternating bg -->
        <rect
          v-for="r in numRows" :key="'row'+r"
          x="0" :y="HEADER_H + (r-1) * (ROW_HEIGHT + ROW_PAD)"
          :width="totalWidth" :height="ROW_HEIGHT + ROW_PAD"
          :fill="r % 2 === 0 ? 'rgba(255,255,255,0.015)' : 'transparent'"
        />

        <!-- Books -->
        <g v-for="book in layoutRows" :key="book.userBookId">
          <g :transform="`translate(0, ${HEADER_H + book.row * (ROW_HEIGHT + ROW_PAD) + ROW_PAD / 2})`">
            <!-- Book title -->
            <text
              :x="dateToX(book.startDate) + COVER_W / 2 + 4"
              y="12"
              font-size="10"
              :fill="barColor(book.status)"
              font-family="sans-serif"
              font-weight="600"
              class="cursor-pointer"
              @click="router.push({ name: 'book-detail', params: { bookId: book.userBookId } })"
              @mouseenter="showTip($event, book.title)"
              @mouseleave="hideTip"
            >{{ book.title.length > 30 ? book.title.slice(0,30) + '…' : book.title }}</text>

            <!-- Bar track (background) -->
            <rect
              :x="dateToX(book.startDate)"
              y="22"
              :width="Math.max(4, dateToX(book.finishDate || today.format('YYYY-MM-DD')) - dateToX(book.startDate))"
              height="6"
              rx="3"
              fill="rgba(255,255,255,0.08)"
            />

            <!-- Bar -->
            <rect
              :x="dateToX(book.startDate)"
              y="22"
              :width="Math.max(4, dateToX(book.finishDate || today.format('YYYY-MM-DD')) - dateToX(book.startDate))"
              height="6"
              rx="3"
              :fill="barColor(book.status)"
              opacity="0.85"
              :style="{ filter: barGlow(book.status) }"
              class="cursor-pointer"
              @mouseenter="showTip($event, book.title + ' · ' + book.startDate + (book.finishDate ? ' → ' + book.finishDate : ' → ongoing'))"
              @mouseleave="hideTip"
              @click="router.push({ name: 'book-detail', params: { bookId: book.userBookId } })"
            />

            <!-- Progress dots -->
            <circle
              v-for="day in book.days"
              :key="day.date"
              :cx="dateToX(day.date)"
              cy="25"
              :r="dotR(day.pagesRead, book)"
              fill="white"
              opacity="0.6"
              class="cursor-pointer"
              @mouseenter="showTip($event, dayjs(day.date).format('D MMM YYYY') + ' · ' + day.pagesRead + ' pages')"
              @mouseleave="hideTip"
            />

            <!-- Finish check -->
            <text
              v-if="book.finishDate"
              :x="dateToX(book.finishDate) + 5"
              y="26"
              font-size="11"
              fill="#34d399"
              opacity="0.9"
            >✓</text>

            <!-- Cover -->
            <image
              v-if="book.image"
              :href="'/files/' + book.image"
              :x="dateToX(book.startDate) - COVER_W / 2"
              y="32"
              :width="COVER_W"
              :height="COVER_H"
              preserveAspectRatio="xMidYMid slice"
              style="clip-path: inset(0 round 6px)"
              class="cursor-pointer"
              @click="router.push({ name: 'book-detail', params: { bookId: book.userBookId } })"
              @mouseenter="showTip($event, book.title)"
              @mouseleave="hideTip"
            />
            <rect
              v-if="book.image"
              :x="dateToX(book.startDate) - COVER_W / 2"
              y="32"
              :width="COVER_W"
              :height="COVER_H"
              rx="6" ry="6"
              fill="transparent"
              stroke="rgba(255,255,255,0.1)"
              stroke-width="1"
            />
            <!-- Placeholder if no image -->
            <rect
              v-else
              :x="dateToX(book.startDate) - COVER_W / 2"
              y="32"
              :width="COVER_W"
              :height="COVER_H"
              rx="6"
              fill="rgba(255,255,255,0.08)"
            />
          </g>
        </g>
      </svg>
    </div>

    <!-- Legend -->
    <div class="flex gap-4 mt-2 flex-wrap text-xs opacity-50">
      <div class="flex items-center gap-1"><div class="w-2 h-2 rounded-full" style="background:#34d399" /> Finished</div>
      <div class="flex items-center gap-1"><div class="w-2 h-2 rounded-full" style="background:#38bdf8" /> Reading</div>
      <div class="flex items-center gap-1"><div class="w-2 h-2 rounded-full" style="background:#fbbf24" /> Paused</div>
      <div class="flex items-center gap-1"><div class="w-2 h-2 rounded-full" style="background:#f87171" /> Dropped</div>
      <div class="flex items-center gap-1"><div class="w-1.5 h-1.5 rounded-full bg-white" /> Day logged (dot size = pages)</div>
    </div>

    <!-- Tooltip -->
    <div
      v-if="tooltip"
      class="fixed z-50 bg-base-300 border border-base-content/20 text-base-content text-xs px-3 py-1.5 rounded-lg shadow-xl pointer-events-none"
      :style="{ left: tooltip.x + 14 + 'px', top: tooltip.y - 10 + 'px' }"
    >{{ tooltip.text }}</div>
  </div>
</template>
