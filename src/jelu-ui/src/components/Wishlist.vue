<script setup lang="ts">
import { Ref, ref, computed } from "vue"
import { useI18n } from "vue-i18n"
import { useOruga } from "@oruga-ui/oruga-next"
import { useRouter } from "vue-router"
import dataService from "../services/DataService"
import { Metadata } from "../model/Metadata"
import ScanModal from "./ScanModal.vue"
import { ObjectUtils } from "../utils/ObjectUtils"
import { useTitle } from "@vueuse/core"

const { t } = useI18n({ inheritLocale: true, useScope: "global" })
useTitle("Jelu | Wishlist")
const oruga = useOruga()
const router = useRouter()

const items: Ref<Array<any>> = ref([])
const isLoading = ref(false)
const activeTab = ref("ALL")
const showAddBook = ref(false)
const showAddConcept = ref(false)

// Add book form
const bookForm = ref({
  title: "",
  isbn: "",
  authors: "",
  image: "",
  pageCount: null as number | null,
  publisher: "",
  language: "",
  description: "",
})

// Add concept form
const conceptForm = ref({
  description: "",
})

const filteredItems = computed(() => {
  if (activeTab.value === "ALL") return items.value
  return items.value.filter((i: any) => i.itemType === activeTab.value)
})

const bookItems = computed(() => items.value.filter((i: any) => i.itemType === "BOOK"))
const conceptItems = computed(() => items.value.filter((i: any) => i.itemType === "CONCEPT"))

const loadItems = async () => {
  isLoading.value = true
  try {
    items.value = await dataService.getWishlistItems()
  } catch (e) {
    console.log("Failed to load wishlist: " + e)
  }
  isLoading.value = false
}

const addBook = async () => {
  if (!bookForm.value.title && !bookForm.value.isbn) return
  try {
    await dataService.createWishlistItem({
      itemType: "BOOK",
      title: bookForm.value.title,
      isbn: bookForm.value.isbn,
      authors: bookForm.value.authors,
      image: bookForm.value.image,
      pageCount: bookForm.value.pageCount,
      publisher: bookForm.value.publisher,
      language: bookForm.value.language,
      description: bookForm.value.description,
    })
    ObjectUtils.toast(oruga, "success", "Book added to wishlist", 3000)
    bookForm.value = { title: "", isbn: "", authors: "", image: "", pageCount: null, publisher: "", language: "", description: "" }
    showAddBook.value = false
    loadItems()
  } catch (e) {
    ObjectUtils.toast(oruga, "danger", "Failed to add: " + e, 3000)
  }
}

const addConcept = async () => {
  if (!conceptForm.value.description) return
  try {
    await dataService.createWishlistItem({
      itemType: "CONCEPT",
      description: conceptForm.value.description,
    })
    ObjectUtils.toast(oruga, "success", "Concept added to wishlist", 3000)
    conceptForm.value = { description: "" }
    showAddConcept.value = false
    loadItems()
  } catch (e) {
    ObjectUtils.toast(oruga, "danger", "Failed to add: " + e, 3000)
  }
}

const deleteItem = async (id: string) => {
  try {
    await dataService.deleteWishlistItem(id)
    ObjectUtils.toast(oruga, "success", "Removed from wishlist", 3000)
    loadItems()
  } catch (e) {
    ObjectUtils.toast(oruga, "danger", "Failed to delete: " + e, 3000)
  }
}

const addToLibrary = async (item: any) => {
  router.push({
    name: "add-book",
    query: {
      title: item.title || "",
      isbn13: item.isbn || "",
      authors: item.authors || "",
    },
  })
}

const fetchMetadata = async () => {
  const isbn = bookForm.value.isbn
  const title = bookForm.value.title
  if (!isbn && !title) return
  try {
    const res: Metadata = await dataService.fetchMetadata(isbn || undefined, title || undefined)
    if (res.title) bookForm.value.title = res.title
    if (res.authors && res.authors.length > 0) bookForm.value.authors = res.authors.map((a: any) => a.name || a).join(", ")
    if (res.image) bookForm.value.image = res.image
    if (res.pageCount) bookForm.value.pageCount = res.pageCount
    if (res.publisher) bookForm.value.publisher = res.publisher
    if (res.language) bookForm.value.language = res.language
    if (res.summary) bookForm.value.description = res.summary
  } catch (e) {
    console.log("Metadata fetch failed: " + e)
  }
}

const toggleScanModal = () => {
  oruga.modal.open({
    component: ScanModal,
    trapFocus: true,
    active: true,
    canCancel: ["x", "button", "outside"],
    scroll: "keep",
    events: {
      decoded: (barcode: string | null) => {
        if (barcode != null) {
          bookForm.value.isbn = barcode
          fetchMetadata()
        }
      },
    },
    onClose: () => {
      navigator.mediaDevices?.getUserMedia({ video: true }).then(s => s.getTracks().forEach(t => t.stop())).catch(() => {})
    },
  })
}

loadItems()
</script>

<template>
  <section>
    <div class="max-w-3xl mx-auto px-4 py-6">
      <!-- Header -->
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 mb-6">
        <h1 class="text-xl font-medium">
          <i class="mdi mdi-heart-outline mdi-24px mr-1" /> Wishlist
          <span class="badge badge-sm ml-1">{{ items.length }}</span>
        </h1>
        <div class="flex gap-2 flex-wrap">
          <button class="btn btn-success btn-sm" @click="showAddBook = !showAddBook; showAddConcept = false">
            <i class="mdi mdi-book-plus mdi-18px mr-1" /> Add Book
          </button>
          <button class="btn btn-outline btn-sm" @click="showAddConcept = !showAddConcept; showAddBook = false">
            <i class="mdi mdi-lightbulb-outline mdi-18px mr-1" /> Add Concept
          </button>
        </div>
      </div>

      <!-- Add Book Form -->
      <div v-if="showAddBook" class="bg-base-200 rounded-xl border border-base-content/30 p-5 mb-4">
        <p class="text-xs font-medium text-base-content/60 uppercase tracking-wider mb-4">Add a specific book</p>
        <div class="flex gap-2 mb-3">
          <button class="btn btn-success btn-sm" @click="fetchMetadata">
            <i class="mdi mdi-auto-fix mdi-18px mr-1" /> Auto Fill
          </button>
          <button class="btn btn-outline btn-sm" @click="toggleScanModal">
            <i class="mdi mdi-barcode-scan mdi-18px mr-1" /> Scan
          </button>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
          <fieldset class="fieldset">
            <legend class="fieldset-legend">Title</legend>
            <input v-model="bookForm.title" type="text" class="input w-full focus:input-accent" placeholder="Book title">
          </fieldset>
          <fieldset class="fieldset">
            <legend class="fieldset-legend">ISBN</legend>
            <input v-model="bookForm.isbn" type="text" class="input w-full focus:input-accent" placeholder="ISBN-13">
          </fieldset>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
          <fieldset class="fieldset">
            <legend class="fieldset-legend">Authors</legend>
            <input v-model="bookForm.authors" type="text" class="input w-full focus:input-accent" placeholder="Author names">
          </fieldset>
          <fieldset class="fieldset">
            <legend class="fieldset-legend">Publisher</legend>
            <input v-model="bookForm.publisher" type="text" class="input w-full focus:input-accent">
          </fieldset>
        </div>
        <fieldset class="fieldset mb-3">
          <legend class="fieldset-legend">Notes</legend>
          <textarea v-model="bookForm.description" class="textarea w-full focus:textarea-accent" rows="2" placeholder="Why do you want this book?"></textarea>
        </fieldset>
        <div v-if="bookForm.image" class="mb-3">
          <img :src="bookForm.image" alt="cover" class="w-20 h-28 object-cover rounded">
        </div>
        <button class="btn btn-success btn-sm" @click="addBook">
          <i class="mdi mdi-check mdi-18px mr-1" /> Add to Wishlist
        </button>
      </div>

      <!-- Add Concept Form -->
      <div v-if="showAddConcept" class="bg-base-200 rounded-xl border border-base-content/30 p-5 mb-4">
        <p class="text-xs font-medium text-base-content/60 uppercase tracking-wider mb-4">What kind of book are you looking for?</p>
        <fieldset class="fieldset mb-3">
          <legend class="fieldset-legend">Description</legend>
          <textarea v-model="conceptForm.description" class="textarea w-full focus:textarea-accent" rows="2" placeholder="e.g. A book about corporate governance and board structure"></textarea>
        </fieldset>
        <button class="btn btn-success btn-sm" @click="addConcept">
          <i class="mdi mdi-check mdi-18px mr-1" /> Add to Wishlist
        </button>
      </div>

      <!-- Filter tabs -->
      <div class="inline-flex rounded-lg border border-base-content/30 overflow-hidden mb-4">
        <div class="px-4 py-2 text-sm cursor-pointer transition-colors" :class="activeTab === 'ALL' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="activeTab = 'ALL'">
          All <span class="badge badge-xs ml-1">{{ items.length }}</span>
        </div>
        <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="activeTab === 'BOOK' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="activeTab = 'BOOK'">
          <i class="mdi mdi-book-outline mdi-18px mr-1" /> Books <span class="badge badge-xs ml-1">{{ bookItems.length }}</span>
        </div>
        <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="activeTab === 'CONCEPT' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="activeTab = 'CONCEPT'">
          <i class="mdi mdi-lightbulb-outline mdi-18px mr-1" /> Concepts <span class="badge badge-xs ml-1">{{ conceptItems.length }}</span>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="isLoading" class="flex justify-center py-8">
        <span class="loading loading-spinner loading-lg" />
      </div>

      <!-- Empty state -->
      <div v-else-if="filteredItems.length === 0" class="text-center py-12 text-base-content/40">
        <i class="mdi mdi-heart-outline mdi-48px" />
        <p class="mt-2">Your wishlist is empty</p>
        <p class="text-sm">Add books you want to buy or topics you want to explore</p>
      </div>

      <!-- Items -->
      <div v-else class="flex flex-col gap-3">
        <div v-for="item in filteredItems" :key="item.id" class="bg-base-200 rounded-xl border border-base-content/20 p-4 flex gap-4">
          <!-- Book item -->
          <template v-if="item.itemType === 'BOOK'">
            <div v-if="item.image" class="flex-shrink-0">
              <img :src="item.image.startsWith('http') ? item.image : '/files/' + item.image" alt="cover" class="w-16 h-22 object-cover rounded">
            </div>
            <div v-else class="w-16 h-22 bg-base-300 rounded flex items-center justify-center flex-shrink-0">
              <i class="mdi mdi-book-outline mdi-24px text-base-content/30" />
            </div>
            <div class="flex-grow min-w-0">
              <h3 class="font-medium text-sm truncate">{{ item.title || 'Untitled' }}</h3>
              <p v-if="item.authors" class="text-xs text-base-content/60">{{ item.authors }}</p>
              <p v-if="item.publisher" class="text-xs text-base-content/40">{{ item.publisher }}</p>
              <p v-if="item.pageCount" class="text-xs text-base-content/40">{{ item.pageCount }} pages</p>
              <p v-if="item.description" class="text-xs text-base-content/50 mt-1 line-clamp-2">{{ item.description }}</p>
            </div>
          </template>
          <!-- Concept item -->
          <template v-else>
            <div class="w-16 h-22 bg-warning/10 rounded flex items-center justify-center flex-shrink-0">
              <i class="mdi mdi-lightbulb-outline mdi-24px text-warning" />
            </div>
            <div class="flex-grow min-w-0">
              <span class="badge badge-warning badge-sm mb-1">Concept</span>
              <p class="text-sm">{{ item.description }}</p>
            </div>
          </template>
          <!-- Actions -->
          <div class="flex flex-col gap-1 flex-shrink-0">
            <button v-if="item.itemType === 'BOOK'" class="btn btn-success btn-xs" @click="addToLibrary(item)" v-tooltip="'Add to library'">
              <i class="mdi mdi-plus mdi-18px" />
            </button>
            <button class="btn btn-error btn-xs btn-outline" @click="deleteItem(item.id)" v-tooltip="'Remove'">
              <i class="mdi mdi-delete mdi-18px" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
