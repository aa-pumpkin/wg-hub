import type { FastifyRequest } from 'fastify'
import { env } from '../config/env.js'
import { forbidden } from '../lib/errors.js'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/**
 * CSRF protection for the cookie session: SameSite=Lax already stops cross-site cookies
 * on writes, and this rejects any write whose Origin is not the app itself. Browsers
 * always send Origin on cross-site POST/PATCH/DELETE; non-browser clients have no
 * ambient cookies to abuse. The API also only parses JSON bodies, so plain HTML forms
 * cannot reach it.
 */
export async function csrfProtection(request: FastifyRequest) {
  if (SAFE_METHODS.has(request.method)) return
  const origin = request.headers.origin
  if (!origin) return
  if (env.APP_ORIGIN && origin === env.APP_ORIGIN) return
  let originHost: string
  try {
    originHost = new URL(origin).host
  } catch {
    throw forbidden()
  }
  if (originHost !== request.headers.host) throw forbidden()
}
