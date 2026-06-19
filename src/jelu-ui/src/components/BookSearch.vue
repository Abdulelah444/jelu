<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Metadata } from '../model/Metadata'
import { UserBook } from '../model/Book'
import dataService from '../services/DataService'
import useTypography from '../composables/typography'

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })
const router = useRouter()
const { typographyClasses } = useTypography()

const query = ref('')
const results = ref<Metadata[]>([])
const loading = ref(false)
const loadingSecondary = ref(false)
const searched = ref(false)
const errorMessage = ref('')
const addingId = ref<string | null>(null)
const addedIds = ref<Set<string>>(new Set())
const wishlistItems = ref<Array<any>>([])
const wishlistTitles = ref<Set<string>>(new Set())

async function loadWishlist() {
  try {
    wishlistItems.value = await dataService.getWishlistItems('BOOK')
    wishlistTitles.value = new Set(
      wishlistItems.value.map((item: any) =>
        (item.title ?? '').toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]/g, '').trim()
      )
    )
  } catch (e) { /* ignore */ }
}

function isInWishlist(book: Metadata): boolean {
  const key = (book.title ?? '').toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]/g, '').trim()
  return key !== '' && wishlistTitles.value.has(key)
}

function wishlistItemForBook(book: Metadata): any {
  const key = (book.title ?? '').toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]/g, '').trim()
  return wishlistItems.value.find((item: any) =>
    (item.title ?? '').toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]/g, '').trim() === key
  )
}

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
}

function isArabicQuery(q: string): boolean {
  return /[\u0600-\u06FF]/.test(q)
}

const groupedResults = computed((): BookGroup[] => {
  if (results.value.length === 0) return []

  const q = query.value.trim().toLowerCase()
  const arabic = isArabicQuery(query.value)

  // Auto language filter based on query script
  let filtered = [...results.value]
  if (arabic) {
    const arResults = filtered.filter(b => b.language === 'ar')
    if (arResults.length > 0) filtered = arResults
  }

  // Score and sort
  const scored = filtered
    .map(b => ({ book: b, score: scoreBook(b, q) }))
    .sort((a, b) => b.score - a.score)
    .map(s => s.book)

  // Group by normalized title + first author
  const groups = new Map<string, BookGroup>()
  for (const book of scored) {
    const key = normalizeTitle(book.title ?? '') + '|' + normalizeAuthor(book.authors)
    if (!groups.has(key)) {
      groups.set(key, { primary: book, editions: [] })
    } else {
      groups.get(key)!.editions.push(book)
    }
  }

  return [...groups.values()]
})

const hasResults = computed(() => groupedResults.value.length > 0)
const noResults = computed(() => searched.value && !loading.value && groupedResults.value.length === 0)

const expandedGroups = ref<Set<string>>(new Set())
const expandedCards = ref<Set<string>>(new Set())

function toggleCard(key: string) {
  if (expandedCards.value.has(key)) {
    expandedCards.value.delete(key)
  } else {
    expandedCards.value.add(key)
  }
}

function isCardExpanded(key: string): boolean {
  return expandedCards.value.has(key)
}

function toggleEditions(group: BookGroup) {
  const key = bookKey(group.primary)
  if (expandedGroups.value.has(key)) {
    expandedGroups.value.delete(key)
  } else {
    expandedGroups.value.add(key)
  }
}

function isExpanded(group: BookGroup): boolean {
  return expandedGroups.value.has(bookKey(group.primary))
}


async function search() {
  const q = query.value.trim()
  if (!q) return
  loading.value = true
  loadingSecondary.value = true
  searched.value = false
  errorMessage.value = ''
  results.value = []
  loadWishlist()

  // Fire both sources in parallel, render Google first when it arrives
  const googlePromise = dataService.searchMetadataSource(q, 'google')
  const olPromise = dataService.searchMetadataSource(q, 'openlibrary')

  try {
    const googleResults = await googlePromise
    results.value = googleResults
    searched.value = true
    loading.value = false

    // Merge OpenLibrary results when they arrive
    const olResults = await olPromise
    const seenKeys = new Set(results.value.map(b => b.isbn13 ?? b.isbn10 ?? b.googleId ?? b.title))
    const newItems = olResults.filter(b => {
      const key = b.isbn13 ?? b.isbn10 ?? b.title
      return key && !seenKeys.has(key)
    })
    if (newItems.length > 0) {
      results.value = [...results.value, ...newItems]
    }
  } catch (e: any) {
    errorMessage.value = e?.message ?? 'Search failed'
    loading.value = false
  } finally {
    loadingSecondary.value = false
  }
}

function authorSearch(authorName: string) {
  query.value = `author:${authorName}`
  search()
}

function buildUserBook(book: Metadata, extra: object = {}): UserBook {
  return {
    ...extra,
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
  } as UserBook
}

async function addToLibrary(book: Metadata) {
  const key = bookKey(book)
  addingId.value = key + '_lib'
  try {
    const saved = await dataService.saveUserBookImage(buildUserBook(book, { owned: true }), null, () => {})
    addedIds.value.add(key)
    // If book was in wishlist, delete it
    const wishlistItem = wishlistItemForBook(book)
    if (wishlistItem) {
      try {
        await dataService.deleteWishlistItem(wishlistItem.id)
        wishlistTitles.value.delete(
          (book.title ?? '').toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]/g, '').trim()
        )
      } catch (e) { /* ignore */ }
    }
    if (saved?.id) {
      router.push({ name: 'book-detail', params: { bookId: saved.id } })
    }
  } catch (e: any) {
    alert('Could not add book: ' + (e?.message ?? 'unknown error'))
  } finally {
    addingId.value = null
  }
}

async function addToWishlist(book: Metadata) {
  const key = bookKey(book)
  // Check if already in wishlist
  if (isInWishlist(book)) {
    alert('This book is already in your wishlist')
    return
  }
  addingId.value = key + '_wish'
  try {
    await dataService.createWishlistItem({
      itemType: 'BOOK',
      title: book.title ?? '',
      isbn: book.isbn13 ?? book.isbn10 ?? '',
      authors: book.authors ? [...book.authors].join(', ') : '',
      image: book.image ?? '',
      pageCount: book.pageCount ?? null,
      publisher: book.publisher ?? '',
      language: book.language ?? '',
      description: book.summary ?? '',
    })
    addedIds.value.add(key)
    wishlistTitles.value.add(
      (book.title ?? '').toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]/g, '').trim()
    )
  } catch (e: any) {
    alert('Could not add to wishlist: ' + (e?.message ?? 'unknown error'))
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

function isAddingLib(book: Metadata): boolean {
  return addingId.value === bookKey(book) + '_lib'
}

function isAddingWish(book: Metadata): boolean {
  return addingId.value === bookKey(book) + '_wish'
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
      <div class="flex items-center gap-2 mb-1">
        <p class="text-base-content/50 text-xs">{{ groupedResults.length }} title{{ groupedResults.length !== 1 ? "s" : "" }} · {{ results.length }} editions total</p>
        <span v-if="loadingSecondary" class="loading loading-dots loading-xs text-primary"></span>
      </div>

      <div
        v-for="group in groupedResults"
        :key="bookKey(group.primary)"
        class="flex flex-col bg-base-200 hover:bg-base-300 transition-colors rounded-xl overflow-hidden"
      >
        <div class="flex gap-4 p-4 cursor-pointer" @click="toggleCard(bookKey(group.primary))">
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
            <div v-if="group.primary.summary" class="mt-1">
              <p class="text-xs text-base-content/70 leading-relaxed"
                :class="{ 'line-clamp-3': !isCardExpanded(bookKey(group.primary)) }">
                {{ group.primary.summary }}
              </p>
            </div>
            <!-- Expanded details -->
            <div v-if="isCardExpanded(bookKey(group.primary))" class="mt-2 flex flex-col gap-1" @click.stop>
              <div class="flex flex-wrap gap-x-3 gap-y-1 text-xs text-base-content/50">
                <span v-if="group.primary.isbn13" class="font-mono">ISBN: {{ group.primary.isbn13 }}</span>
                <span v-if="group.primary.isbn10" class="font-mono">ISBN10: {{ group.primary.isbn10 }}</span>
                <span v-if="group.primary.googleId">
                  <a :href="`https://books.google.com/books?id=${group.primary.googleId}`" target="_blank" class="text-primary hover:underline" @click.stop>Google Books</a>
                </span>
              </div>
            </div>
            <div class="mt-1">
              <span class="text-xs text-base-content/30 italic">
                {{ isCardExpanded(bookKey(group.primary)) ? 'Click to collapse' : 'Click to expand' }}
              </span>
            </div>
          </div>
          <div class="shrink-0 flex flex-col gap-1 justify-start pt-1">
            <template v-if="!isAdded(group.primary)">
              <button
                class="btn btn-xs btn-primary"
                :class="{ 'loading': isAddingLib(group.primary) }"
                :disabled="isAddingLib(group.primary) || isAddingWish(group.primary)"
                @click="addToLibrary(group.primary)"
              >
                <svg v-if="!isAddingLib(group.primary)" xmlns="http://www.w3.org/2000/svg" class="h-3 w-3 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                </svg>
                {{ isAddingLib(group.primary) ? '' : 'Library' }}
              </button>
              <button
                v-if="!isInWishlist(group.primary)"
                class="btn btn-xs btn-outline btn-secondary"
                :class="{ 'loading': isAddingWish(group.primary) }"
                :disabled="isAddingLib(group.primary) || isAddingWish(group.primary)"
                @click="addToWishlist(group.primary)"
              >
                <svg v-if="!isAddingWish(group.primary)" xmlns="http://www.w3.org/2000/svg" class="h-3 w-3 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
                {{ isAddingWish(group.primary) ? '' : 'Wishlist' }}
              </button>
              <div v-else class="text-xs text-secondary font-medium flex items-center gap-1">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
                Wishlisted
              </div>
            </template>
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
            {{ isExpanded(group) ? 'Hide' : 'Show' }} {{ group.editions.length }} other edition{{ group.editions.length !== 1 ? 's' : '' }}
          </button>
          <div v-if="isExpanded(group)" class="mt-2 flex flex-col gap-2">
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
