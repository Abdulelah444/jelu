<script setup lang="ts">
import { computed, onMounted, Ref, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import dayjs from 'dayjs'
import { Bar, Doughnut } from 'vue-chartjs'
import { BarElement, CategoryScale, Chart as ChartJS, Legend, LinearScale, Title, Tooltip, ArcElement } from 'chart.js'
import { ReadingEventType } from '../model/ReadingEvent'
import { UserBook } from '../model/Book'
import { MonthStats, TotalsStats, YearStats } from '../model/YearStats'
import dataService from '../services/DataService'
import useTypography from '../composables/typography'

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })
ChartJS.register(Title, Tooltip, Legend, BarElement, CategoryScale, LinearScale, ArcElement)

const { typographyClasses } = useTypography()
const loading = ref(true)

// Data refs
const totals: Ref<TotalsStats | null> = ref(null)
const currentYearMonths: Ref<Array<MonthStats>> = ref([])
const lastYearMonths: Ref<Array<MonthStats>> = ref([])
const yearStats: Ref<Array<YearStats>> = ref([])
const currentlyReading: Ref<Array<UserBook>> = ref([])
const recentFinished: Ref<Array<any>> = ref([])
const allFinishedEvents: Ref<Array<any>> = ref([])

const now = dayjs()
const currentYear = now.year()
const currentMonth = now.month() + 1

// ═══ LOAD DATA ═══
onMounted(async () => {
  try {
    const [t, ys, cym, lym, cr, finished] = await Promise.all([
      dataService.totalsStats(),
      dataService.yearStats(),
      dataService.monthStatsForYear(currentYear),
      dataService.monthStatsForYear(currentYear - 1).catch(() => []),
      dataService.findUserBookByCriteria([ReadingEventType.CURRENTLY_READING], null, null, null, null, null, 0, 20),
      dataService.findReadingEvents([ReadingEventType.FINISHED], null, null, null, null, null, null, 0, 500, 'modificationDate,desc'),
    ])
    totals.value = t
    yearStats.value = ys
    currentYearMonths.value = cym
    lastYearMonths.value = lym
    currentlyReading.value = cr.content
    allFinishedEvents.value = finished.content
    recentFinished.value = finished.content.slice(0, 5)
  } catch (e) {
    console.log("Failed to load dashboard data: " + e)
  }
  loading.value = false
})

// ═══ READING ACTIVITY METRICS ═══
const booksThisMonth = computed(() => {
  const m = currentYearMonths.value.find(s => s.month === currentMonth)
  return m?.finished ?? 0
})

const booksLastMonth = computed(() => {
  const lastMonth = currentMonth === 1 ? 12 : currentMonth - 1
  const source = currentMonth === 1 ? lastYearMonths.value : currentYearMonths.value
  const m = source.find(s => s.month === lastMonth)
  return m?.finished ?? 0
})

const booksThisYear = computed(() => {
  return currentYearMonths.value.reduce((sum, m) => sum + m.finished, 0)
})

const booksLastYear = computed(() => {
  const ly = yearStats.value.find(y => y.year === currentYear - 1)
  return ly?.finished ?? 0
})

const pagesThisMonth = computed(() => {
  const m = currentYearMonths.value.find(s => s.month === currentMonth)
  return m?.pageCount ?? 0
})

const pagesThisYear = computed(() => {
  return currentYearMonths.value.reduce((sum, m) => sum + m.pageCount, 0)
})

// Weekly pace: pages from finished books in last 7 days + estimate from currently reading
const weeklyPace = computed(() => {
  const sevenDaysAgo = now.subtract(7, 'day')
  let pages = 0
  for (const ev of allFinishedEvents.value) {
    const evDate = dayjs(ev.modificationDate || ev.creationDate)
    if (evDate.isAfter(sevenDaysAgo)) {
      pages += ev.userBook?.book?.pageCount ?? 0
    }
  }
  return Math.round(pages / 7)
})

const avgBooksPerMonth = computed(() => {
  if (currentYearMonths.value.length === 0) return 0
  const monthsElapsed = Math.max(currentMonth, 1)
  return (booksThisYear.value / monthsElapsed).toFixed(1)
})

// ═══ STREAKS ═══
const streakData = computed(() => {
  if (allFinishedEvents.value.length === 0) return { current: 0, best: 0 }
  
  // Build a set of year-month strings that have at least one finished book
  const monthsWithFinish = new Set<string>()
  for (const ev of allFinishedEvents.value) {
    const d = dayjs(ev.modificationDate || ev.creationDate)
    monthsWithFinish.add(d.format('YYYY-MM'))
  }
  
  // Walk backwards from current month to find current streak
  let current = 0
  let check = now.startOf('month')
  while (monthsWithFinish.has(check.format('YYYY-MM'))) {
    current++
    check = check.subtract(1, 'month')
  }
  
  // Find best streak by sorting all months and finding longest consecutive run
  const sorted = Array.from(monthsWithFinish).sort()
  let best = 1
  let run = 1
  for (let i = 1; i < sorted.length; i++) {
    const prev = dayjs(sorted[i - 1] + '-01')
    const curr = dayjs(sorted[i] + '-01')
    if (curr.diff(prev, 'month') === 1) {
      run++
      best = Math.max(best, run)
    } else {
      run = 1
    }
  }
  if (sorted.length === 1) best = 1
  if (sorted.length === 0) best = 0
  
  return { current, best }
})

// ═══ CURRENTLY READING INSIGHTS ═══
const readingInsights = computed(() => {
  return currentlyReading.value.map(ub => {
    const pageCount = ub.book.pageCount ?? 0
    const currentPage = ub.currentPageNumber ?? 0
    const pagesRemaining = Math.max(0, pageCount - currentPage)
    const percent = ub.percentRead ?? 0
    
    // Estimate pace from start date
    let pagesPerDay = 0
    let etaDays = 0
    let etaDate = ''
    
    // Use modificationDate as proxy for when reading started/last updated
    const startDate = ub.creationDate ? dayjs(ub.creationDate) : null
    if (startDate && currentPage > 0) {
      const daysReading = Math.max(1, now.diff(startDate, 'day'))
      pagesPerDay = currentPage / daysReading
      if (pagesPerDay > 0 && pagesRemaining > 0) {
        etaDays = Math.ceil(pagesRemaining / pagesPerDay)
        etaDate = now.add(etaDays, 'day').format('MMM D, YYYY')
      }
    }
    
    return {
      id: ub.id,
      bookId: ub.book.id,
      title: ub.book.title,
      image: ub.book.image,
      pageCount,
      currentPage,
      pagesRemaining,
      percent: Math.round(percent),
      pagesPerDay: Math.round(pagesPerDay * 10) / 10,
      etaDays,
      etaDate,
    }
  })
})

// ═══ LIBRARY COMPOSITION ═══
const readUnreadChart = computed(() => {
  if (!totals.value) return null
  return {
    labels: ['Read', 'Unread', 'Dropped'],
    datasets: [{
      data: [totals.value.read, totals.value.unread, totals.value.dropped],
      backgroundColor: ['#36d399', '#3abff8', '#f87272'],
      borderWidth: 0,
    }]
  }
})

// ═══ MONTHLY TREND CHART ═══
const monthlyChart = computed(() => {
  const labels = currentYearMonths.value.map(m => {
    return dayjs().month(m.month - 1).format('MMM')
  })
  return {
    labels,
    datasets: [
      {
        label: 'Books finished',
        data: currentYearMonths.value.map(m => m.finished),
        backgroundColor: '#36d399',
      },
      {
        label: 'Pages read (x10)',
        data: currentYearMonths.value.map(m => Math.round(m.pageCount / 10)),
        backgroundColor: '#3abff8',
      },
    ]
  }
})

const chartOptions = {
  responsive: true,
  plugins: {
    legend: { labels: { color: '#a1a1aa' } },
  },
  scales: {
    x: { ticks: { color: '#a1a1aa' }, grid: { color: 'rgba(161,161,170,0.1)' } },
    y: { ticks: { color: '#a1a1aa' }, grid: { color: 'rgba(161,161,170,0.1)' } },
  }
}

const doughnutOptions = {
  responsive: true,
  plugins: {
    legend: { position: 'bottom' as const, labels: { color: '#a1a1aa', padding: 15 } },
  },
  cutout: '65%',
}

const changeIndicator = (current: number, previous: number) => {
  if (previous === 0 && current > 0) return '\u2191'
  if (current > previous) return '\u2191'
  if (current < previous) return '\u2193'
  return '='
}
</script>

<template>
  <div class="p-4 sm:p-6 max-w-6xl mx-auto">
    <h1 class="text-2xl mb-6" :class="typographyClasses">Reading Dashboard</h1>
    
    <div v-if="loading" class="flex justify-center py-20">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <div v-else>
      <!-- ═══ STAT CARDS ═══ -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
        <!-- Books this month -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <p class="text-xs opacity-60 uppercase">This Month</p>
            <p class="text-3xl font-bold">{{ booksThisMonth }}</p>
            <p class="text-xs opacity-60">
              books finished
              <span :class="booksThisMonth >= booksLastMonth ? 'text-success' : 'text-error'">
                {{ changeIndicator(booksThisMonth, booksLastMonth) }} vs {{ booksLastMonth }} last month
              </span>
            </p>
          </div>
        </div>
        <!-- Books this year -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <p class="text-xs opacity-60 uppercase">This Year</p>
            <p class="text-3xl font-bold">{{ booksThisYear }}</p>
            <p class="text-xs opacity-60">
              books finished
              <span :class="booksThisYear >= booksLastYear ? 'text-success' : 'text-error'">
                {{ changeIndicator(booksThisYear, booksLastYear) }} vs {{ booksLastYear }} last year
              </span>
            </p>
          </div>
        </div>
        <!-- Pages this month -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <p class="text-xs opacity-60 uppercase">Pages This Month</p>
            <p class="text-3xl font-bold">{{ pagesThisMonth.toLocaleString() }}</p>
            <p class="text-xs opacity-60">{{ weeklyPace }} pages/day this week</p>
          </div>
        </div>
        <!-- Streak -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <p class="text-xs opacity-60 uppercase">Reading Streak</p>
            <p class="text-3xl font-bold">{{ streakData.current }} <span class="text-sm font-normal opacity-60">months</span></p>
            <p class="text-xs opacity-60">
              Best: {{ streakData.best }} months
              <span v-if="streakData.current >= streakData.best && streakData.current > 0" class="text-warning">\u2B50 New record!</span>
            </p>
          </div>
        </div>
      </div>

      <!-- ═══ AVG + TOTAL ═══ -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mb-6">
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <p class="text-xs opacity-60 uppercase">Avg Books/Month</p>
            <p class="text-2xl font-bold">{{ avgBooksPerMonth }}</p>
          </div>
        </div>
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <p class="text-xs opacity-60 uppercase">Pages This Year</p>
            <p class="text-2xl font-bold">{{ pagesThisYear.toLocaleString() }}</p>
          </div>
        </div>
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <p class="text-xs opacity-60 uppercase">Total Library</p>
            <p class="text-2xl font-bold">{{ totals?.total ?? 0 }}</p>
          </div>
        </div>
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <p class="text-xs opacity-60 uppercase">Completion Rate</p>
            <p class="text-2xl font-bold">{{ totals ? Math.round((totals.read / Math.max(totals.total, 1)) * 100) : 0 }}%</p>
          </div>
        </div>
      </div>

      <!-- ═══ CURRENTLY READING ═══ -->
      <div v-if="readingInsights.length > 0" class="mb-6">
        <h2 class="text-lg font-bold mb-3" :class="typographyClasses">Currently Reading</h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div v-for="book in readingInsights" :key="book.id" class="card bg-base-200 border border-base-300">
            <div class="card-body p-4">
              <div class="flex gap-3">
                <router-link :to="{ name: 'book-detail', params: { bookId: book.id } }">
                  <img v-if="book.image" :src="'/files/' + book.image" class="w-12 h-18 object-cover rounded" />
                  <div v-else class="w-12 h-18 bg-base-300 rounded flex items-center justify-center">
                    <i class="mdi mdi-book mdi-24px opacity-30" />
                  </div>
                </router-link>
                <div class="flex-1 min-w-0">
                  <router-link :to="{ name: 'book-detail', params: { bookId: book.id } }" class="font-bold text-sm line-clamp-1 link link-hover">
                    {{ book.title }}
                  </router-link>
                  <progress class="progress progress-primary w-full mt-1" :value="book.percent" max="100" />
                  <div class="flex justify-between text-xs opacity-60 mt-1">
                    <span>{{ book.percent }}%</span>
                    <span v-if="book.pageCount">Page {{ book.currentPage }} / {{ book.pageCount }}</span>
                  </div>
                  <div v-if="book.pagesPerDay > 0" class="mt-2 text-xs">
                    <span class="opacity-60">Pace:</span> {{ book.pagesPerDay }} pages/day
                    <span v-if="book.etaDate" class="opacity-60 ml-2">
                      &middot; ETA: <span class="font-semibold text-primary">{{ book.etaDate }}</span>
                      <span class="opacity-40">({{ book.etaDays }}d)</span>
                    </span>
                  </div>
                  <div v-if="book.pagesRemaining > 0" class="text-xs opacity-50 mt-1">
                    {{ book.pagesRemaining }} pages remaining
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ═══ CHARTS ═══ -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
        <!-- Monthly trend -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4">
            <h3 class="font-bold mb-3">{{ currentYear }} Monthly Progress</h3>
            <Bar v-if="monthlyChart.labels.length > 0" :data="monthlyChart" :options="chartOptions" />
            <p v-else class="text-sm opacity-50">No data yet</p>
          </div>
        </div>
        <!-- Read/Unread donut -->
        <div class="card bg-base-200 border border-base-300">
          <div class="card-body p-4 flex flex-col items-center">
            <h3 class="font-bold mb-3 self-start">Library Composition</h3>
            <div class="w-56">
              <Doughnut v-if="readUnreadChart" :data="readUnreadChart" :options="doughnutOptions" />
            </div>
          </div>
        </div>
      </div>

      <!-- ═══ RECENTLY FINISHED ═══ -->
      <div v-if="recentFinished.length > 0" class="mb-6">
        <h2 class="text-lg font-bold mb-3" :class="typographyClasses">Recently Finished</h2>
        <div class="flex gap-3 overflow-x-auto pb-2">
          <div v-for="ev in recentFinished" :key="ev.id" class="flex-shrink-0">
            <router-link :to="{ name: 'book-detail', params: { bookId: ev.userBook?.id } }">
              <img v-if="ev.userBook?.book?.image" :src="'/files/' + ev.userBook.book.image" 
                   class="w-20 h-28 object-cover rounded hover:ring-2 hover:ring-primary transition-all" 
                   :title="ev.userBook.book.title" />
              <div v-else class="w-20 h-28 bg-base-300 rounded flex items-center justify-center">
                <span class="text-xs text-center px-1 opacity-50">{{ ev.userBook?.book?.title }}</span>
              </div>
            </router-link>
            <p class="text-xs opacity-50 mt-1 w-20 truncate">{{ dayjs(ev.modificationDate || ev.creationDate).format('MMM D') }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
