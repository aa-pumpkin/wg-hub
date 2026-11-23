// Copies the website's translations into the app, so both always say the same thing.
// Run by the Gradle build; the output is generated, do not edit it by hand.
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const locales = resolve(here, '../../frontend/src/locales')
const out = resolve(here, '../shared/src/commonMain/composeResources/files/texts.json')

function flatten(object, prefix, into) {
  for (const [key, value] of Object.entries(object)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (value && typeof value === 'object') flatten(value, path, into)
    else into[path] = String(value)
  }
  return into
}

const texts = {}
for (const locale of ['de', 'en', 'ru']) {
  const module = await import(`${locales}/${locale}.ts`)
  texts[locale] = flatten(module.default, '', {})
}
mkdirSync(dirname(out), { recursive: true })
writeFileSync(out, JSON.stringify(texts))
