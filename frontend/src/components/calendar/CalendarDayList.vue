<script setup lang="ts">
import type { CalendarItem } from '@/lib/calendar'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { GROUP_STYLE } from './groupColors'

defineProps<{ items: readonly CalendarItem[] }>()
defineEmits<{ open: [item: CalendarItem] }>()
</script>

<template>
  <ul class="divide-y divide-line">
    <li v-for="item in items" :key="item.key">
      <button
        type="button"
        class="list-row w-full text-left hover:bg-subtle"
        @click="$emit('open', item)"
      >
        <span class="size-2 shrink-0 rounded-full" :class="GROUP_STYLE[item.group].dot" aria-hidden="true" />
        <span v-if="item.time" class="text-[13px] text-muted tabular-nums">{{ item.time }}</span>
        <span class="min-w-0 flex-1 truncate text-sm" :class="item.done && 'text-muted line-through'">
          {{ item.title }}
        </span>
        <UserAvatar
          v-if="item.occurrence?.assignee"
          :name="item.occurrence.assignee.displayName"
          :color="item.occurrence.assignee.avatarColor"
          size="sm"
        />
      </button>
    </li>
  </ul>
</template>
