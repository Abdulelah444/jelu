<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Metadata } from '../model/Metadata'
import dataService from '../services/DataService'
import useTypography from '../composables/typography'

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })
const { typographyClasses } = useTypography()

const query = ref('')
const results = ref<Metadata[]>([])
const loading = ref(false)
const searched = ref(false)
const errorMessage = ref('')
const addingId = ref<string | null>(null)
const addedIds = ref<Set<string>>(new Set())

// Normalize title for grouping: lowercase, remove subtitles after ':', strip punctuation
function normalizeTitle(title: string): string {
  return title
    .toLowerCase()
    .split(':')[0]
    .replace(/&/g, 'and')
    .replace(/[^a-z0-9\s]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function normalizeAuthor(authors: Set<string> | string[] | undefined): string {
  if (!authors) return ''
  const first = [...authors][0] ?? ''
  return first.toLowerCase().replace(/[^a-z\s]/g, '').trim()
}

// Score a book for ranking — higher is better
function scoreBook(book: Metadata, query: string): number {
  let score = 0
  const q = query.toLowerCase().replace(/&/g, 'and')
  const title = (book.title ?? '').toLowerCase().replace(/&/g, 'and')
  const year = parseInt(book.publishedDate ?? '0')

  // Title match quality
  if (title === q) score += 40
  else if (title.startsWith(q)) score += 25
  else if (title.includes(q)) score += 10
  else score -= 30

  // Data completeness
  if (book.image) score += 15
  if (book.isbn13) score += 15
  if (book.summary && book.summary.length > 100) score += 10
  if (book.pageCount && book.pageCount > 50) score += 10
  if (book.publisher) score += 5
  if (book.publishedDate) score += 5

  // Penalize old books
  if (year > 0 && year < 1900) score -= 40
  if (year > 0 && year < 1950) score -= 20

  // Penalize junk
  if (!book.image) score -= 15
  if (!book.isbn13) score -= 15
  if (book.pageCount && book.pageCount < 50) score -= 25
  if (!book.pageCount) score -= 10

  return score
}

interface BookGroup {
  primary: Metadata
  editions: Metadata[]
  expanded: boolean
}

const groupedResults = computed((): BookGroup[] => {
  if (results.value.length === 0) return []

  // Score and sort first
  const q = query.value.trim().toLowerCase()
  const scored = [...results.value]
    .map(b => ({ book: b, score: scoreBook(b, q) }))
    .sort((a, b) => b.score - a.score)
    .map(s => s.book)

  // Group by normalized title + first author
  const groups = new Map<string, BookGroup>()
  for (const book of scored) {
    const key = normalizeTitle(book.title ?? '') + '|' + normalizeAuthor(book.authors)
    if (!groups.has(key)) {
      groups.set(key, { primary: book, editions: [], expanded: false })
    } else {
      groups.get(key)!.editions.push(book)
    }
  }

  return [...groups.values()]
})

const hasResults = computed(() => groupedResults.value.length > 0)
const noResults = computed(() => searched.value && !loading.value && groupedResults.value.length === 0)

function toggleEditions(group: BookGroup) {
  group.expanded = !group.expanded
}


async function search() {
  const q = query.value.trim()
  if (!q) return
  loading.value = true
  searched.value = false
  errorMessage.value = ''
  results.value = []
  try {
    results.value = await dataService.searchMetadata(q)
    searched.value = true
  } catch (e: any) {
    errorMessage.value = e?.message ?? 'Search failed'
  } finally {
    loading.value = false
  }
}

function authorSearch(authorName: string) {
  query.value = `author:${authorName}`
  search()
}

async function addToLibrary(book: Metadata) {
  const key = bookKey(book)
  addingId.value = key
  try {
    await dataService.saveUserBook({
      book: {
        title: book.title ?? '',
        isbn10: book.isbn10 ?? undefined,
        isbn13: book.isbn13 ?? undefined,
        authors: book.authors ? [...book.authors].map(name => ({ name })) : [],
        image: book.image ?? undefined,
        summary: book.summary ?? undefined,
        publishedDate: book.publishedDate ?? undefined,
        publisher: book.publisher ?? undefined,
        pageCount: book.pageCount ?? undefined,
        language: book.language ?? undefined,
        googleId: book.googleId ?? undefined,
      },
    })
    addedIds.value.add(key)
  } catch (e: any) {
    alert('Could not add book: ' + (e?.message ?? 'unknown error'))
  } finally {
    addingId.value = null
  }
}

function bookKey(book: Metadata): string {
  return book.isbn13 ?? book.isbn10 ?? book.googleId ?? book.title ?? ''
}

function isAdded(book: Metadata): boolean {
  return addedIds.value.has(bookKey(book))
}

function isAdding(book: Metadata): boolean {
  return addingId.value === bookKey(book)
}

function coverUrl(book: Metadata): string | null {
  if (!book.image) return null
  return book.image.replace('zoom=1', 'zoom=3').replace('zoom=5', 'zoom=3')
}

function truncate(text: string | undefined, maxLen = 300): string {
  if (!text) return ''
  return text.length > maxLen ? text.slice(0, maxLen) + '…' : text
}
</script>

<template>
  <div class="flex flex-col items-center w-full px-4 pb-12">

    <div class="w-full max-w-3xl mt-8 mb-6 text-center">
      <h1 class="text-3xl mb-1" :class="typographyClasses">
        Discover Books
      </h1>
      <p class="text-base-content/60 text-sm">
        Search by title, author, or ISBN — use <code class="font-mono bg-base-200 px-1 rounded">author:name</code> to browse an author's works
      </p>
    </div>

    <div class="w-full max-w-3xl flex gap-2 mb-8">
      <input
        v-model="query"
        type="text"
        placeholder="Search title, author, ISBN..."
        class="input input-bordered focus:input-accent flex-1"
        autofocus
        @keyup.enter="search"
      />
      <button
        class="btn btn-primary"
        :class="{ 'loading': loading }"
        :disabled="loading || !query.trim()"
        @click="search"
      >
        <svg v-if="!loading" xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
        {{ loading ? '' : 'Search' }}
      </button>
    </div>

    <div v-if="errorMessage" class="w-full max-w-3xl alert alert-error mb-6">
      <span>{{ errorMessage }}</span>
    </div>

    <div v-if="loading" class="w-full max-w-3xl flex flex-col gap-4">
      <div v-for="i in 5" :key="i" class="flex gap-4 bg-base-200 rounded-xl p-4 animate-pulse">
        <div class="w-20 h-28 bg-base-300 rounded-lg shrink-0" />
        <div class="flex-1 flex flex-col gap-2 justify-center">
          <div class="h-4 bg-base-300 rounded w-2/3" />
          <div class="h-3 bg-base-300 rounded w-1/3" />
          <div class="h-3 bg-base-300 rounded w-full mt-2" />
          <div class="h-3 bg-base-300 rounded w-5/6" />
        </div>
      </div>
    </div>

    <div v-else-if="noResults" class="text-base-content/50 mt-12 text-center">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12 mx-auto mb-3 opacity-30" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
      </svg>
      <p class="text-lg">No results for "{{ query }}"</p>
      <p class="text-sm mt-1">Try a different title or use <code class="font-mono">author:Name</code></p>
    </div>

    <div v-else-if="hasResults" class="w-full max-w-3xl flex flex-col gap-4">
      <p class="text-base-content/50 text-xs mb-1">{{ groupedResults.length }} title{{ groupedResults.length !== 1 ? "s" : "" }} · {{ results.length }} editions total</p>

      <div
        v-for="group in groupedResults"
        :key="bookKey(group.primary)"
        class="flex flex-col bg-base-200 hover:bg-base-300 transition-colors rounded-xl overflow-hidden"
      >
        <div class="flex gap-4 p-4">
          <div class="shrink-0 w-20">
            <img
              v-if="coverUrl(group.primary)"
              :src="coverUrl(group.primary)!"
              :alt="group.primary.title"
              class="w-20 rounded-lg object-cover shadow-md"
            />
            <div v-else class="w-20 h-28 bg-base-300 rounded-lg flex items-center justify-center">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
              </svg>
            </div>
          </div>
          <div class="flex-1 flex flex-col gap-1 min-w-0">
            <h2 class="font-semibold text-base leading-snug" :class="typographyClasses">
              {{ group.primary.title }}
            </h2>
            <div v-if="group.primary.authors && [...group.primary.authors].length > 0" class="flex flex-wrap gap-1 text-sm">
              <button
                v-for="author in [...group.primary.authors]"
                :key="author"
                class="text-primary hover:underline underline-offset-2 text-left"
                @click="authorSearch(author)"
              >
                {{ author }}
              </button>
            </div>
            <div class="flex flex-wrap gap-x-3 text-xs text-base-content/50 mt-0.5">
              <span v-if="group.primary.publishedDate">{{ group.primary.publishedDate.slice(0, 4) }}</span>
              <span v-if="group.primary.publisher">{{ group.primary.publisher }}</span>
              <span v-if="group.primary.pageCount">{{ group.primary.pageCount }} pages</span>
              <span v-if="group.primary.language && group.primary.language !== 'en'" class="uppercase">{{ group.primary.language }}</span>
              <span v-if="group.primary.isbn13" class="font-mono">{{ group.primary.isbn13 }}</span>
            </div>
            <p v-if="group.primary.summary" class="text-xs text-base-content/70 mt-1 leading-relaxed line-clamp-3">
              {{ truncate(group.primary.summary) }}
            </p>
          </div>
          <div class="shrink-0 flex flex-col justify-start pt-1">
            <button
              v-if="!isAdded(group.primary)"
              class="btn btn-sm btn-outline btn-primary"
              :class="{ 'loading': isAdding(group.primary) }"
              :disabled="isAdding(group.primary)"
              @click="addToLibrary(group.primary)"
            >
              <svg v-if="!isAdding(group.primary)" xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              {{ isAdding(group.primary) ? '' : 'Add' }}
            </button>
            <div v-else class="flex items-center gap-1 text-success text-xs font-medium">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
              </svg>
              Added
            </div>
          </div>
        </div>

        <div v-if="group.editions.length > 0" class="px-4 pb-3">
          <button
            class="text-xs text-primary hover:underline"
            @click="toggleEditions(group)"
          >
            {{ group.expanded ? 'Hide' : 'Show' }} {{ group.editions.length }} other edition{{ group.editions.length !== 1 ? 's' : '' }}
          </button>
          <div v-if="group.expanded" class="mt-2 flex flex-col gap-2">
            <div
              v-for="ed in group.editions"
              :key="bookKey(ed)"
              class="flex gap-3 bg-base-300 rounded-lg p-3 text-xs"
            >
              <img v-if="coverUrl(ed)" :src="coverUrl(ed)!" class="w-10 rounded object-cover" />
              <div class="flex flex-col gap-0.5 justify-center">
                <span class="font-medium">{{ ed.title }}</span>
                <span class="text-base-content/50">{{ ed.publishedDate?.slice(0,4) }} · {{ ed.publisher }} · {{ ed.pageCount }} pages</span>
                <span v-if="ed.isbn13" class="font-mono text-base-content/40">{{ ed.isbn13 }}</span>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>

  </div>
</template>
