<script setup lang="ts">
import { ref, computed } from 'vue'
import dataService from '../services/DataService'
import useTypography from '../composables/typography'

const { typographyClasses } = useTypography()

const props = defineProps<{
  existing?: any | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved', goal: any): void
}>()

const periodType = ref<'ANNUAL' | 'MONTHLY'>(props.existing?.periodType ?? 'ANNUAL')
const targetBooks = ref<number>(props.existing?.targetBooks ?? 24)
const startDate = ref<string>(props.existing?.startDate ?? new Date().toISOString().slice(0, 10))
const annualMode = ref<'fromStart' | 'tillYearEnd'>('fromStart')
const repeating = ref<boolean>(props.existing?.repeating ?? false)
const saving = ref(false)

const presets = computed(() => periodType.value === 'ANNUAL' ? [12, 24, 50, 100] : [2, 4, 6, 8])

const computedEnd = computed(() => {
  const start = new Date(startDate.value)
  if (periodType.value === 'MONTHLY') {
    const e = new Date(start); e.setMonth(e.getMonth() + 1); e.setDate(e.getDate() - 1)
    return e.toISOString().slice(0, 10)
  }
  if (annualMode.value === 'tillYearEnd') {
    return `${start.getFullYear()}-12-31`
  }
  const e = new Date(start); e.setFullYear(e.getFullYear() + 1); e.setDate(e.getDate() - 1)
  return e.toISOString().slice(0, 10)
})

const pacePreview = computed(() => {
  const start = new Date(startDate.value)
  const end = new Date(computedEnd.value)
  const days = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / 86400000) + 1)
  if (periodType.value === 'MONTHLY') {
    return `That's about ${(targetBooks.value).toFixed(0)} books this month · ~${Math.ceil(days / Math.max(1, targetBooks.value))} days per book`
  }
  const perMonth = (targetBooks.value / (days / 30.4)).toFixed(1)
  return `That's about ${perMonth} books/month · ~${Math.ceil(days / Math.max(1, targetBooks.value))} days per book`
})

async function save() {
  if (targetBooks.value <= 0) return
  saving.value = true
  try {
    const payload: any = {
      targetBooks: targetBooks.value,
      periodType: periodType.value,
      startDate: startDate.value,
      endDate: computedEnd.value,
      repeating: repeating.value,
    }
    const goal = await dataService.setReadingGoal2(payload)
    emit('saved', goal)
    emit('close')
  } catch (e) {
    alert('Could not save goal')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="p-2 max-w-md w-full">
    <div class="text-center mb-5">
      <div class="text-4xl mb-2">🎯</div>
      <h1 class="text-2xl font-bold" :class="typographyClasses">Set a Reading Goal</h1>
      <p class="text-sm opacity-60">Challenge yourself and track your progress</p>
    </div>

    <!-- Period type toggle -->
    <div class="mb-4">
      <label class="text-xs uppercase tracking-wide opacity-60 mb-1 block">Period</label>
      <div class="join w-full">
        <button class="btn join-item flex-1" :class="periodType === 'ANNUAL' ? 'btn-primary' : 'btn-outline'" @click="periodType = 'ANNUAL'">Annual</button>
        <button class="btn join-item flex-1" :class="periodType === 'MONTHLY' ? 'btn-primary' : 'btn-outline'" @click="periodType = 'MONTHLY'">Monthly</button>
      </div>
    </div>

    <!-- Number of books -->
    <div class="mb-4">
      <label class="text-xs uppercase tracking-wide opacity-60 mb-1 block">How many books?</label>
      <div class="flex gap-2 items-center">
        <input v-model.number="targetBooks" type="number" min="1" class="input input-bordered w-24 focus:input-accent" />
        <div class="flex gap-1 flex-wrap">
          <button v-for="p in presets" :key="p" class="btn btn-xs" :class="targetBooks === p ? 'btn-primary' : 'btn-ghost'" @click="targetBooks = p">{{ p }}</button>
        </div>
      </div>
    </div>

    <!-- Start date -->
    <div class="mb-4">
      <label class="text-xs uppercase tracking-wide opacity-60 mb-1 block">Start date</label>
      <input v-model="startDate" type="date" class="input input-bordered w-full focus:input-accent" />
    </div>

    <!-- Annual mode -->
    <div v-if="periodType === 'ANNUAL'" class="mb-4">
      <label class="text-xs uppercase tracking-wide opacity-60 mb-1 block">Duration</label>
      <div class="join w-full">
        <button class="btn join-item flex-1 btn-sm" :class="annualMode === 'fromStart' ? 'btn-secondary' : 'btn-outline'" @click="annualMode = 'fromStart'">Full year from start</button>
        <button class="btn join-item flex-1 btn-sm" :class="annualMode === 'tillYearEnd' ? 'btn-secondary' : 'btn-outline'" @click="annualMode = 'tillYearEnd'">Until end of year</button>
      </div>
    </div>

    <!-- Repeat -->
    <div class="mb-4">
      <label class="flex items-center gap-2 cursor-pointer">
        <input v-model="repeating" type="checkbox" class="checkbox checkbox-primary checkbox-sm" />
        <span class="text-sm">Repeat every {{ periodType === 'ANNUAL' ? 'year' : 'month' }}</span>
      </label>
    </div>

    <!-- Preview -->
    <div class="bg-base-200 rounded-lg p-3 mb-5 text-center">
      <p class="text-xs opacity-60">{{ startDate }} → {{ computedEnd }}</p>
      <p class="text-sm font-medium mt-1">{{ pacePreview }}</p>
    </div>

    <div class="flex gap-2">
      <button class="btn btn-ghost flex-1" @click="emit('close')">Cancel</button>
      <button class="btn btn-primary flex-1" :class="{ 'loading': saving }" :disabled="saving || targetBooks <= 0" @click="save">Set goal</button>
    </div>
  </div>
</template>
