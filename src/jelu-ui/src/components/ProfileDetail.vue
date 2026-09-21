<script setup lang="ts">
import { useOruga } from "@oruga-ui/oruga-next"
import { useTitle } from '@vueuse/core'
import { computed, ref, Ref } from 'vue'
import Avatar from 'vue-avatar-sdh'
import { useI18n } from 'vue-i18n'
import { useStore } from 'vuex'
import { LoginHistoryInfo, Provider } from "../model/User"
import dataService from "../services/DataService"
import { key } from '../store'
import UserModalVue from './UserModal.vue'
import useTypography from "../composables/typography"

const oruga = useOruga()

const { t } = useI18n({
  inheritLocale: true,
  useScope: 'global'
})

useTitle('Jelu | User page')

const store = useStore(key)

const showModal: Ref<boolean> = ref(false)

const user = computed(() => {
  return store.getters.getUser
})

let loginHistoryInfo: Ref<Array<LoginHistoryInfo>> = ref([])

function toggleUserModal() {
  showModal.value = !showModal.value
  oruga.modal.open({
    component: UserModalVue,
    trapFocus: true,
    active: true,
    canCancel: ['x', 'button', 'outside'],
    scroll: 'keep',
    props: {
      "currentUser": user.value
    },
    onClose: modalClosed
  });
}

function modalClosed() {
  console.log("modal closed")
  store.dispatch('getUser')
}

const fetchHistoryInfo = async () => {
  dataService.userLoginHistory()
    .then( res => loginHistoryInfo.value = res)
    .catch(err => console.log(err))
}

fetchHistoryInfo()

const { typographyClasses } = useTypography()
</script>

<template>
  <div class="max-w-2xl mx-auto px-4 py-6">
    <h1 class="text-2xl font-semibold mb-1">Profile</h1>
    <p class="text-sm opacity-60 mb-6">Your account details and recent sign-in activity.</p>

    <!-- Account card -->
    <div class="border border-base-300 rounded-xl p-4 flex items-center gap-4">
      <Avatar :username="store != null && store.getters.getUsername" />
      <div class="flex-1 min-w-0">
        <div class="text-lg font-medium">{{ store.getters.getUsername }}</div>
        <div class="flex items-center gap-1 mt-1">
          <span v-if="store.getters.isAdmin" class="badge badge-warning badge-sm rounded-md">ADMIN</span>
          <span v-else class="badge badge-info badge-sm rounded-md">USER</span>
        </div>
      </div>
      <button
        v-if="user.provider !== Provider.LDAP && user.provider !== Provider.PROXY"
        v-tooltip="t('profile.edit_user')"
        class="btn btn-sm btn-ghost btn-circle"
        @click="toggleUserModal"
      >
        <i class="mdi mdi-pencil mdi-18px" />
      </button>
    </div>

    <!-- Sign-in activity -->
    <div class="border border-base-300 rounded-xl p-4 mt-3">
      <h2 class="font-medium flex items-center gap-2 mb-3"><i class="mdi mdi-history" /> {{ t('login_info.activity') }}</h2>
      <div class="overflow-x-auto">
        <table class="table table-sm">
          <thead>
            <tr>
              <th>{{ t('login_info.ip') }}</th>
              <th>{{ t('login_info.user_agent') }}</th>
              <th>{{ t('login_info.source') }}</th>
              <th>{{ t('login_info.last_activity') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in loginHistoryInfo" :key="line.date">
              <td>{{ line.ip }}</td>
              <td class="max-w-xs truncate">{{ line.userAgent }}</td>
              <td>{{ line.source }}</td>
              <td>{{ line.date }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
</style>
