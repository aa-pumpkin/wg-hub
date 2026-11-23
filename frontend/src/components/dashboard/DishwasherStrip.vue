<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import { useDishwasher } from '@/composables/useDishwasher'

const { t } = useI18n()
const route = useRoute()
const { state, mine, busy, tap } = useDishwasher()

const crownName = computed(
  () => state.value?.members.find((row) => row.user.id === state.value?.crownHolderId)?.user.displayName,
)
</script>

<template>
  <div v-if="state" class="card flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3">
    <RouterLink :to="{ name: 'dishwasher', params: { householdId: route.params.householdId } }" class="min-w-0 hover:underline">
      <p class="text-sm font-semibold">{{ t('dishwasher.title') }}</p>
      <p class="truncate text-[13px] text-muted">
        {{ t('dishwasher.you', { total: state.total, target: state.target ?? 0, count: mine?.count ?? 0 }) }}
        <template v-if="crownName"> · {{ t('dishwasher.crownOf', { name: crownName }) }}</template>
      </p>
    </RouterLink>
    <AppButton variant="primary" :loading="busy" @click="tap">+1 · {{ t('dishwasher.tap') }}</AppButton>
  </div>
</template>
