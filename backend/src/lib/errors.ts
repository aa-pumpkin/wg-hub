/**
 * An expected failure. `code` is stable and is translated by the frontend,
 * so the API never returns user-facing text.
 */
export class AppError extends Error {
  constructor(
    readonly statusCode: number,
    readonly code: string,
  ) {
    super(code)
  }
}

export const unauthorized = () => new AppError(401, 'unauthorized')
export const forbidden = () => new AppError(403, 'forbidden')
export const notFound = () => new AppError(404, 'not_found')
export const conflict = (code: string) => new AppError(409, code)
export const badRequest = (code: string) => new AppError(400, code)
