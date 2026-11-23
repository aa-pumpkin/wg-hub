<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/components/common/AppButton.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SkeletonList from '@/components/common/SkeletonList.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useDishwasher } from '@/composables/useDishwasher'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const { state, mine, busy, tap, undo } = useDishwasher()

const crownName = computed(
  () => state.value?.members.find((row) => row.user.id === state.value?.crownHolderId)?.user.displayName,
)
const ranked = computed(() => [...(state.value?.members ?? [])].sort((a, b) => b.count - a.count))
const percent = computed(() => Math.min(100, ((state.value?.total ?? 0) / (state.value?.target || 1)) * 100))
</script>

<template>
  <div class="mx-auto max-w-2xl">
    <PageHeader :title="t('dishwasher.title')" />
    <SkeletonList v-if="!state" />
    <div v-else class="space-y-5">
      <section class="card p-5 text-center">
        <p class="text-sm text-muted">{{ t('dishwasher.round', { cycle: state.cycle }) }}</p>
        <p class="mt-1 text-3xl font-semibold tabular-nums">{{ state.total }} / {{ state.target }}</p>
        <div class="mx-auto mt-2 h-2 overflow-hidden rounded-full bg-subtle">
          <div class="h-full rounded-full bg-accent transition-all" :style="{ width: `${percent}%` }" />
        </div>
        <p v-if="crownName" class="mt-1 text-sm font-medium">{{ t('dishwasher.crownOf', { name: crownName }) }}</p>
        <AppButton variant="primary" block class="mt-4 min-h-12 text-base" :loading="busy" @click="tap">
          +1 · {{ t('dishwasher.tap') }}
        </AppButton>
        <button
          v-if="(mine?.count ?? 0) > 0"
          type="button"
          class="tap mt-1 text-[13px] text-muted hover:text-fg"
          @click="undo"
        >
          {{ t('dishwasher.undo') }}
        </button>
      </section>

      <ul class="card divide-y divide-line">
        <li v-for="row in ranked" :key="row.user.id" class="list-row" :class="row.frozen && 'opacity-60'">
          <UserAvatar :name="row.user.displayName" :color="row.user.avatarColor" size="sm" />
          <div class="min-w-0 flex-1">
            <p class="flex flex-wrap items-center gap-x-2 text-sm font-medium">
              <span>
                {{ row.user.displayName }}
                <span v-if="row.user.id === auth.user?.id" class="font-normal text-muted">({{ t('common.you') }})</span>
                <span v-if="row.user.id === state.crownHolderId" aria-hidden="true"> 👑</span>
              </span>
              <span v-if="row.frozen" class="text-[13px] font-normal text-muted">{{ t('members.frozen') }}</span>
              <span v-if="row.crowns" class="text-[13px] font-normal text-muted">{{ t('dishwasher.crowns', { count: row.crowns }) }}</span>
              <span v-if="row.strikes" class="text-[13px] font-normal text-muted">{{ t('dishwasher.strikes', { count: row.strikes }) }}</span>
            </p>
          </div>
          <span class="w-10 text-right text-sm font-semibold tabular-nums">{{ row.count }}</span>
        </li>
      </ul>

      <p v-if="state.lastCycle?.struck.length" class="text-sm text-warn">
        {{ t('dishwasher.lastStruck', { names: state.lastCycle.struck.map((user) => user.displayName).join(', '), points: state.strikePenalty }) }}
      </p>
      <p class="text-[13px] text-muted">{{ t('dishwasher.rule', { target: state.target ?? 0, points: state.strikePenalty }) }}</p>
    </div>
  </div>
</template>
