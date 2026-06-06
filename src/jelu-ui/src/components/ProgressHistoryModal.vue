<script setup lang="ts">
import { Ref, ref, computed, onMounted } from "vue";
import { useI18n } from 'vue-i18n';
import dataService from "../services/DataService";

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })

const props = defineProps<{
  userBookId: string,
  pageCount: number | null,
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

interface Row { pageNumber: number, dateStr: string, isoTime: string }

const rows: Ref<Array<Row>> = ref([])
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')

const pad = (n: number) => String(n).padStart(2, '0')
// Date portion (YYYY-MM-DD) for the <input type="date">
const toDateStr = (iso: string): string => {
  const d = new Date(iso)
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`
}
// Time portion (HH:mm:ss) preserved separately so same-day ordering survives
const toTimeStr = (iso: string): string => {
  const d = new Date(iso)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
// Combine a date string + preserved time back into an ISO string
const toIso = (dateStr: string, timeStr: string): string =>
  new Date(`${dateStr}T${timeStr}`).toISOString()

const load = async () => {
  loading.value = true
  try {
    const hist = await dataService.getProgressHistory(props.userBookId)
    rows.value = hist
      .filter(h => h.pageNumber != null)
      .map(h => ({ pageNumber: h.pageNumber as number, dateStr: toDateStr(h.recordedAt), isoTime: toTimeStr(h.recordedAt) }))
  } catch (e) {
    errorMessage.value = 'Failed to load history'
  } finally {
    loading.value = false
  }
}
onMounted(load)

// Sorted-by-date view with computed deltas (read-only preview)
const sortedRows = computed(() =>
  [...rows.value].sort((a, b) =>
    new Date(`${a.dateStr}T${a.isoTime}`).getTime() - new Date(`${b.dateStr}T${b.isoTime}`).getTime())
)
const deltaFor = (idx: number): number => {
  const s = sortedRows.value
  if (idx === 0) return 0
  return s[idx].pageNumber - s[idx-1].pageNumber
}

const addRow = () => {
  const now = new Date()
  const dateStr = `${now.getFullYear()}-${pad(now.getMonth()+1)}-${pad(now.getDate())}`
  const isoTime = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  const lastPage = rows.value.length ? rows.value[rows.value.length-1].pageNumber : 0
  rows.value.push({ pageNumber: lastPage, dateStr, isoTime })
}
const removeRow = (row: Row) => {
  rows.value = rows.value.filter(r => r !== row)
}

const save = async () => {
  saving.value = true
  errorMessage.value = ''
  try {
    const entries = sortedRows.value.map(r => ({ pageNumber: Number(r.pageNumber), recordedAt: toIso(r.dateStr, r.isoTime) }))
    await dataService.saveProgressHistory(props.userBookId, entries)
    emit('close')
  } catch (e) {
    errorMessage.value = 'Failed to save'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="modal-card bg-base-100 rounded-xl p-5 w-full max-w-2xl">
    <h2 class="text-xl font-bold mb-4 flex items-center gap-2">
      <i class="mdi mdi-chart-line text-emerald-300" />
      Reading progress history
    </h2>

    <div v-if="loading" class="py-8 text-center opacity-60">Loading…</div>

    <div v-else>
      <div v-if="sortedRows.length === 0" class="py-6 text-center opacity-60">
        No entries yet. Add one below.
      </div>

      <div v-else class="space-y-2">
        <div class="grid grid-cols-[1fr_auto_auto_auto] gap-2 items-center text-xs opacity-60 px-1">
          <span>Date</span>
          <span class="w-20 text-center">Page</span>
          <span class="w-16 text-center">Δ pages</span>
          <span class="w-8"></span>
        </div>
        <div
          v-for="(row, idx) in sortedRows"
          :key="idx"
          class="grid grid-cols-[1fr_auto_auto_auto] gap-2 items-center"
        >
          <input
            v-model="row.dateStr"
            type="date"
            class="input input-bordered input-sm w-full"
          >
          <input
            v-model.number="row.pageNumber"
            type="number"
            min="0"
            class="input input-bordered input-sm w-20 text-center"
          >
          <span
            class="w-16 text-center text-sm font-medium"
            :class="deltaFor(idx) > 0 ? 'text-emerald-300' : (deltaFor(idx) < 0 ? 'text-rose-300' : 'opacity-50')"
          >{{ deltaFor(idx) >= 0 ? '+' : '' }}{{ deltaFor(idx) }}</span>
          <button
            class="btn btn-ghost btn-sm btn-circle text-error"
            @click="removeRow(row)"
          ><i class="mdi mdi-close" /></button>
        </div>
      </div>

      <button class="btn btn-sm btn-outline mt-3 gap-1" @click="addRow">
        <i class="mdi mdi-plus" /> Add entry
      </button>

      <p v-if="pageCount" class="text-xs opacity-50 mt-2">Book has {{ pageCount }} pages.</p>
      <p v-if="errorMessage" class="text-error text-sm mt-2">{{ errorMessage }}</p>

      <div class="flex justify-end gap-2 mt-5">
        <button class="btn btn-ghost btn-sm" @click="emit('close')">Cancel</button>
        <button class="btn btn-primary btn-sm" :class="saving ? 'loading' : ''" @click="save">Save</button>
      </div>
    </div>
  </div>
</template>
