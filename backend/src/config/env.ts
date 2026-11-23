import { z } from 'zod'

const schema = z.object({
  DATABASE_URL: z.string().min(1),
  PORT: z.coerce.number().int().positive().default(3000),
  LOG_LEVEL: z.string().default('info'),
  /** Extra allowed origin, for proxies that rewrite the Host header. */
  APP_ORIGIN: z.string().optional(),
  RATE_LIMIT_AUTH_MAX: z.coerce.number().int().positive().default(20),
})

export const env = schema.parse(process.env)
