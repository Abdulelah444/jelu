<script setup lang="ts">
import { useThrottleFn, useTitle } from '@vueuse/core';
import { useRouteQuery } from '@vueuse/router';
import { computed, Ref, ref, watch, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import usePagination from '../composables/pagination';
import useSort from '../composables/sort';
import useBulkEdition from '../composables/bulkEdition';
import { UserBook } from '../model/Book';
import { ReadingEventType } from '../model/ReadingEvent';
import dataService from "../services/DataService";
import BookCard from "./BookCard.vue";
import draggable from "vuedraggable";
import SortFilterBarVue from "./SortFilterBar.vue";
import useTypography from '../composables/typography';

const { t } = useI18n({
      inheritLocale: true,
      useScope: 'global'
    })

useTitle('Jelu | ' + t('nav.to_read'))

const books: Ref<Array<UserBook>> = ref([]);


const onReorder = async () => {
  if (userId.value != null) return  // only reorder your own To-Read list
  try {
    const ids = books.value.map(b => b.id).filter((x): x is string => !!x)
    if (ids.length === 0) return
    await dataService.reorderToRead(ids)
  } catch (e) {
    console.log("failed to persist to-read order: " + e)
  }
}

const { total, page, pageAsNumber, perPage, updatePage, getPageIsLoading, updatePageLoading, pageCount } = usePagination()

const { sortQuery, sortOrder, sortBy, sortOrderUpdated } = useSort('creationDate,desc')

const { showSelect, selectAll, checkedCards, cardChecked, toggleEdit } = useBulkEdition(modalClosed)

const eventTypes: Ref<Array<ReadingEventType>> = useRouteQuery('lastEventTypes', [])

const userId: Ref<string|null> = useRouteQuery('userId', null)
const username = ref("")

const getUsername = async () => {
  if (userId.value != null) {
    username.value = await dataService.usernameById(userId.value)
  }
}

getUsername()

const open = ref(false)

const owned: Ref<string|null> = useRouteQuery('owned', "null")
const ownedAsBool = computed(() => {
  if (owned.value?.toLowerCase() === "null") {
    return null
  } else if (owned.value?.toLowerCase() === "true") {
    return true
  } else {
    return false
  }
  }
)

const getToReadIsLoading: Ref<boolean> = ref(false)

const getToRead = async () => {
  getToReadIsLoading.value = true
  try {
    const res = await dataService.findUserBookByCriteria(
      eventTypes.value, null, userId.value,
    true, ownedAsBool.value, null,
    pageAsNumber.value - 1, perPage.value, sortQuery.value)
    total.value = res.totalElements
    books.value = res.content
    if (! res.empty) {
      page.value =  (res.number + 1).toString(10)
    }
    else {
      page.value = "1"
    }
    getToReadIsLoading.value = false
    updatePageLoading(false)
    removeIds()
  } catch (error) {
    console.log("failed get books : " + error);
    getToReadIsLoading.value = false
    updatePageLoading(false)
  }
};

const removeIds = () => {
  if (userId.value != null) {
    books.value.forEach(b => b.id = undefined)
  }
}

// --- LocalStorage keys for ToReadList ---
const SORT_BY_KEY = "toReadSortBy";
const SORT_ORDER_KEY = "toReadSortOrder";
const EVENT_TYPES_KEY = "toReadEventTypes";
const OWNED_KEY = "toReadOwned";

// Restore saved settings
onMounted(() => {
  const savedSortBy = localStorage.getItem(SORT_BY_KEY);
  const savedSortOrder = localStorage.getItem(SORT_ORDER_KEY);
  if (savedSortBy) sortBy.value = savedSortBy;
  if (savedSortOrder) sortOrder.value = savedSortOrder;

  const savedEventTypes = localStorage.getItem(EVENT_TYPES_KEY);
  if (savedEventTypes) {
    try {
      eventTypes.value = JSON.parse(savedEventTypes);
    } catch {}
  }

  const savedOwned = localStorage.getItem(OWNED_KEY);
  if (savedOwned) owned.value = savedOwned;
});

// Persist changes
watch(sortBy, (newVal) => {
  localStorage.setItem(SORT_BY_KEY, newVal);
});
watch(sortOrder, (newVal) => {
  localStorage.setItem(SORT_ORDER_KEY, newVal);
});
watch(eventTypes, (newVal) => {
  localStorage.setItem(EVENT_TYPES_KEY, JSON.stringify(newVal));
}, { deep: true });
watch(owned, (newVal) => {
  localStorage.setItem(OWNED_KEY, newVal ?? "null");
});



// watches set above sometimes called twice
// so getBooks was sometimes called twice at the same instant
const throttledGetToRead = useThrottleFn(() => {
  getToRead()
}, 100, false)

watch([page, eventTypes, sortQuery, owned], (newVal, oldVal) => {
  console.log("all " + newVal + " " + oldVal)
  if (newVal !== oldVal) {
    throttledGetToRead()
  }
})

function modalClosed() {
  console.log("modal closed")
  throttledGetToRead()
}

const message = computed(() => {
  if (userId.value != null) {
    return t('labels.reading_list_from_name', { name: username.value })
  } else {
    return t('nav.to_read')
  }
} )

getToRead()

const { typographyClasses } = useTypography()
</script>

<template>
  <sort-filter-bar-vue
    :open="open"
    :order="sortOrder"
    @update:open="open = $event"
    @update:sort-order="sortOrderUpdated"
  >
    <template #sort-fields>
      <label class="label">{{ t('sorting.sort_by') }} : </label>
      <div class="field">
        <input
          v-model="sortBy"
          type="radio"
          name="radio-20"
          class="radio radio-primary my-2"
          value="creationDate"
        >
        <span class="label-text">{{ t('sorting.date_added_to_list') }}</span>
      </div>
      <div class="field">
        <input
          v-model="sortBy"
          type="radio"
          name="radio-20"
          class="radio radio-primary my-2"
          value="lastReadingEventDate"
        >
        <span class="label-text">{{ t('sorting.last_reading_event_date') }}</span>
      </div>
      <div class="field">
        <input
          v-model="sortBy"
          type="radio"
          name="radio-20"
          class="radio radio-primary mb-2"
          value="title"
        >
        <span class="label-text">{{ t('sorting.title') }}</span>
      </div>
      <div class="field">
        <input
          v-model="sortBy"
          type="radio"
          name="radio-20"
          class="radio radio-primary mb-2"
          value="publisher"
        >
        <span class="label-text">{{ t('sorting.publisher') }}</span>
      </div>
      <div class="field">
        <input
          v-model="sortBy"
          type="radio"
          name="radio-20"
          class="radio radio-primary mb-2"
          value="pageCount"
        >
        <span class="label-text">{{ t('sorting.page_count') }}</span>
      </div>
      <div class="field">
        <input
          v-model="sortBy"
          type="radio"
          name="radio-20"
          class="radio radio-primary mb-2"
          value="usrAvgRating"
        >
        <span class="label-text">{{ t('sorting.user_avg_rating') }}</span>
      </div>
      <div class="field">
        <input
          v-model="sortBy"
          type="radio"
          name="radio-20"
          class="radio radio-primary mb-2"
          value="avgRating"
        >
        <span class="label-text">{{ t('sorting.avg_rating') }}</span>
        <div class="field">
          <input
            v-model="sortBy"
            type="radio"
            name="radio-20"
            class="radio radio-primary"
            value="random"
          >
          <span class="label-text">{{ t('sorting.random') }}</span>
        </div>
      </div>
    </template>
    <template #filters>
      <div class="field flex flex-col gap-1 capitalize">
        <label class="label">{{ t('reading_events.last_event_type') }} : </label>
        <label class="label">
          <input
            v-model="eventTypes"
            type="checkbox"
            class="checkbox checkbox-primary"
            value="FINISHED"
          >
          {{ t('reading_events.finished') }}
        </label>
        <label class="label">
          <input
            v-model="eventTypes"
            type="checkbox"
            class="checkbox checkbox-primary"
            value="CURRENTLY_READING"
          >
          {{ t('reading_events.currently_reading') }}
        </label>
        <label class="label">
          <input
            v-model="eventTypes"
            type="checkbox"
            class="checkbox checkbox-primary"
            value="DROPPED"
          >
          {{ t('reading_events.dropped') }}
        </label>
      </div>
      <div class="field flex flex-col items-start">
        <label class="label">{{ t('filtering.owned') }} : </label>
        <div class="field">
          <input
            v-model="owned"
            type="radio"
            name="radio-31"
            class="radio radio-primary my-2"
            value="null"
          >
          <span class="label-text">{{ t('filtering.unset') }}</span>
        </div>
        <div class="field">
          <input
            v-model="owned"
            type="radio"
            name="radio-31"
            class="radio radio-primary mb-2"
            value="false"
          >
          <span class="label-text">{{ t('labels.false') }}</span>
        </div>
        <div class="field">
          <input
            v-model="owned"
            type="radio"
            name="radio-31"
            class="radio radio-primary"
            value="true"
          >
          <span class="label-text">{{ t('labels.true') }}</span>
        </div>
      </div>
    </template>
  </sort-filter-bar-vue>
  <div class="flex flex-row justify-between mb-2">
    <div class="flex flex-row gap-1 order-last sm:order-first">
      <button
        class="btn btn-outline btn-success"
        @click="open = !open"
      >
        <span class="icon text-lg">
          <i class="mdi mdi-filter-variant" />
        </span>
      </button>
      <button
        v-tooltip="t('bulk.toggle')"
        class="btn btn-outline btn-primary"
        @click="showSelect = !showSelect"
      >
        <span class="icon text-lg">
          <i class="mdi mdi-pencil" />
        </span>
      </button>
      <button
        v-if="showSelect"
        v-tooltip="t('bulk.select_all')"
        class="btn btn-outline btn-accent"
        @click="selectAll = !selectAll"
      >
        <span class="icon text-lg">
          <i class="mdi mdi-checkbox-multiple-marked" />
        </span>
      </button>
      <button
        v-if="showSelect && checkedCards.length > 0"
        v-tooltip="t('bulk.edit')"
        class="btn btn-outline btn-info"
        @click="toggleEdit(checkedCards)"
      >
        <span class="icon text-lg">
          <i class="mdi mdi-book-edit" />
        </span>
      </button>
    </div>
    <h2
      class="text-3xl capitalize"
      :class="typographyClasses"
    >
      {{ message }} :
    </h2>
    <div />
  </div>
  <o-pagination
    v-if="books.length > 0 && pageCount > 1"
    v-model:current="pageAsNumber"
    :total="total"
    order="centered"
    :per-page="perPage"
    @change="updatePage"
  />
  <draggable
    v-if="books.length > 0"
    v-model="books"
    item-key="id"
    handle=".drag-handle"
    :animation="180"
    class="grid grid-cols-3 md:grid-cols-5 xl:grid-cols-7 2xl:grid-cols-9 gap-0 my-3"
    @end="onReorder"
  >
    <template #item="{ element: book, index }">
      <div
        class="books-grid-item m-2 relative group rounded-lg"
        :class="(userId == null && index < 2) ? 'ring-2 ring-primary ring-offset-2 ring-offset-base-100' : ''"
      >
        <div
          v-if="userId == null && index < 2"
          class="absolute -top-2 -right-2 z-20 badge badge-primary badge-sm font-semibold shadow"
        >
          Up Next {{ index + 1 }}
        </div>
        <div
          class="drag-handle absolute top-1 left-1 z-10 cursor-move bg-base-100/80 rounded p-1 opacity-60 hover:opacity-100"
          title="Drag to reorder"
        >
          <i class="mdi mdi-drag" />
        </div>
        <book-card
          :book="book"
          :force-select="selectAll"
          :public="false"
          :show-select="showSelect"
          :checked-ids="checkedCards"
          :propose-add="userId == null"
          class="h-full"
          @update:modal-closed="modalClosed"
          @update:checked="cardChecked"
        />
      </div>
    </template>
  </draggable>
  <div
    v-else-if="getToReadIsLoading"
    class="flex flex-row justify-center justify-items-center gap-3"
  >
    <o-skeleton
      class="justify-self-center basis-36"
      height="250px"
      :animated="true"
    />
    <o-skeleton
      class="justify-self-center basis-36"
      height="250px"
      :animated="true"
    />
    <o-skeleton
      class="justify-self-center basis-36"
      height="250px"
      :animated="true"
    />
  </div>
  <div v-else>
    <h2
      class="text-3xl"
      :class="typographyClasses"
    >
      {{ t('labels.nothing_to_read') }}
    </h2>
    <span class="icon">
      <i class="mdi mdi-book-open-page-variant-outline mdi-48px" />
    </span>
  </div>
  <o-pagination
    v-if="books.length > 0"
    v-model:current="pageAsNumber"
    :total="total"
    order="centered"
    :per-page="perPage"
    @change="updatePage"
  />
  <o-loading
    v-model:active="getPageIsLoading"
    :full-page="true"
    :cancelable="true"
  />
</template>

<style scoped>

label.label {
    font-weight: bold;
}
</style>
