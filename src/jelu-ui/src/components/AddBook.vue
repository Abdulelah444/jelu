<script setup lang="ts">
import { useOruga } from "@oruga-ui/oruga-next";
import IsbnVerify from '@w0s/isbn-verify';
import { useTitle } from '@vueuse/core';
import { computed, reactive, Ref, ref, watch } from "vue";
import { useI18n } from 'vue-i18n';
import { useRouter, onBeforeRouteLeave } from 'vue-router';
import { useStore } from 'vuex';
import { Author } from "../model/Author";
import { Wrapper } from "../model/autocomplete-wrapper";
import { UserBook } from "../model/Book";
import { Path } from "../model/DirectoryListing";
import { Metadata } from "../model/Metadata";
import { SeriesOrder } from "../model/Series";
import { Tag } from "../model/Tag";
import dataService from "../services/DataService";
import { key } from '../store';
import { ObjectUtils } from "../utils/ObjectUtils";
import { StringUtils } from "../utils/StringUtils";
import AutoImportFileModalVue from "./AutoImportFileModal.vue";
import ScanModal from "./ScanModal.vue";
import AutoImportFormModalVue from "./AutoImportFormModal.vue";
import ImagePickerModal from "./ImagePickerModal.vue";
import SeriesCompleteInput from "./SeriesCompleteInput.vue";
import ClosableBadge from "./ClosableBadge.vue";
import FormField from "./FormField.vue";
import { Role } from "../model/Role";
import useTypography from "../composables/typography";

const { t } = useI18n({
      inheritLocale: true,
      useScope: 'global'
    })

useTitle('Jelu | ' + t('nav.add_book'))

const store = useStore(key)
const router = useRouter()
onBeforeRouteLeave(() => {
  navigator.mediaDevices?.getUserMedia({ video: true }).then(s => s.getTracks().forEach(t => t.stop())).catch(() => {})
  oruga.modal.closeAll()
})
import { onBeforeRouteLeave } from "vue-router"
const oruga = useOruga()

const datepicker = ref(null);
const publishedDate: Ref<Date | null> = ref(null)
const progress: Ref<boolean> = ref(false)
const form = reactive({
  title: "",
  summary: "",
  isbn10: "",
  isbn13: "",
  publisher: "",
  pageCount: null,
  personalNotes: "",
  owned: true,
  borrowed: null,
  toRead: null,
  percentRead: 0,
  currentPageNumber: null,
  googleId: "",
  amazonId: "",
  goodreadsId: "",
  librarythingId: "",
  isfdbId: "",
  openlibraryId: "",
  noosfereId: "",
  inventaireId: "",
  language: "",
  originalTitle: "",
  price: null
});
const eventType = ref(null);
const eventDate: Ref<Date|null> = ref(new Date());
const selectedShelfId: Ref<string | null> = ref(null)
const shelfOptions: Ref<Array<{id: string, label: string}>> = ref([])
const imageUrl = ref<string | null>(null);
const imagePath = ref<string | null>(null);
const file = ref(null);
const uploadType = ref('web');

const uploadPercentage = ref(0);
const errorMessage = ref("");
const ownedDisplay = computed(() => {
  if (form.owned) {
    return t('book.owned')
  }
  return ""
})
const borrowedDisplay = computed(() => {
  if (form.borrowed) {
    return t('book.borrowed')
  }
  return ""
})
const toReadDisplay = computed(() => {
  if (form.toRead) {
    return t('labels.book_will_be_added')
  }
  return ""
})

watch(() => [form.currentPageNumber, form.percentRead, form.pageCount],(newVal, oldVal) => {
  if (form.pageCount != null) {
    ObjectUtils.computePages(newVal, oldVal, form, form.pageCount)
  }
})

const filteredAuthors: Ref<Array<Wrapper>> = ref([]);
const authors: Ref<Array<Author>> = ref([]);

const filteredTags: Ref<Array<Wrapper>> = ref([]);
const tags: Ref<Array<Tag>> = ref([]);

const translators: Ref<Array<Author>> = ref([]);
const filteredTranslators: Ref<Array<Wrapper>> = ref([]);

const narrators: Ref<Array<Author>> = ref([]);
const filteredNarrators: Ref<Array<Wrapper>> = ref([]);

const filteredPublishers: Ref<Array<string>> = ref([])

const seriesCopy: Ref<Array<SeriesOrder>> = ref([])

const showModal: Ref<boolean> = ref(false)
const metadata: Ref<Metadata | null> = ref(null)

const showImagePickerModal: Ref<boolean> = ref(false)

const hasImage = computed(() => {
  return StringUtils.isNotBlank(metadata.value?.image)
})
const deleteImage: Ref<boolean> = ref(false)

function toggleRemoveImage() {
  deleteImage.value = !deleteImage.value
}

const loadShelfOptions = async () => {
  try {
    const locations = await dataService.getPhysicalLocations()
    const options: Array<{id: string, label: string}> = []
    for (const loc of locations) {
      const bookcases = await dataService.getPhysicalBookcases(loc.id!)
      for (const bc of bookcases) {
        const fullBc = await dataService.getPhysicalBookcaseById(bc.id!)
        if (fullBc.shelves) {
          for (const shelf of fullBc.shelves) {
            const label = loc.name + ' > ' + bc.name + ' > ' + (shelf.label || 'Shelf ' + shelf.position)
            options.push({ id: shelf.id!, label })
          }
        }
      }
    }
    shelfOptions.value = options
  } catch (e) {
    console.log("Failed to load shelf options: " + e)
  }
}
loadShelfOptions()

const importBook = async () => {
  console.log("import book");
  if (StringUtils.isNotBlank(form.title)) {
    const alreadyExisting = await dataService.checkIsbnExists(form.isbn10, form.isbn13)
    console.log('already existing')
    console.log(alreadyExisting)
    let saveBook = true
    if (alreadyExisting != null) {
      saveBook = false
      await ObjectUtils.swalYesNoMixin.fire({
        html: `<p>${t('labels.book_with_same_isbn_already_exists')}:<br>${alreadyExisting.title}<br>${t('labels.save_new_anyway')}</p>`,
        showDenyButton: false,
        confirmButtonText: t('labels.save'),
        cancelButtonText: t('labels.dont_save')
      }).then((result) => {
        if (result.isConfirmed) {
          saveBook = true
        } else if (result.isDenied) {
          ObjectUtils.baseSwalMixin.fire('', t('labels.changes_not_saved'), 'info')
        }
      })
    }
    console.log(`save book ${saveBook}`)
    if (!saveBook) {
      return
    }
    const userBook: UserBook = fillBook(form, publishedDate.value)
    authors.value.forEach((a) => {
        userBook.book.authors?.push(a)
    });
    tags.value.forEach((t) => userBook.book.tags?.push(t));
    translators.value.forEach((tr) => userBook.book.translators?.push(tr));
    narrators.value.forEach((n) => userBook.book.narrators?.push(n))
    seriesCopy.value.forEach((s) => {
      if (s.name.trim().length > 0) {
        userBook.book.series?.push(s)
      }
    })
    if (StringUtils.isNotBlank(imageUrl.value)) {
      userBook.book.image = imageUrl.value;
    }
    else if (imagePath.value != null && StringUtils.isNotBlank(imagePath.value)) {
      userBook.book.image = imagePath.value
    }
    else if (!deleteImage.value
      && metadata.value != null
      && metadata.value?.image != null
      && StringUtils.isNotBlank(metadata.value.image)) {
      userBook.book.image = metadata.value.image
    }
    if (eventType.value !== null && eventType.value !== "NONE" && eventDate.value != null) {
      console.log(
        "type " + StringUtils.readingEventTypeForValue(eventType.value)
      );
      userBook.lastReadingEvent = StringUtils.readingEventTypeForValue(eventType.value);
      userBook.lastReadingEventDate = eventDate.value?.toISOString()
    }
    try {
      console.log(`push book ` + userBook);
      console.log(userBook);
      progress.value = true
      const res: UserBook = await dataService.saveUserBookImage(
        userBook,
        file.value,
        (event: { loaded: number; total: number }) => {
          const percent = Math.round((100 * event.loaded) / event.total);
          console.log("percent " + percent);
          uploadPercentage.value = percent;
        }
      );
      progress.value = false
      console.log(`saved book ${res.book.title}`);
      ObjectUtils.toast(oruga, "success", t('labels.book_title_saved', {title : res.book.title}), 4000)
      if (selectedShelfId.value && res.id) {
        try {
          await dataService.assignBookToShelf(selectedShelfId.value, { userBookId: res.id })
        } catch (e) {
          console.log('Failed to assign to shelf: ' + e)
        }
      }
      clearForm();
      selectedShelfId.value = null
      await router.push({name: 'my-books'})
    } catch (error: any) {
      progress.value = false
      ObjectUtils.toast(oruga, "danger", t('labels.error_message', {msg : error.message}), 4000)
    }
  } else {
    errorMessage.value = t('labels.provide_title');
  }
};

const fillBook = (formdata: any, publishedDate: Date | null): UserBook => {
  const userBook: UserBook = {
    book: {
      title: formdata.title,
      isbn10: formdata.isbn10,
      isbn13: formdata.isbn13,
      summary: formdata.summary,
      publisher: formdata.publisher,
      image: formdata.image,
      pageCount: formdata.pageCount,
      publishedDate: publishedDate?.toISOString(),
      series: [],
      googleId: formdata.googleId,
      amazonId: formdata.amazonId,
      goodreadsId: formdata.goodreadsId,
      librarythingId: formdata.librarythingId,
      isfdbId: formdata.isfdbId,
      openlibraryId: formdata.openlibraryId,
      noosfereId: formdata.noosfereId,
      inventaireId: formdata.inventaireId,
      language: formdata.language,
      authors: [],
      translators: [],
      narrators: [],
      tags: [],
      originalTitle: formdata.originalTitle.length > 0 ? formdata.originalTitle : null
    },
    owned: formdata.owned,
    personalNotes: formdata.personalNotes,
    toRead: formdata.toRead,
    percentRead: formdata.percentRead,
    currentPageNumber: formdata.currentPageNumber,
    borrowed: formdata.borrowed,
    price: (formdata.price == null || formdata.price < 0) ? null : formdata.price
  }
  return userBook
}

const clearForm = () => {
  clearImageField();
  errorMessage.value = "";
  eventType.value = null;
  file.value = null;
  authors.value = [];
  tags.value = [];
  translators.value = [];
  narrators.value = []
  uploadPercentage.value = 0;
  form.title = "";
  form.summary = "";
  form.isbn10 = "";
  form.isbn13 = "";
  form.publisher = "";
  form.pageCount = null;
  publishedDate.value = null
  seriesCopy.value = []
  form.language = ""
  form.owned = true
  form.personalNotes = ""
  form.amazonId = ""
  form.googleId = ""
  form.goodreadsId = ""
  form.librarythingId = ""
  form.isfdbId = ""
  form.openlibraryId = ""
  form.noosfereId = ""
  form.inventaireId = ""
  form.percentRead = 0
  form.currentPageNumber = null
  form.originalTitle = ""
  form.price= null
};

const clearDatePicker = () => {
  // close datepicker on reset
  publishedDate.value = null
};

const handleFileUpload = (event: any) => {
  file.value = event.target.files[0];
};

const clearImageField = () => {
  imageUrl.value = "";
};

function getFilteredData(text: string, target: Array<Wrapper>) {
  dataService.findAuthorByCriteria(Role.ANY, text).then((data) => {
    target.splice(0, target.length)
    data.content.forEach(a => target.push(ObjectUtils.wrapForOptions(a)))
  })
}

function getFilteredTags(text: string) {
  filteredTags.value.splice(0, filteredTags.value.length)
  dataService.findTagsByCriteria(text).then((data) => data.content.forEach(t => filteredTags.value.push(ObjectUtils.wrapForOptions(t))))
}

function getFilteredPublishers(text: string) {
  dataService.findPublisherByCriteria(text).then(data => filteredPublishers.value = data.content)
}

function beforeAdd(item: Author | string, target: Array<Author>) {
  let shouldAdd = true
  if (item instanceof Object) {
    target.forEach(author => {
      console.log(`author ${author.name}`)
      if (author.name === item.name) {
        console.log(`author ${author.name} item ${item.name}`)
        shouldAdd = false;
      }
    });
  }
  else {
    target.forEach(author => {
      console.log(`author ${author.name}`)
      if (author.name === item) {
        console.log(`author ${author.name} item ${item}`)
        shouldAdd = false;
      }
    });
  }
  return shouldAdd
}

function beforeAddTag(item: Tag | string) {
  let shouldAdd = true
  if (item instanceof Object) {
    tags.value.forEach(tag => {
      console.log(`tag ${tag.name}`)
      if (tag.name === item.name) {
        console.log(`tag ${tag.name} item ${item.name}`)
        shouldAdd = false;
      }
    });
  }
  else {
    tags.value.forEach(tag => {
      console.log(`tag ${tag.name}`)
      if (tag.name === item) {
        console.log(`tag ${tag.name} item ${item}`)
        shouldAdd = false;
      }
    });
  }
  return shouldAdd
}

function selectPublisher(publisher: string) {
  // we receive from oruga weird events while nothing is selected
  // so try to get rid of those null data we receive
  if (publisher != null) {
    form.publisher = publisher
  }
}

const toggleModal = (file: boolean) => {
  showModal.value = !showModal.value
  oruga.modal.open({
    parent: this,
    component: file ? AutoImportFileModalVue : AutoImportFormModalVue,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'keep',
    props: {
        "book": undefined,
      },
    events: {
      metadataReceived: (modalMetadata: Metadata) => {
        console.log("received metadata")
        console.log(modalMetadata)
        metadata.value = modalMetadata
        mergeMetadata()
      }
    },
    onClose: modalClosed
  });
}
let scanBarcodeReader: any = null
const toggleScanModal = () => {
  oruga.modal.open({
    component: ScanModal,
    trapFocus: true,
    active: true,
    canCancel: ["x", "button", "outside"],
    scroll: "keep",
    events: {
      decoded: (barcode: string | null) => {
        if (barcode != null) {
          form.isbn13 = barcode
        }
      },
    },
    onClose: () => {
      navigator.mediaDevices?.getUserMedia({ video: true }).then(s => s.getTracks().forEach(t => t.stop())).catch(() => {})
    },
  })
}

const toggleImagePickerModal = () => {
  showImagePickerModal.value = !showImagePickerModal.value
  oruga.modal.open({
    parent: this,
    component: ImagePickerModal,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'keep',
    events: {
      choose: (path: Path) => {
        console.log("received path")
        console.log(path)
        imagePath.value = path.path
      }
    },
    onClose: modalClosed
  });
}

function modalClosed() {
  console.log("modal closed")
}

const mergeMetadata = () => {
  for (const key in metadata.value) {
    console.log("key")
    console.log(key)
    if (key in form) {
      const castKey = key as (keyof typeof metadata.value & keyof typeof form);
      (form[castKey] as any) = metadata.value[castKey];
    }
  }
  if (metadata.value?.authors != null && metadata.value.authors.length > 0) {
    const auths: Array<Author> = []
    metadata.value.authors.forEach(a => auths.push(ObjectUtils.createNamedItem(a)))
    authors.value = auths
  }
  if (metadata.value?.tags != null && metadata.value.tags.length > 0) {
    const importedTags: Array<Tag> = []
    metadata.value.tags.forEach(t => importedTags.push(ObjectUtils.createNamedItem(t)))
    tags.value = importedTags
  }
  if (metadata.value?.publishedDate && StringUtils.isNotBlank(metadata.value?.publishedDate)) {
    publishedDate.value = new Date(metadata.value?.publishedDate)
  }
  if (metadata.value?.series != null && metadata.value?.series?.length > 0) {
    seriesCopy.value.push({
      "name": metadata.value?.series,
      "numberInSeries" : metadata.value.numberInSeries
    })
  }
  if (metadata.value?.publisher) {
    selectPublisher(metadata.value.publisher)
  }
}

const { typographyClasses } = useTypography()

const isbn10ValidationMessage = ref("")

const isbn13ValidationMessage = ref("")

const validateIsbn10 = (isbn: string) => {
  if (StringUtils.isNotBlank(isbn)) {
    const isbnVerify = new IsbnVerify(isbn);
    if (!isbnVerify.isIsbn10()) {
      isbn10ValidationMessage.value = t('labels.invalid_isbn10')
    }
    else {


      isbn10ValidationMessage.value = ""
    }
  }
  else {
    isbn10ValidationMessage.value = ""
  }
}

const validateIsbn13 = (isbn: string) => {
  if (StringUtils.isNotBlank(isbn)) {
    const isbnVerify = new IsbnVerify(isbn);
    if (!isbnVerify.isIsbn13()) {
      isbn13ValidationMessage.value = t('labels.invalid_isbn13')
    }
    else {
      isbn13ValidationMessage.value = ""
    }
  }
  else {
    isbn13ValidationMessage.value = ""
  }
}

const displayDatepicker = computed(() => {
  return eventType.value !== null && eventType.value !== "NONE"
})

</script>

<template>
  <section>
    <div class="max-w-2xl mx-auto px-4 py-6">
      <!-- Header -->
      <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 mb-6">
        <h1 class="text-xl font-medium capitalize">{{ t('nav.add_book') }}</h1>
        <div class="flex gap-2 flex-wrap">
          <button class="btn btn-success btn-sm uppercase" @click="toggleModal(false)">
            <i class="mdi mdi-auto-fix mdi-18px mr-1" />{{ t('labels.auto_fill') }}
          </button>
          <button class="btn btn-outline btn-sm uppercase" @click="toggleModal(true)">
            <i class="mdi mdi-file-import mdi-18px mr-1" />Import
          </button>
          <button class="btn btn-outline btn-sm uppercase" @click="toggleScanModal()">
            <i class="mdi mdi-barcode-scan mdi-18px mr-1" />Scan
          </button>
        </div>
      </div>

      <!-- SECTION 1: Book info -->
      <div class="bg-base-200 rounded-xl border border-base-content/30 p-5 mb-3">
        <p class="text-xs font-medium text-base-content/60 uppercase tracking-wider mb-4">{{ t('book.book') }} info</p>

        <!-- Title -->
        <fieldset class="fieldset mb-3">
          <legend class="fieldset-legend capitalize">{{ t('book.title') }} <span class="text-error">*</span></legend>
          <input v-model="form.title" type="text" class="input w-full focus:input-accent" :placeholder="t('book.title')">
        </fieldset>

        <!-- ISBN13 + Language -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
          <fieldset class="fieldset">
            <legend class="fieldset-legend capitalize">{{ t('book.isbn13') }}</legend>
            <input v-model="form.isbn13" type="text" class="input w-full focus:input-accent validator" :valid="isbn13ValidationMessage.length < 1" @blur="validateIsbn13($event.target.value)">
            <div v-if="isbn13ValidationMessage" class="text-error text-xs mt-1">{{ isbn13ValidationMessage }}</div>
          </fieldset>
          <FormField v-model="form.language" :legend="t('book.language')" placeholder="" />
        </div>

        <!-- Authors -->
        <fieldset class="fieldset mb-3">
          <legend class="fieldset-legend capitalize">{{ t('book.authors') }}</legend>
          <o-taginput v-model="authors" :options="filteredAuthors" :allow-autocomplete="true" autocomplete="off" :allow-new="true" :allow-duplicates="false" :open-on-focus="true" :validate-item="(item: Author) => beforeAdd(item, authors)" :create-item="ObjectUtils.createNamedItem" icon-pack="mdi" icon="account-plus" field="name" :placeholder="t('labels.add_author')" @input="(v: string) => getFilteredData(v, filteredAuthors)">
            <template #default="{ value }"><div class="jl-taginput-item">{{ value.name }}</div></template>
            <template #selected="{ removeItem, items }"><ClosableBadge v-for="(item, index) in items" :key="item.name" :content="item.name" class="badge-primary" @closed="removeItem(index, $event)" /></template>
          </o-taginput>
        </fieldset>

        <!-- Series + Page count -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <fieldset class="fieldset col-span-2">
            <legend class="fieldset-legend capitalize">{{ t('book.series') }}</legend>
            <div class="flex flex-col grow w-full">
              <SeriesCompleteInput v-model="seriesCopy" />
            </div>
          </fieldset>
          <fieldset class="fieldset">
            <legend class="fieldset-legend capitalize">{{ t('book.page_count') }}</legend>
            <input v-model="form.pageCount" type="number" class="input w-full focus:input-accent" min="0">
          </fieldset>
        </div>
      </div>

      <!-- SECTION 2: Cover image -->
      <div class="bg-base-200 rounded-xl border border-base-content/30 p-5 mb-3">
        <p class="text-xs font-medium text-base-content/60 uppercase tracking-wider mb-4">{{ t('labels.upload_cover') }}</p>
        <div class="flex gap-4 items-start">
          <!-- Preview -->
          <div v-if="hasImage && !deleteImage" class="indicator flex-shrink-0">
            <span class="badge indicator-item indicator-bottom indicator-start cursor-pointer" @click="toggleRemoveImage">
              <i class="mdi mdi-delete" />
            </span>
            <figure class="small-cover">
              <img :src="metadata?.image?.startsWith('http') ? metadata?.image : '/files/' + metadata?.image" alt="cover image">
            </figure>
          </div>
          <div v-else class="w-20 h-28 bg-base-300 border border-dashed border-base-content/20 rounded-lg flex items-center justify-center flex-shrink-0">
            <i class="mdi mdi-image-outline mdi-24px text-base-content/30" />
          </div>
          <!-- Upload options -->
          <div class="flex flex-col gap-2 flex-grow">
            <div class="flex gap-2 flex-wrap">
              <label class="flex items-center gap-1 cursor-pointer">
                <input v-model="uploadType" type="radio" name="upload-type" class="radio radio-primary radio-sm" value="web">
                <span class="text-sm">{{ t('labels.upload_from_web') }}</span>
              </label>
              <label class="flex items-center gap-1 cursor-pointer">
                <input v-model="uploadType" type="radio" name="upload-type" class="radio radio-primary radio-sm" value="computer">
                <span class="text-sm">{{ t('labels.upload_from_computer') }}</span>
              </label>
              <label class="flex items-center gap-1 cursor-pointer">
                <input v-model="uploadType" type="radio" name="upload-type" class="radio radio-primary radio-sm" value="server">
                <span class="text-sm">{{ t('labels.upload_from_server') }}</span>
              </label>
            </div>
            <!-- Web URL -->
            <div v-if="uploadType === 'web'" class="mt-1">
              <input v-model="imageUrl" type="url" class="input input-sm w-full focus:input-accent" :placeholder="t('labels.url_must_start')" pattern="https?://.*">
            </div>
            <!-- Computer upload -->
            <div v-else-if="uploadType === 'computer'" class="mt-1">
              <input type="file" accept="image/*" class="file-input file-input-sm w-full" @change="handleFileUpload($event)">
              <progress v-if="uploadPercentage > 0" max="100" :value.prop="uploadPercentage" class="progress progress-primary mt-2" />
            </div>
            <!-- Server picker -->
            <div v-else class="mt-1">
              <button class="btn btn-outline btn-sm" @click="toggleImagePickerModal()">
                <i class="mdi mdi-file-question mdi-18px mr-1" />{{ t('labels.choose_file') }}
              </button>
              <span v-if="imagePath" class="text-sm ml-2">{{ imagePath }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- SECTION 3: Reading status -->
      <div class="bg-base-200 rounded-xl border border-base-content/30 p-5 mb-3">
        <p class="text-xs font-medium text-base-content/60 uppercase tracking-wider mb-4">{{ t('book.status') }}</p>

        <!-- Status segmented toggle -->
        <div class="mb-4">
          <label class="text-sm text-base-content/60 block mb-2">{{ t('book.status') }}</label>
          <div class="inline-flex rounded-lg border border-base-content/30 overflow-hidden">
            <div class="px-4 py-2 text-sm cursor-pointer transition-colors" :class="eventType === 'NONE' || eventType === null ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="eventType = 'NONE'">
              {{ t('reading_events.none') }}
            </div>
            <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/40 transition-colors" :class="eventType === 'CURRENTLY_READING' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="eventType = 'CURRENTLY_READING'">
              {{ t('reading_events.currently_reading') }}
            </div>
            <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/40 transition-colors" :class="eventType === 'PAUSED' ? 'bg-warning/20 text-warning font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="eventType = 'PAUSED'">
              {{ t('reading_events.paused') }}
            </div>
            <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/40 transition-colors" :class="eventType === 'FINISHED' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="eventType = 'FINISHED'">
              {{ t('reading_events.finished') }}
            </div>
            <div class="px-4 py-2 text-sm cursor-pointer border-l border-base-content/40 transition-colors" :class="eventType === 'DROPPED' ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="eventType = 'DROPPED'">
              {{ t('reading_events.dropped') }}
            </div>
          </div>
        </div>

        <!-- Event date (shows when status is not None) -->
        <div v-if="displayDatepicker" class="mb-4">
          <fieldset class="fieldset">
            <legend class="fieldset-legend capitalize">{{ t('labels.event_date') }}</legend>
            <o-datepicker ref="datepicker" v-model="eventDate" :show-week-number="false" :locale="undefined" :placeholder="t('labels.click_to_select')" :expanded="true" icon="calendar" icon-right="close" icon-right-clickable="true" mobile-native="false" mobile-modal="false" trap-focus @icon-right-click="eventDate = null" />
          </fieldset>
        </div>

        <!-- Ownership segmented toggle -->
        <div class="mb-4">
          <label class="text-sm text-base-content/60 block mb-2">{{ t('book.owned') }}</label>
          <div class="inline-flex rounded-lg border border-base-content/30 overflow-hidden">
            <div class="px-5 py-2 text-sm cursor-pointer transition-colors" :class="form.owned && !form.borrowed ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="form.owned = true; form.borrowed = false">
              <i class="mdi mdi-book-outline mdi-18px mr-1" />{{ t('book.owned') }}
            </div>
            <div class="px-5 py-2 text-sm cursor-pointer border-l border-base-content/40 transition-colors" :class="form.borrowed ? 'bg-info/20 text-info font-medium' : 'bg-base-100 text-base-content/60 hover:bg-base-300'" @click="form.borrowed = true; form.owned = false">
              <i class="mdi mdi-swap-horizontal mdi-18px mr-1" />{{ t('book.borrowed') }}
            </div>
          </div>
        </div>

        <!-- To read checkbox -->
        <div class="mb-4">
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="form.toRead" type="checkbox" class="checkbox checkbox-primary checkbox-sm">
            <span class="text-sm text-base-content/60">{{ t('labels.book_will_be_added') }}</span>
          </label>
        </div>

        <!-- Progress -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <fieldset class="fieldset">
            <legend class="fieldset-legend capitalize">{{ t('book.current_page_number') }}</legend>
            <input v-model="form.currentPageNumber" type="number" class="input w-full focus:input-accent" min="0" :disabled="form.pageCount == null" :max="form.pageCount">
          </fieldset>
          <fieldset class="fieldset">
            <legend class="fieldset-legend capitalize">{{ t('book.percent_read') }} — {{ form.percentRead }}%</legend>
            <input v-model="form.percentRead" type="range" min="0" max="100" class="range range-primary range-sm mt-2">
          </fieldset>
        </div>
      </div>

      <!-- SECTION 4: Shelf assignment -->
      <div class="bg-base-200 rounded-xl border border-base-content/30 p-5 mb-3">
        <p class="text-xs font-medium text-base-content/60 uppercase tracking-wider mb-4">
          <i class="mdi mdi-bookshelf mdi-18px mr-1" />Assign to shelf
        </p>
        <select v-model="selectedShelfId" class="select select-bordered w-full">
          <option :value="null">None (assign later)</option>
          <option v-for="opt in shelfOptions" :key="opt.id" :value="opt.id">{{ opt.label }}</option>
        </select>
      </div>

      <!-- SECTION 5: More details (collapsed) -->
      <div class="bg-base-200 rounded-xl border border-base-content/30 mb-3 overflow-hidden">
        <div class="collapse collapse-arrow">
          <input type="checkbox" />
          <div class="collapse-title text-xs font-medium text-base-content/60 uppercase tracking-wider">
            <i class="mdi mdi-dots-horizontal mdi-18px mr-1" />More details
          </div>
          <div class="collapse-content">
            <!-- Original title -->
            <FormField v-model="form.originalTitle" :legend="t('book.original_title')" placeholder="" class="mb-3" />

            <!-- ISBN10 + Publisher -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
              <fieldset class="fieldset">
                <legend class="fieldset-legend capitalize">{{ t('book.isbn10') }}</legend>
                <input v-model="form.isbn10" type="text" class="input w-full focus:input-accent validator" :valid="isbn10ValidationMessage.length < 1" @blur="validateIsbn10($event.target.value)">
                <div v-if="isbn10ValidationMessage" class="text-error text-xs mt-1">{{ isbn10ValidationMessage }}</div>
              </fieldset>
              <fieldset class="fieldset">
                <legend class="fieldset-legend capitalize">{{ t('book.publisher') }}</legend>
                <input v-model="form.publisher" type="text" class="input w-full focus:input-accent" list="publishers-list" @input="getFilteredPublishers(($event.target as HTMLInputElement).value)">
                <datalist id="publishers-list">
                  <option v-for="pub in filteredPublishers" :key="pub" :value="pub" />
                </datalist>
              </fieldset>
            </div>

            <!-- Published date + Price -->
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
              <fieldset class="fieldset">
                <legend class="fieldset-legend capitalize">{{ t('book.published_date') }}</legend>
                <o-datepicker ref="datepicker" v-model="publishedDate" :show-week-number="false" :locale="undefined" :placeholder="t('labels.click_to_select')" icon="calendar" icon-right="close" :icon-right-clickable="true" trap-focus expanded @icon-right-click="clearDatePicker" />
              </fieldset>
              <fieldset class="fieldset">
                <legend class="fieldset-legend capitalize">{{ t('book.price') }}</legend>
                <input v-model="form.price" type="number" class="input w-full focus:input-accent" step="0.01" min="0">
              </fieldset>
            </div>

            <!-- Tags -->
            <fieldset class="fieldset mb-3">
              <legend class="fieldset-legend capitalize">{{ t('book.tags') }}</legend>
              <o-taginput v-model="tags" :options="filteredTags" :allow-autocomplete="true" autocomplete="off" :allow-new="true" :allow-duplicates="false" :open-on-focus="true" :validate-item="(item: Tag) => beforeAddTag(item, tags)" :create-item="ObjectUtils.createNamedTagItem" icon-pack="mdi" icon="tag-plus" field="name" :placeholder="t('labels.add_tag')" @input="(v: string) => getFilteredTags(v)">
                <template #default="{ value }"><div class="jl-taginput-item">{{ value.name }}</div></template>
                <template #selected="{ removeItem, items }"><ClosableBadge v-for="(item, index) in items" :key="item.name" :content="item.name" class="badge-primary" @closed="removeItem(index, $event)" /></template>
              </o-taginput>
            </fieldset>

            <!-- Translators -->
            <fieldset class="fieldset mb-3">
              <legend class="fieldset-legend capitalize">{{ t('book.translators') }}</legend>
              <o-taginput v-model="translators" :options="filteredTranslators" :allow-autocomplete="true" autocomplete="off" :allow-new="true" :allow-duplicates="false" :open-on-focus="true" :validate-item="(item: Author) => beforeAdd(item, translators)" :create-item="ObjectUtils.createNamedItem" icon-pack="mdi" icon="account-plus" field="name" :placeholder="t('labels.add_translator')" @input="(v: string) => getFilteredData(v, filteredTranslators)">
                <template #default="{ value }"><div class="jl-taginput-item">{{ value.name }}</div></template>
                <template #selected="{ removeItem, items }"><ClosableBadge v-for="(item, index) in items" :key="item.name" :content="item.name" class="badge-primary" @closed="removeItem(index, $event)" /></template>
              </o-taginput>
            </fieldset>

            <!-- Narrators -->
            <fieldset class="fieldset mb-3">
              <legend class="fieldset-legend capitalize">{{ t('book.narrators') }}</legend>
              <o-taginput v-model="narrators" :options="filteredNarrators" :allow-autocomplete="true" autocomplete="off" :allow-new="true" :allow-duplicates="false" :open-on-focus="true" :validate-item="(item: Author) => beforeAdd(item, narrators)" :create-item="ObjectUtils.createNamedItem" icon-pack="mdi" icon="account-plus" field="name" :placeholder="t('labels.add_narrator')" @input="(v: string) => getFilteredData(v, filteredNarrators)">
                <template #default="{ value }"><div class="jl-taginput-item">{{ value.name }}</div></template>
                <template #selected="{ removeItem, items }"><ClosableBadge v-for="(item, index) in items" :key="item.name" :content="item.name" class="badge-primary" @closed="removeItem(index, $event)" /></template>
              </o-taginput>
            </fieldset>

            <!-- Summary -->
            <fieldset class="fieldset mb-3">
              <legend class="fieldset-legend capitalize">{{ t('book.summary') }}</legend>
              <textarea v-model="form.summary" maxlength="50000" class="textarea focus:textarea-accent w-full" rows="3" />
            </fieldset>

            <!-- Personal notes -->
            <fieldset class="fieldset mb-3">
              <legend class="fieldset-legend capitalize">{{ t('book.personal_notes') }}</legend>
              <textarea v-model="form.personalNotes" maxlength="5000" class="textarea focus:textarea-accent w-full" rows="2" />
            </fieldset>

            <!-- Identifiers -->
            <p class="text-xs font-medium text-base-content/40 uppercase tracking-wider mb-2 mt-4">{{ t('book.identifiers') }}</p>
            <div class="grid grid-cols-2 sm:grid-cols-3 gap-2">
              <input v-model="form.googleId" :placeholder="t('book.google_id')" class="input input-sm focus:input-accent w-full">
              <input v-model="form.goodreadsId" :placeholder="t('book.goodreads_id')" class="input input-sm focus:input-accent w-full">
              <input v-model="form.amazonId" :placeholder="t('book.amazon_id')" class="input input-sm focus:input-accent w-full">
              <input v-model="form.librarythingId" :placeholder="t('book.librarything_id')" class="input input-sm focus:input-accent w-full">
              <input v-model="form.isfdbId" :placeholder="t('book.isfdb_id')" class="input input-sm focus:input-accent w-full">
              <input v-model="form.openlibraryId" :placeholder="t('book.openlibrary_id')" class="input input-sm focus:input-accent w-full">
              <input v-model="form.noosfereId" :placeholder="t('book.noosfere_id')" class="input input-sm focus:input-accent w-full">
              <input v-model="form.inventaireId" :placeholder="t('book.inventaire_id')" class="input input-sm focus:input-accent w-full">
            </div>
          </div>
        </div>
      </div>

      <!-- Save button -->
      <div class="flex gap-2 justify-end mt-4 mb-8">
        <button class="btn btn-outline" @click="router.push({name: 'my-books'})">{{ t('labels.cancel') }}</button>
        <button class="btn btn-success" :disabled="!StringUtils.isNotBlank(form.title)" :class="{'btn-disabled' : progress}" @click="importBook">
          <span v-if="progress" class="loading loading-spinner" />
          <i v-else class="mdi mdi-check mdi-18px mr-1" />
          {{ t('labels.import_book') }}
        </button>
      </div>

      <p v-if="errorMessage" class="text-error text-center mb-4">{{ errorMessage }}</p>
    </div>

    <!-- Modals -->
  </section>
</template>
<style lang="scss">
</style>
