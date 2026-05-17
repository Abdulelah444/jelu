<script setup lang="ts">
import { computed, Ref, ref, watch } from "vue";
import { useI18n } from 'vue-i18n';
import { UserBookUpdate } from "../model/Book";
import dataService from "../services/DataService";
import { ObjectUtils } from "../utils/ObjectUtils";
import useTypography from "../composables/typography";

const { t } = useI18n({
      inheritLocale: true,
      useScope: 'global'
    })

const props = defineProps<{
  userBookId: string,
  bookId: string,
  pageCount: number|null,
  currentProgress: number|null,
  currentPage: number|null,
}>()

const localPageCount: Ref<number|null> = ref(props.pageCount)
const percentRead: Ref<number> = ref(Math.round(props.currentProgress ?? 0))
const currentPageNumber: Ref<number|null> = ref(props.currentPage)
const progress: Ref<boolean> = ref(false)
const updatingFrom: Ref<string> = ref("")

const emit = defineEmits<{
  (e: 'close'): void
}>()

watch(percentRead, (newVal) => {
  if (updatingFrom.value === "page") return
  updatingFrom.value = "percent"
  if (localPageCount.value && newVal != null) {
    currentPageNumber.value = Math.round((newVal / 100) * localPageCount.value)
  }
  updatingFrom.value = ""
})

watch(currentPageNumber, (newVal) => {
  if (updatingFrom.value === "percent") return
  updatingFrom.value = "page"
  if (localPageCount.value && newVal != null) {
    percentRead.value = Math.round((newVal / localPageCount.value) * 100)
  }
  updatingFrom.value = ""
})

watch(localPageCount, (newVal) => {
  if (newVal && currentPageNumber.value != null) {
    percentRead.value = Math.round((currentPageNumber.value / newVal) * 100)
  }
})

const update = async () => {
  progress.value = true
  try {
    if (localPageCount.value && localPageCount.value !== props.pageCount && props.bookId) {
      const existingUserBook = await dataService.getUserBookById(props.userBookId)
      const existingBook = existingUserBook.book
      existingBook.pageCount = localPageCount.value
      await dataService.updateBook(props.bookId, existingBook)
    }
    const userBookUpdate: UserBookUpdate = {
      id: props.userBookId,
      percentRead: percentRead.value,
      currentPageNumber: currentPageNumber.value,
    }
    await dataService.updateUserBook(userBookUpdate)
    progress.value = false
    emit('close')
  } catch (e) {
    console.log("error updating progress: " + e)
    progress.value = false
  }
}

const { typographyClasses } = useTypography()
</script>
<template>
  <section class="event-modal p-4">
    <div>
      <h1
        class="text-2xl first-letter:capitalize mb-4"
        :class="typographyClasses"
      >
        {{ t('labels.set_progress') }}
      </h1>
      <div class="flex flex-col gap-4">
        <div>
          <label class="label">
            <span class="label-text font-semibold first-letter:capitalize">{{ t('book.page_count') }} :</span>
          </label>
          <input
            v-model.number="localPageCount"
            type="number"
            min="1"
            placeholder="Enter total pages"
            class="input input-bordered w-full"
          >
        </div>
        <div>
          <label class="label">
            <span class="label-text font-semibold first-letter:capitalize">{{ t('book.percent_read') }} :</span>
            <span class="label-text-alt text-lg font-bold">{{ percentRead }}%</span>
          </label>
          <input
            v-model.number="percentRead"
            type="range"
            min="0"
            max="100"
            step="1"
            class="range range-primary"
          >
        </div>
        <div v-if="localPageCount != null">
          <label class="label">
            <span class="label-text font-semibold first-letter:capitalize">{{ t('book.current_page_number') }} :</span>
            <span class="label-text-alt">{{ currentPageNumber ?? 0 }} / {{ localPageCount }}</span>
          </label>
          <input
            v-model.number="currentPageNumber"
            type="number"
            min="0"
            :max="localPageCount"
            class="input input-bordered w-full"
          >
        </div>
        <div class="mt-2 place-self-center">
          <button
            class="btn btn-success uppercase"
            :disabled="progress"
            @click="update"
          >
            <span v-if="progress" class="loading loading-spinner loading-sm"></span>
            <span class="icon"><i class="mdi mdi-pencil mdi-18px" /></span>
            <span>{{ t('labels.submit') }}</span>
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
