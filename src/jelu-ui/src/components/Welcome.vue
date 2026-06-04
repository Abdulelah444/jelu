<script setup lang="ts">
import { useOruga } from "@oruga-ui/oruga-next"
import { useTitle } from '@vueuse/core'
import { computed, Ref, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useStore } from 'vuex'
import useEvents from "../composables/events"
import { UserBook } from '../model/Book'
import { CreateReadingEvent, ReadingEvent, ReadingEventType, ReadingEventWithUserBook } from '../model/ReadingEvent'
import { Review } from "../model/Review"
import dataService from "../services/DataService"
import { key } from '../store'
import BookCard from "./BookCard.vue"
import QuotesDisplay from './QuotesDisplay.vue'
import ReadingEventModalVue from './ReadingEventModal.vue'
import ReadProgressModal from './ReadProgressModal.vue'
import ReviewBookCard from './ReviewBookCard.vue';
import useTypography from "../composables/typography"

useTitle('Jelu | Home')

const store = useStore(key)
const oruga = useOruga()
const { t } = useI18n({
      inheritLocale: true,
      useScope: 'global'
    })
const { eventClass, eventLabel } = useEvents()

const bannerClass = (type: string) => {
  if (type === 'FINISHED') {
    return "bg-success text-success-content";
  } else if (type === 'DROPPED') {
    return "bg-error text-error-content";
  } else if (type === 'CURRENTLY_READING') {
    return "bg-info text-info-content";
  } else if (type === 'PAUSED') {
    return "bg-warning text-warning-content";
  } else return "bg-base-300";
}

const isLogged = computed(() => {
    return store != null && store != undefined && store.getters.getLogged
  })

const initialLoad : Ref<boolean> = ref(true)

const showModal: Ref<boolean> = ref(false)

const currentlyReadingIsLoading: Ref<boolean> = ref(false)

const recentEventsIsLoading: Ref<boolean> = ref(false)

const books: Ref<Array<UserBook>> = ref([]);
const pausedBooks: Ref<Array<UserBook>> = ref([]);
const pausedIsLoading: Ref<boolean> = ref(false);

const randomBooks: Ref<Array<UserBook>> = ref([])
const upNext: Ref<Array<UserBook>> = ref([])
const getUpNext = async () => {
  try {
    const res = await dataService.findUserBookByCriteria(
      null, null, null, true, null, null, 0, 2, undefined)
    upNext.value = res.content
  } catch (error) {
    console.log("failed get up next : " + error)
  }
}
const showRecentEvents: Ref<boolean> = ref(false)
const showPaused: Ref<boolean> = ref(false)

const events: Ref<Array<ReadingEventWithUserBook>> = ref([]);

const hasBooks = computed(() => books.value.length > 0)

const userReviews: Ref<Array<Review>> = ref([]);

const getCurrentlyReading = async () => {
  currentlyReadingIsLoading.value = true
  try {
    const res = await dataService.findUserBookByCriteria([ReadingEventType.CURRENTLY_READING], null, null, null)
    if (res.numberOfElements <= 6) {
      books.value = res.content
    }
    else {
      books.value = res.content.slice(0,6)
    }
    currentlyReadingIsLoading.value = false
  } catch (error) {
    console.log("failed get books : " + error)
    currentlyReadingIsLoading.value = false
  }

};

const getPausedBooks = async () => {
  pausedIsLoading.value = true
  try {
    const res = await dataService.findUserBookByCriteria([ReadingEventType.PAUSED], null, null, null)
    pausedBooks.value = res.content.slice(0, 6)
    pausedIsLoading.value = false
  } catch (error) {
    console.log("failed get paused books : " + error)
    pausedIsLoading.value = false
  }
}
const nonCurrentlyReadingEvents: Array<ReadingEventType> = [ReadingEventType.DROPPED, ReadingEventType.FINISHED]

const getMyEvents = async () => {
  recentEventsIsLoading.value = true
  try {
    const res = await dataService.myReadingEvents(nonCurrentlyReadingEvents, undefined, undefined, undefined, undefined, undefined, 0, 8, 'endDate,desc')
    const notCurrentlyReading = res.content.filter(e => e.eventType !== ReadingEventType.CURRENTLY_READING)
    events.value = notCurrentlyReading
    recentEventsIsLoading.value = false
  } catch (error) {
    console.log("failed get events : " + error)
    recentEventsIsLoading.value = false
  }
};

const getUserReviews = async () => {
  try {
    const res = await dataService.findReviews(
      undefined, undefined, null,
    null, null,
    0, 20, null)
    userReviews.value = res.content
  } catch (error) {
    console.log("failed get reviews : " + error);
  }
};

const getRandomBook = async () => {
  try {
    // Fetch a few random books and pick the first that isn't finished or dropped
    const res = await dataService.findUserBookByCriteria(
      null, null, null, null, null, null, 0, 10, 'random,desc')
    if (res.content.length > 0) {
      randomBooks.value = res.content.filter(b =>
        b.lastReadingEvent !== ReadingEventType.FINISHED &&
        b.lastReadingEvent !== ReadingEventType.DROPPED
      ).slice(0, 2)
    }
  } catch (error) {
    console.log("failed get random book : " + error)
  }
}

if (isLogged.value) {
  try {
      getCurrentlyReading()
      getUpNext()
      getPausedBooks()
      getMyEvents()
      getUserReviews()
      getRandomBook()
  } catch (error) {
    console.log("failed get books : " + error);
  }
}

watch(() => isLogged.value, (newValue, oldValue) => {
  console.log('logged changed ' + isLogged.value)
  if (initialLoad.value && isLogged.value) {
    try {
      initialLoad.value = false
      getCurrentlyReading()
      getUpNext()
      getMyEvents()
      getPausedBooks()
      getRandomBook()
  } catch (error) {
    console.log("failed get books : " + error);
  }
  }
})

function modalClosed() {
  console.log("modal closed")
  getCurrentlyReading()
}

function defaultCreateEvent(bookId: string): CreateReadingEvent {
  return {
  eventType: ReadingEventType.FINISHED,
  eventDate: new Date(),
  bookId: bookId
}
}

function toggleReadingEventModal(currentEvent: ReadingEvent, edit: boolean) {
  showModal.value = !showModal.value
  oruga.modal.open({
    component: ReadingEventModalVue,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'keep',
    props: {
      "readingEvent": currentEvent,
      "edit": edit
    },
    onClose: modalClosed
  });
}

function toggleReadProgressModal(userBookId: string, bookId: string, pageCount: number|null, currentProgress: number|null, currentPage: number|null) {
  showModal.value = !showModal.value
  oruga.modal.open({
    component: ReadProgressModal,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'keep',
    props: {
      "userBookId": userBookId,
      "bookId": bookId,
      "pageCount": pageCount,
      "currentProgress": currentProgress,
      "currentPage": currentPage,
    },
    onClose: modalClosed
  });
}

const { typographyClasses } = useTypography()
</script>
<template>
  <div v-if="isLogged">
    <div v-if="hasBooks || upNext.length > 0 || pausedBooks.length > 0 || randomBooks.length > 0">

      <!-- TOP ROW: Continue reading | Up next -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6 px-2 sm:px-0">

        <!-- Continue reading -->
        <section v-if="books.length > 0">
          <h2 class="text-lg sm:text-xl font-bold mb-3 flex items-center gap-2">
            <i class="mdi mdi-book-open-page-variant text-sky-300" />
            {{ t('home.currently_reading') }}
            <span class="badge badge-sm bg-sky-200/70 text-sky-900 border-0">{{ books.length }}</span>
          </h2>
          <div class="grid grid-cols-2 gap-2 sm:gap-3">
            <div v-for="book in books" :key="book.id">
              <book-card
                :book="book"
                :public="false"
                size="xl"
                :force-select="false"
                :show-select="false"
                :propose-add="true"
              >
                <template #icon>
                  <span
                    v-tooltip="t('labels.mark_read_or_drop')"
                    class="icon text-info"
                    @click.prevent="toggleReadingEventModal(defaultCreateEvent(book.book.id!!), false)"
                  >
                    <i class="mdi mdi-check-circle mdi-18px" />
                  </span>
                  <span
                    v-tooltip="t('labels.set_progress')"
                    class="icon text-info"
                    @click.prevent="toggleReadProgressModal(book.id!!, book.book.id!!, book.book.pageCount ?? null, book.percentRead ?? null, book.currentPageNumber ?? null)"
                  >
                    <i class="mdi mdi-progress-check mdi-18px" />
                  </span>
                </template>
              </book-card>
            </div>
          </div>
        </section>
        <section v-else-if="currentlyReadingIsLoading">
          <h2 class="text-lg sm:text-xl font-bold mb-3">{{ t('home.currently_reading') }}</h2>
          <div class="grid grid-cols-2 gap-3">
            <o-skeleton height="250px" :animated="true" />
            <o-skeleton height="250px" :animated="true" />
          </div>
        </section>
        <section v-else>
          <h2 class="text-lg sm:text-xl font-bold mb-3 flex items-center gap-2">
            <i class="mdi mdi-book-open-page-variant-outline text-base-content/50" />
            {{ t('home.not_reading') }}
          </h2>
        </section>

        <!-- Up next -->
        <section v-if="upNext.length > 0">
          <h2 class="text-lg sm:text-xl font-bold mb-3 flex items-center gap-2">
            <i class="mdi mdi-bookmark-multiple text-violet-300" />
            Up Next
          </h2>
          <div class="grid grid-cols-2 gap-2 sm:gap-3">
            <div
              v-for="(book, i) in upNext"
              :key="book.id"
              class="relative rounded-lg ring-2 ring-violet-300/70 ring-offset-2 ring-offset-base-100"
            >
              <div class="absolute -top-2 -right-2 z-20 badge badge-sm bg-violet-300 text-violet-950 border-0 font-semibold shadow">
                {{ i + 1 }}
              </div>
              <book-card
                :book="book"
                :public="false"
                size="xl"
                :force-select="false"
                :show-select="false"
                :propose-add="false"
                class="h-full"
              />
            </div>
          </div>
        </section>

      </div>

      <!-- BOTTOM ROW: Paused | Rediscover -->
      <div
        v-if="pausedBooks.length > 0 || randomBooks.length > 0"
        class="grid grid-cols-1 lg:grid-cols-2 gap-4 sm:gap-6 px-2 sm:px-0 mt-4 sm:mt-6"
      >

        <!-- Paused -->
        <section v-if="pausedBooks.length > 0">
          <h2 class="text-lg sm:text-xl font-bold mb-3 flex items-center gap-2">
            <i class="mdi mdi-pause-circle text-amber-300" />
            Paused
            <span class="badge badge-sm bg-amber-200/70 text-amber-900 border-0">{{ pausedBooks.length }}</span>
          </h2>
          <div class="grid grid-cols-2 gap-2 sm:gap-3">
            <div v-for="book in pausedBooks" :key="book.id">
              <book-card
                :book="book"
                :public="false"
                size="xl"
                :force-select="false"
                :show-select="false"
                :propose-add="true"
              >
                <template #icon>
                  <span
                    v-tooltip="'Resume reading'"
                    class="icon text-warning"
                    @click.prevent="toggleReadingEventModal(defaultCreateEvent(book.book.id!!), false)"
                  >
                    <i class="mdi mdi-play-circle mdi-18px" />
                  </span>
                </template>
              </book-card>
            </div>
          </div>
        </section>

        <!-- Rediscover -->
        <section v-if="randomBooks.length > 0">
          <h2 class="text-lg sm:text-xl font-bold mb-3 flex items-center gap-2">
            <i class="mdi mdi-shuffle-variant text-violet-300" />
            Rediscover
          </h2>
          <div class="grid grid-cols-2 gap-2 sm:gap-3">
            <div v-for="book in randomBooks" :key="book.id">
              <book-card
                :book="book"
                :public="false"
                size="xl"
                :force-select="false"
                :show-select="false"
                :propose-add="false"
                class="h-full"
              />
            </div>
          </div>
        </section>

      </div>

    </div>
  </div>
  <div v-else>
    <p class="capitalize">
      {{ t('user.log_first') }}
    </p>
  </div>
</template>
