<script setup lang="ts">
import { useLocalStorage, useTitle } from '@vueuse/core'
import { themeChange } from 'theme-change'
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import StyledTitle from './StyledTitle.vue'

useTitle('Jelu | User settings')

const { t, locale, availableLocales } = useI18n({
      inheritLocale: true,
      useScope: 'global'
    })

availableLocales.forEach(locale => {
      console.log(`${locale} locale `)
    })

const themes = [
        "light",
        "dark",
        "jelu",
        "cupcake",
        "bumblebee",
        "emerald",
        "corporate",
        "synthwave",
        "retro",
        "cyberpunk",
        "valentine",
        "halloween",
        "garden",
        "forest",
        "aqua",
        "lofi",
        "pastel",
        "fantasy",
        "wireframe",
        "black",
        "luxury",
        "dracula",
        "cmyk",
        "autumn",
        "business",
        "acid",
        "lemonade",
        "night",
        "coffee",
        "winter",
        "dim",
        "nord",
        "sunset",
        "caramellatte",
        "abyss",
        "silk",
      ];

const activeTheme = ref(localStorage.getItem('theme') || '')
const themeOpen = ref(false)
onMounted(() => {
  themeChange(false);
  activeTheme.value = localStorage.getItem('theme') || ''
});
const applyTheme = (theme: string) => {
  if (theme) {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem('theme', theme)
  } else {
    document.documentElement.removeAttribute('data-theme')
    localStorage.removeItem('theme')
  }
  activeTheme.value = theme
}

watch(() => locale.value,(newValue, oldValue) => {
  console.log('locale changed: ' + newValue + " " + oldValue)
  const storedLanguage = useLocalStorage("jelu_language", oldValue)
  storedLanguage.value = newValue
})

const currency = ref("EUR")
const saved = localStorage.getItem("JL_CURRENCY")
if (saved != null) {
  currency.value = saved
}

watch(() => currency.value, (newVal, oldVal) => {
  console.log('currency changed ' + currency.value)
  if (currency.value.length === 3) {
    localStorage.setItem("JL_CURRENCY", currency.value)
  }
})

const curr = localStorage.getItem("JL_FONT") ?? "typewriter"
const font = ref(curr)

const fontClasses = computed(() => {
  if (font.value === 'cormorant') {
    return "cormorant text-3xl"
  } else if (font.value === 'typewriter') {
    return "typewriter text-2xl"
  }
  return 'text-2xl'
})

watch(() => font.value, (newVal, oldVal) => {
  localStorage.setItem("JL_FONT", font.value)
})


// ── Custom logo ──────────────────────────────────────────────────
import dataService from '../services/DataService'
import { useOruga } from '@oruga-ui/oruga-next'
import { ObjectUtils } from '../utils/ObjectUtils'
import { useAppSettings } from '../composables/appSettings'

const orugaLogo = useOruga()
const appS = useAppSettings()
const logoUploading = ref(false)
const logoPreview = ref<string | null>(null)
const customLogoActive = ref(appS.getBool('custom_logo_enabled'))

const onLogoSelected = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  logoUploading.value = true
  try {
    await dataService.uploadAppLogo(file)
    await appS.load(true)
    customLogoActive.value = true
    logoPreview.value = '/files/custom-logo.png?v=' + Date.now()
    ObjectUtils.toast(orugaLogo, 'success', 'Logo updated — reload to see it everywhere', 3000)
  } catch (err) {
    ObjectUtils.toast(orugaLogo, 'danger', 'Upload failed: ' + err, 3000)
  } finally {
    logoUploading.value = false
  }
}

const resetLogo = async () => {
  try {
    await dataService.resetAppLogo()
    await appS.load(true)
    customLogoActive.value = false
    logoPreview.value = null
    ObjectUtils.toast(orugaLogo, 'success', 'Logo reset to default — reload to apply', 3000)
  } catch (err) {
    ObjectUtils.toast(orugaLogo, 'danger', 'Reset failed: ' + err, 3000)
  }
}
</script>

<template>
  <div class="max-w-2xl mx-auto px-4 py-6">
    <h1 class="text-2xl font-semibold mb-1">Appearance</h1>
    <p class="text-sm opacity-60 mb-6">Theme, language, currency, fonts, and your app logo.</p>

    <div class="flex flex-col gap-3">
      <!-- Theme -->
      <div class="border border-base-300 rounded-xl p-4 flex flex-col gap-3">
        <button class="flex items-center justify-between w-full" @click="themeOpen = !themeOpen">
          <span class="font-medium flex items-center gap-2"><i class="mdi mdi-palette-outline" /> {{ t('settings.pick_theme') }}</span>
          <span class="flex items-center gap-2">
            <span class="text-xs opacity-60 capitalize">{{ activeTheme || 'Default' }}</span>
            <i class="mdi" :class="themeOpen ? 'mdi-chevron-up' : 'mdi-chevron-down'" />
          </span>
        </button>
        <div v-if="themeOpen" class="grid grid-cols-2 sm:grid-cols-3 gap-2">
          <button
            class="rounded-lg border-2 p-2 text-left transition hover:scale-[1.02]"
            :class="activeTheme === '' ? 'border-primary' : 'border-base-300'"
            @click="applyTheme('')"
          >
            <div class="flex items-center justify-between">
              <span class="text-xs font-medium">Default</span>
              <i v-if="activeTheme === ''" class="mdi mdi-check-circle text-primary text-sm" />
            </div>
            <div class="flex gap-1 mt-2">
              <div class="w-4 h-4 rounded-full bg-base-content/20" />
              <div class="w-4 h-4 rounded-full bg-base-content/40" />
              <div class="w-4 h-4 rounded-full bg-base-content/60" />
            </div>
          </button>
          <button
            v-for="theme in themes"
            :key="theme"
            :data-theme="theme"
            class="rounded-lg border-2 p-2 text-left transition hover:scale-[1.02] bg-base-100"
            :class="activeTheme === theme ? 'border-primary' : 'border-base-300'"
            @click="applyTheme(theme)"
          >
            <div class="flex items-center justify-between">
              <span class="text-xs font-medium text-base-content capitalize">{{ theme }}</span>
              <i v-if="activeTheme === theme" class="mdi mdi-check-circle text-primary text-sm" />
            </div>
            <div class="flex gap-1 mt-2">
              <div class="w-4 h-4 rounded-full bg-primary" />
              <div class="w-4 h-4 rounded-full bg-secondary" />
              <div class="w-4 h-4 rounded-full bg-accent" />
              <div class="w-4 h-4 rounded-full bg-neutral" />
            </div>
          </button>
        </div>
        <p v-if="themeOpen" class="text-xs opacity-50">{{ t('settings.theme_warning') }}</p>
      </div>

      <!-- Display / font -->
      <div class="border border-base-300 rounded-xl p-4 flex flex-col gap-4">
        <h2 class="font-medium flex items-center gap-2"><i class="mdi mdi-format-font" /> Display</h2>
        <label class="flex items-center justify-between gap-4">
          <span class="text-sm">{{ t('settings.choose_font') }}</span>
          <select v-model="font" class="select select-sm select-bordered w-44">
            <option>typewriter</option>
            <option>cormorant</option>
            <option>system</option>
          </select>
        </label>
        <p :class="fontClasses" class="opacity-80">The Great Gatsby</p>
      </div>

      <!-- Locale -->
      <div class="border border-base-300 rounded-xl p-4 flex flex-col gap-4">
        <h2 class="font-medium flex items-center gap-2"><i class="mdi mdi-translate" /> Locale</h2>
        <label class="flex items-center justify-between gap-4">
          <span class="text-sm">{{ t('settings.pick_language') }}</span>
          <select v-model="locale" class="select select-sm select-bordered w-44">
            <option v-for="loc in availableLocales" :key="(loc as string)" :value="loc">{{ loc }}</option>
          </select>
        </label>
        <label class="flex items-center justify-between gap-4">
          <span class="text-sm">{{ t('settings.choose_currency') }}</span>
          <input v-model="currency" type="text" minlength="3" maxlength="3" class="input input-sm input-bordered w-24 uppercase">
        </label>
        <p class="text-xs" :class="currency.length != 3 ? 'text-error' : 'opacity-50'">{{ t('settings.currency_description') }}</p>
      </div>

      <!-- App logo -->
      <div class="border border-base-300 rounded-xl p-4">
        <h2 class="font-medium flex items-center gap-2 mb-1"><i class="mdi mdi-image-outline" /> App logo</h2>
        <p class="text-sm opacity-60 mb-3">Upload a custom logo to replace the default in the navigation bar. PNG recommended.</p>
        <div class="flex items-center gap-4">
          <div class="w-16 h-16 rounded-lg bg-base-200 flex items-center justify-center overflow-hidden border border-base-300">
            <img
              v-if="logoPreview || customLogoActive"
              :src="logoPreview || ('/files/custom-logo.png?v=' + Date.now())"
              alt="logo preview"
              class="max-w-full max-h-full object-contain"
            >
            <i v-else class="mdi mdi-image-outline mdi-24px opacity-30" />
          </div>
          <div class="flex flex-col gap-2">
            <label class="btn btn-sm btn-outline" :class="{ 'loading': logoUploading }">
              <i class="mdi mdi-upload mr-1" /> Upload logo
              <input type="file" accept="image/*" class="hidden" @change="onLogoSelected">
            </label>
            <button v-if="customLogoActive" class="btn btn-sm btn-ghost btn-error" @click="resetLogo">
              <i class="mdi mdi-restore mr-1" /> Reset to default
            </button>
          </div>
        </div>
        <p class="text-xs opacity-50 mt-2">Reload the app after changing to see it everywhere.</p>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>

</style>
