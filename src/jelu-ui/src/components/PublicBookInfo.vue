<script setup lang="ts">
import { ref, onMounted } from "vue"
import { useRoute } from "vue-router"
import axios from "axios"

const route = useRoute()
const book = ref<any>(null)
const loading = ref(true)
const error = ref(false)

onMounted(async () => {
  try {
    const res = await axios.get(`/api/v1/public/book/${route.params.bookId}`)
    book.value = res.data
  } catch (e) {
    error.value = true
  }
  loading.value = false
})
const showBorrowForm = ref(false)
const borrowerName = ref("")
const borrowMessage = ref("")
const borrowBook = async () => {
  if (!borrowerName.value) return
  try {
    const res = await axios.post(`/api/v1/public/book/${route.params.bookId}/borrow`, { borrowerName: borrowerName.value })
    borrowMessage.value = "Book borrowed successfully!"
    showBorrowForm.value = false
    book.value.borrowed = true
    book.value.borrowerName = borrowerName.value
  } catch (e) {
    borrowMessage.value = "Failed to borrow"
  }
}
const returnBook = async () => {
  try {
    await axios.post(`/api/v1/public/book/${route.params.bookId}/return`)
    borrowMessage.value = "Book returned successfully!"
    book.value.borrowed = false
    book.value.borrowerName = null
  } catch (e) {
    borrowMessage.value = "Failed to return"
  }
}
</script>

<template>
  <div class="min-h-screen bg-base-300 flex items-center justify-center p-4">
    <div v-if="loading" class="text-center">
      <span class="loading loading-spinner loading-lg" />
    </div>
    <div v-else-if="error" class="text-center text-base-content/60">
      <i class="mdi mdi-book-off-outline mdi-48px" />
      <p class="mt-2">Book not found</p>
    </div>
    <div v-else class="bg-base-200 rounded-2xl border border-base-content/20 p-6 max-w-sm w-full shadow-xl">
      <!-- Cover -->
      <div class="text-center mb-4">
        <img v-if="book.image" :src="book.image?.startsWith('http') ? book.image : '/files/' + book.image" alt="cover" class="w-32 h-44 object-cover rounded-lg mx-auto shadow-md">
        <div v-else class="w-32 h-44 bg-base-300 rounded-lg mx-auto flex items-center justify-center">
          <i class="mdi mdi-book-outline mdi-48px text-base-content/20" />
        </div>
      </div>

      <!-- Title & Author -->
      <h1 class="text-xl font-bold text-center">{{ book.title }}</h1>
      <p v-if="book.owner" class="text-sm text-base-content/50 text-center mt-1">
        <i class="mdi mdi-account mdi-18px mr-1" />{{ book.owner }}
      </p>

      <!-- Badges -->
      <div class="flex flex-wrap justify-center gap-2 mt-3">
        <span v-if="book.owned" class="badge badge-success badge-sm gap-1">
          <i class="mdi mdi-check-circle mdi-14px" /> Owned
        </span>
        <span v-if="book.borrowed" class="badge badge-warning badge-sm gap-1">
          <i class="mdi mdi-swap-horizontal mdi-14px" /> Borrowed
        </span>
        <span v-if="!book.borrowed" class="badge badge-success badge-outline badge-sm gap-1">
          <i class="mdi mdi-bookshelf mdi-14px" /> Available
        </span>
        <span v-if="book.borrowed" class="badge badge-error badge-outline badge-sm gap-1">
          <i class="mdi mdi-clock-outline mdi-14px" /> Lent out
        </span>
        <span v-if="book.lastReadingEvent" class="badge badge-info badge-sm gap-1">
          {{ book.lastReadingEvent.replace('_', ' ').toLowerCase() }}
        </span>
      </div>

      <!-- Info rows -->
      <div class="mt-4 divide-y divide-base-content/10">
        <div v-if="book.location" class="flex justify-between py-2 text-sm">
          <span class="text-base-content/50">Location</span>
          <span class="text-right">{{ book.location }}</span>
        </div>
        <div v-if="book.pageCount" class="flex justify-between py-2 text-sm">
          <span class="text-base-content/50">Pages</span>
          <span>{{ book.pageCount }}</span>
        </div>
        <div v-if="book.publisher" class="flex justify-between py-2 text-sm">
          <span class="text-base-content/50">Publisher</span>
          <span>{{ book.publisher }}</span>
        </div>
        <div v-if="book.language" class="flex justify-between py-2 text-sm">
          <span class="text-base-content/50">Language</span>
          <span>{{ book.language }}</span>
        </div>
      </div>

      <!-- Borrow section -->
      <div v-if="!book.borrowed" class="mt-4">
        <button v-if="!showBorrowForm" class="btn btn-warning btn-sm w-full" @click="showBorrowForm = true">
          <i class="mdi mdi-account-arrow-right mdi-18px mr-1" /> Borrow this book
        </button>
        <div v-else class="bg-warning/10 rounded-lg border border-warning/30 p-3">
          <p class="text-sm font-medium mb-2">Enter your name to borrow</p>
          <input v-model="borrowerName" type="text" class="input input-sm w-full mb-2 focus:input-accent" placeholder="Your name">
          <div class="flex gap-2">
            <button class="btn btn-warning btn-sm flex-1" :disabled="!borrowerName" @click="borrowBook">Confirm</button>
            <button class="btn btn-ghost btn-sm" @click="showBorrowForm = false">Cancel</button>
          </div>
        </div>
      </div>
      <div v-else class="mt-4 bg-warning/10 rounded-lg border border-warning/30 p-3">
        <div class="text-center mb-2">
          <i class="mdi mdi-account-arrow-right mdi-18px text-warning" />
          <span class="text-sm"> Lent to {{ book.borrowerName }}</span>
        </div>
        <button class="btn btn-success btn-sm w-full" @click="returnBook">
          <i class="mdi mdi-book-check mdi-18px mr-1" /> Return this book
        </button>
      </div>
      <div v-if="borrowMessage" class="mt-2 text-center text-sm text-success">{{ borrowMessage }}</div>
      <!-- Footer -->
      <p class="text-center text-base-content/30 text-xs mt-6">
        <i class="mdi mdi-bookshelf mdi-14px mr-1" /> Ex Libris {{ book.owner }}
      </p>
    </div>
  </div>
</template>
