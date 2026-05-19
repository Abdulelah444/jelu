<script setup lang="ts">
import { computed, Ref, ref, watch } from "vue";
import { useI18n } from 'vue-i18n';
import { UserBook } from "../model/Book";
import { ReadingEventType } from "../model/ReadingEvent";
import { ObjectUtils } from "../utils/ObjectUtils";
import dataService from "../services/DataService";
import { ShelfLocation } from "../model/PhysicalLibrary";

const { t } = useI18n({
      inheritLocale: true,
      useScope: 'global'
    })

const props = defineProps<{
  book: UserBook,
  size?: string,
  forceSelect: boolean,
  checkedIds?: Array<string>,
  showSelect: boolean,
  proposeAdd: boolean,
  seriesId?: string,
  public: boolean // is it on a public facing page (so hide links etc...)
}>();
const emit = defineEmits<{
  (e: 'update:modalClosed', open: boolean): void,
  (e: 'update:checked', id: string|null, checked: boolean): void
}>()

const bookId = props.book.id != null ? props.book.id as string : props.book.book.id as string
const checked: Ref<boolean> = ref(props.checkedIds?.includes(bookId) ?? false)

const shelfLocation: Ref<ShelfLocation | null> = ref(null)
const fetchLocation = async () => {
  if (props.book.id) {
    try {
      shelfLocation.value = await dataService.getBookPhysicalLocation(props.book.id)
    } catch (e) { /* ignore */ }
  }
}
fetchLocation()

watch(() => props.forceSelect, (newVal, oldVal) => {
  checked.value = props.forceSelect
})

const eventClass = computed(() => {
  if (props.book.lastReadingEvent) {
    if (props.book.lastReadingEvent === ReadingEventType.FINISHED) {
      return "badge-info";
    } else if (props.book.lastReadingEvent === ReadingEventType.DROPPED) {
      return "badge-error";
    } else if (
      props.book.lastReadingEvent === ReadingEventType.CURRENTLY_READING
    ) {
      return "badge-success";
    } else return "";
  }
  return "";
});

const eventText = computed(() => {
  if (props.book.lastReadingEvent) {
    if (props.book.lastReadingEvent === ReadingEventType.CURRENTLY_READING) {
      return t('reading_events.reading');
    } else if (props.book.lastReadingEvent === ReadingEventType.DROPPED) {
      return t('reading_events.dropped');
    } else if (props.book.lastReadingEvent === ReadingEventType.FINISHED) {
      return t('reading_events.finished');
    }
  }
  return "";
});

const bannerClass = computed(() => {
  if (props.book.lastReadingEvent) {
    if (props.book.lastReadingEvent === ReadingEventType.FINISHED) {
      return "bg-success text-success-content";
    } else if (props.book.lastReadingEvent === ReadingEventType.DROPPED) {
      return "bg-error text-error-content";
    } else if (props.book.lastReadingEvent === ReadingEventType.CURRENTLY_READING) {
      return "bg-info text-info-content";
    }
  }
  return "";
});

const authorsText = computed(() => {
  let txt = "";
  if (props.book.book.authors && props.book.book.authors.length > 0) {
    let first = true;
    for (let author of props.book.book.authors) {
      if (first) {
        txt += "";
        first = false;
      } else {
        txt += ", ";
      }
      txt += author.name;
    }
  }
  return txt;
});

const showProgressBar = (book: UserBook) => {
  return book.percentRead
      && book.percentRead > 0
      && book.lastReadingEvent != null
      && book.lastReadingEvent === ReadingEventType.CURRENTLY_READING
}

const progressBarTooltip = computed(() => {
  return props.book.currentPageNumber != null ? `p. ${props.book.currentPageNumber}` : `${props.book.percentRead} %`
})

const currentSeries = computed(() => {
  if (props.book.book.series != null &&      props.book.book.series?.length > 0) {
    if (props.seriesId != null) {
      return props.book.book.series?.find(s => s.seriesId === props.seriesId)
    } else {
      return props.book.book.series[0]
    }
  }
  return null
})

watch(checked, (newVal, oldVal) => {
  console.log(props.book.id != null ? props.book.id : props.book.book.id + " " + checked.value)
  emit("update:checked", props.book.id != null ? props.book.id as string : props.book.book.id as string , checked.value)
})

const currentTimestamp = ObjectUtils.timestamp()

</script>

<template>
  <div
    class="card card-sm bg-base-200 border border-base-300 shadow-md w-full overflow-hidden"
  >
    <div
      v-if="book.lastReadingEvent"
      class="text-center text-xs font-semibold py-1 uppercase tracking-wide"
      :class="bannerClass"
    >{{ eventText }}</div>
    <div>
      <router-link
        v-if="book.id != null"
        :to="{ name: 'book-detail', params: { bookId: book.id } }"
      >
        <figure>
          <img
            v-if="book.book.image"
            :src="'/files/' + book.book.image + '?timestamp=' + currentTimestamp"
            alt="cover image"
            class="object-contain"
            :class="props.size === 'xl' ? 'h-44' : 'h-32'"
          >
          <img
            v-else
            src="../assets/placeholder_asset.jpg"
            alt="cover placeholder"
            class="h-44 object-contain"
          >
        </figure>
      </router-link>
      <router-link
        v-else
        :to="{ name: 'book-reviews', params: { bookId: book.book.id } }"
      >
        <figure>
          <img
            v-if="book.book.image"
            :src="'/files/' + book.book.image"
            alt="cover image"
            class="object-cover w-full"
            :class="props.size === 'xl' ? 'h-80' : 'h-56'"
          >
          <img
            v-else
            src="../assets/placeholder_asset.jpg"
            alt="cover placeholder"
            class="h-56 object-contain"
          >
        </figure>
      </router-link>
      <div
        v-if="showProgressBar(book)"
        v-tooltip="progressBarTooltip"
        class="bg-success absolute h-1.5"
        :style="{ width: book.percentRead + '%' }"
      />
      <div
        v-if="props.showSelect"
        class="absolute top-0 left-0 p-3 z-10"
        @click.stop
      >
        <input
          v-model="checked"
          type="checkbox"
          class="checkbox checkbox-accent checkbox-lg rounded-md border-2 bg-base-100/80 shadow-md"
        >
      </div>
    </div>
    <div class="card-body">
      <router-link
        v-if="book.id != null"
        class="grow"
        :to="{ name: 'book-detail', params: { bookId: book.id } }"
      >
        <h2
          v-tooltip="book.book.title"
          class="card-title text-sm line-clamp-2 hover:link""
        >
          {{ book.book.title }}
        </h2>
      </router-link>
      <router-link
        v-else
        class="grow"
        :to="{ name: 'book-reviews', params: { bookId: book.book.id } }"
      >
        <h2
          v-tooltip="book.book.title"
          class="card-title text-sm line-clamp-2 hover:link""
        >
          {{ book.book.title }}
        </h2>
      </router-link>
      <div v-if="book.book.authors != null && book.book.authors.length > 0">
        <span
          v-for="author in book.book.authors.slice(0,2)"
          :key="author.id"
        >
          <router-link
            v-if="!public"
            class="link hover:underline hover:decoration-2 hover:decoration-secondary line-clamp-1 inline-block text-xs opacity-70"
            :to="{ name: 'author-detail', params: { authorId: author.id } }"
          >
            {{ author.name }}
          </router-link>
          <span v-else>{{ author.name }}</span>
          <span>&nbsp;</span>
        </span>
        <span
          v-if="book.book.authors.length > 3"
          v-tooltip="authorsText"
        >&#8230;</span>
      </div>
      <div class="card-actions justify-end items-center gap-1">
        <span
          v-if="book.lastReadingEvent"
          :class="eventClass"
          class="badge"
        >{{ eventText }}</span>
        <div class="flex items-center gap-1">
          <router-link
            v-if="currentSeries != null && ! props.public"
            v-tooltip="currentSeries.name"
            class="badge mx-1"
            :to="{ name: 'series', params: { seriesId: currentSeries.seriesId } }"
          >
            #{{ currentSeries.numberInSeries }}
          </router-link>
          <span v-if="currentSeries != null && props.public">#{{ currentSeries.numberInSeries }}</span>
          <span
            v-if="book.userAvgRating"
            v-tooltip="t('labels.user_avg_rating', {rating : book.userAvgRating})"
            class="icon text-info"
          >
            <i class="mdi mdi-star mdi-18px" />
            {{ book.userAvgRating }}
          </span>
          <span
            v-if="book.avgRating"
            v-tooltip="t('labels.avg_rating', {rating : book.avgRating})"
            class="icon text-info"
          >
            <i class="mdi mdi-star-outline mdi-18px" />
            {{ book.avgRating }}
          </span>
          <span
            v-if="book.owned"
            v-tooltip="t('book.owned')"
            class="icon text-info"
          >
            <i class="mdi mdi-bookshelf mdi-18px" />
          </span>
          <span
            v-if="book.toRead"
            v-tooltip="t('book.in_read_list')"
            class="icon text-info"
          >
            <i class="mdi mdi-eye mdi-18px" />
          </span>
          <span
            v-if="proposeAdd === true && book.id == null"
            v-tooltip="t('labels.book_not_yet_in_books')"
            class="icon text-error"
          >
            <i class="mdi mdi-plus-circle mdi-18px" />
          </span>
	  <span
            v-if="shelfLocation"
            v-tooltip="shelfLocation.displayString"
            class="icon text-info"
          >
            <i class="mdi mdi-map-marker mdi-18px" />
          </span>
          <slot name="icon" />
          <slot name="date" />
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>

</style>
