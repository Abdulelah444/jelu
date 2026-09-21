<script setup lang="ts">
import { useOruga } from "@oruga-ui/oruga-next";
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useStore } from 'vuex';
import { User } from "../model/User";
import dataService from "../services/DataService";
import { key } from '../store';
import { ObjectUtils } from "../utils/ObjectUtils";
import useTypography from "../composables/typography";

const { t } = useI18n({ inheritLocale: true, useScope: 'global' })
const oruga = useOruga()
const store = useStore(key)
const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const form = ref({ password: '', password_confirm: '' })
const showPassword = ref(false)
const saving = ref(false)

const matches = computed(() => form.value.password === form.value.password_confirm)
const isValid = computed(() => matches.value)

async function editUser() {
  if (props.currentUser.id == null) return
  saving.value = true
  try {
    const modified = await dataService.updateUser(props.currentUser.id, { isAdmin: undefined, password: form.value.password })
    store.commit('user', modified)
    ObjectUtils.toast(oruga, "success", "Password changed. You may need to sign in again.", 3500)
    emit('close')
  } catch (err: any) {
    console.log('failed to change password', err)
    ObjectUtils.toast(oruga, "danger", "Could not change password: " + (err?.message || err), 3500)
  } finally {
    saving.value = false
  }
}

const { typographyClasses } = useTypography()
</script>

<template>
  <section class="event-modal">
    <div class="max-w-md">
      <h1 class="text-xl font-semibold mb-1" :class="typographyClasses">Change password</h1>
      <p class="text-sm opacity-60 mb-4">Set a new password for <strong>{{ props.currentUser.login }}</strong>.</p>

      <div class="flex flex-col gap-3">
        <label class="flex flex-col gap-1">
          <span class="text-sm">New password</span>
          <div class="join w-full">
            <input
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              class="input input-sm input-bordered join-item w-full"
              placeholder="Leave blank for no password"
              autocomplete="new-password"
            >
            <button
              type="button"
              class="btn btn-sm btn-outline join-item"
              @click="showPassword = !showPassword"
            >
              <i class="mdi" :class="showPassword ? 'mdi-eye-off' : 'mdi-eye'" />
            </button>
          </div>
        </label>

        <label class="flex flex-col gap-1">
          <span class="text-sm">Confirm password</span>
          <input
            v-model="form.password_confirm"
            :type="showPassword ? 'text' : 'password'"
            class="input input-sm input-bordered w-full"
            placeholder="Re-enter the password"
            autocomplete="new-password"
          >
          <span v-if="form.password_confirm.length > 0 && !matches" class="text-xs text-error">Passwords don't match</span>
        </label>

        <div v-if="form.password.length === 0 && matches" class="text-xs opacity-60 flex items-center gap-1">
          <i class="mdi mdi-information-outline" /> This will set an empty password (no password required to sign in).
        </div>

        <div class="flex justify-end gap-2 mt-2">
          <button class="btn btn-sm btn-ghost" @click="emit('close')">Cancel</button>
          <button
            class="btn btn-sm btn-primary"
            :class="{ 'loading': saving }"
            :disabled="!isValid || saving"
            @click="editUser"
          >
            Save password
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style lang="scss">
</style>
