import { reactive, readonly } from 'vue'
import dataService from '../services/DataService'

// ── Default values (the previously-hardcoded constants) ──────────────
// Adding a new setting = add a default here + read it where needed.
export const SETTINGS_DEFAULTS: Record<string, string> = {
  // Reading goals & pace
  default_daily_goal: '20',
  autoprompt_goal_on_reading: 'true',
  default_pace_window: 'since_start',
  // Streaks
  weekly_makeup_rule: 'true',
  autorebuild_on_history_edit: 'true',
  min_pages_per_day: '1',
  // Reading challenge
  default_goal_period: 'ANNUAL',
  weekly_report_popup: 'true',
  // Timeline
  timeline_default_zoom: '2',
  timeline_bar_scheme: 'status',
  // Wishlist / Shelfmark downloads
  shelfmark_url: 'http://192.168.88.20:8084',
  ebooks_path: '/files/ebooks',
  download_format_priority: 'epub,pdf,mobi',
  download_language: 'en',
  match_threshold: '70',
  autolink_on_add: 'true',
  // Search typeahead
  typeahead_enabled: 'true',
  typeahead_count: '6',
  typeahead_debounce: '250',
  typeahead_enter_top_hit: 'false',
  // Notifications
  notify_goal_milestones: 'true',
  notify_streak_at_risk: 'true',
  notify_download_complete: 'true',
  notify_weekly_report: 'true',
  // Appearance
  custom_logo_enabled: 'false',
}

const state = reactive<{ values: Record<string, string>, loaded: boolean }>({
  values: { ...SETTINGS_DEFAULTS },
  loaded: false,
})

let loadPromise: Promise<void> | null = null

async function load(force = false): Promise<void> {
  if (state.loaded && !force) return
  if (loadPromise && !force) return loadPromise
  loadPromise = (async () => {
    try {
      const fetched = await dataService.getAppSettings()
      // Merge fetched over defaults so missing keys fall back
      state.values = { ...SETTINGS_DEFAULTS, ...fetched }
    } catch (e) {
      state.values = { ...SETTINGS_DEFAULTS }
    } finally {
      state.loaded = true
    }
  })()
  return loadPromise
}

function getString(key: string): string {
  return state.values[key] ?? SETTINGS_DEFAULTS[key] ?? ''
}
function getInt(key: string): number {
  const v = parseInt(getString(key))
  return isNaN(v) ? parseInt(SETTINGS_DEFAULTS[key] ?? '0') : v
}
function getBool(key: string): boolean {
  return getString(key) === 'true'
}

async function save(updates: Record<string, string | null>): Promise<void> {
  const result = await dataService.updateAppSettings(updates)
  state.values = { ...SETTINGS_DEFAULTS, ...result }
}

export function useAppSettings() {
  return {
    settings: readonly(state),
    load,
    getString,
    getInt,
    getBool,
    save,
    defaults: SETTINGS_DEFAULTS,
  }
}
