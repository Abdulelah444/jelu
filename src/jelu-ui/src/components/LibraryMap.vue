<script setup lang="ts">
import { useOruga } from "@oruga-ui/oruga-next"
import { useTitle } from '@vueuse/core'
import { computed, onMounted, Ref, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhysicalLocation, PhysicalBookcase, PhysicalShelf, PhysicalShelfBook } from "../model/PhysicalLibrary"
import { UserBook } from "../model/Book"
import { Page } from "../model/Page"
import dataService from "../services/DataService"
import { ObjectUtils } from "../utils/ObjectUtils"
import ShelfAssignmentModal from "./ShelfAssignmentModal.vue"
import useTypography from "../composables/typography"

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })
useTitle('Jelu | Library Map')
const oruga = useOruga()
const { typographyClasses } = useTypography()

const locations: Ref<Array<PhysicalLocation>> = ref([])
const bookcasesByLocation: Ref<Map<string, Array<PhysicalBookcase>>> = ref(new Map())
const expandedLocations: Ref<Set<string>> = ref(new Set())
const expandedBookcases: Ref<Set<string>> = ref(new Set())
const shelvesByBookcase: Ref<Map<string, Array<PhysicalShelf>>> = ref(new Map())
const shelfBooks: Ref<Map<string, Array<PhysicalShelfBook>>> = ref(new Map())
const bookDetailsCache: Ref<Map<string, UserBook>> = ref(new Map())
const bookcaseBookCount: Ref<Map<string, number>> = ref(new Map())
const unassignedBooks: Ref<Page<UserBook> | null> = ref(null)
const searchQuery: Ref<string> = ref('')
const filterAuthor: Ref<string> = ref('')
const filterTag: Ref<string> = ref('')
const filterLanguage: Ref<string> = ref('')
const editingShelfId: Ref<string | null> = ref(null)
const editingShelfLabel: Ref<string> = ref('')
const allBookcases = computed(() => {
  const all: Array<PhysicalBookcase> = []
  bookcasesByLocation.value.forEach(bcs => all.push(...bcs))
  return all
})
const draggedBook: Ref<{ userBookId: string, fromShelfId: string, fromBookcaseId: string } | null> = ref(null)
const dragOverShelfId: Ref<string | null> = ref(null)
const shelfViewMode: Ref<'list' | 'covers'> = ref('covers')
const editingBookcaseId: Ref<string | null> = ref(null)
const editingBookcaseName: Ref<string> = ref('')
const editingBookcaseShelfCount: Ref<number> = ref(0)
const editingLocationId: Ref<string | null> = ref(null)
const editingLocationName: Ref<string> = ref('')

const uniqueAuthors = computed(() => {
  if (!unassignedBooks.value) return []
  const authors = new Set<string>()
  unassignedBooks.value.content.forEach(ub => {
    ub.book.authors?.forEach(a => authors.add(a.name))
  })
  return Array.from(authors).sort()
})

const uniqueTags = computed(() => {
  if (!unassignedBooks.value) return []
  const tags = new Set<string>()
  unassignedBooks.value.content.forEach(ub => {
    ub.book.tags?.forEach(t => tags.add(t.name))
  })
  return Array.from(tags).sort()
})

const uniqueLanguages = computed(() => {
  if (!unassignedBooks.value) return []
  const langs = new Set<string>()
  unassignedBooks.value.content.forEach(ub => {
    if (ub.book.language) langs.add(ub.book.language)
  })
  return Array.from(langs).sort()
})

const filteredUnassigned = computed(() => {
  if (!unassignedBooks.value) return []
  let books = unassignedBooks.value.content
  const q = searchQuery.value.toLowerCase().trim()
  if (q) {
    books = books.filter(ub =>
      ub.book.title?.toLowerCase().includes(q) ||
      ub.book.authors?.some(a => a.name.toLowerCase().includes(q)) ||
      ub.book.isbn13?.includes(q) ||
      ub.book.isbn10?.includes(q)
    )
  }
  if (filterAuthor.value) {
    books = books.filter(ub =>
      ub.book.authors?.some(a => a.name === filterAuthor.value)
    )
  }
  if (filterTag.value) {
    books = books.filter(ub =>
      ub.book.tags?.some(t => t.name === filterTag.value)
    )
  }
  if (filterLanguage.value) {
    books = books.filter(ub =>
      ub.book.language === filterLanguage.value
    )
  }
  return books
})
const checkedUnassigned: Ref<Array<string>> = ref([])
const selectAllUnassigned: Ref<boolean> = ref(false)

const newLocationName: Ref<string> = ref("")
const newBookcaseName: Ref<string> = ref("")
const newBookcaseShelfCount: Ref<number> = ref(3)
const addingBookcaseForLocation: Ref<string | null> = ref(null)
const showAddLocation: Ref<boolean> = ref(false)
const loadingBookcase: Ref<string | null> = ref(null)

const loadLocations = async () => {
  try {
    locations.value = await dataService.getPhysicalLocations()
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error loading locations", 4000)
  }
}

const toggleLocation = async (locationId: string) => {
  if (expandedLocations.value.has(locationId)) {
    expandedLocations.value.delete(locationId)
  } else {
    expandedLocations.value.add(locationId)
    try {
      const bookcases = await dataService.getPhysicalBookcases(locationId)
      bookcasesByLocation.value.set(locationId, bookcases)
      // Auto-expand and load all bookcases
      for (const bc of bookcases) {
        if (bc.id && !expandedBookcases.value.has(bc.id)) {
          toggleBookcase(bc.id)
        }
      }
    } catch (error) {
      ObjectUtils.toast(oruga, "danger", "Error loading bookcases", 4000)
    }
  }
}

const loadBookcaseCounts = async (bookcases: Array<any>) => {
  for (const bc of bookcases) {
    if (bc.id && !bookcaseBookCount.value.has(bc.id)) {
      try {
        const fullBc = await dataService.getPhysicalBookcaseById(bc.id)
        let total = 0
        if (fullBc.shelves) {
          for (const shelf of fullBc.shelves) {
            if (shelf.id) {
              const books = await dataService.getBooksOnShelf(shelf.id)
              total += books.length
            }
          }
        }
        bookcaseBookCount.value.set(bc.id, total)
      } catch (e) {
        // silently fail — count just won't show
      }
    }
  }
}

const toggleBookcase = async (bookcaseId: string) => {
  if (expandedBookcases.value.has(bookcaseId)) {
    expandedBookcases.value.delete(bookcaseId)
    return
  }
  expandedBookcases.value.add(bookcaseId)
  loadingBookcase.value = bookcaseId
  try {
    const bookcase = await dataService.getPhysicalBookcaseById(bookcaseId)
    if (bookcase.shelves) {
      shelvesByBookcase.value.set(bookcaseId, bookcase.shelves)
      let totalBooks = 0
      const allUserBookIds: Array<string> = []
      for (const shelf of bookcase.shelves) {
        if (shelf.id) {
          const books = await dataService.getBooksOnShelf(shelf.id)
          shelfBooks.value.set(shelf.id, books)
          totalBooks += books.length
          for (const sb of books) {
            if (!bookDetailsCache.value.has(sb.userBookId)) {
              allUserBookIds.push(sb.userBookId)
            }
          }
        }
      }
      bookcaseBookCount.value.set(bookcaseId, totalBooks)
      const batchSize = 5
      for (let i = 0; i < allUserBookIds.length; i += batchSize) {
        const batch = allUserBookIds.slice(i, i + batchSize)
        await Promise.all(batch.map(async (ubId) => {
          try {
            const ub = await dataService.getUserBookById(ubId)
            bookDetailsCache.value.set(ubId, ub)
          } catch (e) { /* skip */ }
        }))
      }
    }
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error loading bookcase", 4000)
  } finally {
    loadingBookcase.value = null
  }
}

const refreshBookcase = async (bookcaseId: string) => {
  expandedBookcases.value.delete(bookcaseId)
  await toggleBookcase(bookcaseId)
}

const addLocation = async () => {
  if (newLocationName.value.trim().length === 0) return
  try {
    await dataService.createPhysicalLocation({ name: newLocationName.value.trim() })
    newLocationName.value = ""
    showAddLocation.value = false
    await loadLocations()
    ObjectUtils.toast(oruga, "success", "Location created", 2000)
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error creating location", 4000)
  }
}

const deleteLocation = async (id: string) => {
  try {
    await dataService.deletePhysicalLocation(id)
    expandedLocations.value.delete(id)
    bookcasesByLocation.value.delete(id)
    await loadLocations()
    ObjectUtils.toast(oruga, "success", "Location deleted", 2000)
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error deleting location", 4000)
  }
}

const startAddBookcase = (locationId: string) => {
  addingBookcaseForLocation.value = locationId
  newBookcaseName.value = ""
  newBookcaseShelfCount.value = 3
}

const addBookcase = async (locationId: string) => {
  if (newBookcaseName.value.trim().length === 0) return
  try {
    await dataService.createPhysicalBookcase(locationId, {
      name: newBookcaseName.value.trim(),
      shelfCount: newBookcaseShelfCount.value,
    })
    addingBookcaseForLocation.value = null
    const bookcases = await dataService.getPhysicalBookcases(locationId)
    bookcasesByLocation.value.set(locationId, bookcases)
    ObjectUtils.toast(oruga, "success", "Bookcase created", 2000)
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error creating bookcase", 4000)
  }
}

const deleteBookcase = async (locationId: string, bookcaseId: string) => {
  try {
    await dataService.deletePhysicalBookcase(bookcaseId)
    expandedBookcases.value.delete(bookcaseId)
    const bookcases = await dataService.getPhysicalBookcases(locationId)
    bookcasesByLocation.value.set(locationId, bookcases)
    ObjectUtils.toast(oruga, "success", "Bookcase deleted", 2000)
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error deleting bookcase", 4000)
  }
}

const removeFromShelf = async (shelfId: string, userBookId: string, bookcaseId: string) => {
  try {
    await dataService.removeBookFromShelf(shelfId, userBookId)
    const books = await dataService.getBooksOnShelf(shelfId)
    shelfBooks.value.set(shelfId, books)
    let total = 0
    const shelves = shelvesByBookcase.value.get(bookcaseId) || []
    for (const s of shelves) {
      if (s.id && shelfBooks.value.has(s.id)) {
        total += shelfBooks.value.get(s.id)!.length
      }
    }
    bookcaseBookCount.value.set(bookcaseId, total)
    await loadUnassigned()
    ObjectUtils.toast(oruga, "success", "Book removed", 2000)
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error removing book", 4000)
  }
}

const loadUnassigned = async () => {
  try {
    unassignedBooks.value = await dataService.getUnassignedBooks(0, 500)
    checkedUnassigned.value = []
    selectAllUnassigned.value = false
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error loading unassigned books", 4000)
  }
}

const toggleSelectAll = () => {
  if (selectAllUnassigned.value) {
    checkedUnassigned.value = unassignedBooks.value?.content.map(ub => ub.id!).filter(id => id != null) || []
  } else {
    checkedUnassigned.value = []
  }
}

const openAssignModal = (userBookIds: Array<string>) => {
  oruga.modal.open({
    component: ShelfAssignmentModal,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'clip',
    props: { userBookIds: userBookIds },
    events: {
      assigned: async () => {
        await loadUnassigned()
        for (const bcId of Array.from(expandedBookcases.value)) {
          await refreshBookcase(bcId)
        }
      }
    }
  })
}

const getBookTitle = (userBookId: string): string => {
  const ub = bookDetailsCache.value.get(userBookId)
  return ub ? ub.book.title : "..."
}

const getBookImage = (userBookId: string): string | null => {
  const ub = bookDetailsCache.value.get(userBookId)
  return ub?.book?.image || null
}

const onDragStart = (event: DragEvent, userBookId: string, fromShelfId: string, fromBookcaseId: string) => {
  draggedBook.value = { userBookId, fromShelfId, fromBookcaseId }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', userBookId)
  }
}

const onDragOver = (event: DragEvent, shelfId: string) => {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragOverShelfId.value = shelfId
}

const onDragLeave = () => {
  dragOverShelfId.value = null
}

const onDrop = async (event: DragEvent, toShelfId: string, toBookcaseId: string) => {
  event.preventDefault()
  dragOverShelfId.value = null
  if (!draggedBook.value) {
    // Dropped from unassigned books
    const userBookId = event.dataTransfer?.getData('text/plain')
    if (!userBookId) return
    try {
      await dataService.assignBookToShelf(toShelfId, { userBookId })
      const toBooks = await dataService.getBooksOnShelf(toShelfId)
      shelfBooks.value.set(toShelfId, toBooks)
      const updateCount = (bcId: string) => {
        let total = 0
        const shelves = shelvesByBookcase.value.get(bcId) || []
        for (const s of shelves) {
          if (s.id && shelfBooks.value.has(s.id)) total += shelfBooks.value.get(s.id)!.length
        }
        bookcaseBookCount.value.set(bcId, total)
      }
      updateCount(toBookcaseId)
      await loadUnassigned()
      ObjectUtils.toast(oruga, "success", "Book assigned", 2000)
    } catch (e) {
      console.log("Failed to assign book: " + e)
      ObjectUtils.toast(oruga, "danger", "Failed to assign book", 3000)
    }
    return
  }
  const { userBookId, fromShelfId, fromBookcaseId } = draggedBook.value
  if (fromShelfId === toShelfId) {
    draggedBook.value = null
    return
  }
  try {
    // Remove from old shelf
    await dataService.removeBookFromShelf(fromShelfId, userBookId)
    // Add to new shelf
    await dataService.assignBookToShelf(toShelfId, { userBookId })
    // Refresh both shelves
    const fromBooks = await dataService.getBooksOnShelf(fromShelfId)
    shelfBooks.value.set(fromShelfId, fromBooks)
    const toBooks = await dataService.getBooksOnShelf(toShelfId)
    shelfBooks.value.set(toShelfId, toBooks)
    // Update book counts
    const updateCount = (bcId: string) => {
      let total = 0
      const shelves = shelvesByBookcase.value.get(bcId) || []
      for (const s of shelves) {
        if (s.id && shelfBooks.value.has(s.id)) total += shelfBooks.value.get(s.id)!.length
      }
      bookcaseBookCount.value.set(bcId, total)
    }
    updateCount(fromBookcaseId)
    if (toBookcaseId !== fromBookcaseId) updateCount(toBookcaseId)
    ObjectUtils.toast(oruga, "success", "Book moved", 2000)
  } catch (e) {
    console.log("Failed to move book: " + e)
    ObjectUtils.toast(oruga, "danger", "Failed to move book", 3000)
  }
  draggedBook.value = null
}

const onDropFromUnassigned = async (event: DragEvent, toShelfId: string, toBookcaseId: string) => {
  event.preventDefault()
  dragOverShelfId.value = null
  const userBookId = event.dataTransfer?.getData('text/plain')
  if (!userBookId) return
  // If it came from a shelf, use onDrop instead
  if (draggedBook.value) {
    await onDrop(event, toShelfId, toBookcaseId)
    return
  }
  try {
    await dataService.assignBookToShelf(toShelfId, { userBookId })
    const toBooks = await dataService.getBooksOnShelf(toShelfId)
    shelfBooks.value.set(toShelfId, toBooks)
    const updateCount = (bcId: string) => {
      let total = 0
      const shelves = shelvesByBookcase.value.get(bcId) || []
      for (const s of shelves) {
        if (s.id && shelfBooks.value.has(s.id)) total += shelfBooks.value.get(s.id)!.length
      }
      bookcaseBookCount.value.set(bcId, total)
    }
    updateCount(toBookcaseId)
    await loadUnassigned()
    ObjectUtils.toast(oruga, "success", "Book assigned", 2000)
  } catch (e) {
    console.log("Failed to assign book: " + e)
  }
}

const onDragStartUnassigned = (event: DragEvent, userBookId: string) => {
  draggedBook.value = null
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', userBookId)
  }
}

const startEditLocation = (location: any) => {
  editingLocationId.value = location.id!
  editingLocationName.value = location.name
}

const saveLocation = async (locationId: string) => {
  try {
    await dataService.updatePhysicalLocation(locationId, { name: editingLocationName.value })
    const loc = locations.value.find(l => l.id === locationId)
    if (loc) loc.name = editingLocationName.value
    editingLocationId.value = null
    ObjectUtils.toast(oruga, "success", "Location updated", 2000)
  } catch (e) {
    console.log("Failed to update location: " + e)
    ObjectUtils.toast(oruga, "danger", "Failed to update location", 3000)
  }
}

const cancelEditLocation = () => {
  editingLocationId.value = null
}

const startEditBookcase = (bookcase: any) => {
  editingBookcaseId.value = bookcase.id!
  editingBookcaseName.value = bookcase.name
  editingBookcaseShelfCount.value = bookcase.shelfCount
}

const saveBookcase = async (bookcaseId: string, locationId: string) => {
  try {
    const updated = await dataService.updatePhysicalBookcase(bookcaseId, {
      name: editingBookcaseName.value,
      shelfCount: editingBookcaseShelfCount.value,
    })
    // Update local state
    const bookcases = bookcasesByLocation.value.get(locationId)
    if (bookcases) {
      const bc = bookcases.find(b => b.id === bookcaseId)
      if (bc) {
        bc.name = updated.name
        bc.shelfCount = updated.shelfCount
      }
    }
    // Reload shelves for this bookcase since count may have changed
    if (expandedBookcases.value.has(bookcaseId)) {
      const loc = locations.value.find(l => l.id === locationId)
      if (loc) {
        const bc = bookcasesByLocation.value.get(locationId)?.find(b => b.id === bookcaseId)
        if (bc && bc.shelves) {
          shelvesByBookcase.value.set(bookcaseId, bc.shelves)
        } else {
          // Refetch
          const fullBc = await dataService.getPhysicalBookcaseById(bookcaseId)
          if (fullBc.shelves) shelvesByBookcase.value.set(bookcaseId, fullBc.shelves)
        }
      }
    }
    editingBookcaseId.value = null
    ObjectUtils.toast(oruga, "success", "Bookcase updated", 2000)
  } catch (e) {
    console.log("Failed to update bookcase: " + e)
    ObjectUtils.toast(oruga, "danger", "Failed to update bookcase", 3000)
  }
}

const cancelEditBookcase = () => {
  editingBookcaseId.value = null
}

const shelfBookCount = (shelfId: string): number => {
  if (shelfBooks.value.has(shelfId)) return shelfBooks.value.get(shelfId)!.length
  return 0
}

const startEditLabel = (shelf: any) => {
  editingShelfId.value = shelf.id!
  editingShelfLabel.value = shelf.label || ''
}

const saveShelfLabel = async (shelfId: string, bookcaseId: string) => {
  try {
    await dataService.updatePhysicalShelf(shelfId, { label: editingShelfLabel.value || undefined })
    // Update local state
    const shelves = shelvesByBookcase.value.get(bookcaseId)
    if (shelves) {
      const shelf = shelves.find(s => s.id === shelfId)
      if (shelf) shelf.label = editingShelfLabel.value || undefined
    }
    editingShelfId.value = null
    editingShelfLabel.value = ''
  } catch (e) {
    console.log("Failed to save shelf label: " + e)
  }
}

const cancelEditLabel = () => {
  editingShelfId.value = null
  editingShelfLabel.value = ''
}

onMounted(() => {
  loadLocations()
  loadUnassigned()
})

const moveShelfUp = async (bookcaseId: string, shelfId: string) => {
  const shelves = shelvesByBookcase.value.get(bookcaseId)
  if (!shelves) return
  const idx = shelves.findIndex(s => s.id === shelfId)
  if (idx <= 0) return
  const ids = shelves.map(s => s.id!)
  ;[ids[idx - 1], ids[idx]] = [ids[idx], ids[idx - 1]]
  try {
    await dataService.reorderShelves(bookcaseId, ids)
    await refreshBookcase(bookcaseId)
    ObjectUtils.toast(oruga, "success", "Shelf moved up", 1500)
  } catch (e) {
    ObjectUtils.toast(oruga, "danger", "Failed to reorder", 3000)
  }
}

const moveShelfDown = async (bookcaseId: string, shelfId: string) => {
  const shelves = shelvesByBookcase.value.get(bookcaseId)
  if (!shelves) return
  const idx = shelves.findIndex(s => s.id === shelfId)
  if (idx < 0 || idx >= shelves.length - 1) return
  const ids = shelves.map(s => s.id!)
  ;[ids[idx], ids[idx + 1]] = [ids[idx + 1], ids[idx]]
  try {
    await dataService.reorderShelves(bookcaseId, ids)
    await refreshBookcase(bookcaseId)
    ObjectUtils.toast(oruga, "success", "Shelf moved down", 1500)
  } catch (e) {
    ObjectUtils.toast(oruga, "danger", "Failed to reorder", 3000)
  }
}

const moveShelfToOtherBookcase = async (shelfId: string, fromBookcaseId: string, targetBookcaseId: string) => {
  try {
    await dataService.moveShelfToBookcase(shelfId, targetBookcaseId)
    await refreshBookcase(fromBookcaseId)
    await refreshBookcase(targetBookcaseId)
    ObjectUtils.toast(oruga, "success", "Shelf moved to another bookcase", 2000)
  } catch (e) {
    ObjectUtils.toast(oruga, "danger", "Failed to move shelf", 3000)
  }
}

</script>

<template>
  <div class="p-4 sm:p-6">
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl" :class="typographyClasses">{{ t('library_map.title') }}</h1>
      <div class="flex gap-2">
        <div class="btn-group">
          <button class="btn btn-sm" :class="shelfViewMode === 'list' ? 'btn-active' : 'btn-ghost'" @click="shelfViewMode = 'list'">
            <i class="mdi mdi-format-list-bulleted mdi-18px" />
          </button>
          <button class="btn btn-sm" :class="shelfViewMode === 'covers' ? 'btn-active' : 'btn-ghost'" @click="shelfViewMode = 'covers'">
            <i class="mdi mdi-view-grid mdi-18px" />
          </button>
        </div>
        <button class="btn btn-primary btn-sm" @click="showAddLocation = !showAddLocation">
        <i class="mdi mdi-plus mdi-18px" /> {{ t('library_map.add_location') }}
        </button>
      </div>
    </div>

    <div v-if="showAddLocation" class="card bg-base-200 p-4 mb-4">
      <div class="flex gap-2 items-end">
        <div class="form-control flex-1">
          <label class="label"><span class="label-text">{{ t('library_map.location_name') }}</span></label>
          <input v-model="newLocationName" type="text" class="input input-bordered w-full" @keyup.enter="addLocation" />
        </div>
        <button class="btn btn-success btn-sm" @click="addLocation">{{ t('labels.save') }}</button>
        <button class="btn btn-ghost btn-sm" @click="showAddLocation = false">{{ t('labels.cancel') }}</button>
      </div>
    </div>

    <div v-if="locations.length === 0 && !showAddLocation" class="text-center py-10 opacity-60">
      {{ t('library_map.no_locations') }}
    </div>

    <div v-for="location in locations" :key="location.id!" class="mb-6">
      <div class="card bg-base-200 border border-base-300">
        <!-- Editing mode -->
        <div v-if="editingLocationId === location.id!" class="card-body p-3 flex-row items-center gap-2">
          <i class="mdi mdi-map-marker mdi-24px text-primary" />
          <input v-model="editingLocationName" type="text" class="input input-sm input-bordered flex-1"
                 @keyup.enter="saveLocation(location.id!)" @keyup.escape="cancelEditLocation" />
          <button class="btn btn-success btn-xs" @click.stop="saveLocation(location.id!)">
            <i class="mdi mdi-check mdi-14px" />
          </button>
          <button class="btn btn-ghost btn-xs" @click.stop="cancelEditLocation">
            <i class="mdi mdi-close mdi-14px" />
          </button>
        </div>
        <!-- Normal display -->
        <div v-else class="card-body p-3 flex-row items-center gap-3 cursor-pointer" @click="toggleLocation(location.id!)">
          <i class="mdi mdi-map-marker mdi-24px text-primary" />
          <i :class="expandedLocations.has(location.id!) ? 'mdi mdi-chevron-down' : 'mdi mdi-chevron-right'" class="mdi-18px opacity-50" />
          <h2 class="text-lg font-bold flex-1">{{ location.name }}</h2>
          <span v-if="bookcasesByLocation.has(location.id!)" class="text-xs bg-base-300 px-1.5 py-0.5 rounded-md">
            {{ bookcasesByLocation.get(location.id!)?.length || 0 }} bookcases
          </span>
          <button class="btn btn-ghost btn-xs opacity-40 hover:opacity-100" @click.stop="startEditLocation(location)">
            <i class="mdi mdi-pencil mdi-14px" />
          </button>
          <button class="btn btn-ghost btn-xs text-error" @click.stop="deleteLocation(location.id!)">
            <i class="mdi mdi-delete mdi-14px" />
          </button>
        </div>
      </div>

      <div v-if="expandedLocations.has(location.id!)" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4 mt-3">
        <div v-for="bookcase in (bookcasesByLocation.get(location.id!) || [])" :key="bookcase.id!"
             class="card bg-base-200">
          <div class="card-body p-3">
            <!-- Editing mode -->
            <div v-if="editingBookcaseId === bookcase.id!" class="space-y-2 p-1">
              <div class="flex gap-2 items-center">
                <input v-model="editingBookcaseName" type="text" class="input input-sm input-bordered flex-1" placeholder="Bookcase name" />
              </div>
              <div class="flex gap-2 items-center">
                <label class="text-xs opacity-70">Shelves:</label>
                <input v-model.number="editingBookcaseShelfCount" type="number" min="1" max="20" class="input input-sm input-bordered w-20" />
              </div>
              <div class="flex gap-1">
                <button class="btn btn-success btn-xs" @click.stop="saveBookcase(bookcase.id!, location.id!)">
                  <i class="mdi mdi-check mdi-14px" /> Save
                </button>
                <button class="btn btn-ghost btn-xs" @click.stop="cancelEditBookcase">Cancel</button>
              </div>
            </div>
            <!-- Normal display -->
            <div v-else class="cursor-pointer" @click="toggleBookcase(bookcase.id!)">
              <div class="flex items-start gap-2">
                <i class="mdi mdi-bookshelf mdi-18px mt-0.5" />
                <h3 class="font-bold text-sm leading-tight line-clamp-2 flex-1 min-h-[2.5rem]">{{ bookcase.name }}</h3>
                <button class="btn btn-ghost btn-xs p-0 opacity-40 hover:opacity-100 flex-shrink-0" @click.stop="startEditBookcase(bookcase)">
                  <i class="mdi mdi-pencil mdi-12px" />
                </button>
                <button class="btn btn-ghost btn-xs p-0 text-error flex-shrink-0" @click.stop="deleteBookcase(location.id!, bookcase.id!)">
                  <i class="mdi mdi-delete mdi-12px" />
                </button>
              </div>
              <div class="flex items-center gap-2 mt-1">
                <span class="text-xs bg-base-300 px-1.5 py-0.5 rounded-md flex items-center gap-1"><i class="mdi mdi-view-headline mdi-14px" />{{ bookcase.shelfCount }}</span>
                <span v-if="bookcaseBookCount.has(bookcase.id!)" class="text-xs bg-primary text-primary-content px-1.5 py-0.5 rounded-md flex items-center gap-1">
                  <i class="mdi mdi-book-multiple mdi-14px" />{{ bookcaseBookCount.get(bookcase.id!) }}
                </span>
              </div>
            </div>

            <div v-if="loadingBookcase === bookcase.id!" class="flex justify-center py-4">
              <span class="loading loading-spinner loading-md"></span>
            </div>

            <div v-else-if="expandedBookcases.has(bookcase.id!) && shelvesByBookcase.has(bookcase.id!)" class="mt-2 space-y-1">
              <div v-for="shelf in shelvesByBookcase.get(bookcase.id!)" :key="shelf.id!"
                   class="bg-base-100 rounded px-2 py-1 border-l-4 transition-colors duration-150"
                   :class="dragOverShelfId === shelf.id! ? 'border-primary bg-primary/10' : 'border-secondary'"
                   @dragover="onDragOver($event, shelf.id!)"
                   @dragleave="onDragLeave"
                   @drop="onDrop($event, shelf.id!, bookcase.id!)">
                <div class="text-xs font-mono opacity-70 mb-1 flex items-center gap-1">
                  <span v-if="!shelf.label">Shelf {{ shelf.position }}</span>
                  <template v-if="editingShelfId === shelf.id!">
                    <input
                      v-model="editingShelfLabel"
                      type="text"
                      placeholder="e.g. Fiction, Top shelf..."
                      class="input input-xs input-bordered w-32"
                      @keyup.enter="saveShelfLabel(shelf.id!, bookcase.id!)"
                      @keyup.escape="cancelEditLabel"
                    />
                    <button class="btn btn-ghost btn-xs p-0 text-success" @click="saveShelfLabel(shelf.id!, bookcase.id!)">
                      <i class="mdi mdi-check mdi-14px" />
                    </button>
                    <button class="btn btn-ghost btn-xs p-0" @click="cancelEditLabel">
                      <i class="mdi mdi-close mdi-14px" />
                    </button>
                  </template>
                  <template v-else>
                    <span v-if="shelf.label" class="text-xs bg-accent text-accent-content px-1.5 py-0.5 rounded-md">{{ shelf.label }}</span>
                    <button class="btn btn-ghost btn-xs p-0 opacity-40 hover:opacity-100" @click="startEditLabel(shelf)">
                      <i class="mdi mdi-pencil mdi-12px" />
                    </button>
                  </template>
                  <span class="ml-auto flex items-center gap-0.5">
                    <button class="btn btn-ghost btn-xs p-0 opacity-40 hover:opacity-100" @click="moveShelfUp(bookcase.id!, shelf.id!)">
                      <i class="mdi mdi-arrow-up mdi-14px" />
                    </button>
                    <button class="btn btn-ghost btn-xs p-0 opacity-40 hover:opacity-100" @click="moveShelfDown(bookcase.id!, shelf.id!)">
                      <i class="mdi mdi-arrow-down mdi-14px" />
                    </button>
                    <select
                      class="select select-xs select-ghost opacity-40 hover:opacity-100 w-20"
                      @change="(e) => { const t = (e.target as HTMLSelectElement); if (t.value) { moveShelfToOtherBookcase(shelf.id!, bookcase.id!, t.value); t.value = ''; } }"
                    >
                      <option value="" selected>Move</option>
                      <option v-for="bc in allBookcases.filter(b => b.id !== bookcase.id)" :key="bc.id" :value="bc.id!">{{ bc.name }}</option>
                    </select>
                    <span v-if="shelfBooks.has(shelf.id!)" class="text-xs bg-base-300 px-1.5 py-0.5 rounded-md ml-1">
                      {{ shelfBookCount(shelf.id!) }}
                    </span>
                  </span>
                </div>
                <div v-if="shelfBooks.has(shelf.id!)">
                  <div v-if="shelfBooks.get(shelf.id!)!.length === 0" class="text-xs opacity-40 italic">empty</div>
                  <!-- List view -->
                  <template v-else-if="shelfViewMode === 'list'">
                    <div v-for="sb in shelfBooks.get(shelf.id!)" :key="sb.id"
                         class="flex items-center gap-1 py-0.5 cursor-grab active:cursor-grabbing"
                         draggable="true"
                         @dragstart="onDragStart($event, sb.userBookId, shelf.id!, bookcase.id!)">
                      <img v-if="getBookImage(sb.userBookId)" :src="'/files/' + getBookImage(sb.userBookId)" class="w-5 h-7 object-cover rounded-sm" />
                      <router-link :to="{ name: 'book-detail', params: { bookId: sb.userBookId } }"
                                   class="text-xs link link-hover truncate flex-1">
                        {{ getBookTitle(sb.userBookId) }}
                      </router-link>
                      <button class="btn btn-ghost btn-xs p-0 text-error" @click="removeFromShelf(shelf.id!, sb.userBookId, bookcase.id!)">
                        <i class="mdi mdi-close mdi-14px" />
                      </button>
                    </div>
                  </template>
                  <!-- Covers view -->
                  <div v-else class="flex flex-wrap gap-1 mt-1">
                    <div v-for="sb in shelfBooks.get(shelf.id!)" :key="sb.id"
                         class="relative group cursor-grab active:cursor-grabbing"
                         draggable="true"
                         @dragstart="onDragStart($event, sb.userBookId, shelf.id!, bookcase.id!)">
                      <router-link :to="{ name: 'book-detail', params: { bookId: sb.userBookId } }">
                        <img v-if="getBookImage(sb.userBookId)"
                             :src="'/files/' + getBookImage(sb.userBookId)"
                             :title="getBookTitle(sb.userBookId)"
                             class="w-10 h-14 object-cover rounded-sm hover:ring-2 hover:ring-primary transition-all" />
                        <div v-else
                             :title="getBookTitle(sb.userBookId)"
                             class="w-10 h-14 bg-base-300 rounded-sm flex items-center justify-center text-xs opacity-50 hover:ring-2 hover:ring-primary">
                          <i class="mdi mdi-book mdi-18px" />
                        </div>
                      </router-link>
                      <button class="btn btn-ghost btn-xs p-0 text-error absolute -top-1 -right-1 opacity-0 group-hover:opacity-100 bg-base-100 rounded-full"
                              @click="removeFromShelf(shelf.id!, sb.userBookId, bookcase.id!)">
                        <i class="mdi mdi-close mdi-12px" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="addingBookcaseForLocation === location.id!" class="card bg-base-100 p-3">
          <div class="space-y-2">
            <div class="form-control">
              <label class="label py-0"><span class="label-text text-xs">{{ t('library_map.bookcase_name') }}</span></label>
              <input v-model="newBookcaseName" type="text" class="input input-bordered input-sm" />
            </div>
            <div class="form-control">
              <label class="label py-0"><span class="label-text text-xs">{{ t('library_map.shelf_count') }}</span></label>
              <input v-model.number="newBookcaseShelfCount" type="number" min="1" max="20" class="input input-bordered input-sm w-20" />
            </div>
            <div class="flex gap-1">
              <button class="btn btn-success btn-xs" @click="addBookcase(location.id!)">{{ t('labels.save') }}</button>
              <button class="btn btn-ghost btn-xs" @click="addingBookcaseForLocation = null">{{ t('labels.cancel') }}</button>
            </div>
          </div>
        </div>

        <button v-else class="btn btn-outline btn-sm h-20" @click="startAddBookcase(location.id!)">
          <i class="mdi mdi-plus mdi-24px" /> {{ t('library_map.add_bookcase') }}
        </button>
      </div>
    </div>

    <div v-if="unassignedBooks && unassignedBooks.totalElements > 0" class="mt-10">
      <div class="flex justify-between items-center mb-3">
        <h2 class="text-xl" :class="typographyClasses">
          {{ t('library_map.unassigned_books') }} ({{ unassignedBooks.totalElements }})
        </h2>
        <div class="flex gap-2">
          <label class="flex items-center gap-1 text-sm cursor-pointer">
            <input type="checkbox" v-model="selectAllUnassigned" class="checkbox checkbox-sm" @change="toggleSelectAll" />
            Select all
          </label>
          <button v-if="checkedUnassigned.length > 0" class="btn btn-primary btn-sm"
                  @click="openAssignModal(checkedUnassigned)">
            {{ t('library_map.assign_to_shelf') }} ({{ checkedUnassigned.length }})
          </button>
        </div>
      </div>
      <!-- Search and filters -->
      <div class="flex flex-wrap gap-2 mb-3">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search by title, author, ISBN..."
          class="input input-sm input-bordered flex-1 min-w-48"
        />
        <select v-model="filterAuthor" class="select select-sm select-bordered">
          <option value="">All authors</option>
          <option v-for="a in uniqueAuthors" :key="a" :value="a">{{ a }}</option>
        </select>
        <select v-model="filterTag" class="select select-sm select-bordered">
          <option value="">All tags</option>
          <option v-for="t in uniqueTags" :key="t" :value="t">{{ t }}</option>
        </select>
        <select v-model="filterLanguage" class="select select-sm select-bordered">
          <option value="">All languages</option>
          <option v-for="l in uniqueLanguages" :key="l" :value="l">{{ l }}</option>
        </select>
        <button v-if="searchQuery || filterAuthor || filterTag || filterLanguage"
                class="btn btn-ghost btn-sm" @click="searchQuery = ''; filterAuthor = ''; filterTag = ''; filterLanguage = ''">
          <i class="mdi mdi-close mdi-18px" /> Clear
        </button>
      </div>
      <p class="text-xs opacity-60 mb-2">Showing {{ filteredUnassigned.length }} of {{ unassignedBooks.totalElements }} books</p>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        <div v-for="ub in filteredUnassigned" :key="ub.id!" class="card bg-base-200 p-2">
          <div class="flex items-center gap-2 cursor-grab active:cursor-grabbing"
               draggable="true"
               @dragstart="onDragStartUnassigned($event, ub.id!)">
            <input type="checkbox" :value="ub.id!" v-model="checkedUnassigned" class="checkbox checkbox-sm checkbox-accent" @click.stop />
            <img v-if="ub.book.image" :src="'/files/' + ub.book.image" class="w-8 h-12 object-cover rounded" />
            <router-link :to="{ name: 'book-detail', params: { bookId: ub.book.id } }"
                         class="link link-hover text-sm font-semibold truncate flex-1">
              {{ ub.book.title }}
            </router-link>
            <button class="btn btn-primary btn-xs flex-shrink-0" @click="openAssignModal([ub.id!])">
              <i class="mdi mdi-bookshelf mdi-14px" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
