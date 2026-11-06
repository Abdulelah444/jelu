<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import useTypography from '../composables/typography'

const { typographyClasses } = useTypography()

const props = defineProps<{
  events: Array<any>
  windowLabel?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

// Dedup by book (keep earliest finish), sort newest first
const books = computed(() => {
  const byBook = new Map<string, any>()
  for (const ev of props.events) {
    const id = ev.userBook?.id
    if (!id) continue
    const date = ev.endDate || ev.startDate || ev.modificationDate || ev.creationDate
    const existing = byBook.get(id)
    if (!existing || dayjs(date).isBefore(dayjs(existing.date))) {
      byBook.set(id, {
        id,
        title: ev.userBook?.book?.title ?? 'Untitled',
        image: ev.userBook?.book?.image,
        authors: ev.userBook?.book?.authors?.map((a: any) => a.name).join(', ') ?? '',
        date,
      })
    }
  }
  return [...byBook.values()].sort((a, b) => dayjs(b.date).valueOf() - dayjs(a.date).valueOf())
})
</script>

<template>
  <div class="p-2 max-w-lg w-full">
    <div class="text-center mb-4">
      <div class="text-3xl mb-1">📚</div>
      <h1 class="text-xl font-bold" :class="typographyClasses">
        Books Finished
        <span v-if="windowLabel" class="opacity-50 font-normal text-base">· {{ windowLabel }}</span>
      </h1>
      <p class="text-sm opacity-60">{{ books.length }} book{{ books.length !== 1 ? 's' : '' }}</p>
    </div>

    <div v-if="books.length > 0" class="flex flex-col gap-2 max-h-[60vh] overflow-y-auto pr-1">
      <div v-for="b in books" :key="b.id" class="flex items-center gap-3 bg-base-200 rounded-lg p-2">
        <img v-if="b.image" :src="'/files/' + b.image" class="w-10 h-14 object-cover rounded flex-shrink-0" />
        <div v-else class="w-10 h-14 bg-base-300 rounded flex items-center justify-center flex-shrink-0">
          <i class="mdi mdi-book opacity-30" />
        </div>
        <div class="flex-1 min-w-0">
          <p class="font-semibold text-sm line-clamp-1">{{ b.title }}</p>
          <p v-if="b.authors" class="text-xs opacity-60 line-clamp-1">{{ b.authors }}</p>
        </div>
        <span class="text-xs opacity-70 flex-shrink-0">{{ dayjs(b.date).format('D MMM YYYY') }}</span>
      </div>
    </div>
    <div v-else class="text-center py-8 opacity-50">No finished books in this period.</div>

    <button class="btn btn-primary w-full mt-4" @click="emit('close')">Close</button>
  </div>
</template>
