<script setup lang="ts">
import { X } from 'lucide-vue-next'
import { nextTick, onMounted, ref, watch } from 'vue'
import IconButton from './IconButton.vue'

/** Built on native <dialog>: focus trap, Esc and top layer come from the browser. */
defineProps<{ title: string }>()
const open = defineModel<boolean>('open', { required: true })
const dialog = ref<HTMLDialogElement>()

async function sync(isOpen: boolean) {
  await nextTick()
  const element = dialog.value
  if (!element) return
  if (isOpen && !element.open) {
    element.showModal()
    element.querySelector<HTMLElement>('[autofocus]')?.focus()
  } else if (!isOpen && element.open) {
    element.close()
  }
}

watch(open, sync)
onMounted(() => sync(open.value))

function closeOnBackdrop(event: MouseEvent) {
  if (event.target === dialog.value) open.value = false
}
</script>

<template>
  <dialog
    ref="dialog"
    class="modal"
    :aria-label="title"
    @close="open = false"
    @mousedown="closeOnBackdrop"
  >
    <template v-if="open">
      <header class="flex items-center justify-between gap-3 px-5 pt-4 pb-2">
        <h2 class="text-base font-semibold">{{ title }}</h2>
        <IconButton :label="$t('common.close')" class="-mr-2" @click="open = false">
          <X class="size-5" aria-hidden="true" />
        </IconButton>
      </header>
      <div class="flex-1 overflow-y-auto px-5 pt-1 pb-5">
        <slot />
      </div>
    </template>
  </dialog>
</template>
