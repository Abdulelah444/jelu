import { useLocalStorage } from '@vueuse/core';
import { computed } from 'vue';

export default function useTypography() {

  const fontPreference = useLocalStorage("JL_FONT", "cormorant")

  const typographyClasses = computed(() => {
    if (fontPreference.value === 'cormorant') {
      return "cormorant font-bold"
    }
      return ""
    })

    return {
      typographyClasses
    }
}
