import { z } from 'zod'

/** Parses input and throws ZodError, which the central error handler turns into a 400. */
export function parse<S extends z.ZodType>(schema: S, input: unknown): z.output<S> {
  return schema.parse(input)
}

export const idParam = z.object({ id: z.string().min(1).max(64) })

export const isoDate = z.iso.date()
export const shortText = (max = 120) => z.string().trim().min(1).max(max)

/**
 * Optional free text: an empty string clears the field. Absent stays undefined, so a
 * partial update leaves the stored value alone.
 */
export const optionalText = (max = 2000) =>
  z
    .string()
    .trim()
    .max(max)
    .nullish()
    .transform((value) => (value === undefined ? undefined : value || null))

export const idList = z
  .array(z.string().min(1).max(64))
  .max(100)
  .refine((ids) => new Set(ids).size === ids.length, { message: 'duplicate' })
