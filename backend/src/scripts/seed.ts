/**
 * Demo data for local development: one WG with three people (password: demo12345).
 * Refuses to run twice, so it cannot pile up on top of real data.
 */
import type { Prisma } from '@prisma/client'
import { addDays, dateToDb, todayIn, wallClockToInstant } from '../lib/dates.js'
import { prisma } from '../lib/prisma.js'
import { DEFAULT_POINTS } from '../modules/chores/routes.js'
import { sundayOf } from '../modules/chores/recurrence.js'
import { generateOccurrences, DEFAULT_HORIZON_DAYS } from '../modules/chores/service.js'
import { splitEqually } from '../modules/expenses/split.js'
import { hashPassword } from '../modules/users/service.js'

const TIMEZONE = 'Europe/Berlin'

if (await prisma.user.findUnique({ where: { username: 'anna' } })) {
  console.log('Demo data already exists.')
  await prisma.$disconnect()
  process.exit(0)
}

const passwordHash = await hashPassword('demo12345')
const makeUser = (name: string, avatarColor: string) =>
  prisma.user.create({
    data: { username: name.toLowerCase(), displayName: name, avatarColor, passwordHash },
  })
const anna = await makeUser('Anna', 'rose')
const max = await makeUser('Max', 'sky')
const lisa = await makeUser('Lisa', 'amber')
const everyone = [anna.id, max.id, lisa.id]

const household = await prisma.household.create({
  data: {
    name: 'WG Sonnenallee',
    address: 'Sonnenallee 42, 12045 Berlin',
    icon: '🌻',
    timezone: TIMEZONE,
    members: {
      create: [
        { userId: anna.id, role: 'ADMIN', rotationPosition: 0 },
        { userId: max.id, role: 'MEMBER', rotationPosition: 1 },
        { userId: lisa.id, role: 'MEMBER', rotationPosition: 2 },
      ],
    },
  },
})
const householdId = household.id
// Turned on after everyone joined, so the first round counts for all of them.
await prisma.household.update({
  where: { id: householdId },
  data: { dishwasherTarget: 20, dishwasherCycleStart: new Date() },
})
const today = todayIn(TIMEZONE)

const mainTasks = [
  { title: 'Küche putzen', category: 'KITCHEN' },
  { title: 'Bad putzen', category: 'BATHROOM' },
  { title: 'Müll rausbringen', category: 'TRASH' },
] as const
for (const [position, task] of mainTasks.entries()) {
  const created = await prisma.chore.create({
    data: {
      ...task,
      kind: 'MAIN',
      points: DEFAULT_POINTS.MAIN,
      position,
      recurrence: 'WEEKLY',
      householdId,
      createdById: anna.id,
      startDate: dateToDb(sundayOf(today)),
    },
  })
  await generateOccurrences(created.id, TIMEZONE, addDays(today, DEFAULT_HORIZON_DAYS))
}

const extraTasks = [
  { title: 'Kühlschrank ausräumen', category: 'KITCHEN' },
  { title: 'Fenster putzen', category: 'OTHER' },
] as const
for (const task of extraTasks) {
  const created = await prisma.chore.create({
    data: {
      ...task,
      kind: 'EXTRA',
      points: DEFAULT_POINTS.EXTRA,
      recurrence: 'MONTHLY',
      householdId,
      createdById: anna.id,
      startDate: dateToDb(addDays(today, 3)),
      rotation: { create: everyone.map((userId, position) => ({ userId, position })) },
    },
  })
  await generateOccurrences(created.id, TIMEZONE, addDays(today, DEFAULT_HORIZON_DAYS))
}

const shoppingItems: Omit<Prisma.ShoppingItemCreateManyInput, 'householdId' | 'createdById'>[] = [
  { name: 'Milch', quantity: 2, unit: 'l' },
  { name: 'Eier', quantity: 10 },
  { name: 'Toilettenpapier', quantity: 1, unit: 'pack' },
  { name: 'Spülmittel', quantity: 1, note: 'Das ohne Duft, bitte' },
]
await prisma.shoppingItem.createMany({
  data: shoppingItems.map((item) => ({ ...item, householdId, createdById: max.id })),
})

const notes: Omit<Prisma.NoteCreateManyInput, 'householdId'>[] = [
  { content: 'Der Vermieter kommt am Freitag um 14 Uhr!', color: 'YELLOW', pinned: true, authorId: anna.id },
  { content: 'Bitte keine Schuhe im Flur stehen lassen :)', color: 'GREEN', authorId: lisa.id },
  { content: 'Ich bin übers Wochenende weg.', color: 'BLUE', authorId: max.id },
]
await prisma.note.createMany({ data: notes.map((note) => ({ ...note, householdId })) })

await prisma.calendarEvent.create({
  data: {
    householdId,
    createdById: lisa.id,
    title: 'WG-Abendessen',
    category: 'WG',
    startsAt: wallClockToInstant(`${addDays(today, 2)}T19:00`, TIMEZONE)!,
    participants: { create: everyone.map((userId) => ({ userId })) },
  },
})
await prisma.calendarEvent.create({
  data: {
    householdId,
    createdById: anna.id,
    title: 'Handwerker: Heizung',
    category: 'OTHER',
    startsAt: wallClockToInstant(`${addDays(today, 5)}T10:00`, TIMEZONE)!,
  },
})

await prisma.expense.create({
  data: {
    householdId,
    description: 'Putzmittel',
    amountCents: 1800,
    paidById: max.id,
    createdById: max.id,
    date: dateToDb(today),
    participants: { create: splitEqually(1800, everyone) },
  },
})

console.log('Demo data created. Log in as anna, max or lisa / demo12345')
await prisma.$disconnect()
