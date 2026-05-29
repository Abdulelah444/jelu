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
const origin = window.location.origin

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

const qrUrl = (bookId: string) => {
  return "https://api.qrserver.com/v1/create-qr-code/?size=170x170&data=" + encodeURIComponent(origin + "/public/book/" + bookId)
}

const printAllLabels = () => {
  const win = window.open('', '_blank')
  if (!win) return
  win.document.write('<html><head><title>Book Labels</title>')
  win.document.write('<style>')
  win.document.write('body{margin:0;padding:20px;background:#fff;font-family:Georgia,serif;}')
  win.document.write('.grid{display:flex;flex-wrap:wrap;gap:16px;justify-content:center;}')
  win.document.write('.label{border:2px solid #333;padding:16px;border-radius:8px;text-align:center;width:220px;page-break-inside:avoid;break-inside:avoid;}')
  win.document.write('.label .logo{width:32px;height:32px;border-radius:50%;margin:0 auto 6px;display:block;}')
  win.document.write('.label .qr{width:140px;height:140px;}')
  win.document.write('.label .divider{border-bottom:1px solid #ccc;margin:8px 0;}')
  win.document.write('.label .title{font-weight:bold;font-size:11px;line-height:1.3;}')
  win.document.write('@media print{body{padding:5mm;} .grid{gap:8px;} @page{size:A4;margin:5mm;}}')
  win.document.write('</style></head><body>')
  win.document.write('<div class="grid">')
  for (const ub of books.value) {
    if (!ub.book?.id) continue
    win.document.write('<div class="label">')
    win.document.write('<img class="logo" src="' + origin + '/android-chrome-192x192.png" />')
    win.document.write('<img class="qr" src="' + qrUrl(ub.book.id) + '" />')
    win.document.write('<div class="divider"></div>')
    win.document.write('<div class="title">' + (ub.book.title || '') + '</div>')
    win.document.write('</div>')
  }
  win.document.write('</div></body></html>')
  win.document.close()
  win.onload = () => { setTimeout(() => win.print(), 2000) }
}

loadBooks()
</script>

<template>
  <section class="px-4 sm:px-8 py-4">
    <h1 :class="['text-2xl mb-4', typographyClasses]">
      <i class="mdi mdi-qrcode mdi-24px mr-2 text-primary" />
      Book Labels
    </h1>
    <p class="text-sm text-base-content/60 mb-4">
      {{ books.length }} owned books — print QR labels to stick on your books
    </p>
    <div class="mb-4">
      <button class="btn btn-primary" :disabled="loading || books.length === 0" @click="printAllLabels">
        <i class="mdi mdi-printer mdi-18px mr-1" /> Print All Labels
      </button>
    </div>
    <div v-if="loading" class="flex justify-center py-8">
      <span class="loading loading-spinner loading-lg" />
    </div>
    <div v-else class="flex flex-wrap gap-4 justify-center">
      <div v-for="ub in books" :key="ub.id"
        class="bg-white text-black p-4 rounded-lg border-2 border-gray-800 text-center" style="width: 220px;">
        <img src="/android-chrome-192x192.png" alt="logo" style="width: 32px; height: 32px; margin: 0 auto 6px; display: block; border-radius: 50%;" />
        <img :src="qrUrl(ub.book.id!)" alt="QR" style="width: 140px; height: 140px;" class="mx-auto" />
        <div class="border-b border-gray-300 mt-2 mb-2" />
        <div class="font-bold text-xs leading-tight" style="font-family: Georgia, serif;">{{ ub.book.title }}</div>
      </div>
    </div>
  </section>
</template>
