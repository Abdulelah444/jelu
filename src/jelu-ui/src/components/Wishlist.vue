<script setup lang="ts">
import { Ref, ref, computed, onActivated } from "vue"
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
const editingItem = ref<any>(null)
const editForm = ref<any>({})
const digitalSearchItem = ref<any>(null)
const digitalSearchResults = ref<any[]>([])
const digitalSearchLoading = ref(false)
const digitalDownloadLoading = ref(false)
const downloadProgress = ref('')
const downloadStatuses = ref<Record<string, any>>({})
const pendingDownloads = ref<Record<string, string>>({}) // wishlistItemId -> downloadId
const ebookFiles = ref<any[]>([])

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

const libraryTitles = ref<Set<string>>(new Set())

const normalizeTitle = (t: string) =>
  (t ?? '').toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]/g, '').trim()

function isInLibrary(item: any): boolean {
  return libraryTitles.value.has(normalizeTitle(item.title ?? ''))
}

const loadLibraryTitles = async () => {
  try {
    const myBooks = await dataService.findUserBookByCriteria(null, null, null, null, null, null, 0, 500)
    console.log('[wishlist] library books fetched:', myBooks?.content?.length)
    libraryTitles.value = new Set(myBooks.content.map((ub: any) => normalizeTitle(ub.book?.title ?? '')))
    console.log('[wishlist] normalized library titles:', [...libraryTitles.value].slice(0, 10))
  } catch (e) {
    console.log('[wishlist] loadLibraryTitles FAILED:', e)
  }
}

const shelfmarkUrl = (item: any) => {
  const base = 'http://192.168.88.20:8084/'
  const params = new URLSearchParams()
  if (item.title) params.set('title', item.title)
  if (item.authors) params.set('author', item.authors)
  if (item.isbn) params.set('isbn', item.isbn)
  return base + '?' + params.toString()
}

// Poll Shelfmark status every 5 seconds if there are pending downloads
const pollStatus = async () => {
  if (Object.keys(pendingDownloads.value).length === 0) return
  const status = await dataService.getWishlistDownloadStatus()
  const allItems = { ...(status.complete||{}), ...(status.downloading||{}), ...(status.error||{}), ...(status.queued||{}), ...(status.locating||{}), ...(status.resolving||{}) }
  downloadStatuses.value = allItems
}

let pollTimer: any = null
const startPolling = () => {
  if (pollTimer) return
  pollTimer = setInterval(pollStatus, 5000)
}

const matchStatusForItem = (item: any) => {
  const dlId = pendingDownloads.value[item.id]
  if (dlId && downloadStatuses.value[dlId]) return downloadStatuses.value[dlId]
  if (item.title) {
    const titleLower = item.title.toLowerCase()
    return Object.values(downloadStatuses.value).find((s: any) =>
      s.title && s.title.toLowerCase().includes(titleLower.slice(0, 12))
    ) || null
  }
  return null
}

const statusLabel = (s: any) => {
  if (!s || !s.status) return 'Working'
  const map: Record<string, string> = {
    downloading: 'Downloading',
    queued: 'Queued',
    locating: 'Locating',
    resolving: 'Resolving',
  }
  return map[s.status] || s.status
}

// Returns an ACTIVE (in-progress) status only — complete/error are not "active"
const getItemStatus = (item: any) => {
  const s: any = matchStatusForItem(item)
  if (!s) return null
  if (s.status === 'complete' || s.status === 'error' || s.status === 'cancelled') return null
  return s
}

const getItemFile = (item: any) => {
  if (!item.title || !ebookFiles.value.length) return null
  // Significant title words only (length > 3, strip punctuation)
  const words = item.title.toLowerCase()
    .replace(/[^a-z0-9 ]/g, '')
    .split(' ')
    .filter((w: string) => w.length > 3)
  if (words.length === 0) return null
  // Require at least 70% of significant title words to appear in the filename
  const needed = Math.max(1, Math.ceil(words.length * 0.7))
  let best: any = null
  let bestScore = 0
  for (const f of ebookFiles.value) {
    const fn = f.filename.toLowerCase()
    const score = words.filter((w: string) => fn.includes(w)).length
    if (score >= needed && score > bestScore) {
      bestScore = score
      best = f
    }
  }
  return best
}

const deleteEbook = async (file: any) => {
  if (!confirm('Delete ' + file.filename + ' from your ebooks folder?')) return
  try {
    await dataService.deleteEbookFile(file.path)
    ebookFiles.value = ebookFiles.value.filter((f: any) => f.path !== file.path)
    ObjectUtils.toast(oruga, 'success', 'File deleted', 2000)
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Could not delete file: ' + e, 3000)
  }
}

const searchDigital = async (item: any) => {
  digitalSearchItem.value = item
  digitalSearchLoading.value = true
  digitalSearchResults.value = []
  try {
    const q = [item.title, item.authors].filter(Boolean).join(' ')
    const result = await dataService.searchWishlistDigital(q)
    digitalSearchResults.value = result.releases || []
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Search failed: ' + e, 3000)
  } finally {
    digitalSearchLoading.value = false
  }
}

const triggerWishlistDownload = async (release: any) => {
  const targetItem = digitalSearchItem.value  // capture BEFORE clearing
  digitalDownloadLoading.value = true
  downloadProgress.value = 'Starting download...'
  digitalSearchItem.value = null
  try {
    const result = await dataService.triggerWishlistDownload(release)
    if (targetItem && result?.download_id) {
      pendingDownloads.value[targetItem.id] = result.download_id
    }
    ObjectUtils.toast(oruga, 'success', 'Download started for: ' + release.title, 4000)
    startPolling()  // always start; matches by title if no id
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Download failed: ' + e, 3000)
  } finally {
    digitalDownloadLoading.value = false
    downloadProgress.value = ''
  }
}

const startEdit = (item: any) => {
  editingItem.value = item
  editForm.value = {
    title: item.title ?? '',
    isbn: item.isbn ?? '',
    authors: item.authors ?? '',
    publisher: item.publisher ?? '',
    pageCount: item.pageCount ?? null,
    language: item.language ?? '',
    description: item.description ?? '',
    image: item.image ?? '',
    itemType: item.itemType,
  }
}

const saveEdit = async () => {
  if (!editingItem.value) return
  try {
    await dataService.updateWishlistItem(editingItem.value.id, editForm.value)
    ObjectUtils.toast(oruga, 'success', 'Updated', 2000)
    editingItem.value = null
    loadItems()
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Failed to update: ' + e, 3000)
  }
}

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
loadLibraryTitles()

// Re-fetch library titles + ebook files every time the user returns to this page,
// so the "In Library" badge and downloaded-file buttons stay current after adding a book.
onActivated(() => {
  loadLibraryTitles()
  dataService.listEbooks().then((files: any[]) => { ebookFiles.value = files }).catch(() => {})
})
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

// Load existing Shelfmark download statuses on init
dataService.getWishlistDownloadStatus().then((status: any) => {
  const allItems = { ...(status.complete||{}), ...(status.downloading||{}), ...(status.error||{}), ...(status.queued||{}), ...(status.locating||{}), ...(status.resolving||{}) }
  downloadStatuses.value = allItems
}).catch(() => {})
dataService.listEbooks().then((files: any[]) => {
  ebookFiles.value = files
}).catch(() => {})
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
        <div v-for="item in filteredItems" :key="item.id" class="bg-base-200 rounded-xl border border-base-content/20 p-4 flex flex-col gap-2">
          <!-- Main row: cover + info + actions -->
          <div class="flex gap-4">
            <!-- Book item -->
            <template v-if="item.itemType === 'BOOK'">
              <div v-if="item.image" class="flex-shrink-0">
                <img :src="item.image.startsWith('http') ? item.image : '/files/' + item.image" alt="cover" class="w-16 h-22 object-cover rounded">
              </div>
              <div v-else class="w-16 h-22 bg-base-300 rounded flex items-center justify-center flex-shrink-0">
                <i class="mdi mdi-book-outline mdi-24px text-base-content/30" />
              </div>
              <div class="flex-grow min-w-0">
                <div class="flex items-center gap-2 flex-wrap">
                  <h3 class="font-medium text-sm truncate">{{ item.title || 'Untitled' }}</h3>
                  <span v-if="isInLibrary(item)" class="badge badge-info badge-xs">In Library</span>
                </div>
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
              <button class="btn btn-primary btn-xs btn-outline" @click="searchDigital(item)" v-tooltip="'Download digital copy'" :disabled="digitalDownloadLoading">
                <i class="mdi mdi-download mdi-18px" />
              </button>
              <button class="btn btn-info btn-xs btn-outline" @click="startEdit(item)" v-tooltip="'Edit'">
                <i class="mdi mdi-pencil mdi-18px" />
              </button>
              <button class="btn btn-error btn-xs btn-outline" @click="deleteItem(item.id)" v-tooltip="'Remove'">
                <i class="mdi mdi-delete mdi-18px" />
              </button>
            </div>
          </div>
          <!-- File status row — full width, inside the card -->
          <div v-if="getItemStatus(item)" class="pt-2 border-t border-base-content/10">
            <div class="flex items-center gap-2 text-xs">
              <span class="loading loading-spinner loading-xs text-primary" />
              <span class="font-medium capitalize">{{ statusLabel(getItemStatus(item)) }}</span>
              <span v-if="getItemStatus(item).progress" class="opacity-60">{{ Math.round(getItemStatus(item).progress) }}%</span>
            </div>
            <progress
              class="progress progress-primary w-full h-1.5 mt-1"
              :value="getItemStatus(item).progress || 0"
              max="100"
            />
          </div>
          <div v-else-if="getItemFile(item)" class="pt-2 border-t border-base-content/10 flex items-center gap-2 flex-wrap">
            <span class="text-success text-xs font-medium">✓ {{ getItemFile(item).format.toUpperCase() }} · {{ getItemFile(item).size }}</span>
            <a :href="dataService.getWishlistFileUrl(getItemFile(item).path, false)" class="btn btn-xs btn-outline" download>
              <i class="mdi mdi-download mdi-14px mr-1" />Save
            </a>
            <a v-if="getItemFile(item).format === 'pdf'" :href="dataService.getWishlistFileUrl(getItemFile(item).path, true)" target="_blank" class="btn btn-xs btn-outline btn-info">
              <i class="mdi mdi-file-pdf mdi-14px mr-1" />Open PDF
            </a>
            <button class="btn btn-xs btn-outline btn-error" @click="deleteEbook(getItemFile(item))" v-tooltip="'Delete file'">
              <i class="mdi mdi-delete mdi-14px" />
            </button>
          </div>
        </div>
      </div>
    </div>
  <!-- Digital search modal -->
  <dialog v-if="digitalSearchItem" class="modal modal-open">
    <div class="modal-box max-w-2xl">
      <h3 class="font-bold text-lg mb-3"><i class="mdi mdi-magnify mr-1" /> Select a version to download</h3>
      <div v-if="digitalSearchLoading" class="flex justify-center py-6"><span class="loading loading-spinner" /></div>
      <div v-else-if="digitalSearchResults.length === 0" class="text-center py-4 text-base-content/50">No results found</div>
      <div v-else class="space-y-2 max-h-96 overflow-y-auto">
        <div
          v-for="(release, index) in digitalSearchResults" :key="index"
          class="flex items-center justify-between p-3 rounded-lg bg-base-200 hover:bg-base-300 cursor-pointer"
          @click="triggerWishlistDownload(release)"
        >
          <div class="flex-1 min-w-0">
            <div class="font-medium text-sm truncate">{{ release.title }}</div>
            <div class="text-xs text-base-content/60 flex gap-2 mt-1">
              <span v-if="release.extra?.author">{{ release.extra.author }}</span>
              <span v-if="release.extra?.publisher">{{ release.extra.publisher }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2 ml-3 shrink-0">
            <span class="badge badge-sm rounded-md">{{ release.format }}</span>
            <span class="badge badge-ghost badge-sm">{{ release.size }}</span>
            <span v-if="release.language" class="badge badge-outline badge-sm uppercase">{{ release.language }}</span>
          </div>
        </div>
      </div>
      <div class="modal-action">
        <button class="btn btn-sm" @click="digitalSearchItem = null">Close</button>
      </div>
    </div>
    <div class="modal-backdrop" @click="digitalSearchItem = null"></div>
  </dialog>

  <!-- Edit modal -->
  <div v-if="editingItem" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50" @click.self="editingItem = null">
    <div class="bg-base-100 rounded-xl p-6 w-full max-w-md mx-4">
      <h2 class="text-lg font-bold mb-4">Edit {{ editingItem.itemType === 'BOOK' ? 'Book' : 'Concept' }}</h2>
      <div v-if="editingItem.itemType === 'BOOK'" class="flex flex-col gap-3">
        <fieldset class="fieldset"><legend class="fieldset-legend">Title</legend>
          <input v-model="editForm.title" type="text" class="input w-full focus:input-accent" /></fieldset>
        <fieldset class="fieldset"><legend class="fieldset-legend">Authors</legend>
          <input v-model="editForm.authors" type="text" class="input w-full focus:input-accent" /></fieldset>
        <fieldset class="fieldset"><legend class="fieldset-legend">ISBN</legend>
          <input v-model="editForm.isbn" type="text" class="input w-full focus:input-accent" /></fieldset>
        <fieldset class="fieldset"><legend class="fieldset-legend">Publisher</legend>
          <input v-model="editForm.publisher" type="text" class="input w-full focus:input-accent" /></fieldset>
        <fieldset class="fieldset"><legend class="fieldset-legend">Notes</legend>
          <textarea v-model="editForm.description" class="textarea w-full focus:textarea-accent" rows="2" /></fieldset>
      </div>
      <div v-else class="flex flex-col gap-3">
        <fieldset class="fieldset"><legend class="fieldset-legend">Description</legend>
          <textarea v-model="editForm.description" class="textarea w-full focus:textarea-accent" rows="3" /></fieldset>
      </div>
      <div class="flex gap-2 mt-4">
        <button class="btn btn-ghost flex-1" @click="editingItem = null">Cancel</button>
        <button class="btn btn-primary flex-1" @click="saveEdit">Save</button>
      </div>
    </div>
  </div>
  </section>
</template>
