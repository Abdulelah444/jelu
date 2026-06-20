<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import dataService from '../services/DataService'
import useTypography from '../composables/typography'

const { typographyClasses } = useTypography()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const report = ref<any>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    report.value = await dataService.getWeeklyReport()
  } catch (e) { /* ignore */ }
  loading.value = false
})

const statusClass = (s: string) => {
  if (s === 'AHEAD') return 'badge-success'
  if (s === 'ON_PACE') return 'badge-info'
  if (s === 'BEHIND') return 'badge-warning'
  return 'badge-ghost'
}
const statusLabel = (s: string) => {
  if (s === 'AHEAD') return 'Ahead this week'
  if (s === 'ON_PACE') return 'On track this week'
  if (s === 'BEHIND') return 'Behind this week'
  return '—'
}

const headline = computed(() => {
  if (!report.value) return ''
  if (report.value.perfectWeek) return 'Perfect week. 100% compliance.'
  const onTrack = report.value.books.filter((b: any) => b.onTrack).length
  const total = report.value.books.length
  if (total === 0) return 'No active reading goals this week.'
  if (onTrack === total) return 'Every book on track. Strong week.'
  if (onTrack === 0) return 'Behind across the board — next week is yours.'
  return `${onTrack} of ${total} books on track. Keep pushing.`
})

const close = () => emit('close')
</script>

<template>
  <div class="p-2 max-w-lg w-full">
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg" />
    </div>
    <div v-else-if="report">
      <!-- Header -->
      <div class="text-center mb-4">
        <div class="text-4xl mb-2">📊</div>
        <h1 class="text-2xl font-bold" :class="typographyClasses">Your Week in Reading</h1>
        <p class="text-sm opacity-60">{{ report.weekStart }} → {{ report.weekEnd }}</p>
      </div>

      <!-- Headline -->
      <div class="text-center mb-4 p-3 rounded-xl"
        :class="report.perfectWeek ? 'bg-success/20 text-success' : 'bg-base-200'">
        <p class="font-semibold">{{ headline }}</p>
      </div>

      <!-- Total + streaks -->
      <div class="grid grid-cols-3 gap-2 mb-4 text-center">
        <div class="bg-base-200 rounded-lg p-3">
          <p class="text-2xl font-bold text-violet-300">{{ report.totalPagesThisWeek }}</p>
          <p class="text-xs opacity-60">pages this week</p>
        </div>
        <div class="bg-base-200 rounded-lg p-3">
          <p class="text-2xl font-bold text-orange-400">{{ report.streak.currentDailyStreak }}</p>
          <p class="text-xs opacity-60">day streak</p>
        </div>
        <div class="bg-base-200 rounded-lg p-3">
          <p class="text-2xl font-bold text-violet-300">{{ report.streak.currentWeeklyStreak }}</p>
          <p class="text-xs opacity-60">week streak</p>
        </div>
      </div>

      <!-- Per-book breakdown -->
      <div v-if="report.books.length > 0" class="flex flex-col gap-2 mb-4">
        <p class="text-xs uppercase tracking-wide opacity-50">By book</p>
        <div v-for="b in report.books" :key="b.userBookId" class="bg-base-200 rounded-lg p-3">
          <div class="flex items-center gap-3">
            <img v-if="b.image" :src="'/files/' + b.image" class="w-10 h-14 object-cover rounded" />
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <span class="font-semibold text-sm line-clamp-1">{{ b.title }}</span>
                <span class="badge badge-xs" :class="statusClass(b.status)">{{ statusLabel(b.status) }}</span>
              </div>
              <progress class="progress progress-primary w-full mt-1" :value="Math.min(b.compliancePercent, 100)" max="100" />
              <p class="text-xs opacity-60 mt-0.5">
                {{ b.pagesThisWeek }} / {{ b.weeklyTargetPages }} pages · {{ b.compliancePercent }}%
              </p>
            </div>
          </div>
        </div>
      </div>

      <button class="btn btn-primary w-full" @click="close">Got it</button>
    </div>
    <div v-else class="text-center py-8">
      <p class="opacity-60">No report available yet.</p>
      <button class="btn btn-sm mt-3" @click="close">Close</button>
    </div>
  </div>
</template>
