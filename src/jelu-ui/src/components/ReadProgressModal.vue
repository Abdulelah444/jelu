<script setup lang="ts">
import { Ref, ref, watch } from "vue";
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
const userBookUpdate: Ref<UserBookUpdate> = ref({
  id: props.userBookId,
  percentRead: Math.round(props.currentProgress ?? 0),
  currentPageNumber: props.currentPage
})
const progress: Ref<boolean> = ref(false)
const emit = defineEmits<{
  (e: 'close'): void
}>()
const update = () => {
  progress.value = true
  if (userBookUpdate.value.percentRead != null) {
    userBookUpdate.value.percentRead = Math.round(userBookUpdate.value.percentRead)
  }
  // Save page count to book if changed
  if (localPageCount.value && localPageCount.value !== props.pageCount && props.bookId) {
    dataService.getUserBookById(props.userBookId).then(existing => {
      const existingBook = existing.book
      existingBook.pageCount = localPageCount.value
      dataService.updateBook(props.bookId, existingBook)
    }).catch(e => console.log("page count save failed: " + e))
  }
  dataService.updateUserBook(userBookUpdate.value)
    .then(res => {
      progress.value = false
      emit('close')
    })
    .catch(e => {
      progress.value = false
    })
}
watch(() => [userBookUpdate.value.currentPageNumber, userBookUpdate.value.percentRead], (newVals, oldVals) => {
  const pc = localPageCount.value ?? props.pageCount
  if (pc != null) {
    ObjectUtils.computePages(newVals, oldVals, userBookUpdate.value, pc)
    if (userBookUpdate.value.percentRead != null) {
      userBookUpdate.value.percentRead = Math.round(userBookUpdate.value.percentRead)
    }
  }
})
const displayPercent = () => {
  return Math.round(userBookUpdate.value.percentRead ?? 0)
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
            <span class="label-text-alt text-lg font-bold">{{ displayPercent() }}%</span>
          </label>
          <input
            v-model.number="userBookUpdate.percentRead"
            type="range"
            min="0"
            max="100"
            step="1"
            :disabled="localPageCount != null"
            class="range range-primary"
          >
        </div>
        <div v-if="localPageCount != null">
          <label class="label">
            <span class="label-text font-semibold first-letter:capitalize">{{ t('book.current_page_number') }} :</span>
            <span class="label-text-alt">{{ userBookUpdate.currentPageNumber ?? 0 }} / {{ localPageCount }}</span>
          </label>
          <input
            v-model.number="userBookUpdate.currentPageNumber"
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
