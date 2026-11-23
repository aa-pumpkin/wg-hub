<script setup lang="ts">
import { computed } from 'vue'
import { useUiStore } from '@/stores/ui'
import AppButton from './AppButton.vue'
import AppModal from './AppModal.vue'

const ui = useUiStore()
const open = computed({
  get: () => ui.confirmation !== null,
  set: (value) => {
    if (!value) ui.answerConfirmation(false)
  },
})
</script>

<template>
  <AppModal v-model:open="open" :title="ui.confirmation?.title ?? ''">
    <p v-if="ui.confirmation?.message" class="text-sm text-muted">{{ ui.confirmation.message }}</p>
    <div class="mt-5 flex justify-end gap-2">
      <AppButton @click="ui.answerConfirmation(false)">{{ $t('common.cancel') }}</AppButton>
      <AppButton
        :variant="ui.confirmation?.danger ? 'danger' : 'primary'"
        autofocus
        @click="ui.answerConfirmation(true)"
      >
        {{ ui.confirmation?.confirmLabel }}
      </AppButton>
    </div>
  </AppModal>
</template>
