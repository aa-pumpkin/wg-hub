<script setup lang="ts">
import { Check } from 'lucide-vue-next'
import { useHouseholdStore } from '@/stores/household'
import UserAvatar from './UserAvatar.vue'

/** Multi-select of household members. The selection order is kept (it is the rotation order). */
defineProps<{ label: string; error?: string; hint?: string; showOrder?: boolean }>()
const model = defineModel<string[]>({ required: true })
const household = useHouseholdStore()

function toggle(userId: string) {
  model.value = model.value.includes(userId)
    ? model.value.filter((id) => id !== userId)
    : [...model.value, userId]
}
</script>

<template>
  <fieldset>
    <legend class="mb-1.5 text-sm font-medium">{{ label }}</legend>
    <div class="flex flex-wrap gap-2">
      <button
        v-for="member in household.members"
        :key="member.id"
        type="button"
        :aria-pressed="model.includes(member.id)"
        class="tap inline-flex items-center gap-2 rounded-full border py-1 pr-3 pl-1 text-sm transition-colors"
        :class="
          model.includes(member.id)
            ? 'border-accent bg-accent-soft text-accent-text'
            : 'border-line hover:bg-subtle'
        "
        @click="toggle(member.id)"
      >
        <span
          v-if="showOrder && model.includes(member.id)"
          class="inline-flex size-6 items-center justify-center rounded-full bg-accent text-[11px] font-semibold text-accent-fg"
        >
          {{ model.indexOf(member.id) + 1 }}
        </span>
        <span
          v-else-if="model.includes(member.id)"
          class="inline-flex size-6 items-center justify-center rounded-full bg-accent text-accent-fg"
        >
          <Check class="size-3.5" aria-hidden="true" />
        </span>
        <UserAvatar v-else :name="member.displayName" :color="member.avatarColor" size="sm" />
        {{ member.displayName }}
      </button>
    </div>
    <p v-if="error" class="mt-1.5 text-[13px] text-danger" role="alert">{{ error }}</p>
    <p v-else-if="hint" class="mt-1.5 text-[13px] text-muted">{{ hint }}</p>
  </fieldset>
</template>
