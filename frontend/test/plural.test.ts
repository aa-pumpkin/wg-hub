import { describe, expect, it } from 'vitest'
import { russianPlural } from '../src/lib/plural'

describe('russianPlural', () => {
  it.each([
    [1, 0],
    [21, 0],
    [101, 0],
    [2, 1],
    [4, 1],
    [22, 1],
    [5, 2],
    [11, 2],
    [12, 2],
    [14, 2],
    [100, 2],
    [111, 2],
    [0, 2],
  ])('uses the right form for %i', (count, form) => {
    expect(russianPlural(count, 3)).toBe(form)
  })

  it('reserves the first of four forms for zero', () => {
    expect(russianPlural(0, 4)).toBe(0)
    expect(russianPlural(1, 4)).toBe(1)
    expect(russianPlural(3, 4)).toBe(2)
    expect(russianPlural(7, 4)).toBe(3)
  })
})
