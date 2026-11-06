<script setup lang="ts">
import { onMounted, Ref, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useOruga } from "@oruga-ui/oruga-next"
import { PhysicalLocation, PhysicalBookcase, PhysicalShelf } from "../model/PhysicalLibrary"
import dataService from "../services/DataService"
import { ObjectUtils } from "../utils/ObjectUtils"

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })
const oruga = useOruga()

const props = defineProps<{
  userBookIds: Array<string>
}>()

const emit = defineEmits<{
  (e: 'close'): void,
  (e: 'assigned'): void
}>()

const locations: Ref<Array<PhysicalLocation>> = ref([])
const bookcases: Ref<Array<PhysicalBookcase>> = ref([])
const shelves: Ref<Array<PhysicalShelf>> = ref([])

const selectedLocationId: Ref<string> = ref("")
const selectedBookcaseId: Ref<string> = ref("")
const selectedShelfId: Ref<string> = ref("")
const isLoading: Ref<boolean> = ref(false)

const loadLocations = async () => {
  try {
    locations.value = await dataService.getPhysicalLocations()
    if (locations.value.length === 1) {
      selectedLocationId.value = locations.value[0].id!
    }
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error loading locations", 4000)
  }
}

watch(selectedLocationId, async (newVal) => {
  bookcases.value = []
  shelves.value = []
  selectedBookcaseId.value = ""
  selectedShelfId.value = ""
  if (newVal) {
    try {
      bookcases.value = await dataService.getPhysicalBookcases(newVal)
      if (bookcases.value.length === 1) {
        selectedBookcaseId.value = bookcases.value[0].id!
      }
    } catch (error) {
      ObjectUtils.toast(oruga, "danger", "Error loading bookcases", 4000)
    }
  }
})

watch(selectedBookcaseId, async (newVal) => {
  shelves.value = []
  selectedShelfId.value = ""
  if (newVal) {
    try {
      shelves.value = await dataService.getPhysicalShelves(newVal)
      if (shelves.value.length === 1) {
        selectedShelfId.value = shelves.value[0].id!
      }
    } catch (error) {
      ObjectUtils.toast(oruga, "danger", "Error loading shelves", 4000)
    }
  }
})

const assign = async () => {
  if (!selectedShelfId.value) return
  isLoading.value = true
  try {
    if (props.userBookIds.length === 1) {
      await dataService.assignBookToShelf(selectedShelfId.value, { userBookId: props.userBookIds[0] })
    } else {
      await dataService.bulkAssignBooksToShelf(selectedShelfId.value, { userBookIds: props.userBookIds })
    }
    ObjectUtils.toast(oruga, "success", props.userBookIds.length + " book(s) assigned", 2000)
    emit('assigned')
    emit('close')
  } catch (error) {
    ObjectUtils.toast(oruga, "danger", "Error assigning book(s)", 4000)
  } finally {
    isLoading.value = false
  }
}

const shelfLabel = (shelf: PhysicalShelf) => {
  if (shelf.label) {
    return shelf.label + " (Shelf " + shelf.position + ")"
  }
  return "Shelf " + shelf.position
}

onMounted(() => {
  loadLocations()
})
</script>

<template>
  <section class="p-4 min-w-80">
    <h1 class="text-xl font-semibold mb-2">{{ t('library_map.assign_to_shelf') }}</h1>
    <p class="text-sm mb-4 opacity-70">{{ props.userBookIds.length }} book(s) selected</p>

    <div class="form-control mb-3">
      <label class="label"><span class="label-text font-semibold">{{ t('library_map.location_name') }}</span></label>
      <select v-model="selectedLocationId" class="select select-bordered w-full">
        <option value="" disabled>-- Select --</option>
        <option v-for="loc in locations" :key="loc.id!" :value="loc.id!">{{ loc.name }}</option>
      </select>
    </div>

    <div class="form-control mb-3">
      <label class="label"><span class="label-text font-semibold">{{ t('library_map.bookcase_name') }}</span></label>
      <select v-model="selectedBookcaseId" class="select select-bordered w-full" :disabled="bookcases.length === 0">
        <option value="" disabled>{{ selectedLocationId ? '-- Select --' : '-- Pick a location first --' }}</option>
        <option v-for="bc in bookcases" :key="bc.id!" :value="bc.id!">{{ bc.name }}</option>
      </select>
    </div>

    <div class="form-control mb-3">
      <label class="label"><span class="label-text font-semibold">Shelf</span></label>
      <select v-model="selectedShelfId" class="select select-bordered w-full" :disabled="shelves.length === 0">
        <option value="" disabled>{{ selectedBookcaseId ? '-- Select --' : '-- Pick a bookcase first --' }}</option>
        <option v-for="shelf in shelves" :key="shelf.id!" :value="shelf.id!">{{ shelfLabel(shelf) }}</option>
      </select>
    </div>

    <div class="flex gap-2 mt-4">
      <button class="btn btn-primary" :disabled="!selectedShelfId || isLoading" @click="assign">
        <span v-if="isLoading" class="loading loading-spinner loading-sm"></span>
        {{ t('library_map.assign_to_shelf') }}
      </button>
      <button class="btn btn-ghost" @click="emit('close')">{{ t('labels.cancel') }}</button>
    </div>
  </section>
</template>
