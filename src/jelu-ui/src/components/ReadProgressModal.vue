<script setup lang="ts">
import { computed, Ref, ref, watch } from "vue";
import { useI18n } from 'vue-i18n';
import { UserBookUpdate } from "../model/Book";
import { ReadingEventType } from "../model/ReadingEvent";
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
  percentRead: props.currentProgress ?? 0,
  currentPageNumber: props.currentPage
})
const progress: Ref<boolean> = ref(false)

const emit = defineEmits<{
  (e: 'close'): void
}>()

const pagesRead = computed(() => {
  if (localPageCount.value && userBookUpdate.value.percentRead != null) {
    return Math.round((userBookUpdate.value.percentRead / 100) * localPageCount.value)
  }
  return null
})

const update = async () => {
  progress.value = true
  try {
    // If user entered a page count that wasn't there before, save it to the book
    if (localPageCount.value && localPageCount.value !== props.pageCount && props.bookId) {
      await dataService.updateBook(props.bookId, { pageCount: localPageCount.value } as any)
    }

    // Update the userbook progress
    await dataService.updateUserBook(userBookUpdate.value)

    progress.value = false
    emit('close')
  } catch (e) {
    console.log("error updating progress: " + e)
    progress.value = false
  }
}

watch(() => [userBookUpdate.value.currentPageNumber, userBookUpdate.value.percentRead], (newVals, oldVals) => {
  if (localPageCount.value != null) {
    ObjectUtils.computePages(newVals, oldVals, userBookUpdate.value, localPageCount.value)
  }
})

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
            <span class="label-text-alt text-lg font-bold">{{ userBookUpdate.percentRead ?? 0 }}%</span>
          </label>
          <input
            v-model.number="userBookUpdate.percentRead"
            type="range"
            min="0"
            max="100"
            :disabled="localPageCount != null"
            class="range range-primary"
          >
        </div>
        <div v-if="localPageCount != null">
          <label class="label">
            <span class="label-text font-semibold first-letter:capitalize">{{ t('book.current_page_number') }} :</span>
            <span v-if="pagesRead != null" class="label-text-alt">{{ pagesRead }} / {{ localPageCount }} pages</span>
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
