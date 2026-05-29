<script setup lang="ts">
import { useOruga } from "@oruga-ui/oruga-next"
import { until, useClipboard, useLocalStorage, usePermission, useTitle } from '@vueuse/core'
import dayjs from 'dayjs'
import { computed, ComputedRef, Ref, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import useDates from '../composables/dates'
import { Book, UserBook } from '../model/Book'
import { BookQuote } from "../model/BookQuote"
import { Metadata } from "../model/Metadata"
import { CreateReadingEvent, ReadingEvent, ReadingEventType } from '../model/ReadingEvent'
import { Review } from '../model/Review'
import { Series } from '../model/Series'
import { User } from '../model/User'
import dataService from "../services/DataService"
import { ShelfLocation } from "../model/PhysicalLibrary"
import { key } from '../store'
import { ObjectUtils } from '../utils/ObjectUtils'
import AutoImportFormModalVue from "./AutoImportFormModal.vue"
import BookQuoteCard from "./BookQuoteCard.vue"
import BookQuoteModalVue from './BookQuoteModal.vue'
import EditBookModal from "./EditBookModal.vue"
import MergeBookModal from './MergeBookModal.vue'
import ReadingEventModalVue from './ReadingEventModal.vue'
import ReadProgressModal from './ReadProgressModal.vue'
import ReviewCard from "./ReviewCard.vue"
import ReviewModalVue from './ReviewModal.vue'
import useTypography from "../composables/typography"

const { t, d } = useI18n({
      inheritLocale: true,
      useScope: 'global'
    })

const { isSupported, copy } = useClipboard()
usePermission('clipboard-read')
usePermission('clipboard-write')

const props = defineProps<{ bookId: string }>()

const store = useStore(key)
const router = useRouter()
const oruga = useOruga();

const { stringToDate } = useDates()

const isAdmin = computed(() => {
  return store !== undefined && store.getters.isAdmin
})
const user: ComputedRef<User> = computed(() => {
  return store !== undefined && store.getters.getUser
})

let currency = localStorage.getItem("JL_CURRENCY")
if (currency == null) {
  currency = "EUR"
}

const book: Ref<UserBook | null> = ref(null)
const edit: Ref<boolean> = ref(false)
const showModal: Ref<boolean> = ref(false)

const getBookIsLoading: Ref<boolean> = ref(false)

const userReviews: Ref<Array<Review>> = ref([])

const bookQuotes: Ref<Array<BookQuote>> = ref([])
const digitalSearchResults: Ref<Array<any>> = ref([])
const digitalSearchLoading: Ref<boolean> = ref(false)
const digitalUploadLoading: Ref<boolean> = ref(false)
const digitalDownloadLoading: Ref<boolean> = ref(false)
const digitalDeleteLoading: Ref<boolean> = ref(false)
const showDigitalSearchModal: Ref<boolean> = ref(false)
const downloadProgress: Ref<string> = ref("")
const users: Ref<Array<User>> = ref([])
const shelfLocation: Ref<ShelfLocation | null> = ref(null)

const getBook = async () => {
  try {
    getBookIsLoading.value = true
    book.value = await dataService.getUserBookById(props.bookId)
    getBookIsLoading.value = false
    useTitle('Jelu | ' + book.value.book.title)
    getUserReviewsForBook()
    getBookQuotesForBook()
    getAllSeriesInfo()
    getBookUsers()
    getShelfLocation()
  } catch (error) {
    console.log("failed get book : " + error);
    getBookIsLoading.value = false
  }
};

const getShelfLocation = async () => {
  try {
    if (book.value?.id) {
      shelfLocation.value = await dataService.getBookPhysicalLocation(book.value.id)
    }
  } catch (error) {
    console.log("failed get shelf location : " + error)
  }
}

const searchDigitalCopies = async () => {
  if (!book.value?.id) return
  digitalSearchLoading.value = true
  try {
    const result = await dataService.searchDigitalCandidates(book.value.id)
    digitalSearchResults.value = result.releases || []
    showDigitalSearchModal.value = true
  } catch (error) {
    oruga.notification.open({ message: "Search failed: " + error, variant: "danger", duration: 4000 })
  } finally {
    digitalSearchLoading.value = false
  }
}

const triggerDownload = async (release: any) => {
  if (!book.value?.id) return
  digitalDownloadLoading.value = true
  downloadProgress.value = "Starting download..."
  showDigitalSearchModal.value = false
  try {
    await dataService.triggerDigitalDownload(book.value.id, release)
    // Poll for completion
    const sourceId = release.source_id || release.sourceId
    if (sourceId) {
      const pollInterval = setInterval(async () => {
        try {
          const status = await dataService.checkDownloadStatus(book.value!.id!, sourceId)
          if (status.status === "complete") {
            clearInterval(pollInterval)
            digitalDownloadLoading.value = false
            downloadProgress.value = ""
            if (status.linked) {
              oruga.notification.open({ message: "Downloaded and linked!", variant: "success", duration: 4000 })
              getBook()
            } else {
              oruga.notification.open({ message: "Download complete but could not auto-link", variant: "warning", duration: 4000 })
            }
          } else if (status.status === "error" || status.status === "cancelled") {
            clearInterval(pollInterval)
            digitalDownloadLoading.value = false
            downloadProgress.value = ""
            oruga.notification.open({ message: "Download failed: " + (status.message || status.status), variant: "danger", duration: 4000 })
          } else {
            const pct = Math.round(status.progress || 0)
            downloadProgress.value = status.status + (pct > 0 ? " " + pct + "%" : "...")
          }
        } catch (e) {
          // Keep polling on transient errors
        }
      }, 3000)
      // Safety timeout after 5 minutes
      setTimeout(() => {
        clearInterval(pollInterval)
        if (digitalDownloadLoading.value) {
          digitalDownloadLoading.value = false
          downloadProgress.value = ""
          oruga.notification.open({ message: "Download timed out - check CWA UI", variant: "warning", duration: 4000 })
        }
      }, 300000)
    }
  } catch (error) {
    digitalDownloadLoading.value = false
    downloadProgress.value = ""
    oruga.notification.open({ message: "Download failed: " + error, variant: "danger", duration: 4000 })
  }
}

const uploadDigitalFile = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || !book.value?.id) return
  digitalUploadLoading.value = true
  try {
    await dataService.uploadDigitalFile(book.value.id, file)
    oruga.notification.open({ message: "File uploaded successfully", variant: "success", duration: 4000 })
    getBook()
  } catch (error) {
    oruga.notification.open({ message: "Upload failed: " + error, variant: "danger", duration: 4000 })
  } finally {
    digitalUploadLoading.value = false
    target.value = ""
  }
}

const downloadQrLabel = async () => {
  if (!book.value?.id) return
  try {
    const response = await dataService.apiClient.get(
      '/userbooks/' + book.value.id + '/label.png',
      { params: { widthMm: 40, heightMm: 30, baseUrl: window.location.origin }, responseType: 'blob' }
    )
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', (book.value.book.title || 'label') + '.png')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    oruga.notification.open({ message: "Label download failed", variant: "danger", duration: 3000 })
  }
}

const printQrLabel = () => {
  const win = window.open('', '_blank', 'width=400,height=500')
  if (!win) return
  const o = window.location.origin
  win.document.write('<html><head><title>' + (book.value?.book?.title || 'Label') + '</title>')
  win.document.write('<style>body{margin:0;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#fff;} .label{border:2px solid #333;padding:20px;border-radius:8px;text-align:center;width:260px;font-family:Georgia,serif;} .label .logo{width:40px;height:40px;border-radius:50%;margin:0 auto 8px;display:block;} .label .qr{width:170px;height:170px;} .label .divider{border-bottom:1px solid #ccc;margin:12px 0;} .label .title{font-weight:bold;font-size:14px;line-height:1.3;} @media print{body{margin:0;} @page{size:auto;margin:10mm;}}</style></head><body>')
  win.document.write('<div class="label">')
  win.document.write('<img class="logo" src="' + o + '/android-chrome-192x192.png" />')
  win.document.write('<img class="qr" src="' + qrCodeUrl.value + '" />')
  win.document.write('<div class="divider"></div>')
  win.document.write('<div class="title">' + (book.value?.book?.title || '') + '</div>')
  win.document.write('</div></body></html>')
  win.document.close()
  win.onload = () => { win.print() }
}

const downloadDigital = async () => {
  if (!book.value?.id) return
  try {
    await dataService.downloadDigitalFile(book.value.id)
  } catch (error) {
    oruga.notification.open({ message: "Download failed: " + error, variant: "danger", duration: 4000 })
  }
}

const deleteDigitalFile = async () => {
  if (!book.value?.id) return
  if (!confirm("Remove the digital copy? The file will be deleted.")) return
  digitalDeleteLoading.value = true
  try {
    await dataService.deleteDigitalFile(book.value.id)
    oruga.notification.open({ message: "Digital copy removed", variant: "success", duration: 4000 })
    getBook()
  } catch (error) {
    oruga.notification.open({ message: "Delete failed: " + error, variant: "danger", duration: 4000 })
  } finally {
    digitalDeleteLoading.value = false
  }
}

const getBookUsers = async () => {
  await until(book.value).not.toBeNull()
  if (book.value?.book.id != null) {
    dataService.findBookUsers(book.value?.book.id, 0, 20, "login,desc")
      .then(res => {
        console.log(res)
        users.value = res.content
      })
      .catch(err => {
        console.log(err)
      })
  }
}

const getAllSeriesInfo = async () => {
  book.value?.book.series?.forEach(s => {
    fetchSeries(s.seriesId as string)
  })
}

const getUserReviewsForBook = async() => {
  await until(user.value).not.toBeNull()
  dataService.findReviews(user.value.id, book.value?.book.id, null, null, null, 0, 20)
  .then(res => {
    console.log(res)
    userReviews.value = res.content
  })
  .catch(err => {
    console.log(err)
  })
}

const getBookQuotesForBook = async() => {
  await until(user.value).not.toBeNull()
  dataService.findBookQuotes(user.value.id, book.value?.book.id, null, 0, 20)
  .then(res => {
    console.log(res)
    bookQuotes.value = res.content
  })
  .catch(err => {
    console.log(err)
  })
}

watch(() => props.bookId, (newValue, oldValue) => {
  console.log('The new bookId is: ' + props.bookId)
})

const sortedEvents = computed(() => {
  if (book.value && book.value.readingEvents) {
    return [...book.value.readingEvents].sort((a, b) => dayjs(a.startDate).isAfter(dayjs(b.startDate)) ? -1 : 1)
  }
  else {
    return []
  }
})

const qrCodeUrl = computed(() => {
  if (!book.value?.book?.id) return ""
  return "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=" + encodeURIComponent(location.origin + "/public/book/" + book.value.book.id)
})
const readingPace = computed(() => {
  if (!book.value?.readingEvents || !book.value?.book?.pageCount) return null
  const currentEvent = book.value.readingEvents.find(
    (e: ReadingEvent) => e.eventType === ReadingEventType.CURRENTLY_READING
  )
  if (!currentEvent?.startDate) return null
  const startDate = dayjs(currentEvent.startDate)
  const daysReading = dayjs().diff(startDate, "day", true)
  if (daysReading < 0.01) return null
  const pagesRead = book.value.currentPageNumber ?? Math.round((book.value.percentRead ?? 0) * book.value.book.pageCount / 100)
  if (pagesRead <= 0) return null
  const effectiveDays = Math.max(daysReading, 1)
  const pace = pagesRead / effectiveDays
  const pagesRemaining = book.value.book.pageCount - pagesRead
  const daysRemaining = pagesRemaining > 0 ? Math.ceil(pagesRemaining / pace) : 0
  const estimatedFinish = dayjs().add(daysRemaining, "day")
  return {
    pagesPerDay: Math.round(pace * 10) / 10,
    daysReading: Math.round(daysReading),
    daysRemaining,
    estimatedFinish: estimatedFinish.format("MMM D, YYYY"),
    pagesRead,
    pagesRemaining,
  }
})

const hasExternalLink = computed(() => book.value?.book.amazonId != null
  || book.value?.book.goodreadsId != null
  || book.value?.book.googleId != null
  || book.value?.book.librarythingId != null
  || book.value?.book.openlibraryId != null
  || book.value?.book.isfdbId != null
  || book.value?.book.noosfereId != null
  || book.value?.book.inventaireId != null)

function modalClosed() {
  console.log("modal closed")
  currentTimestamp = ObjectUtils.timestamp()
  getBook()
}

function reviewModalClosed() {
  console.log("review modal closed")
  getUserReviewsForBook()
}

function bookQuoteModalClosed() {
  console.log("book quote modal closed")
    getBookQuotesForBook()
}

const toggleEdit = () => {
  edit.value = !edit.value
  oruga.modal.open({
    parent: this,
    component: EditBookModal,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'clip',
    props: {
      "book": book.value,
      canAddEvent: false
    },
    onClose: modalClosed
  });
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

function toggleReviewModal(currentBook: Book|undefined, edit: boolean, review: Review|null) {
  if (currentBook != null && currentBook != undefined) {
    oruga.modal.open({
      component: ReviewModalVue,
      trapFocus: true,
      active: true,
      canCancel: ['x', 'button', 'outside'],
      scroll: 'keep',
      props: {
        "book": currentBook,
        "edit" : edit,
        "review": review
      },
      onClose: reviewModalClosed
    });
  }
}

function toggleBookQuoteModal(currentBook: Book|undefined, edit: boolean, bookQuote: BookQuote|null) {
  if (currentBook != null && currentBook != undefined) {
    oruga.modal.open({
      component: BookQuoteModalVue,
      trapFocus: true,
      active: true,
      canCancel: ['x', 'button', 'outside'],
      scroll: 'keep',
      props: {
        "book": currentBook,
        "edit" : edit,
        "bookQuote": bookQuote
      },
      onClose: bookQuoteModalClosed
    });
  }
}

const toggleFetchMetadataModal = (currentBook: Book|undefined) => {
  oruga.modal.open({
    parent: this,
    component: AutoImportFormModalVue,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'keep',
    props: {
        "book": currentBook,
      },
    events: {
      metadataReceived: (modalMetadata: Metadata) => {
        console.log("received metadata")
        console.log(modalMetadata)
        toggleMergeBookModal(currentBook, modalMetadata)
      }
    },
    onClose: modalClosed
  });
}

const toggleMergeBookModal = (currentBook: Book|undefined, metadata: Metadata) => {
  oruga.modal.open({
    parent: this,
    component: MergeBookModal,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'keep',
    props: {
        "book": currentBook,
        "metadata": metadata
      },
    onClose: modalClosed
  });
}

const toggleReadProgressModal = (userBookId: string, bookId: string, pageCount: number|null, currentProgress: number|null, currentPage: number|null) => {
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

const deleteBook = async () => {
  let deleteForUserOnly = true
  let abort = false
  if (isAdmin.value === true) {
    await ObjectUtils.swalMixin.fire({
      html: `<p>${t('labels.delete_for_all_or_only_you')}</p>`,
      showDenyButton: true,
      showCancelButton: true,
      confirmButtonText: t('labels.only_me'),
      denyButtonText: t('labels.all_users'),
      cancelButtonText: t('labels.dont_delete'),
    }).then((result) => {
      if (result.isDenied) {
        deleteForUserOnly = false
      } else if (result.isDismissed) {
        abort = true
        return;
      }
    })
  }
  else {
    await ObjectUtils.swalYesNoMixin.fire({
      html: `<p>${t('labels.delete_this_book')}</p>`,
      showCancelButton: true,
      showConfirmButton: true,
      showDenyButton: false,
      confirmButtonText: t('labels.delete'),
      cancelButtonText: t('labels.dont_delete'),
    }).then((result) => {
      if (result.isDismissed) {
        abort = true
        return;
      }
    })
  }
  if (abort) {
    return
  }
  console.log(`delete for user only ${deleteForUserOnly}`)
  let promise
  if (deleteForUserOnly) {
    if (book.value?.id) {
      promise = dataService.deleteUserBook(book.value?.id)
    }
  }
  else {
    if (book.value?.book?.id) {
      promise = dataService.deleteBook(book.value?.book?.id)
    }
  }
  promise?.then(res => {
    ObjectUtils.toast(oruga, "success", t('labels.book_was_deleted'), 4000);
    router.push({ name: 'home' })
  })
    .catch(err => {
      ObjectUtils.toast(oruga, "danger", t('labels.error_deleting', {msg : err.message}), 4000);
    })
}

const eventClass = (event: ReadingEvent) => {
  if (event.eventType === ReadingEventType.FINISHED) {
    return "bg-info";
  } else if (event.eventType === ReadingEventType.DROPPED) {
    return "bg-error";
  } else if (
    event.eventType === ReadingEventType.CURRENTLY_READING
  ) {
    return "bg-success";
  } else if (event.eventType === ReadingEventType.PAUSED) {
    return "bg-warning";
  }
  else return "";
};

const iconClass = (event: ReadingEvent) => {
  if (event.eventType === ReadingEventType.FINISHED) {
    return "mdi-checkbox-marked-circle";
  } else if (event.eventType === ReadingEventType.DROPPED) {
    return "mdi-close-octagon";
  } else if (
    event.eventType === ReadingEventType.CURRENTLY_READING
  ) {
    return "mdi-book-open-page-variant";
  } else if (event.eventType === ReadingEventType.PAUSED) {
    return "mdi-pause-circle";
  }
  else return "";
};

const eventLabel = (type: ReadingEventType) => {
    if (type === ReadingEventType.FINISHED) {
      return t('reading_events.finished');
    } else if (type === ReadingEventType.DROPPED) {
      return t('reading_events.dropped');
    } else if (type === ReadingEventType.CURRENTLY_READING) {
      return t('reading_events.reading');
    } else if (type === ReadingEventType.PAUSED) {
      return t('reading_events.paused');
    } else return "";
};

function defaultCreateEvent(): CreateReadingEvent {
  const currentType = book.value?.lastReadingEvent ?? ReadingEventType.CURRENTLY_READING
  return {
    eventType: currentType,
    eventDate: new Date(),
    startDate: new Date(),
    bookId: book.value?.book.id
  }
}
const showLendModal = ref(false)
const lendForm = ref({ borrowerName: "", expectedReturnDate: "" })
const lendBook = async () => {
  if (!book.value?.id || !lendForm.value.borrowerName) return
  try {
    await dataService.updateUserBook({id: book.value.id,
      borrowed: true,
      borrowerName: lendForm.value.borrowerName,
      borrowDate: new Date().toISOString(),
      expectedReturnDate: lendForm.value.expectedReturnDate ? new Date(lendForm.value.expectedReturnDate).toISOString() : null,
    })
    showLendModal.value = false
    lendForm.value = { borrowerName: "", expectedReturnDate: "" }
    book.value = await dataService.getUserBookById(props.bookId)
  } catch (e) { console.log("lend failed: " + e) }
}
const returnBook = async () => {
  if (!book.value?.id) return
  try {
    await dataService.updateUserBook({id: book.value.id,
      borrowed: false,
      borrowerName: null,
      borrowDate: null,
      expectedReturnDate: null,
    })
    book.value = await dataService.getUserBookById(props.bookId)
  } catch (e) { console.log("return failed: " + e) }
}

const publisherQuery = computed(() => {
  if (book.value?.book.publisher) {
    return "\"" + book.value.book.publisher + "\""
  }
  return ""
})

const embedCode = computed(() => {
  if (book.value) {
    return generateEmbed(book.value)
  }
  return ''
})

function generateEmbed(book: UserBook) {
  let baseUrl = window.location.origin
  let bookUrl = router.resolve({ name: 'book-detail', params: { bookId: book.id } }).href
  let top = `<div id="embed-body" style="padding: 5px; width: 150px; border: 1px solid #cccccc;}"><div class="embed-element" style="overflow: hidden;list-style: none; text-align: center; padding: 5px; margin: 0px;">`
  if (book.book.image != null) {
    let couv = `<div class="embed-cover"> <a href="${baseUrl}${bookUrl}" target="_blank"><img src="${baseUrl}/files/${book.book.image}" title="${book.book.title}" alt="${book.book.title}" style="border: 1px solid #cccccc;border-width:1px; padding: 3px; background-color: #fff;width:80px;"></a></div>`
    top = top.concat(couv)
  }
  let body = `<div class="embed-book" style="margin: 0px 3px 5px 5px;font-size: 13px;font-family:sans-serif; font-weight : bold;"><a href="${baseUrl}${bookUrl}" target="_blank" style="text-decoration:none;">${book.book.title}</a></div>`
  top = top.concat(body)
  if (book.book.authors != undefined && book.book.authors?.length > 0) {
      let firstAuthor = book?.book.authors[0]
      let authorId = firstAuthor.id
      let rout = router.resolve({ name: 'author-detail', params: { authorId: authorId } }).href
  let authorPart = `<div class="embed-author" style="margin: 0px 3px 5px 5px;font-size: 12px;color: gray;"><a href="${baseUrl}${rout}" target="_blank" style="text-decoration:none;">${firstAuthor.name}</a></div>`
      top = top.concat(authorPart)
    }
  let bottom = `<div class="embed-tail" style="clear:both;"></div></div></div>`
  top = top.concat(bottom)
  return top
}

function copyToClipboard(content: string) {
  copy(content)
  ObjectUtils.toast(oruga, "success", t('labels.saved'), 1000)

}

const deleteReview = async (reviewId: string) => {
  console.log("delete " + reviewId)
  let abort = false
  await ObjectUtils.swalYesNoMixin.fire({
      html: `<p>${t('reviews.delete_review')}</p>`,
      showCancelButton: true,
      showConfirmButton: true,
      showDenyButton: false,
      confirmButtonText: t('labels.delete'),
      cancelButtonText: t('labels.dont_delete'),
      denyButtonText: t('labels.delete'),
    }).then((result) => {
      if (result.isDismissed) {
        abort = true
        return;
      }
    })
    console.log("abort " + abort)
    if (abort) {
      return
    }
    dataService.deleteReview(reviewId)
    .then(res => {
      getUserReviewsForBook()
    })
    .catch(err => {
      console.log(err)
    })
}

const deleteBookQuote = async (bookQuoteId: string) => {
  console.log("delete " + bookQuoteId)
  let abort = false
  await ObjectUtils.swalYesNoMixin.fire({
      html: `<p>${t('book_quotes.delete_quote')}</p>`,
      showCancelButton: true,
      showConfirmButton: true,
      showDenyButton: false,
      confirmButtonText: t('labels.delete'),
      cancelButtonText: t('labels.dont_delete'),
      denyButtonText: t('labels.delete'),
    }).then((result) => {
      if (result.isDismissed) {
        abort = true
        return;
      }
    })
    console.log("abort " + abort)
    if (abort) {
      return
    }
    dataService.deleteBookQuote(bookQuoteId)
    .then(res => {
      getBookQuotesForBook()
    })
    .catch(err => {
      console.log(err)
    })
}

const seriesmap: Map<string, Series> = new Map()

const fetchSeries = async (seriesId: string) => {
  dataService.getSeriesById(seriesId)
    .then(data => {
        seriesmap.set(seriesId, data)
    })
    .catch(e => {
        console.log("fetching series error")
    })
}

const getSeriesInfo = async (seriesId: string) => {
    if (seriesmap.get(seriesId) != null) {
        const s = seriesmap.get(seriesId)
        return await formatSeries(s as Series)
    }
    dataService.getSeriesById(seriesId)
    .then(data => {
        seriesmap.set(seriesId, data)
        return formatSeries(data)
    })
    .catch(e => {
        return "error"
    })
}

const formatSeries = async (series: Series)  => {
    let txt = ""
    if (series.description != null && series.description.length > 0) {
        txt += series.description.substring(0, 40)
        txt += " | "
    }
    if (series.avgRating != null) {
        txt += "avg : "
        txt += series.avgRating
        txt += " "
    }
    if (series.userRating != null) {
        txt += "me : "
        txt += series.userRating
    }
    if (txt.trim().length < 1) {
      return 'no data'
    }
    return txt
}

let currentTimestamp = ObjectUtils.timestamp()

const getIsbn = (): string|null => {
  if (book.value?.book.isbn13 && book.value.book.isbn13.length > 0) {
    return book.value.book.isbn13.replaceAll("-", "")
  }
  if (book.value?.book.isbn10 && book.value.book.isbn10.length > 0) {
    return book.value.book.isbn10.replaceAll("-", "")
  }
  return null
}

const storedLanguage = useLocalStorage("jelu_language", "en")

const { typographyClasses } = useTypography()

getBook()

</script>

<template>
  <div class="grid grid-cols-1 justify-center justify-items-center">
    <div class="flex flex-col sm:grid sm:grid-cols-3 mb-4 w-full px-3 sm:px-0 sm:w-10/12">
      <div class="hidden sm:block" />
      <div class="grow">
        <h3
          class="text-3xl"
          :class="typographyClasses"
        >
          {{ book?.book?.title }}
        </h3>
        <h4
          v-if="book?.book.originalTitle"
          :class="typographyClasses"
        >
          {{ book.book.originalTitle }}
        </h4>
      </div>
      <div
        v-if="book != null"
        class="flex items-center flex-wrap gap-1 mt-2 sm:mt-0"
      >
        <button
          class="btn btn-primary btn-outline btn-sm sm:btn-md mr-1 sm:mr-2 p-1.5 sm:p-2 uppercase"
          @click="toggleEdit"
        >
          <span class="icon">
            <i class="mdi mdi-pencil mdi-18px" />
          </span>
          <span>{{ t('labels.edit') }}</span>
        </button>
        <button
          class="btn btn-error btn-outline btn-sm sm:btn-md mr-1 sm:mr-2 p-1.5 sm:p-2 uppercase"
          @click="deleteBook"
        >
          <span class="icon">
            <i class="mdi mdi-delete mdi-18px" />
          </span>
          <span>{{ t('labels.delete') }}</span>
        </button>
        <button
          class="btn btn-info btn-outline btn-sm sm:btn-md p-1.5 sm:p-2 uppercase"
          @click="toggleReadingEventModal(defaultCreateEvent(), false)"
        >
          <span class="icon">
            <i class="mdi mdi-plus mdi-18px" />
          </span>
          <span>{{ t('labels.event') }}</span>
        </button>
        <button v-if="!book?.borrowed" class="btn btn-warning btn-outline btn-sm sm:btn-md p-1.5 sm:p-2 uppercase" @click="showLendModal = true">
          <i class="mdi mdi-account-arrow-right mdi-18px mr-1" />
          <span>Lend</span>
        </button>
        <button v-else class="btn btn-success btn-outline btn-sm sm:btn-md p-1.5 sm:p-2 uppercase" @click="returnBook">
          <i class="mdi mdi-book-check mdi-18px mr-1" />
          <span>Return</span>
        </button>
        <label
          v-tooltip="t('labels.get_embed_code')"
          for="my-modal-4"
          class="btn btn-circle btn-outline ml-0 border-none modal-button"
        ><svg
          xmlns="http://www.w3.org/2000/svg"
          class="h-5 w-5"
          viewBox="0 0 20 20"
          fill="currentColor"
        >
          <path d="M15 8a3 3 0 10-2.977-2.63l-4.94 2.47a3 3 0 100 4.319l4.94 2.47a3 3 0 10.895-1.789l-4.94-2.47a3.027 3.027 0 000-.74l4.94-2.47C13.456 7.68 14.19 8 15 8z" />
        </svg></label>
        <div class="dropdown dropdown-hover bg-transparent">
          <label
            tabindex="0"
            class="btn m-1 btn-circle btn-outline border-none"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              stroke-width="1.5"
              stroke="currentColor"
              class="w-6 h-6"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                d="M12 6.75a.75.75 0 110-1.5.75.75 0 010 1.5zM12 12.75a.75.75 0 110-1.5.75.75 0 010 1.5zM12 18.75a.75.75 0 110-1.5.75.75 0 010 1.5z"
              />
            </svg>
          </label>
          <ul
            tabindex="0"
            class="dropdown-content menu p-2 shadow-sm bg-base-100 rounded-box w-52"
          >
            <li>
              <button
                v-tooltip="t('reviews.create_review')"
                class="btn btn-circle btn-outline border-none"
                @click="toggleReviewModal(book?.book, false, null)"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  class="h-5 w-5"
                  viewBox="0 0 20 20"
                  fill="currentColor"
                >
                  <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                </svg>
              </button>
            </li>
            <li>
              <button
                v-tooltip="t('book_merge.fetch_metadata')"
                class="btn btn-circle btn-outline border-none"
                @click="toggleFetchMetadataModal(book?.book)"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke-width="1.5"
                  stroke="currentColor"
                  class="w-6 h-6"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m5.231 13.481L15 17.25m-4.5-15H5.625c-.621 0-1.125.504-1.125 1.125v16.5c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9zm3.75 11.625a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z"
                  />
                </svg>
              </button>
            </li>
            <li>
              <button
                v-tooltip="t('labels.set_progress')"
                class="btn btn-circle btn-outline border-none"
                @click="toggleReadProgressModal(book?.id!!, book?.book.id!!, book?.book.pageCount ?? null, book?.percentRead ?? null, book?.currentPageNumber ?? null)"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke-width="1.5"
                  stroke="currentColor"
                  class="w-6 h-6"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="m9 14.25 6-6m4.5-3.493V21.75l-3.75-1.5-3.75 1.5-3.75-1.5-3.75 1.5V4.757c0-1.108.806-2.057 1.907-2.185a48.507 48.507 0 0 1 11.186 0c1.1.128 1.907 1.077 1.907 2.185ZM9.75 9h.008v.008H9.75V9Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm4.125 4.5h.008v.008h-.008V13.5Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z"
                  />
                </svg>
              </button>
            </li>
            <li>
              <button
                v-tooltip="t('labels.add_quote')"
                class="btn btn-circle btn-outline border-none"
                @click="toggleBookQuoteModal(book?.book, false, null)"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke-width="1.5"
                  stroke="currentColor"
                  class="size-6"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    d="M17.593 3.322c1.1.128 1.907 1.077 1.907 2.185V21L12 17.25 4.5 21V5.507c0-1.108.806-2.057 1.907-2.185a48.507 48.507 0 0 1 11.186 0Z"
                  />
                </svg>
              </button>
            </li>
          </ul>
        </div>
      </div>
    </div>
    <div
      class="justify-center justify-items-center sm:gap-10 grid grid-cols-1 sm:grid-cols-2 sm:w-10/12 w-full px-4 sm:px-0"
    >
      <div class="sm:justify-self-end">
        <figure>
          <img
            v-if="book?.book?.image"
            :src="'/files/' + book.book.image + '?timestamp=' + currentTimestamp"
            alt="cover image"
            class="max-h-72 sm:max-h-96 mx-auto"
          >
          <img
            v-else
            src="../assets/placeholder_asset.jpg"
            alt="cover placeholder"
          >
        </figure>
      </div>
      <div class="text-left sm:justify-self-start">
        <p
          v-if="book != null && book.book != null && book.book.authors != null && book?.book?.authors?.length > 0"
        >
          <span class="font-semibold capitalize">{{ t('book.author', 2) }} :</span>
        </p>
        <ul
          v-if="book != null && book.book != null && book.book.authors != null && book?.book?.authors?.length > 0"
        >
          <li
            v-for="author in book?.book?.authors"
            :key="author.id"
          >
            <router-link
              class="link hover:underline hover:decoration-4 hover:decoration-secondary"
              :to="{ name: 'author-detail', params: { authorId: author.id } }"
            >
              {{ author.name }}&nbsp;
            </router-link>
          </li>
        </ul>
        <p
          v-if="book != null && book.book != null && book.book.translators != null && book?.book?.translators?.length > 0"
        >
          <span class="font-semibold capitalize">{{ t('book.translator', 2) }} :</span>
        </p>
        <ul
          v-if="book != null && book.book != null && book.book.translators != null && book?.book?.translators?.length > 0"
        >
          <li
            v-for="translator in book?.book?.translators"
            :key="translator.id"
          >
            <router-link
              class="link hover:underline hover:decoration-4 hover:decoration-secondary"
              :to="{ name: 'author-detail', params: { authorId: translator.id } }"
            >
              {{ translator.name }}&nbsp;
            </router-link>
          </li>
        </ul>
        <p
          v-if="book != null && book.book != null && book.book.narrators != null && book?.book?.narrators?.length > 0"
        >
          <span class="font-semibold capitalize">{{ t('book.narrator', 2) }} :</span>
        </p>
        <ul
          v-if="book != null && book.book != null && book.book.narrators != null && book?.book?.narrators?.length > 0"
        >
          <li
            v-for="narrator in book?.book?.narrators"
            :key="narrator.id"
          >
            <router-link
              class="link hover:underline hover:decoration-4 hover:decoration-secondary"
              :to="{ name: 'author-detail', params: { authorId: narrator.id } }"
            >
              {{ narrator.name }}&nbsp;
            </router-link>
          </li>
        </ul>
        <p v-if="book?.book?.publisher">
          <span class="font-semibold capitalize">{{ t('book.publisher') }} :&nbsp;</span>
          <router-link
            class="link hover:underline hover:decoration-4 hover:decoration-secondary"
            :to="{ name: 'search', query: { q: `publisher:` + publisherQuery } }"
          >
            {{ book.book.publisher }}
          </router-link>
        </p>
        <p v-if="book?.book?.isbn10">
          <span class="font-semibold uppercase">{{ t('book.isbn10') }} :</span>
          {{ book.book.isbn10 }}
        </p>
        <p v-if="book?.book?.isbn13">
          <span class="font-semibold uppercase">{{ t('book.isbn13') }} :</span>
          {{ book.book.isbn13 }}
        </p>
        <div v-if="book?.percentRead != null || book?.currentPageNumber || book?.book?.pageCount" class="my-2">
          <div class="flex items-center justify-between mb-1">
            <span class="font-semibold capitalize text-sm">{{ t('book.percent_read') }}</span>
            <span class="text-sm font-bold">{{ Math.round(book?.percentRead ?? 0) }}%</span>
          </div>
          <progress class="progress progress-primary w-full" :value="Math.round(book?.percentRead ?? 0)" max="100"></progress>
          <div v-if="book?.book?.pageCount || book?.currentPageNumber" class="flex justify-between text-xs opacity-70 mt-1">
            <span v-if="book?.currentPageNumber">Page {{ book.currentPageNumber }}<span v-if="book?.book?.pageCount"> / {{ book.book.pageCount }}</span></span>
            <span v-else-if="book?.book?.pageCount">{{ book.book.pageCount }} pages</span>
          </div>
        </div>
        <div v-if="readingPace" class="my-3 p-3 bg-base-200 rounded-lg text-sm">
          <div class="flex items-center gap-2 mb-1">
            <i class="mdi mdi-speedometer mdi-18px" />
            <span class="font-semibold">Reading Pace</span>
          </div>
          <div class="grid grid-cols-2 gap-x-4 gap-y-1 mt-2">
            <span class="opacity-70">Pace:</span>
            <span class="font-medium">{{ readingPace.pagesPerDay }} pages/day</span>
            <span class="opacity-70">Days reading:</span>
            <span class="font-medium">{{ readingPace.daysReading }} days</span>
            <span class="opacity-70">Pages remaining:</span>
            <span class="font-medium">{{ readingPace.pagesRemaining }}</span>
            <span v-if="readingPace.daysRemaining > 0" class="opacity-70">Est. finish:</span>
            <span v-if="readingPace.daysRemaining > 0" class="font-medium">{{ readingPace.estimatedFinish }} ({{ readingPace.daysRemaining }} days)</span>
          </div>
        </div>
        <p v-if="book?.book?.publishedDate">
          <span class="font-semibold capitalize">{{ t('book.published_date') }} :</span>
          {{ d(stringToDate(book.book.publishedDate)!!, 'short') }}
        </p>
        <p v-if="book?.book?.series && book?.book?.series != null && book?.book?.series.length > 0">
          <span class="font-semibold capitalize">{{ t('book.series') }} :&nbsp;</span>
          <ul>
            <li
              v-for="seriesItem in book?.book?.series"
              :key="seriesItem.seriesId"
              v-tooltip="{
                content: () => getSeriesInfo(seriesItem.seriesId as string)
              }"
            >
              <router-link
                class="link hover:underline hover:decoration-4 hover:decoration-secondary"
                :to="{ name: 'series', params: { seriesId: seriesItem.seriesId } }"
              >
                {{ seriesItem.name }}&nbsp;
                <span
                  v-if="seriesItem.numberInSeries"
                >-&nbsp;{{ seriesItem.numberInSeries }}</span>
              </router-link>
            </li>
          </ul>
        </p>
        <p v-if="book?.book?.language">
          <span class="font-semibold capitalize">{{ t('book.language') }} :</span>
          {{ book.book.language }}
        </p>
        <p v-if="book?.price">
          <span class="font-semibold capitalize">{{ t('book.price') }} :</span>
          {{ ObjectUtils.amountInLocale(book.price, storedLanguage, currency) }}
        </p>
        <div v-if="book?.owned || book?.toRead || book?.borrowed">
          <span
            v-if="book?.owned"
            class="badge badge-info"
          >{{ t('book.owned') }}</span>
          <span
            v-if="book?.toRead"
            class="badge badge-info mx-1"
          >{{ t('book.to_read') }}</span>
          <span
            v-if="book?.borrowed"
            class="badge badge-info"
          >{{ t('book.borrowed') }}</span>
        </div>
        <!-- Borrower info -->
        <div v-if="book?.borrowed && book?.borrowerName" class="mt-2 p-3 bg-warning/10 rounded-lg border border-warning/30">
          <div class="flex items-center gap-2 text-sm">
            <i class="mdi mdi-account-arrow-right mdi-18px text-warning" />
            <span class="font-medium">Lent to {{ book.borrowerName }}</span>
          </div>
          <div v-if="book?.borrowDate" class="text-xs text-base-content/50 mt-1 ml-6">
            Since {{ new Date(book.borrowDate).toLocaleDateString() }}
          </div>
          <div v-if="book?.expectedReturnDate" class="text-xs text-base-content/50 ml-6">
            Expected return: {{ new Date(book.expectedReturnDate).toLocaleDateString() }}
          </div>
        </div>
        <!-- Owner name -->
        <div v-if="book?.ownerName" class="mt-2 text-sm">
          <i class="mdi mdi-account mdi-18px mr-1 text-info" />
          <span class="text-base-content/60">Owner:</span> {{ book.ownerName }}
        </div>
        <div v-if="shelfLocation" class="mt-2">
          <span class="font-semibold capitalize">{{ t('library_map.physical_location') }} :</span>
          <span class="ml-1">{{ shelfLocation.displayString }}</span>
          <router-link :to="{ name: 'library-map' }" class="btn btn-ghost btn-xs ml-2">
            <i class="mdi mdi-map-marker mdi-18px" />
          </router-link>
        </div>
        <div v-else class="mt-2">
          <span class="text-sm opacity-50 italic">{{ t('library_map.no_location') }}</span>
          <button class="btn btn-ghost btn-xs ml-1" @click="$router.push({ name: 'library-map' })">
            {{ t('library_map.assign_to_shelf') }}
          </button>
        </div>
        <!-- Digital Copy Section -->
        <div class="mt-3 p-3 rounded-lg bg-base-200">
          <div class="flex items-center gap-2 mb-2">
            <i class="mdi mdi-book-open-page-variant mdi-24px text-primary" />
            <span class="font-semibold">Digital Copy</span>
          </div>

          <!-- Has digital file -->
          <div v-if="book?.digitalFilePath">
            <div class="flex items-center gap-2 text-sm mb-2">
              <span class="badge badge-primary badge-sm">{{ book?.digitalFileFormat?.toUpperCase() }}</span>
              <span class="text-base-content/60">{{ book?.digitalFileSizeBytes ? (book.digitalFileSizeBytes / 1024 / 1024).toFixed(1) + ' MB' : '' }}</span>
              <span v-if="book?.digitalFileAddedDate" class="text-base-content/60 text-xs">
                Added {{ new Date(book.digitalFileAddedDate).toLocaleDateString() }}
              </span>
              <span v-if="book?.lastSentToReaderDate" class="text-base-content/60 text-xs">
                | Sent {{ new Date(book.lastSentToReaderDate).toLocaleDateString() }}
              </span>
            </div>
            <div class="flex flex-wrap gap-2">
              <button class="btn btn-primary btn-sm" @click="downloadDigital">
                <i class="mdi mdi-download mdi-18px" />
                Download
              </button>
              <button class="btn btn-outline btn-sm btn-error" :disabled="digitalDeleteLoading" @click="deleteDigitalFile">
                <span v-if="digitalDeleteLoading" class="loading loading-spinner loading-xs"></span>
                <i v-else class="mdi mdi-delete mdi-18px" />
                Remove
              </button>
              <label class="btn btn-outline btn-sm">
                <i class="mdi mdi-swap-horizontal mdi-18px" /> Replace
                <input type="file" class="hidden" accept=".epub,.pdf,.mobi,.azw3,.cbz,.cbr" @change="uploadDigitalFile">
              </label>
            </div>
          </div>

          <!-- No digital file -->
          <div v-else>
            <div v-if="downloadProgress" class="flex items-center gap-2 text-sm mb-2">
              <span class="loading loading-spinner loading-sm text-primary"></span>
              <span>{{ downloadProgress }}</span>
            </div>
            <div class="flex flex-wrap gap-2">
              <button class="btn btn-primary btn-sm" :disabled="digitalSearchLoading || digitalDownloadLoading" @click="searchDigitalCopies">
                <span v-if="digitalSearchLoading" class="loading loading-spinner loading-xs"></span>
                <i v-else class="mdi mdi-cloud-download mdi-18px" />
                Download digital copy
              </button>
              <label class="btn btn-outline btn-sm" :class="{ 'loading': digitalUploadLoading }">
                <span v-if="digitalUploadLoading" class="loading loading-spinner loading-xs"></span>
                <i v-else class="mdi mdi-upload mdi-18px" />
                Upload manually
                <input type="file" class="hidden" accept=".epub,.pdf,.mobi,.azw3,.cbz,.cbr" @change="uploadDigitalFile">
              </label>
            </div>
          </div>
        </div>

        <!-- Digital Search Modal -->
        <dialog v-if="showDigitalSearchModal" class="modal modal-open">
          <div class="modal-box max-w-2xl">
            <h3 class="font-bold text-lg mb-3">
              <i class="mdi mdi-magnify mr-1" /> Select a version to download
            </h3>
            <div v-if="digitalSearchResults.length === 0" class="text-center py-4 text-base-content/50">
              No results found
            </div>
            <div v-else class="space-y-2 max-h-96 overflow-y-auto">
              <div
                v-for="(release, index) in digitalSearchResults"
                :key="index"
                class="flex items-center justify-between p-3 rounded-lg bg-base-200 hover:bg-base-300 cursor-pointer"
                @click="triggerDownload(release)"
              >
                <div class="flex-1 min-w-0">
                  <div class="font-medium text-sm truncate">{{ release.title }}</div>
                  <div class="text-xs text-base-content/60 flex gap-2 mt-1">
                    <span v-if="release.extra?.author">{{ release.extra.author }}</span>
                    <span v-if="release.extra?.publisher">{{ release.extra.publisher }}</span>
                  </div>
                </div>
                <div class="flex items-center gap-2 ml-3 shrink-0">
                  <span class="badge badge-sm">{{ release.format }}</span>
                  <span class="badge badge-ghost badge-sm">{{ release.size }}</span>
                  <span v-if="release.language" class="badge badge-outline badge-sm uppercase">{{ release.language }}</span>
                </div>
              </div>
            </div>
            <div class="modal-action">
              <button class="btn btn-sm" @click="showDigitalSearchModal = false">Close</button>
            </div>
          </div>
          <div class="modal-backdrop" @click="showDigitalSearchModal = false"></div>
        </dialog>
        <div class="mt-3">
          <button class="btn btn-outline btn-sm" onclick="document.getElementById('qr-modal').showModal()">
            <i class="mdi mdi-qrcode mdi-18px mr-1" /> QR Code
          </button>
          <dialog id="qr-modal" class="modal">
            <div class="modal-box max-w-sm">
              <div class="bg-white text-black p-5 rounded-lg border-2 border-gray-800 text-center mx-auto" style="width: 260px;">
                <img src="/android-chrome-192x192.png" alt="logo" style="width: 40px; height: 40px; margin: 0 auto 8px auto; display: block; border-radius: 50%;">
                <img :src="qrCodeUrl" alt="QR Code" class="mx-auto" style="width: 170px; height: 170px;">
                <div style="border-bottom: 1px solid #ccc; margin: 12px 0;"></div>
                <div style="font-weight: bold; font-size: 14px; line-height: 1.3; font-family: Georgia, serif;">{{ book?.book?.title }}</div>
              </div>
              <div class="flex justify-center gap-2 mt-4">
                <button class="btn btn-sm btn-primary" @click="printQrLabel"><i class="mdi mdi-printer mdi-18px mr-1" /> Print</button>
                <button class="btn btn-sm btn-secondary" @click="downloadQrLabel"><i class="mdi mdi-download mdi-18px mr-1" /> Download</button>
                <button class="btn btn-sm btn-outline" onclick="document.getElementById('qr-modal').close()">Close</button>
              </div>
            </div>
            <form method="dialog" class="modal-backdrop"><button>close</button></form>
          </dialog>
        </div>
      </div>
    </div>
    <div
      v-if="book?.book?.summary"
      class="flex flex-row justify-center mt-4 prose prose-base dark:prose-invert sm:w-10/12"
    >
      <div
        v-if="book?.book?.summary"
        class="jelu-bordered w-11/12 sm:w-9/12 p-2.5"
      >
        <p
          v-if="book?.book?.summary"
          class="font-semibold capitalize"
        >
          {{ t('book.summary') }} :
        </p>
        <p
          v-if="book?.book?.summary"
          v-html="book.book.summary"
        />
      </div>
    </div>
    <div class="flex flex-row justify-center">
      <span
        v-for="tag in book?.book?.tags"
        :key="tag.id"
        class="badge badge-primary mt-3 m-0.5 hover:font-bold hover:border-4"
      >
        <router-link :to="{ name: 'tag-detail', params: { tagId: tag.id } }">{{ tag.name }}&nbsp;</router-link>
      </span>
    </div>
    <div
      v-if="hasExternalLink"
      class="space-x-2"
    >
      <span
        v-if="book?.book.goodreadsId"
        class="badge badge-warning mt-2 hover:font-bold"
      >
        <a
          :href="'https://www.goodreads.com/book/show/' + book.book.goodreadsId"
          target="_blank"
        >goodreads</a>
      </span>
      <span
        v-if="book?.book.googleId"
        class="badge badge-warning hover:font-bold"
      >
        <a
          :href="'https://books.google.com/books?id=' + book.book.googleId"
          target="_blank"
        >google</a>
      </span>
      <span
        v-if="book?.book.amazonId"
        class="badge badge-warning hover:font-bold"
      >
        <a
          :href="'https://www.amazon.com/dp/' + book.book.amazonId"
          target="_blank"
        >amazon</a>
      </span>
      <span
        v-if="book?.book.librarythingId"
        class="badge badge-warning hover:font-bold"
      >
        <a
          :href="'https://www.librarything.com/work/' + book.book.librarythingId"
          target="_blank"
        >librarything</a>
      </span>
      <span
        v-if="book?.book.isfdbId"
        class="badge badge-warning hover:font-bold"
      >
        <a
          :href="'https://www.isfdb.org/cgi-bin/title.cgi?' + book.book.isfdbId"
          target="_blank"
        >ISFDB</a>
      </span>
      <span
        v-if="book?.book.openlibraryId"
        class="badge badge-warning hover:font-bold"
      >
        <a
          :href="`https://openlibrary.org/works/${book.book.openlibraryId}?mode=all`"
          target="_blank"
        >Openlibrary</a>
      </span>
      <span
        v-if="book?.book.noosfereId"
        class="badge badge-warning hover:font-bold"
      >
        <a
          :href="'https://www.noosfere.org/livres/EditionsLivre.asp?numitem=' + book.book.noosfereId"
          target="_blank"
        >Noosfere</a>
      </span>
      <span
        v-if="getIsbn() != null"
        class="badge badge-warning hover:font-bold"
      >
        <a
          :href="'https://inventaire.io/entity/isbn:' + getIsbn()"
          target="_blank"
        >inventaire</a>
      </span>
      <span
        v-else-if="book?.book.inventaireId"
        class="badge badge-warning hover:font-bold"
      >
        <a
          :href="'https://inventaire.io/entity/inv:' + book.book.inventaireId"
          target="_blank"
        >inventaire</a>
      </span>
    </div>
    <div
      v-if="book?.personalNotes"
      class="mt-4"
    >
      <p
        v-if="book?.personalNotes"
        class="font-semibold capitalize"
      >
        {{ t('book.personal_notes') }} :
      </p>
      <p v-if="book?.personalNotes">
        {{ book.personalNotes }}
      </p>
    </div>
    <div class="mt-2">
      <router-link
        class="link text-2xl"
        :class="typographyClasses"
        :to="{ name: 'book-reviews', params: { bookId: book?.book.id } }"
      >
        {{ t('reviews.all_reviews') }}
      </router-link>
    </div>
    <div
      v-if="userReviews != null && userReviews.length > 0"
      class="w-11/12 sm:w-10/12 flex flex-row flex-wrap justify-center mt-4 gap-4"
    >
      <p
        class="text-2xl mb-3 capitalize sm:w-full"
        :class="typographyClasses"
      >
        {{ t('reviews.my_reviews') }} :
      </p>
      <div
        v-for="review in userReviews"
        :key="review.id"
        class="w-11/12 2xl:basis-10/12"
      >
        <review-card
          v-if="review != null"
          :review="review"
          :show-delete="true"
          :show-edit="true"
          @update:delete="deleteReview($event)"
          @update:edit="toggleReviewModal(book?.book, true, review)"
        />
      </div>
    </div>
    <div
      v-if="bookQuotes != null && bookQuotes.length > 0"
      class="w-11/12 sm:w-10/12"
    >
      <router-link
        class="link text-2xl"
        :class="typographyClasses"
        :to="{ name: 'book-quotes', params: { bookId: book?.book.id } }"
      >
        {{ t('book_quotes.quote', 2) }}
      </router-link>
    </div>
    <div
      v-if="bookQuotes != null && bookQuotes.length > 0"
      class="w-11/12 sm:w-10/12 flex flex-row flex-wrap justify-center mt-4 gap-4"
    >
      <div
        v-for="quote in bookQuotes"
        :key="quote.id"
      >
        <book-quote-card
          v-if="quote != null"
          :book-quote="quote"
          :show-delete="true"
          :show-edit="true"
          @update:delete="deleteBookQuote($event)"
          @update:edit="toggleBookQuoteModal(book?.book, true, quote)"
        />
      </div>
    </div>
    <!-- https://tailwindcomponents.com/component/vertical-timeline -->
    <div
      v-if="book?.readingEvents != null && book?.readingEvents?.length > 0"
      class="mt-4"
    >
      <p
        v-if="book?.readingEvents != null && book?.readingEvents?.length > 0"
        class="text-2xl mb-3 capitalize"
        :class="typographyClasses"
      >
        {{ t('reading_events.reading_events') }} :
      </p>
      <div class="flex flex-col md:grid grid-cols-9 mx-auto p-2 text-blue-50">
        <div class="col-start-5 mb-3 p-2 font-semibold timeline-item capitalize">
          {{ t('reading_events.now') }}
        </div>

        <div
          v-for="(event, index) in sortedEvents"
          :key="event.id"
          class="flex md:contents"
          :class="{ 'flex-row-reverse': index % 2 === 0 }"
        >
          <div
            v-if="index % 2 === 0"
            class="col-start-1 col-end-5 p-2 my-4 ml-auto shadow-md timeline-item"
          >
            <div
              v-if="event.endDate != null"
              class="sm:flex sm:gap-2"
            >
              <h3 class="font-semibold">
                {{ d(event.endDate, 'short') }}
              </h3>
              <p class="capitalize">
                {{ eventLabel(event.eventType) }}&nbsp;-
              </p>
              <h3 class="font-semibold">
                {{ d(event.startDate!!, 'short') }}
              </h3>
              <p class="capitalize">
                {{ t('reading_events.started') }}
              </p>
            </div>
            <div v-else>
              <h3 class="font-semibold">
                {{ d(event.startDate!!, 'short') }}
              </h3>
              <p class="capitalize">
                {{ eventLabel(event.eventType) }}
              </p>
            </div>
            <button
              class="sm:hidden btn btn-xs btn-circle btn-outline mb-0 border-0"
              @click="toggleReadingEventModal(event, true)"
            >
              <i class="mdi mdi-pencil mdi-18px" />
            </button>
          </div>
          <div
            v-if="index % 2 === 0"
            class="col-start-5 col-end-6 md:mx-auto relative mr-10"
          >
            <div class="h-full w-6 flex items-center justify-center">
              <div class="h-full w-1 bg-base-content pointer-events-none" />
            </div>
            <div
              v-tooltip="{ content: t('labels.double_click_to_edit'), delay: { show: 5, hide: 2 } }"
              class="w-6 h-6 absolute top-1/2 -mt-3 rounded-full shadow"
              :class="eventClass(event)"
              @dblclick="toggleReadingEventModal(event, true)"
            >
              <i
                class="mdi"
                :class="iconClass(event)"
              />
            </div>
          </div>
          <div
            v-if="index % 2 !== 0"
            class="col-start-5 col-end-6 mr-10 md:mx-auto relative"
          >
            <div class="h-full w-6 flex items-center justify-center">
              <div class="h-full w-1 bg-base-content pointer-events-none" />
            </div>
            <div
              v-tooltip="{ content: t('labels.double_click_to_edit'), delay: { show: 5, hide: 2 } }"
              class="w-6 h-6 absolute top-1/2 -mt-3 rounded-full shadow"
              :class="eventClass(event)"
              @dblclick="toggleReadingEventModal(event, true)"
            >
              <i
                class="mdi"
                :class="iconClass(event)"
              />
            </div>
          </div>
          <div
            v-if="index % 2 !== 0"
            class="col-start-6 col-end-10 p-2 my-4 mr-auto shadow-md timeline-item"
          >
            <div
              v-if="event.endDate != null"
              class="sm:flex sm:gap-2"
            >
              <h3 class="font-semibold">
                {{ d(event.endDate, 'short') }}
              </h3>
              <p class="capitalize">
                {{ eventLabel(event.eventType) }}&nbsp;-
              </p>
              <h3 class="font-semibold">
                {{ d(event.startDate!!, 'short') }}
              </h3>
              <p class="capitalize">
                {{ t('reading_events.started') }}
              </p>
            </div>
            <div v-else>
              <h3 class="font-semibold">
                {{ d(event.startDate!!, 'short') }}
              </h3>
              <p class="capitalize">
                {{ eventLabel(event.eventType) }}
              </p>
            </div>
            <button
              class="sm:hidden btn btn-xs btn-circle btn-outline mb-0 border-0"
              @click="toggleReadingEventModal(event, true)"
            >
              <i class="mdi mdi-pencil mdi-18px" />
            </button>
          </div>
        </div>
        <div class="col-start-5 mt-3 p-2 font-semibold timeline-item capitalize">
          {{ t('reading_events.before') }}
        </div>
      </div>
    </div>
    <div v-if="users != null && users.filter(u => u.id !== user.id).length > 0" class="mt-2">
        <div class="text-xl " :class="typographyClasses" >{{ t("labels.other_owners")}}&nbsp;:</div>
        <p
          v-for="ppl in users.filter(u => u.id !== user.id)"
          :key="ppl.id"
        >
              <router-link
                class="link hover:underline hover:decoration-4 hover:decoration-secondary"
                :to="{ name: 'user-detail', params: { userId: ppl.id } }"
              >
                <strong>{{ ppl.login }}</strong>&nbsp;
              </router-link>
        </p>
    </div>
  </div>
  <o-loading
    v-model:active="getBookIsLoading"
    :full-page="true"
    :cancelable="true"
  />
  <input
    id="my-modal-4"
    type="checkbox"
    class="modal-toggle"
  >
  <label
    for="my-modal-4"
    class="modal cursor-pointer"
  >
    <label
      class="modal-box relative"
      for=""
    >
      <div class="flex justify-center items-center">
        <h3 class="text-lg font-bold first-letter:capitalize">{{ t('labels.copy_paste_code') }}</h3>
        <button
          v-if="isSupported"
          class="btn btn-outline btn-sm btn-circle border-none ml-1"
          @click="copyToClipboard(embedCode)"
        ><svg
          xmlns="http://www.w3.org/2000/svg"
          class="h-6 w-6"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="2"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="M8 5H6a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-1M8 5a2 2 0 002 2h2a2 2 0 002-2M8 5a2 2 0 012-2h2a2 2 0 012 2m0 0h2a2 2 0 012 2v3m2 4H10m0 0l3-3m-3 3l3 3"
          />
        </svg></button>
      </div>
      <div class="py-4 prose"><pre><code>{{ embedCode }}</code></pre></div>
      <div class="mt-2 capitalize">{{ t('labels.preview') }} : </div>
      <div
        class="inline-block mt-2"
        v-html="embedCode"
      />
    </label>
  </label>
    <!-- Lend modal -->
    <dialog v-if="showLendModal" class="modal modal-open">
      <div class="modal-box">
        <h3 class="font-bold text-lg mb-4"><i class="mdi mdi-account-arrow-right mdi-24px mr-2 text-warning" />Lend Book</h3>
        <fieldset class="fieldset mb-3">
          <legend class="fieldset-legend">Who are you lending to?</legend>
          <input v-model="lendForm.borrowerName" type="text" class="input w-full focus:input-accent" placeholder="Borrower name">
        </fieldset>
        <fieldset class="fieldset mb-3">
          <legend class="fieldset-legend">Expected return date (optional)</legend>
          <input v-model="lendForm.expectedReturnDate" type="date" class="input w-full focus:input-accent">
        </fieldset>
        <div class="modal-action">
          <button class="btn btn-ghost" @click="showLendModal = false">Cancel</button>
          <button class="btn btn-warning" :disabled="!lendForm.borrowerName" @click="lendBook">Lend</button>
        </div>
      </div>
      <div class="modal-backdrop" @click="showLendModal = false" />
    </dialog>
</template>

<style scoped>

.dropdown-content.menu {
  width: fit-content !important;
}

</style>
