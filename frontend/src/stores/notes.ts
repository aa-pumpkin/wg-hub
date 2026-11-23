import { defineStore } from 'pinia'
import { ref } from 'vue'
import { noteService } from '@/services/modules'
import type { Note } from '@/types'

export const useNoteStore = defineStore('notes', () => {
  const notes = ref<Note[]>([])

  async function load(householdId: string, archived: boolean) {
    notes.value = await noteService.list(householdId, archived)
  }

  return { notes, load }
})
