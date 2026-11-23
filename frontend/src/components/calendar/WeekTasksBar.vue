<script setup lang="ts">
import { Check } from 'lucide-vue-next'
import UserAvatar from '@/components/common/UserAvatar.vue'
import type { Occurrence } from '@/types'

/** The week's main tasks: they belong to the whole week, not to its last day. */
defineProps<{ items: readonly Occurrence[] }>()
defineEmits<{ open: [item: Occurrence] }>()
</script>

<template>
  <div v-if="items.length" class="flex flex-wrap gap-1 bg-accent-soft/60 p-1.5" data-week-tasks>
    <button
      v-for="item in items"
      :key="item.id"
      type="button"
      class="flex max-w-full min-w-0 items-center gap-1.5 rounded-md bg-surface px-1.5 py-1 text-xs hover:brightness-95 dark:hover:brightness-125"
      :class="item.status === 'COMPLETED' && 'text-muted'"
      @click="$emit('open', item)"
    >
      <Check v-if="item.status === 'COMPLETED'" class="size-3 shrink-0 text-accent-text" aria-hidden="true" />
      <span class="truncate" :class="item.status === 'COMPLETED' && 'line-through'">{{ item.title }}</span>
      <template v-if="item.assignee">
        <UserAvatar :name="item.assignee.displayName" :color="item.assignee.avatarColor" size="xs" />
        <span class="sr-only">{{ item.assignee.displayName }}</span>
      </template>
    </button>
  </div>
</template>
