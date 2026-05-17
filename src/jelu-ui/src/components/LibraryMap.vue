<script setup lang="ts">
import { useOruga } from "@oruga-ui/oruga-next"
import { useTitle } from '@vueuse/core'
import { onMounted, Ref, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhysicalLocation, PhysicalBookcase, PhysicalShelfBook } from "../model/PhysicalLibrary"
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
const shelfBooks: Ref<Map<string, Array<PhysicalShelfBook>>> = ref(new Map())
const shelfBookDetails: Ref<Map<string, UserBook>> = ref(new Map())
const unassignedBooks: Ref<Page<UserBook> | null> = ref(null)

const newLocationName: Ref<string> = ref("")
const newBookcaseName: Ref<string> = ref("")
const newBookcaseShelfCount: Ref<number> = ref(3)
const addingBookcaseForLocation: Ref<string | null> = ref(null)
const showAddLocation: Ref<boolean> = ref(false)

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
    if (!bookcasesByLocation.value.has(locationId)) {
      try {
        const bookcases = await dataService.getPhysicalBookcases(locationId)
        bookcasesByLocation.value.set(locationId, bookcases)
      } catch (error) {
        ObjectUtils.toast(oruga, "danger", "Error loading bookcases", 4000)
      }
    }
  }
}

const toggleBookcase = async (bookcaseId: string) => {
  if (expandedBookcases.value.has(bookcaseId)) {
    expandedBookcases.value.delete(bookcaseId)
  } else {
    expandedBookcases.value.add(bookcaseId)
    try {
      const bookcase = await dataService.getPhysicalBookcaseById(bookcaseId)
      if (bookcase.shelves) {
        for (const shelf of bookcase.shelves) {
          if (shelf.id) {
            const books = await dataService.getBooksOnShelf(shelf.id)
            shelfBooks.value.set(shelf.id, books)
            for (const sb of books) {
              if (!shelfBookDetails.value.has(sb.userBookId)) {
                try {
                  const ub = await dataService.getUserBookById(sb.userBookId)
                  shelfBookDetails.value.set(sb.userBookId, ub)
                } catch (e) { /* ignore */ }
              }
            }
          }
        }
      }
    } catch (error) {
      ObjectUtils.toast(oruga, "danger", "Error loading shelves", 4000)
    }
  }
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

const removeFromShelf = async (shelfId: string, userBookId: string) => {
  try {
    await dataService.removeBookFromShelf(shelfId, userBookId)
    const books = await dataService.getBooksOnShelf(shelfId)
    shelfBooks.value.set(shelfId, books)
    await loadUnassigned()
    ObjectUtils.toast(oruga, "success", "Book removed from shelf", 2000)
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error removing book", 4000)
  }
}

const loadUnassigned = async () => {
  try {
    unassignedBooks.value = await dataService.getUnassignedBooks(0, 50)
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error loading unassigned books", 4000)
  }
}

const openAssignModal = (userBookIds: Array<string>) => {
  oruga.modal.open({
    component: ShelfAssignmentModal,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'clip',
    props: {
      userBookIds: userBookIds,
    },
    events: {
      assigned: () => {
        loadUnassigned()
        expandedBookcases.value.forEach((bcId) => {
          toggleBookcase(bcId)
          toggleBookcase(bcId)
        })
      }
    }
  })
}

const getBookTitle = (userBookId: string): string => {
  const ub = shelfBookDetails.value.get(userBookId)
  return ub ? ub.book.title : userBookId.substring(0, 8) + "..."
}

onMounted(() => {
  loadLocations()
  loadUnassigned()
})
</script>

<template>
  <div class="p-4 sm:p-6 max-w-4xl mx-auto">
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl" :class="typographyClasses">{{ t('library_map.title') }}</h1>
      <button class="btn btn-primary btn-sm" @click="showAddLocation = !showAddLocation">
        <i class="mdi mdi-plus mdi-18px" />
        {{ t('library_map.add_location') }}
      </button>
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

    <div v-for="location in locations" :key="location.id!" class="mb-4">
      <div class="card bg-base-200">
        <div class="card-body p-3">
          <div class="flex justify-between items-center cursor-pointer" @click="toggleLocation(location.id!)">
            <h2 class="card-title text-lg">
              <i :class="expandedLocations.has(location.id!) ? 'mdi mdi-chevron-down' : 'mdi mdi-chevron-right'" class="mdi-24px" />
              {{ location.name }}
            </h2>
            <button class="btn btn-ghost btn-xs text-error" @click.stop="deleteLocation(location.id!)">
              <i class="mdi mdi-delete mdi-18px" />
            </button>
          </div>

          <div v-if="expandedLocations.has(location.id!)" class="mt-3 ml-4">
            <div v-for="bookcase in (bookcasesByLocation.get(location.id!) || [])" :key="bookcase.id!" class="mb-3">
              <div class="card bg-base-100">
                <div class="card-body p-3">
                  <div class="flex justify-between items-center cursor-pointer" @click="toggleBookcase(bookcase.id!)">
                    <h3 class="font-semibold">
                      <i :class="expandedBookcases.has(bookcase.id!) ? 'mdi mdi-chevron-down' : 'mdi mdi-chevron-right'" class="mdi-18px" />
                      {{ bookcase.name }}
                      <span class="badge badge-sm ml-2">{{ bookcase.shelfCount }} shelves</span>
                    </h3>
                    <button class="btn btn-ghost btn-xs text-error" @click.stop="deleteBookcase(location.id!, bookcase.id!)">
                      <i class="mdi mdi-delete mdi-18px" />
                    </button>
                  </div>

                  <div v-if="expandedBookcases.has(bookcase.id!) && bookcase.shelves" class="mt-2 ml-4">
                    <div v-for="shelf in bookcase.shelves" :key="shelf.id!" class="border-l-4 border-secondary pl-3 py-1 mb-2">
                      <div class="flex justify-between items-center">
                        <span class="font-mono text-sm">
                          Shelf {{ shelf.position }}
                          <span v-if="shelf.label" class="badge badge-accent badge-sm ml-1">{{ shelf.label }}</span>
                        </span>
                      </div>
                      <div v-if="shelfBooks.has(shelf.id!)" class="mt-1">
                        <span v-if="shelfBooks.get(shelf.id!)!.length === 0" class="text-xs opacity-50 italic">empty</span>
                        <div v-for="sb in shelfBooks.get(shelf.id!)" :key="sb.id" class="inline-flex items-center gap-1 mr-2 mb-1">
                          <router-link :to="{ name: 'book-detail', params: { bookId: sb.userBookId } }" class="badge badge-outline badge-sm link link-hover">
                            {{ getBookTitle(sb.userBookId) }}
                          </router-link>
                          <button class="btn btn-ghost btn-xs p-0 text-error" @click="removeFromShelf(shelf.id!, sb.userBookId)">
                            <i class="mdi mdi-close mdi-14px" />
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="addingBookcaseForLocation === location.id!" class="card bg-base-100 p-3 mb-2">
              <div class="flex gap-2 items-end flex-wrap">
                <div class="form-control">
                  <label class="label"><span class="label-text">{{ t('library_map.bookcase_name') }}</span></label>
                  <input v-model="newBookcaseName" type="text" class="input input-bordered input-sm" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">{{ t('library_map.shelf_count') }}</span></label>
                  <input v-model.number="newBookcaseShelfCount" type="number" min="1" max="20" class="input input-bordered input-sm w-20" />
                </div>
                <button class="btn btn-success btn-sm" @click="addBookcase(location.id!)">{{ t('labels.save') }}</button>
                <button class="btn btn-ghost btn-sm" @click="addingBookcaseForLocation = null">{{ t('labels.cancel') }}</button>
              </div>
            </div>

            <button v-else class="btn btn-outline btn-sm mt-1" @click="startAddBookcase(location.id!)">
              <i class="mdi mdi-plus mdi-18px" />
              {{ t('library_map.add_bookcase') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="unassignedBooks && unassignedBooks.totalElements > 0" class="mt-8">
      <h2 class="text-xl mb-3" :class="typographyClasses">
        {{ t('library_map.unassigned_books') }} ({{ unassignedBooks.totalElements }})
      </h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <div v-for="ub in unassignedBooks.content" :key="ub.id!" class="card bg-base-200 p-2">
          <div class="flex items-center gap-2 justify-between">
            <div class="flex items-center gap-2">
              <img v-if="ub.book.image" :src="'/files/' + ub.book.image" class="w-8 h-12 object-cover rounded" />
              <router-link :to="{ name: 'book-detail', params: { bookId: ub.book.id } }" class="link link-hover text-sm font-semibold">
                {{ ub.book.title }}
              </router-link>
            </div>
            <button class="btn btn-primary btn-xs" @click="openAssignModal([ub.id!])">
              {{ t('library_map.assign_to_shelf') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
