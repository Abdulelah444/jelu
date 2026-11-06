<script setup lang="ts">
import { nextTick, Ref, ref, watch } from "vue";
import { useI18n } from 'vue-i18n';
import { UserBookUpdate } from "../model/Book";
import { CreateReadingEvent, ReadingEventType } from "../model/ReadingEvent";
import dataService from "../services/DataService";
import useTypography from "../composables/typography";

const { t } = useI18n({
  inheritLocale: true,
  useScope: 'global'
})

const props = defineProps<{
  userBookId: string,
  bookId: string,
  pageCount: number | null,
  currentProgress: number | null,
  currentPage: number | null,
}>()

// ── Reactive state ──────────────────────────────────────────────────
// Single ref, initialized from props → persistence works on reopen
const userBookUpdate: Ref<UserBookUpdate> = ref({
  id: props.userBookId,
  percentRead: props.currentProgress ?? 0,
  currentPageNumber: props.currentPage ?? 0,
})

const localPageCount: Ref<number | null> = ref(props.pageCount)
const newPageCount: Ref<number | null> = ref(null)
const progress: Ref<boolean> = ref(false)
const errorMessage: Ref<string> = ref('')

const emit = defineEmits<{
  (e: 'close'): void
}>()

// ── Bidirectional sync (THE CRASH FIX) ──────────────────────────────
// The old code had ONE watcher on [currentPageNumber, percentRead] that
// called computePages. computePages mutated percentRead, which re-triggered
// the watcher → infinite loop → browser freeze.
//
// Fix: two separate watchers with a syncing guard.
// When watcher A sets the other field, watcher B sees syncing=true and skips.
let syncing = false

// Percent slider changed → update page number
watch(() => userBookUpdate.value.percentRead, (newPercent) => {
  if (syncing) return
  if (localPageCount.value != null && newPercent != null) {
    syncing = true
    userBookUpdate.value.currentPageNumber =
      Math.round((newPercent / 100) * localPageCount.value)
    nextTick(() => { syncing = false })
  }
})

// Page number changed → update percentage
watch(() => userBookUpdate.value.currentPageNumber, (newPage) => {
  if (syncing) return
  if (localPageCount.value != null && newPage != null) {
    syncing = true
    const clamped = Math.min(Math.max(newPage, 0), localPageCount.value)
    userBookUpdate.value.percentRead =
      Math.round((clamped / localPageCount.value) * 100)
    nextTick(() => { syncing = false })
  }
})

// ── Save page count to Book entity ──────────────────────────────────
// IMPORTANT: PUT /api/v1/books/{id} replaces the whole object.
// We must fetch the full book first, set pageCount, then PUT it back.
const setPageCount = async () => {
  if (!newPageCount.value || newPageCount.value < 1) return
  errorMessage.value = ''
  try {
    const userBook = await dataService.getUserBookById(props.userBookId)
    const fullBook = userBook.book
    fullBook.pageCount = newPageCount.value
    await dataService.updateBook(fullBook.id!, fullBook)
    localPageCount.value = newPageCount.value
    newPageCount.value = null
  } catch (e) {
    console.error("Failed to save page count", e)
    errorMessage.value = 'Failed to save page count'
  }
}

// ── Submit progress ─────────────────────────────────────────────────
const update = async () => {
  progress.value = true
  errorMessage.value = ''
  try {
    // Clamp values
    if (localPageCount.value != null && userBookUpdate.value.currentPageNumber != null) {
      userBookUpdate.value.currentPageNumber =
        Math.min(Math.max(userBookUpdate.value.currentPageNumber, 0), localPageCount.value)
    }
    userBookUpdate.value.percentRead =
      Math.round(Math.min(100, Math.max(0, userBookUpdate.value.percentRead ?? 0)))

    // 1. Record a CURRENTLY_READING event FIRST
    //    (backend resets progress to 0 on CURRENTLY_READING events,
    //     so we must create the event before setting our actual progress)
    const readingEvent: CreateReadingEvent = {
      eventType: ReadingEventType.CURRENTLY_READING,
      eventDate: undefined,
      startDate: new Date(),
      bookId: props.bookId,
    }
    await dataService.createReadingEvent(readingEvent)

    // 2. Now update the UserBook with actual progress (overwrites the reset)
    await dataService.updateUserBook(userBookUpdate.value)

    progress.value = false
    emit('close')
  } catch (e) {
    console.error("Failed to update progress", e)
    errorMessage.value = 'Failed to save progress'
    progress.value = false
  }
}

const displayPercent = () => {
  return Math.round(userBookUpdate.value.percentRead ?? 0)
}

const { typographyClasses } = useTypography()
</script>

<template>
  <section class="event-modal">
    <div>
      <div>
        <div>
          <h1
            class="text-2xl first-letter:capitalize"
            :class="typographyClasses"
          >
            {{ t('labels.set_progress') }}
          </h1>
        </div>
      </div>
      <div class="flex flex-col">
        <!-- Percentage slider + numeric display -->
        <div class="field">
          <label class="label">
            <span class="label-text font-semibold first-letter:capitalize">{{ t('book.percent_read') }} : </span>
          </label>
          <div class="flex items-center gap-2">
            <input
              v-model.number="userBookUpdate.percentRead"
              type="range"
              min="0"
              max="100"
              step="1"
              class="range range-xs range-primary grow"
            >
            <span class="text-sm font-mono w-10 text-right">{{ displayPercent() }}%</span>
          </div>
        </div>

        <!-- Set total page count (only if book doesn't have one yet) -->
        <div
          v-if="localPageCount == null"
          class="field"
        >
          <label class="label">
            <span class="label-text font-semibold first-letter:capitalize">{{ t('book.page_count') || 'Total pages' }} : </span>
          </label>
          <div class="flex items-center gap-2">
            <input
              v-model.number="newPageCount"
              type="number"
              min="1"
              placeholder="e.g. 350"
              class="input focus:input-accent grow"
            >
            <button
              class="btn btn-sm btn-primary"
              :disabled="!newPageCount || newPageCount < 1"
              @click="setPageCount"
            >
              {{ t('labels.save') || 'Set' }}
            </button>
          </div>
        </div>

        <!-- Current page number (visible once pageCount is known) -->
        <div
          v-if="localPageCount != null"
          class="field"
        >
          <label class="label">
            <span class="label-text font-semibold first-letter:capitalize">{{ t('book.current_page_number') }} : </span>
          </label>
          <div class="flex items-center gap-2">
            <input
              v-model.number="userBookUpdate.currentPageNumber"
              type="number"
              min="0"
              :max="localPageCount"
              class="input focus:input-accent grow"
            >
            <span class="text-sm opacity-70">/ {{ localPageCount }}</span>
          </div>
        </div>

        <!-- Error message -->
        <p
          v-if="errorMessage"
          class="text-error text-sm mt-1"
        >
          {{ errorMessage }}
        </p>

        <!-- Submit -->
        <div class="mt-3 place-self-center">
          <button
            class="btn btn-success mr-2 uppercase"
            :class="{ 'btn-disabled': progress }"
            @click="update"
          >
            <span
              v-if="progress"
              class="loading loading-spinner"
            />
            <span class="icon">
              <i class="mdi mdi-pencil mdi-18px" />
            </span>
            <span>{{ t('labels.submit') }}</span>
          </button>
        </div>
      </div>
    </div>
    <progress
      v-if="progress"
      class="animate-pulse progress progress-success mt-5"
      max="100"
    />
  </section>
</template>

<style lang="scss">
</style>
