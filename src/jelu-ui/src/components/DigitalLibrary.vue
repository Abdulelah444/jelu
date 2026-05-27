<script setup lang="ts">
import { Ref, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useOruga } from "@oruga-ui/oruga-next"
import { UserBook } from '../model/Book'
import dataService from "../services/DataService"
import useTypography from "../composables/typography"

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })
const oruga = useOruga()
const { typographyClasses } = useTypography()

const digitalBooks: Ref<Array<UserBook>> = ref([])
const loading: Ref<boolean> = ref(false)
const deleteLoading: Ref<string | null> = ref(null)

const getDigitalBooks = async () => {
  loading.value = true
  try {
    const result = await dataService.findUserBookByCriteria(
      null, null, null, null, null, null, null, true, 0, 200, "modificationDate,desc"
    )
    digitalBooks.value = result.content || []
  } catch (error) {
    console.log("error fetching digital books: " + error)
  } finally {
    loading.value = false
  }
}

const deleteDigital = async (ub: UserBook) => {
  if (!ub.id) return
  if (!confirm("Remove the digital copy of " + ub.book.title + "?")) return
  deleteLoading.value = ub.id
  try {
    await dataService.deleteDigitalFile(ub.id)
    oruga.notification.open({ message: "Digital copy removed: " + ub.book.title, variant: "success", duration: 3000 })
    getDigitalBooks()
  } catch (error) {
    oruga.notification.open({ message: "Delete failed: " + error, variant: "danger", duration: 4000 })
  } finally {
    deleteLoading.value = null
  }
}

const formatSize = (bytes: number | null | undefined) => {
  if (!bytes) return ''
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

getDigitalBooks()
</script>

<template>
  <section class="px-4 sm:px-8 py-4">
    <h1 :class="['text-2xl mb-4', typographyClasses]">
      <i class="mdi mdi-book-open-page-variant mdi-24px mr-2 text-primary" />
      Digital Library
    </h1>
    <p class="text-sm text-base-content/60 mb-4">
      {{ digitalBooks.length }} books with digital copies
    </p>
    <div v-if="loading" class="flex justify-center py-8">
      <span class="loading loading-spinner loading-lg" />
    </div>
    <div v-else-if="digitalBooks.length === 0" class="text-center py-8 text-base-content/50">
      No digital copies yet. Go to a book detail page to download or upload one.
    </div>
    <div v-else class="overflow-x-auto">
      <table class="table table-zebra w-full">
        <thead>
          <tr>
            <th>Cover</th>
            <th>Title</th>
            <th>Author</th>
            <th>Format</th>
            <th>Size</th>
            <th>Added</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ub in digitalBooks" :key="ub.id">
            <td>
              <img v-if="ub.book.image" :src="'/files/' + ub.book.image" alt="cover" class="h-14 object-contain rounded" />
            </td>
            <td>
              <router-link :to="{ name: 'book-detail', params: { bookId: ub.id } }" class="link hover:underline font-medium">
                {{ ub.book.title }}
              </router-link>
            </td>
            <td class="text-sm">{{ ub.book.authors && ub.book.authors.length > 0 ? ub.book.authors[0].name : '' }}</td>
            <td><span class="badge badge-primary badge-sm">{{ ub.digitalFileFormat ? ub.digitalFileFormat.toUpperCase() : '' }}</span></td>
            <td class="text-sm">{{ formatSize(ub.digitalFileSizeBytes) }}</td>
            <td class="text-sm">{{ ub.digitalFileAddedDate ? new Date(ub.digitalFileAddedDate).toLocaleDateString() : '' }}</td>
            <td>
              <div class="flex gap-1">
                <a :href="'/api/v1/userbooks/' + ub.id + '/digital/download-file'" class="btn btn-ghost btn-xs" download><i class="mdi mdi-download mdi-18px" /></a>
                <button class="btn btn-ghost btn-xs text-error" :disabled="deleteLoading === ub.id" @click="deleteDigital(ub)">
                  <span v-if="deleteLoading === ub.id" class="loading loading-spinner loading-xs" />
                  <i v-else class="mdi mdi-delete mdi-18px" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
