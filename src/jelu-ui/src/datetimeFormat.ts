import { I18nOptions } from "vue-i18n";

// Every supported locale formats dates the same way, so the formats are
// defined once and shared. Locales are looked up through the shared object,
// so this list only needs to name the languages we ship translations for.
const baseFormat = {
  short: {
    year: "numeric",
    month: "short",
    day: "numeric",
  },
  long: {
    year: "numeric",
    month: "short",
    day: "numeric",
    weekday: "short",
    hour: "numeric",
    minute: "numeric",
  },
} as const;

/**
 * Locales that have a translations file in `src/locales/`.
 * Keep this list in sync with `src/jelu-ui/src/locales/*.json`.
 */
export const datetimeLocales = [
  "cs",
  "da",
  "de",
  "el",
  "en",
  "es",
  "et",
  "fi",
  "fil",
  "fr",
  "hi",
  "hr",
  "hu",
  "id",
  "is",
  "it",
  "lb",
  "lt",
  "lv",
  "mk",
  "ms",
  "nl",
  "no",
  "pa",
  "pl",
  "pt",
  "ro",
  "sk",
  "sl",
  "sv",
  "tl",
  "tr",
  "vi",
] as const;

export const datetimeFormats: I18nOptions["datetimeFormats"] = Object.fromEntries(
  datetimeLocales.map((locale) => [locale, baseFormat]),
);
