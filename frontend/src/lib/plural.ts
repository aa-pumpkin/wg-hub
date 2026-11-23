/**
 * Russian has three plural forms (1 задача, 2 задачи, 5 задач). A message with four forms
 * has its first form reserved for zero.
 */
export function russianPlural(choice: number, choicesLength: number): number {
  const n = Math.abs(choice)
  const last = n % 10
  const lastTwo = n % 100
  let form = 2
  if (last === 1 && lastTwo !== 11) form = 0
  else if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) form = 1
  if (choicesLength === 4) return n === 0 ? 0 : form + 1
  return Math.min(form, choicesLength - 1)
}
