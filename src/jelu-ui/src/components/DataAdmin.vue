<script setup lang="ts">
import { useTitle } from '@vueuse/core'
import { ref, onMounted } from "vue"
import { useI18n } from 'vue-i18n'
import { useOruga } from "@oruga-ui/oruga-next"
import { useRouter } from 'vue-router'
import SeriesAdmin from "./SeriesAdmin.vue"
import TagsAdmin from "./TagsAdmin.vue"
import AdminAuthors from './AdminAuthors.vue'
import AuthorsAdmin from './AuthorsAdmin.vue'
import dataService from "../services/DataService"
import { ObjectUtils } from "../utils/ObjectUtils"

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })
const oruga = useOruga()
const router = useRouter()
useTitle('Jelu | Settings — Library Data')

const currentView = ref("AUTHOR")
const changeView = (viewName: string) => { currentView.value = viewName }

interface HealthCheck { key: string; label: string; icon: string; count: number | null; loading: boolean }

const missingChecks = ref<HealthCheck[]>([
  { key: 'author', label: 'Missing author', icon: 'mdi-account-off-outline', count: null, loading: false },
  { key: 'pagecount', label: 'Missing page count', icon: 'mdi-book-off-outline', count: null, loading: false },
  { key: 'isbn', label: 'Missing ISBN', icon: 'mdi-barcode-off', count: null, loading: false },
  { key: 'cover', label: 'Missing cover', icon: 'mdi-image-off-outline', count: null, loading: false },
  { key: 'summary', label: 'Missing summary', icon: 'mdi-text-box-remove-outline', count: null, loading: false },
])

const orphanChecks = ref<HealthCheck[]>([
  { key: 'authors', label: 'Orphan authors', icon: 'mdi-account-question-outline', count: null, loading: false },
  { key: 'series', label: 'Orphan series', icon: 'mdi-bookmark-off-outline', count: null, loading: false },
  { key: 'tags', label: 'Orphan tags', icon: 'mdi-tag-off-outline', count: null, loading: false },
])

const duplicatesCheck = ref<HealthCheck>({ key: 'duplicates', label: 'Possible duplicates', icon: 'mdi-content-duplicate', count: null, loading: false })
const healthOpen = ref(false)
const activeList = ref<{ title: string; kind: 'missing' | 'orphan'; key: string; items: any[] } | null>(null)
const listLoading = ref(false)

const loadHealthCounts = async () => {
  for (const c of missingChecks.value) {
    c.loading = true
    try {
      const res = await dataService.findBooksMissing(c.key, 0, 1)
      c.count = res.totalElements
    } catch (e) { c.count = null }
    c.loading = false
  }
  for (const c of orphanChecks.value) {
    c.loading = true
    try {
      let res: any
      if (c.key === 'authors') res = await dataService.getOrphanAuthors(0, 1)
      else if (c.key === 'series') res = await dataService.getOrphanSeries(0, 1)
      else res = await dataService.getOrphanTags(0, 1)
      c.count = res.totalElements
    } catch (e) { c.count = null }
    c.loading = false
  }
  duplicatesCheck.value.loading = true
  try {
    const dres = await dataService.findDuplicateBooks(0, 1)
    duplicatesCheck.value.count = dres.totalElements
  } catch (e) { duplicatesCheck.value.count = null }
  duplicatesCheck.value.loading = false
}

const openDuplicatesList = async () => {
  listLoading.value = true
  activeList.value = { title: 'Possible duplicates', kind: 'missing', key: 'duplicates', items: [] }
  try {
    const res = await dataService.findDuplicateBooks(0, 200)
    activeList.value.items = res.content
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Could not load list', 2500)
  }
  listLoading.value = false
}

const openMissingList = async (check: HealthCheck) => {
  listLoading.value = true
  activeList.value = { title: check.label, kind: 'missing', key: check.key, items: [] }
  try {
    const res = await dataService.findBooksMissing(check.key, 0, 200)
    activeList.value.items = res.content
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Could not load list', 2500)
  }
  listLoading.value = false
}

const openOrphanList = async (check: HealthCheck) => {
  listLoading.value = true
  activeList.value = { title: check.label, kind: 'orphan', key: check.key, items: [] }
  try {
    let res: any
    if (check.key === 'authors') res = await dataService.getOrphanAuthors(0, 200)
    else if (check.key === 'series') res = await dataService.getOrphanSeries(0, 200)
    else res = await dataService.getOrphanTags(0, 200)
    activeList.value.items = res.content
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Could not load list', 2500)
  }
  listLoading.value = false
}

const goToBook = (book: any) => {
  const id = book.id || book.book?.id
  if (id) router.push({ name: 'book-detail', params: { bookId: id } })
}

const deleteOrphan = async (item: any) => {
  if (!activeList.value) return
  try {
    if (activeList.value.key === 'authors') await dataService.deleteAuthor(item.id)
    else if (activeList.value.key === 'series') await dataService.deleteSeries(item.id)
    else await dataService.deleteTag(item.id)
    activeList.value.items = activeList.value.items.filter(i => i.id !== item.id)
    ObjectUtils.toast(oruga, 'success', 'Deleted', 1500)
    loadHealthCounts()
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Could not delete', 2500)
  }
}

const deleteAllOrphans = async () => {
  if (!activeList.value || activeList.value.kind !== 'orphan') return
  if (!confirm('Delete all ' + activeList.value.items.length + ' orphaned entries? This cannot be undone.')) return
  const items = [...activeList.value.items]
  for (const item of items) {
    try {
      if (activeList.value.key === 'authors') await dataService.deleteAuthor(item.id)
      else if (activeList.value.key === 'series') await dataService.deleteSeries(item.id)
      else await dataService.deleteTag(item.id)
    } catch (e) { /* skip */ }
  }
  activeList.value.items = []
  ObjectUtils.toast(oruga, 'success', 'Cleaned up orphans', 2000)
  loadHealthCounts()
}

onMounted(() => { loadHealthCounts() })
</script>

<template>
  <div class="max-w-4xl mx-auto px-4 py-6">
    <h1 class="text-2xl font-semibold mb-1">Library data</h1>
    <p class="text-sm opacity-60 mb-6">Maintain the authors, series, and tags behind your books — and find records that need attention.</p>

    <div class="border border-base-300 rounded-xl p-4 mb-4">
      <button class="flex items-center justify-between w-full" @click="healthOpen = !healthOpen">
        <span class="font-medium flex items-center gap-2"><i class="mdi mdi-heart-pulse" /> Library health</span>
        <i class="mdi" :class="healthOpen ? 'mdi-chevron-up' : 'mdi-chevron-down'" />
      </button>

      <div v-if="healthOpen" class="mt-4">
        <p class="text-xs opacity-60 mb-2">Books missing metadata</p>
        <div class="grid grid-cols-2 sm:grid-cols-3 gap-2 mb-4">
          <button
            v-for="c in missingChecks"
            :key="c.key"
            class="border border-base-300 rounded-lg p-3 text-left hover:bg-base-200 transition"
            @click="openMissingList(c)"
          >
            <div class="flex items-center gap-2">
              <i class="mdi" :class="c.icon" />
              <span class="text-sm">{{ c.label }}</span>
            </div>
            <div class="text-xl font-semibold mt-1">
              <span v-if="c.loading" class="loading loading-spinner loading-xs" />
              <span v-else :class="c.count ? 'text-warning' : 'opacity-40'">{{ c.count ?? '—' }}</span>
            </div>
          </button>
        </div>

        <p class="text-xs opacity-60 mb-2">Duplicates</p>
        <div class="grid grid-cols-2 sm:grid-cols-3 gap-2 mb-4">
          <button
            class="border border-base-300 rounded-lg p-3 text-left hover:bg-base-200 transition"
            @click="openDuplicatesList"
          >
            <div class="flex items-center gap-2">
              <i class="mdi" :class="duplicatesCheck.icon" />
              <span class="text-sm">{{ duplicatesCheck.label }}</span>
            </div>
            <div class="text-xl font-semibold mt-1">
              <span v-if="duplicatesCheck.loading" class="loading loading-spinner loading-xs" />
              <span v-else :class="duplicatesCheck.count ? 'text-warning' : 'opacity-40'">{{ duplicatesCheck.count ?? '\u2014' }}</span>
            </div>
          </button>
        </div>

        <p class="text-xs opacity-60 mb-2">Orphaned entries (referenced by no books)</p>
        <div class="grid grid-cols-2 sm:grid-cols-3 gap-2">
          <button
            v-for="c in orphanChecks"
            :key="c.key"
            class="border border-base-300 rounded-lg p-3 text-left hover:bg-base-200 transition"
            @click="openOrphanList(c)"
          >
            <div class="flex items-center gap-2">
              <i class="mdi" :class="c.icon" />
              <span class="text-sm">{{ c.label }}</span>
            </div>
            <div class="text-xl font-semibold mt-1">
              <span v-if="c.loading" class="loading loading-spinner loading-xs" />
              <span v-else :class="c.count ? 'text-warning' : 'opacity-40'">{{ c.count ?? '—' }}</span>
            </div>
          </button>
        </div>

        <div v-if="activeList" class="mt-4 border-t border-base-300 pt-4">
          <div class="flex items-center justify-between mb-2">
            <span class="font-medium">{{ activeList.title }} <span class="opacity-50">({{ activeList.items.length }})</span></span>
            <div class="flex gap-2">
              <button
                v-if="activeList.kind === 'orphan' && activeList.items.length"
                class="btn btn-xs btn-outline btn-error"
                @click="deleteAllOrphans"
              >
                <i class="mdi mdi-delete-sweep mr-1" /> Delete all
              </button>
              <button class="btn btn-xs btn-ghost" @click="activeList = null"><i class="mdi mdi-close" /></button>
            </div>
          </div>

          <div v-if="listLoading" class="flex justify-center py-6"><span class="loading loading-spinner" /></div>
          <div v-else-if="!activeList.items.length" class="text-sm opacity-50 py-4 text-center">Nothing here — all clean.</div>

          <div v-else-if="activeList.kind === 'missing'" class="flex flex-col gap-1 max-h-96 overflow-y-auto">
            <button
              v-for="b in activeList.items"
              :key="b.id"
              class="flex items-center gap-3 px-2 py-1.5 rounded hover:bg-base-200 text-left"
              @click="goToBook(b)"
            >
              <img v-if="b.image" :src="'/files/' + b.image" class="w-7 h-10 object-cover rounded flex-shrink-0">
              <div v-else class="w-7 h-10 bg-base-300 rounded flex items-center justify-center flex-shrink-0"><i class="mdi mdi-book-outline opacity-30" /></div>
              <span class="text-sm truncate">{{ b.title }}</span>
              <i class="mdi mdi-pencil ml-auto opacity-40" />
            </button>
          </div>

          <div v-else class="flex flex-col gap-1 max-h-96 overflow-y-auto">
            <div
              v-for="item in activeList.items"
              :key="item.id"
              class="flex items-center gap-3 px-2 py-1.5 rounded hover:bg-base-200"
            >
              <span class="text-sm truncate flex-1">{{ item.name }}</span>
              <button class="btn btn-xs btn-ghost btn-error" @click="deleteOrphan(item)"><i class="mdi mdi-delete" /></button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="border border-base-300 rounded-xl p-4">
      <div class="flex items-center gap-2 mb-4">
        <i class="mdi mdi-database-edit-outline" />
        <span class="font-medium">Manage entities</span>
      </div>
      <div class="join mb-4">
        <button class="btn btn-sm join-item" :class="currentView === 'AUTHOR' ? 'btn-primary' : 'btn-ghost'" @click="changeView('AUTHOR')">{{ t("book.author", 2) }}</button>
        <button class="btn btn-sm join-item" :class="currentView === 'SERIES' ? 'btn-primary' : 'btn-ghost'" @click="changeView('SERIES')">{{ t("book.series", 2) }}</button>
        <button class="btn btn-sm join-item" :class="currentView === 'MERGE' ? 'btn-primary' : 'btn-ghost'" @click="changeView('MERGE')">{{ t("authors_merge.merge_authors") }}</button>
        <button class="btn btn-sm join-item" :class="currentView === 'TAG' ? 'btn-primary' : 'btn-ghost'" @click="changeView('TAG')">{{ t("book.tag", 2) }}</button>
      </div>

      <AuthorsAdmin v-if="currentView === 'AUTHOR'" />
      <SeriesAdmin v-if="currentView === 'SERIES'" />
      <AdminAuthors v-if="currentView === 'MERGE'" />
      <TagsAdmin v-if="currentView === 'TAG'" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
</style>
