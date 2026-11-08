<script setup lang="ts">
import { useTitle } from '@vueuse/core'
import { ref, reactive, onMounted } from 'vue'
import { useOruga } from '@oruga-ui/oruga-next'
import { ObjectUtils } from '../utils/ObjectUtils'
import { useAppSettings } from '../composables/appSettings'
import dataService from '../services/DataService'

useTitle('Jelu | Settings — Preferences')
const oruga = useOruga()
const { load, getString, getInt, getBool, save, defaults } = useAppSettings()

const loading = ref(true)
const saving = ref(false)

// Shelfmark connection check
const testingShelfmark = ref(false)
const shelfmarkStatus = ref<null | { ok: boolean, reason?: string }>(null)

// Shelfmark downloader settings (managed here, applied to Shelfmark via its settings API)
const shelfmarkSettingsLoaded = ref(false)
const shelfmarkSettingsError = ref('')
const applyingShelfmark = ref(false)
const dl = reactive({
  aaMirrors: '',
  libgenMirrors: '',
  customDns: 'auto',
  customDnsManual: '',
  useDoh: true,
  maxConcurrent: 1,
  // Download destination Shelfmark writes books into (the shared ebooks volume).
  destination: '',
  destinationCheck: null as null | { writable: boolean, reason?: string, checkedPath?: string, note?: string },
})

// Local editable copy
const form = ref<Record<string, string>>({})

// Which sections are expanded
const open = ref<Record<string, boolean>>({
  reading: false, downloads: false, search: false,
})

const initForm = () => {
  const keys = Object.keys(defaults)
  const f: Record<string, string> = {}
  for (const k of keys) f[k] = getString(k)
  form.value = f
}

const testShelfmark = async (url?: string) => {
  const target = (url ?? form.value.shelfmark_url ?? '').trim()
  if (!target) {
    shelfmarkStatus.value = { ok: false, reason: 'Enter a Shelfmark URL first' }
    return
  }
  testingShelfmark.value = true
  try {
    const result = await dataService.checkShelfmarkConnection(target)
    shelfmarkStatus.value = { ok: result.ok, reason: result.reason }
  } finally {
    testingShelfmark.value = false
  }
}

const fieldValue = (tab: any, key: string): any => {
  const field = tab?.fields?.find((f: any) => f.key === key)
  return field?.value !== undefined ? field.value : field?.default
}

const loadShelfmarkSettings = async () => {
  shelfmarkSettingsLoaded.value = false
  shelfmarkSettingsError.value = ''
  try {
    const [mirrors, network, downloads] = await Promise.all([
      dataService.getShelfmarkSettings('mirrors'),
      dataService.getShelfmarkSettings('network'),
      dataService.getShelfmarkSettings('downloads'),
    ])
    dl.aaMirrors = (fieldValue(mirrors, 'AA_MIRROR_URLS') || []).join(', ')
    dl.libgenMirrors = (fieldValue(mirrors, 'LIBGEN_MIRROR_URLS') || []).join(', ')
    dl.customDns = fieldValue(network, 'CUSTOM_DNS') || 'auto'
    dl.customDnsManual = fieldValue(network, 'CUSTOM_DNS_MANUAL') || ''
    dl.useDoh = fieldValue(network, 'USE_DOH') !== false
    dl.maxConcurrent = Number(fieldValue(downloads, 'MAX_CONCURRENT_DOWNLOADS') || 1)
    dl.destination = String(fieldValue(downloads, 'DESTINATION') ?? '')
    dl.destinationCheck = downloads?.destination ?? null
    shelfmarkSettingsLoaded.value = true
  } catch (e) {
    shelfmarkSettingsError.value = String(e)
  }
}

const applyShelfmarkSettings = async () => {
  applyingShelfmark.value = true
  try {
    const splitList = (s: string) => s.split(',').map(x => x.trim()).filter(Boolean)
    await dataService.updateShelfmarkSettings('mirrors', {
      AA_MIRROR_URLS: splitList(dl.aaMirrors),
      LIBGEN_MIRROR_URLS: splitList(dl.libgenMirrors),
    })
    await dataService.updateShelfmarkSettings('network', {
      CUSTOM_DNS: dl.customDns,
      CUSTOM_DNS_MANUAL: dl.customDnsManual,
      USE_DOH: dl.useDoh,
    })
    if (dl.destination.trim()) {
      await dataService.updateShelfmarkSettings('downloads', {
        DESTINATION: dl.destination.trim(),
      })
    }
    await loadShelfmarkSettings()
    ObjectUtils.toast(oruga, 'success', 'Shelfmark downloader settings applied', 2000)
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Could not apply: ' + e, 3000)
  } finally {
    applyingShelfmark.value = false
  }
}

onMounted(async () => {
  await load(true)
  initForm()
  loading.value = false
  if (form.value.shelfmark_url?.trim()) testShelfmark()
  loadShelfmarkSettings()
})

const saveAll = async () => {
  saving.value = true
  try {
    await save({ ...form.value })
    ObjectUtils.toast(oruga, 'success', 'Preferences saved', 2000)
  } catch (e) {
    ObjectUtils.toast(oruga, 'danger', 'Could not save: ' + e, 3000)
  } finally {
    saving.value = false
  }
}

const toggle = (k: string) => { open.value[k] = !open.value[k] }
</script>

<template>
  <div class="max-w-2xl mx-auto px-4 py-6">
    <h1 class="text-2xl font-semibold mb-1">Preferences</h1>
    <p class="text-sm opacity-60 mb-6">Customize how the app behaves. Changes apply across the app.</p>

    <div v-if="loading" class="flex justify-center py-12"><span class="loading loading-spinner loading-lg" /></div>

    <div v-else class="flex flex-col gap-3">
      <!-- READING -->
      <div class="border border-base-300 rounded-xl overflow-hidden">
        <button class="w-full flex items-center justify-between px-4 py-3 bg-base-200 hover:bg-base-300" @click="toggle('reading')">
          <span class="font-medium flex items-center gap-2"><i class="mdi mdi-book-open-page-variant" /> Reading goals &amp; pace</span>
          <i class="mdi" :class="open.reading ? 'mdi-chevron-up' : 'mdi-chevron-down'" />
        </button>
        <div v-show="open.reading" class="p-4 flex flex-col gap-4">
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Default daily page goal</span>
            <input v-model="form.default_daily_goal" type="number" min="1" class="input input-sm input-bordered w-24">
          </label>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Auto-prompt for a goal when I start a book</span>
            <input v-model="form.autoprompt_goal_on_reading" type="checkbox" class="toggle toggle-sm" :true-value="'true'" :false-value="'false'">
          </label>
          <div class="divider my-0 text-xs opacity-50">Challenge</div>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Show weekly report popup on Sundays</span>
            <input v-model="form.weekly_report_popup" type="checkbox" class="toggle toggle-sm" :true-value="'true'" :false-value="'false'">
          </label>
        </div>
      </div>

      <!-- DOWNLOADS -->
      <div class="border border-base-300 rounded-xl overflow-hidden">
        <button class="w-full flex items-center justify-between px-4 py-3 bg-base-200 hover:bg-base-300" @click="toggle('downloads')">
          <span class="font-medium flex items-center gap-2"><i class="mdi mdi-download" /> Downloads (Shelfmark)</span>
          <i class="mdi" :class="open.downloads ? 'mdi-chevron-up' : 'mdi-chevron-down'" />
        </button>
        <div v-show="open.downloads" class="p-4 flex flex-col gap-4">
          <label class="flex flex-col gap-1">
            <span class="text-sm">Shelfmark URL</span>
            <div class="flex gap-2">
              <input v-model="form.shelfmark_url" type="text" class="input input-sm input-bordered w-full" placeholder="http://192.168.88.20:8084">
              <button class="btn btn-sm btn-outline shrink-0" :class="{ 'loading': testingShelfmark }" :disabled="testingShelfmark" @click="testShelfmark()">
                <i v-if="!testingShelfmark" class="mdi mdi-cloud-check mdi-18px" />
                Test connection
              </button>
            </div>
          </label>
          <div v-if="shelfmarkStatus" class="flex items-center gap-2 text-sm" :class="shelfmarkStatus.ok ? 'text-success' : 'text-error'">
            <i class="mdi mdi-18px" :class="shelfmarkStatus.ok ? 'mdi-check-circle' : 'mdi-alert-circle'" />
            <span v-if="shelfmarkStatus.ok">Shelfmark is reachable at this URL</span>
            <span v-else>{{ shelfmarkStatus.reason }}</span>
          </div>

          <div class="divider my-0 text-xs opacity-50">Shelfmark settings (applied to the downloader)</div>
          <div class="flex flex-col gap-2">
            <label class="flex flex-col gap-1">
              <span class="text-sm font-medium">Download destination (Shelfmark DESTINATION)</span>
              <input v-model="dl.destination" type="text" class="input input-sm input-bordered w-full" placeholder="/books">
              <span class="text-xs opacity-60">The folder Shelfmark writes completed books into (inside its container). Must point at the shared ebooks volume — see README.</span>
            </label>
            <div v-if="dl.destinationCheck && !dl.destinationCheck.writable" class="flex items-start gap-2 text-sm text-error rounded-lg bg-base-200 p-2">
              <i class="mdi mdi-alert-circle mdi-18px mt-0.5 shrink-0" />
              <span><strong>Destination is not writable.</strong> {{ dl.destinationCheck.reason }} Fix it here and press “Apply to Shelfmark”, or change it in Shelfmark's own Settings → Downloads.</span>
            </div>
            <div v-else-if="dl.destinationCheck && dl.destinationCheck.writable" class="flex items-start gap-2 text-sm text-success rounded-lg bg-base-200 p-2">
              <i class="mdi mdi-check-circle mdi-18px mt-0.5 shrink-0" />
              <span>Destination resolves to a writable path{{ dl.destinationCheck.checkedPath ? ` (${dl.destinationCheck.checkedPath})` : '' }}.</span>
            </div>
            <div v-if="dl.destinationCheck?.note" class="text-xs opacity-60">Note: {{ dl.destinationCheck.note }}</div>
          </div>
          <label class="flex flex-col gap-1">
            <span class="text-sm">Anna's Archive mirrors — tried in order</span>
            <input v-model="dl.aaMirrors" type="text" class="input input-sm input-bordered w-full" placeholder="https://annas-archive.gd, https://annas-archive.org">
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-sm">LibGen mirrors — tried in order</span>
            <input v-model="dl.libgenMirrors" type="text" class="input input-sm input-bordered w-full" placeholder="https://libgen.is">
          </label>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">DNS provider</span>
            <select v-model="dl.customDns" class="select select-sm select-bordered w-40">
              <option value="auto">Auto (recommended)</option>
              <option value="system">System</option>
              <option value="google">Google</option>
              <option value="cloudflare">Cloudflare</option>
              <option value="quad9">Quad9</option>
              <option value="opendns">OpenDNS</option>
              <option value="manual">Manual</option>
            </select>
          </label>
          <label v-if="dl.customDns === 'manual'" class="flex flex-col gap-1">
            <span class="text-sm">Manual DNS servers</span>
            <input v-model="dl.customDnsManual" type="text" class="input input-sm input-bordered w-full" placeholder="1.1.1.1, 8.8.8.8">
          </label>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Use DNS-over-HTTPS</span>
            <input v-model="dl.useDoh" type="checkbox" class="toggle toggle-sm">
          </label>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Max concurrent downloads (container env)</span>
            <input v-model="dl.maxConcurrent" type="number" min="1" max="10" disabled class="input input-sm input-bordered w-24">
          </label>
          <div v-if="shelfmarkSettingsError" class="text-sm text-warning">
            Could not load current Shelfmark settings: {{ shelfmarkSettingsError }}
          </div>
          <div class="flex items-center gap-2">
            <button class="btn btn-sm btn-outline" :class="{ 'loading': applyingShelfmark }" :disabled="applyingShelfmark" @click="applyShelfmarkSettings">
              <i v-if="!applyingShelfmark" class="mdi mdi-cloud-upload mdi-18px" />
              Apply to Shelfmark
            </button>
            <span v-if="shelfmarkSettingsLoaded" class="text-xs opacity-60">Current settings loaded from Shelfmark</span>
          </div>

          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">File-match strictness (%)</span>
            <input v-model="form.match_threshold" type="number" min="0" max="100" class="input input-sm input-bordered w-24">
          </label>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Auto-link downloaded file when adding to library</span>
            <input v-model="form.autolink_on_add" type="checkbox" class="toggle toggle-sm" :true-value="'true'" :false-value="'false'">
          </label>
        </div>
      </div>

      <!-- SEARCH -->
      <div class="border border-base-300 rounded-xl overflow-hidden">
        <button class="w-full flex items-center justify-between px-4 py-3 bg-base-200 hover:bg-base-300" @click="toggle('search')">
          <span class="font-medium flex items-center gap-2"><i class="mdi mdi-magnify" /> Search</span>
          <i class="mdi" :class="open.search ? 'mdi-chevron-up' : 'mdi-chevron-down'" />
        </button>
        <div v-show="open.search" class="p-4 flex flex-col gap-4">
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Enable search suggestions (typeahead)</span>
            <input v-model="form.typeahead_enabled" type="checkbox" class="toggle toggle-sm" :true-value="'true'" :false-value="'false'">
          </label>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Number of suggestions</span>
            <input v-model="form.typeahead_count" type="number" min="1" max="20" class="input input-sm input-bordered w-24">
          </label>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Suggestion delay (ms)</span>
            <input v-model="form.typeahead_debounce" type="number" min="0" step="50" class="input input-sm input-bordered w-24">
          </label>
          <label class="flex items-center justify-between gap-4">
            <span class="text-sm">Enter opens top suggestion (instead of full search)</span>
            <input v-model="form.typeahead_enter_top_hit" type="checkbox" class="toggle toggle-sm" :true-value="'true'" :false-value="'false'">
          </label>
        </div>
      </div>

      <div class="flex justify-end mt-2">
        <button class="btn btn-primary" :class="{ 'loading': saving }" @click="saveAll">Save preferences</button>
      </div>
    </div>
  </div>
</template>
