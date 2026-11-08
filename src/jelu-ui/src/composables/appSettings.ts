import { reactive, readonly } from 'vue'
import dataService from '../services/DataService'

// ── Default values (the previously-hardcoded constants) ──────────────
// Adding a new setting = add a default here + read it where needed.
export const SETTINGS_DEFAULTS: Record<string, string> = {
  // Reading goals & pace
  default_daily_goal: '20',
  autoprompt_goal_on_reading: 'true',
  // Reading challenge
  weekly_report_popup: 'true',
  // Wishlist / Shelfmark downloads
  shelfmark_url: 'http://192.168.88.20:8084',
  match_threshold: '70',
  autolink_on_add: 'true',
  // Search typeahead
  typeahead_enabled: 'true',
  typeahead_count: '6',
  typeahead_debounce: '250',
  typeahead_enter_top_hit: 'false',
  // Appearance
  custom_logo_enabled: 'false',
}

// Keys that used to be persisted but are not read anywhere. A null value on
// save deletes the row, so stale rows are removed the next time settings save.
const OBSOLETE_KEYS = [
  'default_pace_window',
  'weekly_makeup_rule',
  'autorebuild_on_history_edit',
  'min_pages_per_day',
  'default_goal_period',
  'timeline_default_zoom',
  'timeline_bar_scheme',
  'ebooks_path',
  'download_format_priority',
  'download_language',
  'notify_goal_milestones',
  'notify_streak_at_risk',
  'notify_download_complete',
  'notify_weekly_report',
]

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
  const obsolete = Object.fromEntries(OBSOLETE_KEYS.map(key => [key, null]))
  const result = await dataService.updateAppSettings({ ...obsolete, ...updates })
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
