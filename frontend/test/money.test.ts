import { describe, expect, it } from 'vitest'
import { centsToInput, parseEurosToCents } from '../src/lib/money'

describe('parseEurosToCents', () => {
  it.each([
    ['12,50', 1250],
    ['12.50', 1250],
    ['12,5', 1250],
    ['12', 1200],
    ['0,01', 1],
    ['1.234,56', 123456],
    ['1,234.56', 123456],
    ['1.234', 123400],
    ['12,50 €', 1250],
    // Values that go wrong with parseFloat(x) * 100.
    ['19,99', 1999],
    ['1,15', 115],
    ['4,35', 435],
  ])('reads %s', (input, expected) => {
    expect(parseEurosToCents(input)).toBe(expected)
  })

  it.each(['', 'abc', '12,345,6', '-5', '1,2,3', '1.2.3'])('rejects %s', (input) => {
    expect(parseEurosToCents(input)).toBeNull()
  })
})

describe('centsToInput', () => {
  it('round-trips', () => {
    expect(centsToInput(1250, 'de')).toBe('12,50')
    expect(parseEurosToCents(centsToInput(123456, 'de'))).toBe(123456)
  })
})
