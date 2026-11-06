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

onMounted(() => {
  themeChange(false);
});

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
import { ref as vref } from 'vue'

const orugaLogo = useOruga()
const appS = useAppSettings()
const logoUploading = vref(false)
const logoPreview = vref<string | null>(null)
const customLogoActive = vref(appS.getBool('custom_logo_enabled'))

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
  <div class="w-fit flex flex-col">
    <label class="label">
      <span class="label-text text-lg">
        <svg
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
            d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01"
          />
        </svg>
        {{ t('settings.pick_theme') }} :
      </span>
    </label>
    <select
      class="select select-bordered select-primary"
      data-choose-theme
    >
      <option value="">
        Default
      </option>
      <option
        v-for="theme in themes"
        :key="theme"
        :value="theme"
      >
        {{ theme }}
      </option>
    </select>
    <label class="label">
      <span class="label-text-alt font-bold">{{ t('settings.theme_warning') }}</span>
    </label>
    <label class="label">
      <span class="label-text text-lg">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          class="h-6 w-6"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth="2"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M3 5h12M9 3v2m1.048 9.5A18.022 18.022 0 016.412 9m6.088 9h7M11 21l5-10 5 10M12.751 5C11.783 10.77 8.07 15.61 3 18.129"
          />
        </svg>
        {{ t('settings.pick_language') }} : </span>
    </label>
    <select
      v-model="locale"
      class="select select-bordered select-accent"
    >
      <option
        disabled
        selected
      >
        {{ t('settings.pick_language') }}
      </option>
      <option
        v-for="loc in availableLocales"
        :key="(loc as string)"
        :value="loc"
      >
        {{ loc }}
      </option>
    </select>
    <label class="label">
      <span class="label-text text-lg">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          stroke-width="1.5"
          stroke="currentColor"
          class="h-6 w-6"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="M14.25 7.756a4.5 4.5 0 1 0 0 8.488M7.5 10.5h5.25m-5.25 3h5.25M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"
          />
        </svg>

        {{ t('settings.choose_currency') }} : </span>
    </label>
    <input
      v-model="currency"
      type="text"
      minlength="3"
      maxlength="3"
      class="input input-accent"
    >
    <p :class="currency.length != 3 ? 'text-error':''">
      {{ t('settings.currency_description') }}
    </p>
    <label class="label">
      <span class="label-text text-lg">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="lucide lucide-type-icon lucide-type h-6"
        ><path d="M12 4v16" /><path d="M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2" /><path d="M9 20h6" /></svg>
        {{ t('settings.choose_font') }} : </span>
    </label>
    <select
      v-model="font"
      class="select select-bordered select-accent"
    >
      <option
        disabled
        selected
      >
        {{ t('settings.choose_font') }}
      </option>
      <option>
        typewriter
      </option>
      <option>
        cormorant
      </option>
      <option>
        system
      </option>
    </select>
    <p
      :class="fontClasses"
      class="mt-3"
    >
      {{ t('settings.choose_font') }}
    </p>
  </div>
    <!-- Custom App Logo -->
    <div class="my-6 max-w-md">
      <h2 class="text-lg font-medium mb-2">App logo</h2>
      <p class="text-sm opacity-60 mb-3">Upload a custom logo to replace the default in the navigation bar. PNG recommended.</p>
      <div class="flex items-center gap-4">
        <div class="w-16 h-16 rounded-lg bg-base-200 flex items-center justify-center overflow-hidden border border-base-content/15">
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
</template>

<style lang="scss" scoped>

</style>
