<script setup lang="ts">
import type { CalendarItem } from '@/lib/calendar'
import { GROUP_STYLE } from './groupColors'

defineProps<{ item: CalendarItem }>()
defineEmits<{ open: [] }>()
</script>

<template>
  <button
    type="button"
    class="block w-full truncate rounded-md px-1.5 py-0.5 text-left text-xs hover:brightness-95 dark:hover:brightness-125"
    :class="[GROUP_STYLE[item.group].pill, item.done && 'line-through opacity-60']"
    @click.stop="$emit('open')"
  >
    <span v-if="item.time" class="mr-1 tabular-nums opacity-70">{{ item.time }}</span>{{ item.title }}
  </button>
</template>
