<script setup lang="ts">
import { MoreHorizontal } from 'lucide-vue-next'
import { nextTick, onUnmounted, ref, watch } from 'vue'
import IconButton from './IconButton.vue'

export interface MenuItem {
  label: string
  action: () => void
  danger?: boolean
}

defineProps<{ items: MenuItem[]; label: string }>()
const open = ref(false)
const root = ref<HTMLElement>()
const menu = ref<HTMLElement>()

function onDocumentClick(event: MouseEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}

function buttons() {
  return [...(menu.value?.querySelectorAll<HTMLElement>('button') ?? [])]
}

function moveFocus(step: number) {
  const list = buttons()
  const index = list.indexOf(document.activeElement as HTMLElement)
  list[(index + step + list.length) % list.length]?.focus()
}

function closeAndRefocus() {
  open.value = false
  root.value?.querySelector<HTMLElement>('button')?.focus()
}

watch(open, async (isOpen) => {
  if (isOpen) {
    document.addEventListener('click', onDocumentClick)
    await nextTick()
    buttons()[0]?.focus()
  } else {
    document.removeEventListener('click', onDocumentClick)
  }
})
onUnmounted(() => document.removeEventListener('click', onDocumentClick))

function choose(item: MenuItem) {
  open.value = false
  item.action()
}
</script>

<template>
  <div ref="root" class="row-end relative inline-flex" @keydown.esc.stop="closeAndRefocus">
    <IconButton :label="label" aria-haspopup="menu" :aria-expanded="open" @click="open = !open">
      <MoreHorizontal class="size-5" aria-hidden="true" />
    </IconButton>
    <div
      v-if="open"
      ref="menu"
      role="menu"
      class="card absolute top-full right-0 z-30 mt-1 min-w-52 p-1 shadow-lg"
      @keydown.down.prevent="moveFocus(1)"
      @keydown.up.prevent="moveFocus(-1)"
    >
      <button
        v-for="item in items"
        :key="item.label"
        type="button"
        role="menuitem"
        class="tap flex w-full items-center rounded-lg px-3 text-left text-sm hover:bg-subtle"
        :class="item.danger && 'text-danger'"
        @click="choose(item)"
      >
        {{ item.label }}
      </button>
    </div>
  </div>
</template>
