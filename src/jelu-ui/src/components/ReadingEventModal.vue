<script setup lang="ts">
import { Ref, ref, watch } from "vue";
import { CreateReadingEvent, ReadingEvent, ReadingEventType } from "../model/ReadingEvent";
import dataService from "../services/DataService";
import { useI18n } from 'vue-i18n'
import Datepicker from 'vue3-datepicker'
import useTypography from "../composables/typography";

const { t } = useI18n({
      inheritLocale: true,
      useScope: 'global'
    })

const props = defineProps<{
  readingEvent: ReadingEvent|CreateReadingEvent,
  edit: boolean
}>()

const currentEvent: Ref<ReadingEvent> = ref(props.readingEvent)
const currentCreateEvent: Ref<CreateReadingEvent> = ref(props.readingEvent)
console.log(currentEvent.value)
console.log(currentCreateEvent.value)

watch(() => currentCreateEvent.value.eventType, (newValue, oldValue) => {
  if (currentCreateEvent.value.eventType == ReadingEventType.CURRENTLY_READING) {
    currentCreateEvent.value.eventDate = undefined
    currentCreateEvent.value.startDate = new Date()
  } else {
    currentCreateEvent.value.startDate = undefined
    currentCreateEvent.value.eventDate = new Date()
  }
})

const progress: Ref<boolean> = ref(false)

const emit = defineEmits<{
  (e: 'close'): void
}>()

const create = () => {
  progress.value = true
  if (currentCreateEvent.value.eventType === ReadingEventType.CURRENTLY_READING) {
    console.log("cleaning event date if currently reading")
    currentCreateEvent.value.eventDate = undefined
  }
  dataService.createReadingEvent(currentCreateEvent.value)
    .then(res => {
      progress.value = false
      emit('close')
    })
    .catch(e => {
      progress.value = false
    })
}

const update = () => {
  progress.value = true
  // if user changed a finished event to a currently reading -> remove end date
  if (currentEvent.value.eventType === ReadingEventType.CURRENTLY_READING) {
    currentEvent.value.endDate = undefined
  }
  dataService.updateReadingEvent(currentEvent.value)
    .then(res => {
      progress.value = false
      emit('close')
    })
    .catch(e => {
      progress.value = false
    })
}

const deleteEvent = () => {
  if (currentEvent.value.id != null) {
    progress.value = true
    dataService.deleteReadingEvent(currentEvent.value.id)
    .then(res => {
      progress.value = false
      emit('close')
    })
    .catch(e => {
      progress.value = false
    })
  }
}

const { typographyClasses } = useTypography()
</script>
<template>
  <section class="event-modal p-4">
    <!-- EDIT MODE -->
    <div v-if="props.edit">
      <h1 class="text-xl font-medium capitalize mb-4" :class="typographyClasses">
        {{ t('reading_events.edit_event') }}
      </h1>

      <!-- Status segmented toggle -->
      <div class="mb-4">
        <label class="text-sm text-base-content/60 block mb-2 capitalize">{{ t('reading_events.event_type') }}</label>
        <div class="inline-flex rounded-lg border border-base-content/30 overflow-hidden">
          <div class="px-4 py-2 text-sm cursor-pointer transition-colors" :class="currentCreateEvent.eventType === 'CURRENTLY_READING' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="currentCreateEvent.eventType = ReadingEventType.CURRENTLY_READING">
            {{ t('reading_events.currently_reading') }}
          </div>
          <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="currentCreateEvent.eventType === 'PAUSED' ? 'bg-warning/20 text-warning font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="currentCreateEvent.eventType = ReadingEventType.PAUSED">
            {{ t('reading_events.paused') }}
          </div>
          <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="currentCreateEvent.eventType === 'FINISHED' ? 'bg-success/20 text-success font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="currentCreateEvent.eventType = ReadingEventType.FINISHED">
            {{ t('reading_events.finished') }}
          </div>
          <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="currentCreateEvent.eventType === 'DROPPED' ? 'bg-error/20 text-error font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="currentCreateEvent.eventType = ReadingEventType.DROPPED">
            {{ t('reading_events.dropped') }}
          </div>
        </div>
      </div>

      <!-- Start date -->
      <div class="mb-4">
        <label class="text-sm text-base-content/60 block mb-2 capitalize">{{ t('reading_events.start_date') }}</label>
        <datepicker v-model="currentEvent.startDate" class="input input-bordered w-full" :typeable="true" :clearable="false" />
      </div>

      <!-- End date (not for currently reading) -->
      <div v-if="currentEvent.eventType !== ReadingEventType.CURRENTLY_READING" class="mb-4">
        <label class="text-sm text-base-content/60 block mb-2 capitalize">{{ t('reading_events.event_date') }}</label>
        <datepicker v-model="currentEvent.endDate" class="input input-bordered w-full" :typeable="true" :clearable="true" />
      </div>

      <!-- Buttons -->
      <div class="flex gap-2 mt-4">
        <button class="btn btn-success" @click="update">
          <i class="mdi mdi-check mdi-18px mr-1" />
          {{ t('labels.submit') }}
        </button>
        <button class="btn btn-error btn-outline" @click="deleteEvent">
          <i class="mdi mdi-delete mdi-18px mr-1" />
          {{ t('labels.delete') }}
        </button>
      </div>
    </div>

    <!-- CREATE MODE -->
    <div v-else>
      <h1 class="text-xl font-medium capitalize mb-4" :class="typographyClasses">
        {{ t('reading_events.choose_event') }}
      </h1>

      <!-- Status segmented toggle -->
      <div class="mb-4">
        <label class="text-sm text-base-content/60 block mb-2 capitalize">{{ t('reading_events.event_type') }}</label>
        <div class="inline-flex rounded-lg border border-base-content/30 overflow-hidden">
          <div class="px-4 py-2 text-sm cursor-pointer transition-colors" :class="currentCreateEvent.eventType === 'CURRENTLY_READING' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="currentCreateEvent.eventType = ReadingEventType.CURRENTLY_READING">
            {{ t('reading_events.currently_reading') }}
          </div>
          <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="currentCreateEvent.eventType === 'PAUSED' ? 'bg-warning/20 text-warning font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="currentCreateEvent.eventType = ReadingEventType.PAUSED">
            {{ t('reading_events.paused') }}
          </div>
          <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="currentCreateEvent.eventType === 'FINISHED' ? 'bg-success/20 text-success font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="currentCreateEvent.eventType = ReadingEventType.FINISHED">
            {{ t('reading_events.finished') }}
          </div>
          <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/30 transition-colors" :class="currentCreateEvent.eventType === 'DROPPED' ? 'bg-error/20 text-error font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="currentCreateEvent.eventType = ReadingEventType.DROPPED">
            {{ t('reading_events.dropped') }}
          </div>
        </div>
      </div>

      <!-- Start date (for currently reading) -->
      <div v-if="currentCreateEvent.eventType === ReadingEventType.CURRENTLY_READING" class="mb-4">
        <label class="text-sm text-base-content/60 block mb-2 capitalize">{{ t('reading_events.start_date') }}</label>
        <datepicker v-model="currentCreateEvent.startDate" class="input input-bordered w-full" :clearable="true" :typeable="true" />
      </div>

      <!-- Event date (for non-currently reading) -->
      <div v-if="currentCreateEvent.eventType !== ReadingEventType.CURRENTLY_READING" class="mb-4">
        <label class="text-sm text-base-content/60 block mb-2 capitalize">{{ t('reading_events.event_date') }}</label>
        <datepicker v-model="currentCreateEvent.eventDate" class="input input-bordered w-full" :clearable="true" :typeable="true" />
      </div>

      <!-- Create button -->
      <div class="mt-4">
        <button class="btn btn-success" @click="create">
          <i class="mdi mdi-check mdi-18px mr-1" />
          {{ t('labels.create') }}
        </button>
      </div>
    </div>

    <progress v-if="progress" class="animate-pulse progress progress-success mt-5" max="100" />
  </section>
</template>
<style lang="scss">
</style>
