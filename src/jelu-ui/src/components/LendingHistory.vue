<script setup lang="ts">
import { ref, computed, onMounted } from "vue"
import { useTitle } from "@vueuse/core"
import dataService from "../services/DataService"

useTitle("Jelu | Lending History")

const items = ref<Array<any>>([])
const isLoading = ref(false)
const activeTab = ref("ALL")

const loadHistory = async () => {
  isLoading.value = true
  try {
    const response = await dataService.apiClient.get("/public/lending-history")
    items.value = response.data
  } catch (e) {
    console.log("Failed to load lending history: " + e)
  }
  isLoading.value = false
}

const currentlyLent = computed(() => items.value.filter((i: any) => !i.returnDate))
const returned = computed(() => items.value.filter((i: any) => i.returnDate))
const filtered = computed(() => {
  if (activeTab.value === "LENT") return currentlyLent.value
  if (activeTab.value === "RETURNED") return returned.value
  return items.value
})

const returnBook = async (bookId: string) => {
  try {
    await dataService.apiClient.post("/public/book/" + bookId + "/return")
    loadHistory()
  } catch (e) {
    console.log("Failed to return: " + e)
  }
}
onMounted(() => loadHistory())
</script>

<template>
  <section>
    <div class="max-w-3xl mx-auto px-4 py-6">
      <div class="flex items-center justify-between mb-6">
        <h1 class="text-xl font-medium">
          <i class="mdi mdi-history mdi-24px mr-1" /> Lending History
          <span class="badge badge-sm ml-1">{{ items.length }}</span>
        </h1>
      </div>

      <!-- Filter tabs -->
      <div class="inline-flex rounded-lg border border-base-content/30 overflow-hidden mb-4">
        <div class="px-4 py-2 text-sm cursor-pointer transition-colors" :class="activeTab === 'ALL' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="activeTab = 'ALL'">
          All <span class="badge badge-xs ml-1">{{ items.length }}</span>
        </div>
        <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="activeTab === 'LENT' ? 'bg-warning/20 text-warning font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="activeTab = 'LENT'">
          <i class="mdi mdi-account-arrow-right mdi-18px mr-1" /> Currently Lent <span class="badge badge-xs badge-warning ml-1">{{ currentlyLent.length }}</span>
        </div>
        <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="activeTab === 'RETURNED' ? 'bg-success/20 text-success font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="activeTab = 'RETURNED'">
          <i class="mdi mdi-book-check mdi-18px mr-1" /> Returned <span class="badge badge-xs badge-success ml-1">{{ returned.length }}</span>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="isLoading" class="flex justify-center py-8">
        <span class="loading loading-spinner loading-lg" />
      </div>

      <!-- Empty -->
      <div v-else-if="filtered.length === 0" class="text-center py-12 text-base-content/40">
        <i class="mdi mdi-book-arrow-right-outline mdi-48px" />
        <p class="mt-2">No lending history yet</p>
      </div>

      <!-- Items -->
      <div v-else class="flex flex-col gap-3">
        <div v-for="item in filtered" :key="item.id" class="bg-base-200 rounded-xl border border-base-content/20 p-4 flex gap-4 items-center">
          <div v-if="item.bookImage" class="flex-shrink-0">
            <img :src="item.bookImage.startsWith('http') ? item.bookImage : '/files/' + item.bookImage" alt="cover" class="w-12 h-16 object-cover rounded">
          </div>
          <div v-else class="w-12 h-16 bg-base-300 rounded flex items-center justify-center flex-shrink-0">
            <i class="mdi mdi-book-outline mdi-18px text-base-content/30" />
          </div>
          <div class="flex-grow min-w-0">
            <router-link :to="{ name: 'book-detail', params: { bookId: item.bookId } }" class="font-medium text-sm truncate hover:underline">{{ item.bookTitle }}</router-link>
            <div class="flex items-center gap-2 mt-1">
              <span v-if="!item.returnDate" class="badge badge-warning badge-xs">Lent out</span>
              <span v-else class="badge badge-success badge-xs">Returned</span>
              <span class="text-xs text-base-content/50">
                <i class="mdi mdi-account mdi-14px" /> {{ item.borrowerName }}
              </span>
            </div>
            <div class="text-xs text-base-content/40 mt-1">
              Lent: {{ new Date(item.lendDate).toLocaleDateString() }}
              <span v-if="item.returnDate"> · Returned: {{ new Date(item.returnDate).toLocaleDateString() }}</span>
            </div>
          </div>
          <div v-if="!item.returnDate" class="flex-shrink-0">
            <button class="btn btn-success btn-xs" @click="returnBook(item.bookId)">
              <i class="mdi mdi-book-check mdi-18px mr-1" /> Return
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
