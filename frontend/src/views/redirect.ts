/** Only follows redirects within the app, so a crafted link cannot send people elsewhere. */
export function safeRedirect(target: unknown): string {
  return typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')
    ? target
    : '/'
}
