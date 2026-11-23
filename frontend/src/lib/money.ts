/**
 * Reads a euro amount typed by a person ("12,50", "12.5", "1.234,56", "1,234.56") into
 * integer cents, working on the digits only so no floating-point error can sneak in.
 * Returns null if the text is not a valid amount.
 */
export function parseEurosToCents(input: string): number | null {
  const text = input.trim().replace(/\s|€/g, '')
  if (!text) return null

  // The last separator followed by 1–2 digits is the decimal point. Any other is grouping.
  const match = /^(\d[\d.,]*?)(?:([.,])(\d{1,2}))?$/.exec(text)
  if (!match) return null
  const whole = match[1]!
  const decimalMark = match[2]
  const fraction = match[3] ?? ''
  if (/[.,]/.test(whole) && !/^\d{1,3}([.,]\d{3})+$/.test(whole)) return null
  if (decimalMark && whole.includes(decimalMark)) return null

  const euros = Number(whole.replace(/[.,]/g, ''))
  const cents = Number(fraction.padEnd(2, '0'))
  const total = euros * 100 + cents
  return Number.isSafeInteger(total) ? total : null
}

/** Cents as an editable input value in the given locale, without currency sign. */
export function centsToInput(cents: number, locale: string): string {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    useGrouping: false,
  }).format(cents / 100)
}

export function formatCents(cents: number, locale: string): string {
  return new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' }).format(cents / 100)
}
