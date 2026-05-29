<script setup lang="ts">
import { Ref, ref } from 'vue'
import { useOruga } from "@oruga-ui/oruga-next"
import { UserBook } from '../model/Book'
import dataService from "../services/DataService"
import useTypography from "../composables/typography"

const oruga = useOruga()
const { typographyClasses } = useTypography()
const books: Ref<Array<UserBook>> = ref([])
const loading: Ref<boolean> = ref(false)
const bulkLoading: Ref<boolean> = ref(false)
const labelWidth: Ref<number> = ref(40)
const labelHeight: Ref<number> = ref(30)

const sizes = [
  { label: '40 x 30 mm', w: 40, h: 30 },
  { label: '50 x 30 mm', w: 50, h: 30 },
  { label: '40 x 40 mm', w: 40, h: 40 },
  { label: '50 x 40 mm', w: 50, h: 40 },
  { label: '50 x 50 mm', w: 50, h: 50 },
  { label: '60 x 40 mm', w: 60, h: 40 },
]

const setSize = (w: number, h: number) => {
  labelWidth.value = w
  labelHeight.value = h
}

const loadBooks = async () => {
  loading.value = true
  try {
    const result = await dataService.findUserBookByCriteria(
      null, null, null, null, true, null, 0, 500, "title,asc", null, null
    )
    books.value = result.content || []
  } catch (error) {
    console.log("error: " + error)
  } finally {
    loading.value = false
  }
}

const labelPreviewUrl = (ubId: string) => {
  return '/api/v1/userbooks/' + ubId + '/label.png?widthMm=' + labelWidth.value + '&heightMm=' + labelHeight.value + '&baseUrl=' + encodeURIComponent(window.location.origin)
}

const downloadLabel = async (ub: UserBook) => {
  if (!ub.id) return
  try {
    const response = await dataService.apiClient.get(
      '/userbooks/' + ub.id + '/label.png',
      { params: { widthMm: labelWidth.value, heightMm: labelHeight.value, baseUrl: window.location.origin }, responseType: 'blob' }
    )
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', (ub.book.title || 'label') + '.png')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    oruga.notification.open({ message: "Download failed", variant: "danger", duration: 3000 })
  }
}

const downloadAll = async () => {
  bulkLoading.value = true
  try {
    const response = await dataService.apiClient.get(
      '/labels/bulk.zip',
      { params: { widthMm: labelWidth.value, heightMm: labelHeight.value, baseUrl: window.location.origin }, responseType: 'blob' }
    )
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'book-labels.zip')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    oruga.notification.open({ message: "Bulk download failed", variant: "danger", duration: 3000 })
  } finally {
    bulkLoading.value = false
  }
}

loadBooks()
</script>

<template>
  <section class="px-4 sm:px-8 py-4">
    <h1 :class="['text-2xl mb-4', typographyClasses]">
      <i class="mdi mdi-qrcode mdi-24px mr-2 text-primary" />
      Book Labels
    </h1>

    <div class="flex flex-wrap items-center gap-3 mb-4">
      <span class="text-sm font-medium">Label size:</span>
      <div class="flex flex-wrap gap-1">
        <button v-for="s in sizes" :key="s.label"
          class="btn btn-xs" :class="labelWidth === s.w && labelHeight === s.h ? 'btn-primary' : 'btn-outline'"
          @click="setSize(s.w, s.h)">
          {{ s.label }}
        </button>
      </div>
    </div>

    <div class="flex items-center gap-3 mb-4">
      <p class="text-sm text-base-content/60">{{ books.length }} owned books</p>
      <button class="btn btn-primary btn-sm" :disabled="bulkLoading || books.length === 0" @click="downloadAll">
        <span v-if="bulkLoading" class="loading loading-spinner loading-xs" />
        <i v-else class="mdi mdi-download mdi-18px mr-1" /> Download All (ZIP)
      </button>
    </div>

    <div v-if="loading" class="flex justify-center py-8">
      <span class="loading loading-spinner loading-lg" />
    </div>

    <div v-else class="flex flex-wrap gap-4 justify-center">
      <div v-for="ub in books" :key="ub.id" class="flex flex-col items-center gap-2">
        <img :src="labelPreviewUrl(ub.id!)" :alt="ub.book.title" class="rounded shadow border" />
        <button class="btn btn-ghost btn-xs" @click="downloadLabel(ub)">
          <i class="mdi mdi-download mdi-14px mr-1" /> Download
        </button>
      </div>
    </div>
  </section>
</template>
