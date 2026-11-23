import type { FastifyInstance } from 'fastify'
import { DateTime } from 'luxon'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { buildApp } from '../src/app.js'
import { prisma } from '../src/lib/prisma.js'
import { createHousehold, registerUser, send } from './helpers.js'

let app: FastifyInstance

beforeAll(async () => {
  app = await buildApp()
})
afterAll(async () => {
  await app.close()
  await prisma.$disconnect()
})

const berlinDate = (plusDays = 0) =>
  DateTime.now().setZone('Europe/Berlin').plus({ days: plusDays }).toISODate()!

describe('auth', () => {
  it('registers, logs in, reads /me and logs out', async () => {
    const user = await registerUser(app, 'Anna')
    expect((await user.request('GET', '/api/auth/me')).json.user.displayName).toBe('Anna')

    const login = await send(app, 'POST', '/api/auth/login', {
      username: user.username,
      password: 'correct horse battery',
    })
    expect(login.status).toBe(200)
    expect(String(login.cookie)).toMatch(/HttpOnly/i)
    expect(String(login.cookie)).toMatch(/SameSite=Lax/i)

    expect((await user.request('POST', '/api/auth/logout')).status).toBe(204)
    expect((await user.request('GET', '/api/auth/me')).status).toBe(401)
  })

  it('stores Argon2id hashes and never returns them', async () => {
    const user = await registerUser(app)
    const row = await prisma.user.findUniqueOrThrow({ where: { id: user.id } })
    expect(row.passwordHash).toMatch(/^\$argon2id\$/)
    expect(JSON.stringify((await user.request('GET', '/api/auth/me')).json)).not.toContain('argon2')
  })

  it('rejects wrong passwords and duplicate logins', async () => {
    const user = await registerUser(app)
    const wrong = await send(app, 'POST', '/api/auth/login', { username: user.username, password: 'nope' })
    expect(wrong.status).toBe(401)
    const duplicate = await send(app, 'POST', '/api/auth/register', {
      username: user.username.toUpperCase(),
      displayName: 'X',
      password: 'correct horse battery',
    })
    expect(duplicate.status).toBe(409)
    expect(duplicate.json.error).toBe('username_taken')
  })

  it('reports validation errors per field', async () => {
    const response = await send(app, 'POST', '/api/auth/register', {
      username: 'a b',
      displayName: '',
      password: 'short',
    })
    expect(response.status).toBe(400)
    expect(Object.keys(response.json.fields).sort()).toEqual(['displayName', 'password', 'username'])
  })

  it('changing the password logs out other sessions', async () => {
    const user = await registerUser(app)
    const other = await send(app, 'POST', '/api/auth/login', {
      username: user.username,
      password: 'correct horse battery',
    })
    const otherCookie = String(other.cookie).split(';')[0]!
    const changed = await user.request('POST', '/api/me/password', {
      currentPassword: 'correct horse battery',
      newPassword: 'a new long password',
    })
    expect(changed.status).toBe(204)
    expect((await user.request('GET', '/api/auth/me')).status).toBe(200)
    const stale = await send(app, 'GET', '/api/auth/me', undefined, { cookie: otherCookie })
    expect(stale.status).toBe(401)
  })

  it('blocks cross-site writes and allows same-origin ones', async () => {
    const user = await registerUser(app)
    const evil = await send(app, 'POST', '/api/households', { name: 'x' }, {
      cookie: user.cookie,
      origin: 'https://evil.example',
      host: 'wg.example',
    })
    expect(evil.status).toBe(403)
    const same = await send(app, 'POST', '/api/households', { name: 'x' }, {
      cookie: user.cookie,
      origin: 'https://wg.example',
      host: 'wg.example',
    })
    expect(same.status).toBe(201)
  })
})

describe('password reset link', () => {
  it('lets an admin help a member back in, once', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const { json, status } = await admin.request('POST', `${base}/members/${max.id}/password-reset`)
    expect(status).toBe(201)

    const reset = await send(app, 'POST', '/api/auth/reset', {
      token: json.token,
      newPassword: 'a brand new password',
    })
    expect(reset.status).toBe(200)
    expect(reset.json.user.id).toBe(max.id)

    // Old sessions and the old password are gone, the new one works on any device.
    expect((await max.request('GET', '/api/auth/me')).status).toBe(401)
    const login = await send(app, 'POST', '/api/auth/login', {
      username: max.username,
      password: 'a brand new password',
    })
    expect(login.status).toBe(200)

    const again = await send(app, 'POST', '/api/auth/reset', {
      token: json.token,
      newPassword: 'another new password',
    })
    expect(again.status).toBe(410)
  })

  it('is only for admins, and only for members of their own WG', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const other = await registerUser(app)
    expect((await members[0]!.request('POST', `${base}/members/${admin.id}/password-reset`)).status).toBe(403)
    expect((await admin.request('POST', `${base}/members/${other.id}/password-reset`)).status).toBe(404)
  })

  it('rejects expired links', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const { json } = await admin.request('POST', `${base}/members/${members[0]!.id}/password-reset`)
    await prisma.passwordResetToken.updateMany({
      where: { userId: members[0]!.id },
      data: { expiresAt: new Date(Date.now() - 1000) },
    })
    const reset = await send(app, 'POST', '/api/auth/reset', {
      token: json.token,
      newPassword: 'a brand new password',
    })
    expect(reset.status).toBe(410)
  })
})

describe('main task circle', () => {
  const plan = async (client: { request: Function }, base: string) =>
    (await client.request('GET', `${base}/chore-plan`)).json

  async function setup() {
    const house = await createHousehold(app, ['Max', 'Lisa'])
    const [max, lisa] = house.members as [typeof house.admin, typeof house.admin]
    for (const title of ['Küche', 'Bad', 'Müll']) {
      const made = await house.admin.request('POST', `${house.base}/chores`, { kind: 'MAIN', title, startDate: berlinDate() })
      expect(made.status).toBe(201)
    }
    return { ...house, max, lisa, people: [house.admin.id, max.id, lisa.id] }
  }
  const row = (week: { items: { title: string; assignee: { id: string } | null }[] }) =>
    week.items.map((item) => item.assignee?.id)

  it('gives everyone one task and moves it along clockwise every week', async () => {
    const { admin, base, people } = await setup()
    const { weeks } = await plan(admin, base)
    expect(weeks.length).toBe(4)
    expect(weeks[0].items.map((i: { title: string }) => i.title)).toEqual(['Küche', 'Bad', 'Müll'])
    const [a, m, l] = people
    expect(row(weeks[0])).toEqual([a, m, l])
    expect(row(weeks[1])).toEqual([l, a, m])
    expect(row(weeks[2])).toEqual([m, l, a])
    expect(row(weeks[3])).toEqual([a, m, l])
  })

  it('stays due at the end of the week, on a Sunday', async () => {
    const { admin, base } = await setup()
    const { weeks } = await plan(admin, base)
    expect(DateTime.fromISO(weeks[0].dueDate).weekday).toBe(7)
  })

  it('lets only admins create or edit main tasks', async () => {
    const { max, base, admin } = await setup()
    const made = await max.request('POST', `${base}/chores`, { kind: 'MAIN', title: 'x', startDate: berlinDate() })
    expect(made.status).toBe(403)
    const { chores } = (await admin.request('GET', `${base}/chores`)).json
    expect((await max.request('PATCH', `${base}/chores/${chores[0].id}`, { title: 'x' })).status).toBe(403)
    expect((await admin.request('PATCH', `${base}/chores/${chores[0].id}`, { title: 'Küche!', points: 25 })).status).toBe(200)
    expect((await max.request('PUT', `${base}/chore-plan/circle`, { userIds: [max.id] })).status).toBe(403)
    expect((await max.request('POST', `${base}/chore-plan/shift`, { weeks: 1 })).status).toBe(403)
  })

  it('follows the order the admin sets', async () => {
    const { admin, base, people } = await setup()
    const [a, m, l] = people
    await admin.request('PUT', `${base}/chore-plan/circle`, { userIds: [l, m, a] })
    expect(row((await plan(admin, base)).weeks[0])).toEqual([l, m, a])
    expect((await admin.request('PUT', `${base}/chore-plan/circle`, { userIds: [a, m] })).status).toBe(400)
  })

  describe('freezing a member', () => {
    const freeze = (client: { request: Function }, base: string, id: string, frozen = true) =>
      client.request('POST', `${base}/members/${id}/freeze`, { frozen })

    it('moves only their tasks this week and plans ahead without them', async () => {
      const { admin, base, people } = await setup()
      const [a, m, l] = people
      expect((await freeze(admin, base, l!)).status).toBe(200)
      const { weeks, circle } = await plan(admin, base)
      const thisWeek = row(weeks[0])
      expect(thisWeek[0]).toBe(a)
      expect(thisWeek[1]).toBe(m)
      expect(thisWeek[2]).not.toBe(l)
      expect(thisWeek[2]).toBeTruthy()
      expect(weeks.slice(1).flatMap(row)).not.toContain(l)
      expect(circle.find((c: { user: { id: string } }) => c.user.id === l).frozen).toBe(true)
      const members = (await admin.request('GET', base)).json.members
      expect(members.find((x: { id: string }) => x.id === l).frozen).toBe(true)
    })

    it('brings them back to their seat from next week', async () => {
      const { admin, base, people } = await setup()
      const l = people[2]!
      const before = (await plan(admin, base)).weeks
      await freeze(admin, base, l)
      const frozenWeek = row((await plan(admin, base)).weeks[0])
      await freeze(admin, base, l, false)
      const after = (await plan(admin, base)).weeks
      expect(row(after[0])).toEqual(frozenWeek)
      expect(row(after[1])).toEqual(row(before[1]))
      expect(row(after[2])).toEqual(row(before[2]))
    })

    it('hands their tasks to whoever has the least to do', async () => {
      const { admin, base, people } = await setup()
      await admin.request('POST', `${base}/chores`, { kind: 'MAIN', title: 'Flur', startDate: berlinDate() })
      const l = people[2]!
      await freeze(admin, base, l)
      const counts = new Map<string, number>()
      for (const id of row((await plan(admin, base)).weeks[0])) counts.set(id as string, (counts.get(id as string) ?? 0) + 1)
      expect([...counts.values()].sort()).toEqual([2, 2])
    })

    it('keeps them out of extra tasks too, and frees their overdue work', async () => {
      const { admin, max, base, people } = await setup()
      const extra = await admin.request('POST', `${base}/chores`, {
        title: 'Fenster',
        recurrence: 'WEEKLY',
        startDate: berlinDate(1),
        rotation: people,
      })
      const late = await admin.request('POST', `${base}/chores`, {
        title: 'Alt',
        startDate: berlinDate(-3),
        rotation: [max.id],
      })
      await freeze(admin, base, max.id)
      const open = (await admin.request('GET', `${base}/chore-occurrences?choreId=${extra.json.id}&status=PENDING`)).json.occurrences
      expect(open.length).toBeGreaterThan(0)
      expect(open.every((o: { assignee: { id: string } }) => o.assignee.id !== max.id)).toBe(true)
      const old = (await admin.request('GET', `${base}/chore-occurrences?choreId=${late.json.id}`)).json.occurrences
      expect(old[0].assignee).toBeNull()

      await freeze(admin, base, max.id, false)
      const back = (await admin.request('GET', `${base}/chore-occurrences?choreId=${extra.json.id}&status=PENDING`)).json.occurrences
      expect(back.some((o: { assignee: { id: string } }) => o.assignee.id === max.id)).toBe(true)
    })

    it('lets a frozen person still use the app, and keeps finished work', async () => {
      const { admin, max, base } = await setup()
      const mine = (await plan(admin, base)).weeks[0].items.find(
        (i: { assignee: { id: string } }) => i.assignee.id === max.id,
      )
      await max.request('POST', `${base}/chore-occurrences/${mine.id}/complete`)
      await freeze(admin, base, max.id)
      expect((await max.request('POST', `${base}/shopping`, { name: 'Milch' })).status).toBe(201)
      const stillDone = (await plan(admin, base)).weeks[0].items.find((i: { id: string }) => i.id === mine.id)
      expect(stillDone.status).toBe('COMPLETED')
      expect(stillDone.assignee.id).toBe(max.id)
    })

    it('anyone can freeze themselves, and only admins can freeze others', async () => {
      const { admin, max, lisa, base } = await setup()
      const other = await registerUser(app)
      expect((await freeze(max, base, max.id)).status).toBe(200)
      expect((await freeze(max, base, max.id, false)).status).toBe(200)
      expect((await freeze(max, base, lisa.id)).status).toBe(403)
      expect((await freeze(max, base, admin.id)).status).toBe(403)
      expect((await freeze(admin, base, other.id)).status).toBe(404)
    })

    it('does nothing when frozen twice', async () => {
      const { admin, max, base } = await setup()
      await freeze(admin, base, max.id)
      const once = row((await plan(admin, base)).weeks[0])
      await freeze(admin, base, max.id)
      expect(row((await plan(admin, base)).weeks[0])).toEqual(once)
    })
  })

  it('shifts the whole circle by one step', async () => {
    const { admin, base, people } = await setup()
    const [a, m, l] = people
    await admin.request('POST', `${base}/chore-plan/shift`, { weeks: 1 })
    expect(row((await plan(admin, base)).weeks[0])).toEqual([l, a, m])
    await admin.request('POST', `${base}/chore-plan/shift`, { weeks: -1 })
    expect(row((await plan(admin, base)).weeks[0])).toEqual([a, m, l])
  })

  it('brings a new member in from next week, leaving this week as announced', async () => {
    const { admin, base, people } = await setup()
    const before = (await plan(admin, base)).weeks
    const invite = await admin.request('POST', `${base}/invitations`, { expiresInDays: 1 })
    const newbie = await registerUser(app, 'Neu')
    await newbie.request('POST', `/api/invitations/${invite.json.invitation.code}/accept`)
    const after = (await plan(admin, base)).weeks
    expect(row(after[0])).toEqual(row(before[0]))
    expect(after.slice(1).flatMap(row)).toContain(newbie.id)
    expect(people).toHaveLength(3)
  })

  it('takes this week away from someone who leaves', async () => {
    const { admin, max, base } = await setup()
    await admin.request('DELETE', `${base}/members/${max.id}`)
    const { weeks } = await plan(admin, base)
    expect(weeks[0].items.every((i: { assignee: { id: string } | null }) => i.assignee && i.assignee.id !== max.id)).toBe(true)
  })

  it('keeps finished work when the plan changes', async () => {
    const { admin, base, people } = await setup()
    const first = (await plan(admin, base)).weeks[0].items[0]
    await admin.request('POST', `${base}/chore-occurrences/${first.id}/complete`)
    await admin.request('POST', `${base}/chore-plan/shift`, { weeks: 1 })
    const again = (await plan(admin, base)).weeks[0].items[0]
    expect(again.id).toBe(first.id)
    expect(again.status).toBe('COMPLETED')
    expect(again.assignee.id).toBe(people[0])
  })

  it('does not double up on a week when task titles change', async () => {
    const { admin, base } = await setup()
    const { chores } = (await admin.request('GET', `${base}/chores`)).json
    await admin.request('PATCH', `${base}/chores/${chores[0].id}`, { title: 'Neu', recurrence: 'DAILY' })
    const { weeks } = await plan(admin, base)
    expect(weeks[0].items).toHaveLength(3)
    const after = (await admin.request('GET', `${base}/chores`)).json.chores[0]
    expect(after.recurrence).toBe('WEEKLY')
  })

  it('shows this week on the dashboard from Monday on', async () => {
    const { admin, base } = await setup()
    const { json } = await admin.request('GET', `${base}/dashboard`)
    expect(json.tasks.filter((t: { kind: string }) => t.kind === 'MAIN')).toHaveLength(3)
    expect(json.upcomingTasks.every((t: { dueDate: string }) => t.dueDate > (json.tasks[0]?.dueDate ?? ''))).toBe(true)
  })
})

describe('points', () => {
  const board = async (client: { request: Function }, base: string) =>
    (await client.request('GET', `${base}/leaderboard`)).json.members

  it('awards points to whoever did it and takes them back on reopen', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    await admin.request('POST', `${base}/chores`, { title: 'x', startDate: berlinDate(), points: 30 })
    const [occurrence] = (await admin.request('GET', `${base}/chore-occurrences`)).json.occurrences

    const done = await max.request('POST', `${base}/chore-occurrences/${occurrence.id}/complete`)
    expect(done.json.earned).toBe(30)
    const top = (await board(admin, base))[0]
    expect(top.user.id).toBe(max.id)
    expect(top.points).toBe(30)
    expect(top.badges).toContain('first')

    const twice = await max.request('POST', `${base}/chore-occurrences/${occurrence.id}/complete`)
    expect(twice.json.earned).toBe(0)
    await max.request('POST', `${base}/chore-occurrences/${occurrence.id}/reopen`)
    expect((await board(admin, base)).every((m: { points: number }) => m.points === 0)).toBe(true)
  })

  it('gives half for late work', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('POST', `${base}/chores`, { title: 'x', startDate: berlinDate(-3), points: 15 })
    const [occurrence] = (await admin.request('GET', `${base}/chore-occurrences`)).json.occurrences
    const done = await admin.request('POST', `${base}/chore-occurrences/${occurrence.id}/complete`)
    expect(done.json.earned).toBe(8)
  })

  it('reports reaching a new level', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('POST', `${base}/chores`, { title: 'x', startDate: berlinDate(), points: 50 })
    const [occurrence] = (await admin.request('GET', `${base}/chore-occurrences`)).json.occurrences
    const done = await admin.request('POST', `${base}/chore-occurrences/${occurrence.id}/complete`)
    expect(done.json.leveledUp).toBe(true)
    expect(done.json.level).toBe(2)
  })

  it('does not let members hand themselves points', async () => {
    const { members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const made = await max.request('POST', `${base}/chores`, { title: 'x', startDate: berlinDate(), points: 999 })
    expect(made.status).toBe(201)
    const [occurrence] = (await max.request('GET', `${base}/chore-occurrences`)).json.occurrences
    const done = await max.request('POST', `${base}/chore-occurrences/${occurrence.id}/complete`)
    expect(done.json.earned).toBe(0)
    expect((await max.request('PATCH', `${base}/chores/${made.json.id}`, { points: 999 })).status).toBe(403)
  })

  it('blocks collecting points for weeks ahead', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('POST', `${base}/chores`, { title: 'x', recurrence: 'WEEKLY', startDate: berlinDate(), points: 10 })
    const list = (await admin.request('GET', `${base}/chore-occurrences`)).json.occurrences
    const far = list.find((o: { dueDate: string }) => o.dueDate >= berlinDate(7))
    const response = await admin.request('POST', `${base}/chore-occurrences/${far.id}/complete`)
    expect(response.json.error).toBe('too_early')
  })

  it('keeps points inside each WG', async () => {
    const a = await createHousehold(app)
    const b = await createHousehold(app)
    expect((await b.admin.request('GET', `${a.base}/leaderboard`)).status).toBe(404)
  })
})

describe('household isolation', () => {
  it('hides every resource of another household', async () => {
    const a = await createHousehold(app)
    const b = await createHousehold(app)

    // Outsider asks for household A's data directly.
    for (const path of [
      '',
      '/dashboard',
      '/chores',
      '/chore-occurrences',
      '/shopping',
      '/notes',
      '/expenses',
      '/balances',
      '/settlements',
      '/leaderboard',
      '/chore-plan',
      '/dishwasher',
      '/stream',
      '/calendar?from=2026-01-01&to=2026-01-31',
    ]) {
      const response = await b.admin.request('GET', `${a.base}${path}`)
      expect(response.status, path).toBe(404)
    }
    expect((await b.admin.request('PATCH', a.base, { name: 'hacked' })).status).toBe(404)
    expect((await b.admin.request('DELETE', a.base)).status).toBe(404)
    expect((await b.admin.request('GET', `${a.base}/invitations`)).status).toBe(404)
  })

  it('does not reach a resource of household A through the URL of household B', async () => {
    const a = await createHousehold(app)
    const b = await createHousehold(app)

    const item = await a.admin.request('POST', `${a.base}/shopping`, { name: 'Milch' })
    const note = await a.admin.request('POST', `${a.base}/notes`, { content: 'privat' })
    const event = await a.admin.request('POST', `${a.base}/events`, {
      title: 'x',
      startsAt: `${berlinDate(1)}T10:00`,
    })
    const chore = await a.admin.request('POST', `${a.base}/chores`, {
      title: 'x',
      startDate: berlinDate(),
    })
    const occurrences = await a.admin.request('GET', `${a.base}/chore-occurrences`)
    const occurrenceId = occurrences.json.occurrences[0].id
    const expense = await a.admin.request('POST', `${a.base}/expenses`, {
      description: 'x',
      amountCents: 100,
      paidById: a.admin.id,
      date: berlinDate(),
      participants: [{ userId: a.admin.id }],
    })

    const attempts: [string, string, unknown?][] = [
      ['PATCH', `/shopping/${item.json.item.id}`, { name: 'hacked' }],
      ['POST', `/shopping/${item.json.item.id}/status`, { status: 'BOUGHT' }],
      ['DELETE', `/shopping/${item.json.item.id}`],
      ['PATCH', `/notes/${note.json.note.id}`, { content: 'hacked' }],
      ['POST', `/notes/${note.json.note.id}/pin`, { pinned: true }],
      ['DELETE', `/notes/${note.json.note.id}`],
      ['DELETE', `/events/${event.json.event.id}`],
      ['DELETE', `/chores/${chore.json.id}`],
      ['POST', `/chore-occurrences/${occurrenceId}/complete`],
      ['DELETE', `/expenses/${expense.json.expense.id}`],
    ]
    for (const [method, path, body] of attempts) {
      const response = await b.admin.request(method as 'GET', `${b.base}${path}`, body)
      expect(response.status, `${method} ${path}`).toBe(404)
    }
    // Nothing changed.
    const notes = await a.admin.request('GET', `${a.base}/notes`)
    expect(notes.json.notes[0].content).toBe('privat')
  })

  it('rejects members of another household as assignees or payers', async () => {
    const a = await createHousehold(app)
    const b = await createHousehold(app)
    const chore = await a.admin.request('POST', `${a.base}/chores`, {
      title: 'x',
      startDate: berlinDate(),
      rotation: [b.admin.id],
    })
    expect(chore.status).toBe(404)
    const expense = await a.admin.request('POST', `${a.base}/expenses`, {
      description: 'x',
      amountCents: 100,
      paidById: b.admin.id,
      date: berlinDate(),
      participants: [{ userId: a.admin.id }],
    })
    expect(expense.status).toBe(404)
  })
})

describe('membership and roles', () => {
  it('lets a member join by invitation, once, and only while it is valid', async () => {
    const { admin, base, householdId } = await createHousehold(app)
    const invite = await admin.request('POST', `${base}/invitations`, { expiresInDays: 7 })
    const code = invite.json.invitation.code

    // Public preview works without login, accepting does not.
    const preview = await send(app, 'GET', `/api/invitations/${code}`)
    expect(preview.json.invitation.householdName).toBe('Test WG')
    expect((await send(app, 'POST', `/api/invitations/${code}/accept`)).status).toBe(401)

    const joiner = await registerUser(app)
    expect((await joiner.request('POST', `/api/invitations/${code}/accept`)).json.householdId).toBe(
      householdId,
    )
    await joiner.request('POST', `/api/invitations/${code}/accept`)
    const detail = await admin.request('GET', base)
    expect(detail.json.members).toHaveLength(2)

    // Revoked links stop working.
    await admin.request('DELETE', `${base}/invitations/${invite.json.invitation.id}`)
    const late = await registerUser(app)
    expect((await late.request('POST', `/api/invitations/${code}/accept`)).status).toBe(410)
    expect((await send(app, 'GET', `/api/invitations/${code}`)).status).toBe(410)
  })

  it('rejects expired invitations', async () => {
    const { admin, base } = await createHousehold(app)
    const invite = await admin.request('POST', `${base}/invitations`, { expiresInDays: 1 })
    await prisma.householdInvitation.update({
      where: { id: invite.json.invitation.id },
      data: { expiresAt: new Date(Date.now() - 1000) },
    })
    const late = await registerUser(app)
    const response = await late.request('POST', `/api/invitations/${invite.json.invitation.code}/accept`)
    expect(response.status).toBe(410)
  })

  it('keeps admin actions away from members', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const member = members[0]!
    expect((await member.request('PATCH', base, { name: 'x' })).status).toBe(403)
    expect((await member.request('DELETE', base)).status).toBe(403)
    expect((await member.request('POST', `${base}/invitations`, { expiresInDays: 1 })).status).toBe(403)
    expect((await member.request('DELETE', `${base}/members/${admin.id}`)).status).toBe(403)
    expect((await member.request('PATCH', `${base}/members/${member.id}`, { role: 'ADMIN' })).status).toBe(403)
  })

  it('always keeps one admin', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    expect((await admin.request('POST', `${base}/leave`)).json.error).toBe('last_admin')
    expect((await admin.request('PATCH', `${base}/members/${admin.id}`, { role: 'MEMBER' })).json.error).toBe('last_admin')

    await admin.request('PATCH', `${base}/members/${members[0]!.id}`, { role: 'ADMIN' })
    expect((await admin.request('POST', `${base}/leave`)).status).toBe(204)
  })

  it('removes a member and takes away their access immediately', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const member = members[0]!
    expect((await member.request('GET', base)).status).toBe(200)
    expect((await admin.request('DELETE', `${base}/members/${member.id}`)).status).toBe(204)
    expect((await member.request('GET', base)).status).toBe(404)
  })

  it('blocks leaving with an open balance', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const member = members[0]!
    await admin.request('POST', `${base}/expenses`, {
      description: 'x',
      amountCents: 1000,
      paidById: admin.id,
      date: berlinDate(),
      participants: [{ userId: admin.id }, { userId: member.id }],
    })
    expect((await member.request('POST', `${base}/leave`)).json.error).toBe('open_balance')
    await member.request('POST', `${base}/settlements`, {
      fromUserId: member.id,
      toUserId: admin.id,
      amountCents: 500,
      date: berlinDate(),
    })
    expect((await member.request('POST', `${base}/leave`)).status).toBe(204)
  })

  it('deletes a household when the last person leaves', async () => {
    const { admin, base, householdId } = await createHousehold(app)
    expect((await admin.request('POST', `${base}/leave`)).status).toBe(204)
    expect(await prisma.household.findUnique({ where: { id: householdId } })).toBeNull()
  })
})

describe('chores', () => {
  const pendingOf = async (client: { request: Function }, base: string, choreId: string) =>
    (await client.request('GET', `${base}/chore-occurrences?choreId=${choreId}&status=PENDING`)).json
      .occurrences as { id: string; dueDate: string; assignee: { id: string } | null }[]

  it('completing one occurrence leaves the series intact', async () => {
    const { admin, base } = await createHousehold(app)
    const { json } = await admin.request('POST', `${base}/chores`, {
      title: 'Bad putzen',
      recurrence: 'WEEKLY',
      startDate: berlinDate(),
      rotation: [admin.id],
    })
    const before = await pendingOf(admin, base, json.id)
    expect(before.length).toBeGreaterThan(5)

    const done = await admin.request('POST', `${base}/chore-occurrences/${before[0]!.id}/complete`)
    expect(done.json.occurrence.status).toBe('COMPLETED')
    expect(done.json.occurrence.completedBy.id).toBe(admin.id)

    const after = await pendingOf(admin, base, json.id)
    expect(after).toHaveLength(before.length - 1)
    expect(after[0]!.dueDate).toBe(before[1]!.dueDate)
  })

  it('completing twice creates one history entry, reopening removes it', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('POST', `${base}/chores`, { title: 'x', startDate: berlinDate() })
    const [occurrence] = (await admin.request('GET', `${base}/chore-occurrences`)).json.occurrences
    const url = `${base}/chore-occurrences/${occurrence.id}`
    await admin.request('POST', `${url}/complete`)
    expect((await admin.request('POST', `${url}/complete`)).status).toBe(200)
    expect(await prisma.choreCompletion.count({ where: { assignmentId: occurrence.id } })).toBe(1)

    await admin.request('POST', `${url}/reopen`)
    expect(await prisma.choreCompletion.count({ where: { assignmentId: occurrence.id } })).toBe(0)
  })

  it('rotates extra tasks through the selected people', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max', 'Lisa'])
    const people = [admin.id, members[0]!.id, members[1]!.id]
    const { json } = await admin.request('POST', `${base}/chores`, {
      title: 'Küche',
      recurrence: 'WEEKLY',
      startDate: berlinDate(),
      rotation: people,
    })
    const pending = await pendingOf(admin, base, json.id)
    expect(pending.slice(0, 6).map((o) => o.assignee!.id)).toEqual([...people, ...people])
  })

  it('starts several chores on different people, as in the spec example', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max', 'Lisa'])
    const people = [admin.id, members[0]!.id, members[1]!.id]
    const firstAssignees: string[] = []
    for (const title of ['Kitchen', 'Bathroom', 'Trash']) {
      const { json } = await admin.request('POST', `${base}/chores`, {
        title,
        recurrence: 'WEEKLY',
        startDate: berlinDate(),
        rotation: people,
      })
      firstAssignees.push((await pendingOf(admin, base, json.id))[0]!.assignee!.id)
    }
    expect(firstAssignees).toEqual(people)
  })

  it('hands over open work when someone leaves, and keeps finished work', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const { json } = await admin.request('POST', `${base}/chores`, {
      title: 'Küche',
      recurrence: 'WEEKLY',
      startDate: berlinDate(),
      rotation: [max.id, admin.id],
    })
    const pending = await pendingOf(admin, base, json.id)
    expect(pending[0]!.assignee!.id).toBe(max.id)
    await admin.request('POST', `${base}/chore-occurrences/${pending[0]!.id}/complete`)

    await admin.request('DELETE', `${base}/members/${max.id}`)

    const after = await pendingOf(admin, base, json.id)
    expect(after.length).toBeGreaterThan(0)
    expect(after.every((o) => o.assignee?.id === admin.id)).toBe(true)
    const done = await admin.request('GET', `${base}/chore-occurrences?choreId=${json.id}&status=COMPLETED`)
    expect(done.json.occurrences[0].assignee.id).toBe(max.id)
  })

  it('applies a changed rotation only to the future', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const { json } = await admin.request('POST', `${base}/chores`, {
      title: 'x',
      recurrence: 'WEEKLY',
      startDate: berlinDate(),
      rotation: [admin.id],
    })
    const before = await pendingOf(admin, base, json.id)
    await admin.request('POST', `${base}/chore-occurrences/${before[0]!.id}/complete`)
    await admin.request('PATCH', `${base}/chores/${json.id}`, { rotation: [max.id] })
    const after = await pendingOf(admin, base, json.id)
    expect(after.every((o) => o.assignee?.id === max.id)).toBe(true)
    expect(after[0]!.dueDate).toBe(before[1]!.dueDate)
  })

  it('ending a series removes open work and keeps the history', async () => {
    const { admin, base } = await createHousehold(app)
    const { json } = await admin.request('POST', `${base}/chores`, {
      title: 'x',
      recurrence: 'DAILY',
      startDate: berlinDate(),
    })
    const pending = await pendingOf(admin, base, json.id)
    await admin.request('POST', `${base}/chore-occurrences/${pending[0]!.id}/complete`)
    await admin.request('DELETE', `${base}/chores/${json.id}`)
    expect(await pendingOf(admin, base, json.id)).toHaveLength(0)
    const done = await admin.request('GET', `${base}/chore-occurrences?choreId=${json.id}&status=COMPLETED`)
    expect(done.json.occurrences).toHaveLength(1)
  })

  it('lets members manage only their own chores', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const own = await max.request('POST', `${base}/chores`, { title: 'x', startDate: berlinDate() })
    const foreign = await admin.request('POST', `${base}/chores`, { title: 'y', startDate: berlinDate() })
    expect((await max.request('PATCH', `${base}/chores/${own.json.id}`, { title: 'z' })).status).toBe(200)
    expect((await max.request('PATCH', `${base}/chores/${foreign.json.id}`, { title: 'z' })).status).toBe(403)
    expect((await admin.request('PATCH', `${base}/chores/${own.json.id}`, { title: 'w' })).status).toBe(200)
  })
})

describe('partial updates', () => {
  it('leave fields that were not sent untouched', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('PATCH', base, { address: 'Sonnenallee 42' })
    await admin.request('PATCH', base, { name: 'Neuer Name' })
    const detail = await admin.request('GET', base)
    expect(detail.json.household.name).toBe('Neuer Name')
    expect(detail.json.household.address).toBe('Sonnenallee 42')

    const chore = await admin.request('POST', `${base}/chores`, {
      title: 'x',
      description: 'genau so',
      startDate: berlinDate(),
    })
    await admin.request('PATCH', `${base}/chores/${chore.json.id}`, { title: 'y' })
    const chores = await admin.request('GET', `${base}/chores`)
    expect(chores.json.chores[0].description).toBe('genau so')

    const item = await admin.request('POST', `${base}/shopping`, { name: 'Milch', note: 'laktosefrei', quantity: 2 })
    const updated = await admin.request('PATCH', `${base}/shopping/${item.json.item.id}`, { name: 'Hafermilch' })
    expect(updated.json.item.note).toBe('laktosefrei')
    expect(updated.json.item.quantity).toBe(2)
  })

  it('clear a field when it is sent empty', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('PATCH', base, { address: 'Sonnenallee 42' })
    await admin.request('PATCH', base, { address: '' })
    expect((await admin.request('GET', base)).json.household.address).toBeNull()
  })
})

describe('shopping', () => {
  it('runs the full item lifecycle', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const created = await admin.request('POST', `${base}/shopping`, {
      name: 'Milch',
      quantity: 2,
      unit: 'L',
      category: 'DAIRY',
    })
    expect(created.status).toBe(201)
    expect(created.json.item.quantity).toBe(2)
    const id = created.json.item.id

    const bought = await members[0]!.request('POST', `${base}/shopping/${id}/status`, { status: 'BOUGHT' })
    expect(bought.json.item.boughtBy.displayName).toBe('Max')

    const back = await admin.request('POST', `${base}/shopping/${id}/status`, { status: 'PENDING' })
    expect(back.json.item.boughtBy).toBeNull()

    await admin.request('POST', `${base}/shopping/${id}/status`, { status: 'BOUGHT' })
    await admin.request('POST', `${base}/shopping`, { name: 'Eier' })
    expect((await admin.request('POST', `${base}/shopping/clear-bought`)).json.count).toBe(1)
    const list = await admin.request('GET', `${base}/shopping`)
    expect(list.json.items.map((i: { name: string }) => i.name)).toEqual(['Eier'])
  })
})

describe('buying from the list', () => {
  const balances = async (client: { request: Function }, base: string) =>
    Object.fromEntries(
      (await client.request('GET', `${base}/balances`)).json.balances.map(
        (b: { user: { id: string }; cents: number }) => [b.user.id, b.cents],
      ),
    )
  const buy = (client: { request: Function }, base: string, id: string, priceCents?: number) =>
    client.request('POST', `${base}/shopping/${id}/status`, { status: 'BOUGHT', priceCents })

  it('splits a WG item between everyone, and the buyer gets it back', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max', 'Lisa'])
    const [max, lisa] = members as [typeof admin, typeof admin]
    const { json } = await admin.request('POST', `${base}/shopping`, { name: 'Spülmittel' })
    const bought = await max.request('POST', `${base}/shopping/${json.item.id}/status`, { status: 'BOUGHT', priceCents: 900 })
    expect(bought.json.item.expense.amountCents).toBe(900)
    const b = await balances(admin, base)
    expect(b[max.id]).toBe(600)
    expect(b[admin.id]).toBe(-300)
    expect(b[lisa.id]).toBe(-300)
    const list = (await admin.request('GET', `${base}/expenses`)).json.expenses
    expect(list[0].description).toBe('Spülmittel')
    expect(list[0].paidBy.id).toBe(max.id)
  })

  it('makes only the requester owe for a personal item', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max', 'Lisa'])
    const [max, lisa] = members as [typeof admin, typeof admin]
    const { json } = await lisa.request('POST', `${base}/shopping`, { name: 'Shampoo', personal: true })
    expect(json.item.personal).toBe(true)
    await buy(max, base, json.item.id, 450)
    const b = await balances(admin, base)
    expect(b[max.id]).toBe(450)
    expect(b[lisa.id]).toBe(-450)
    expect(b[admin.id]).toBe(0)
  })

  it('creates no debt for your own personal item or without a price', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const own = await max.request('POST', `${base}/shopping`, { name: 'x', personal: true })
    expect((await buy(max, base, own.json.item.id, 500)).json.item.expense).toBeNull()
    const free = await admin.request('POST', `${base}/shopping`, { name: 'y' })
    const done = await buy(max, base, free.json.item.id)
    expect(done.json.item.status).toBe('BOUGHT')
    expect(done.json.item.expense).toBeNull()
    expect((await admin.request('GET', `${base}/expenses`)).json.expenses).toHaveLength(0)
  })

  it('leaves frozen people out of the split, unless they bought it', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max', 'Lisa'])
    const [max, lisa] = members as [typeof admin, typeof admin]
    await admin.request('POST', `${base}/members/${lisa.id}/freeze`, { frozen: true })
    const first = await admin.request('POST', `${base}/shopping`, { name: 'x' })
    await buy(max, base, first.json.item.id, 1000)
    const b = await balances(admin, base)
    expect(b[lisa.id]).toBe(0)
    expect(b[max.id]).toBe(500)
    const second = await admin.request('POST', `${base}/shopping`, { name: 'y' })
    await buy(lisa, base, second.json.item.id, 300)
    expect((await balances(admin, base))[lisa.id]).toBe(200)
  })

  it('takes the debt back when the item returns to the list', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const { json } = await admin.request('POST', `${base}/shopping`, { name: 'x' })
    await buy(max, base, json.item.id, 1000)
    const url = `${base}/shopping/${json.item.id}/status`
    expect((await max.request('POST', url, { status: 'PENDING' })).json.item.expense).toBeNull()
    expect(Object.values(await balances(admin, base)).every((c) => c === 0)).toBe(true)
    expect((await admin.request('GET', `${base}/expenses`)).json.expenses).toHaveLength(0)
  })

  it("does not let others cancel someone's paid purchase", async () => {
    const { admin, members, base } = await createHousehold(app, ['Max', 'Lisa'])
    const [max, lisa] = members as [typeof admin, typeof admin]
    const { json } = await admin.request('POST', `${base}/shopping`, { name: 'x' })
    await buy(max, base, json.item.id, 1000)
    const url = `${base}/shopping/${json.item.id}/status`
    expect((await lisa.request('POST', url, { status: 'PENDING' })).status).toBe(403)
    expect((await admin.request('POST', url, { status: 'PENDING' })).status).toBe(200)
  })

  it('counts a double tap once', async () => {
    const { admin, base } = await createHousehold(app, ['Max'])
    const { json } = await admin.request('POST', `${base}/shopping`, { name: 'x' })
    await buy(admin, base, json.item.id, 1000)
    await buy(admin, base, json.item.id, 1000)
    expect((await admin.request('GET', `${base}/expenses`)).json.expenses).toHaveLength(1)
  })

  it('keeps the expense when bought items are cleared', async () => {
    const { admin, base } = await createHousehold(app, ['Max'])
    const { json } = await admin.request('POST', `${base}/shopping`, { name: 'x' })
    await buy(admin, base, json.item.id, 1000)
    await admin.request('POST', `${base}/shopping/clear-bought`)
    expect((await admin.request('GET', `${base}/expenses`)).json.expenses).toHaveLength(1)
  })

  it('keeps the item bought when its expense is deleted', async () => {
    const { admin, base } = await createHousehold(app, ['Max'])
    const { json } = await admin.request('POST', `${base}/shopping`, { name: 'x' })
    const done = await buy(admin, base, json.item.id, 1000)
    await admin.request('DELETE', `${base}/expenses/${done.json.item.expense.id}`)
    const item = (await admin.request('GET', `${base}/shopping`)).json.items[0]
    expect(item.status).toBe('BOUGHT')
    expect(item.expense).toBeNull()
  })
})

describe('settling debts another way', () => {
  async function setup() {
    const house = await createHousehold(app, ['Max'])
    const max = house.members[0]!
    await house.admin.request('POST', `${house.base}/expenses`, {
      description: 'x',
      amountCents: 1200,
      paidById: house.admin.id,
      date: berlinDate(),
      participants: [{ userId: house.admin.id }, { userId: max.id }],
    })
    return { ...house, max }
  }
  const owed = async (client: { request: Function }, base: string, id: string) =>
    (await client.request('GET', `${base}/balances`)).json.balances.find(
      (b: { user: { id: string } }) => b.user.id === id,
    ).cents

  it('lets the creditor forgive, and nobody else', async () => {
    const { admin, max, base } = await setup()
    const body = { fromUserId: max.id, toUserId: admin.id, amountCents: 600, date: berlinDate(), kind: 'FORGIVEN' }
    expect((await max.request('POST', `${base}/settlements`, body)).status).toBe(403)
    expect((await admin.request('POST', `${base}/settlements`, body)).status).toBe(201)
    expect(await owed(admin, base, max.id)).toBe(0)
    const { settlements } = (await admin.request('GET', `${base}/settlements`)).json
    expect(settlements[0].kind).toBe('FORGIVEN')
  })

  it('turns a debt into beer only once it has arrived', async () => {
    const { admin, max, base } = await setup()
    const asked = await admin.request('POST', `${base}/beer-requests`, { fromUserId: max.id, amountCents: 600 })
    expect(asked.json.beers).toBe(4)
    expect(await owed(admin, base, max.id)).toBe(-600)
    const { beerRequests } = (await max.request('GET', `${base}/balances`)).json
    expect(beerRequests[0].beers).toBe(4)

    expect((await max.request('POST', `${base}/beer-requests/${asked.json.id}/received`)).status).toBe(403)
    expect((await admin.request('POST', `${base}/beer-requests/${asked.json.id}/received`)).status).toBe(204)
    expect(await owed(admin, base, max.id)).toBe(0)
    const { settlements } = (await admin.request('GET', `${base}/settlements`)).json
    expect(settlements[0].kind).toBe('BEER')
    expect(settlements[0].beers).toBe(4)
    expect((await admin.request('GET', `${base}/balances`)).json.beerRequests).toHaveLength(0)
  })

  it('uses the WG beer price and rounds up', async () => {
    const { admin, max, base } = await setup()
    await admin.request('PATCH', base, { beerPriceCents: 250 })
    const asked = await admin.request('POST', `${base}/beer-requests`, { fromUserId: max.id, amountCents: 600 })
    expect(asked.json.beers).toBe(3)
  })

  it('never settles more than is still owed', async () => {
    const { admin, max, base } = await setup()
    const asked = await admin.request('POST', `${base}/beer-requests`, { fromUserId: max.id, amountCents: 600 })
    await max.request('POST', `${base}/settlements`, { fromUserId: max.id, toUserId: admin.id, amountCents: 500, date: berlinDate() })
    await admin.request('POST', `${base}/beer-requests/${asked.json.id}/received`)
    expect(await owed(admin, base, max.id)).toBe(0)
    expect(await owed(admin, base, admin.id)).toBe(0)
  })

  it('lets the creditor take the request back', async () => {
    const { admin, max, base } = await setup()
    const asked = await admin.request('POST', `${base}/beer-requests`, { fromUserId: max.id, amountCents: 600 })
    expect((await max.request('DELETE', `${base}/beer-requests/${asked.json.id}`)).status).toBe(403)
    expect((await admin.request('DELETE', `${base}/beer-requests/${asked.json.id}`)).status).toBe(204)
    expect(await owed(admin, base, max.id)).toBe(-600)
  })

  it('stays inside the WG', async () => {
    const { admin, base } = await setup()
    const other = await createHousehold(app)
    const asked = await admin.request('POST', `${base}/beer-requests`, { fromUserId: other.admin.id, amountCents: 600 })
    expect(asked.status).toBe(404)
  })
})

describe('dishwasher counter', () => {
  async function setup(target = 3) {
    const house = await createHousehold(app, ['Max', 'Lisa'])
    await house.admin.request('PATCH', house.base, { dishwasherTarget: target })
    const [max, lisa] = house.members as [typeof house.admin, typeof house.admin]
    return { ...house, max, lisa }
  }
  const state = async (client: { request: Function }, base: string) =>
    (await client.request('GET', `${base}/dishwasher`)).json
  interface Row { user: { id: string }; count: number; crowns: number; strikes: number }
  const of = (s: { members: Row[] }, id: string) => s.members.find((m) => m.user.id === id)!

  it('is off until a WG turns it on', async () => {
    const { admin, base } = await createHousehold(app)
    expect((await admin.request('POST', `${base}/dishwasher/entries`)).status).toBe(409)
    expect((await state(admin, base)).target).toBeNull()
  })

  const tap = (client: { request: Function }, base: string) =>
    client.request('POST', `${base}/dishwasher/entries`)

  it('ends the round at the WG total and crowns whoever did the most', async () => {
    const { admin, max, lisa, base } = await setup()
    await tap(admin, base)
    await tap(max, base)
    const before = await state(admin, base)
    expect(before.total).toBe(2)
    expect(of(before, max.id).count).toBe(1)
    // Admin taps last and finishes the round, yet Max could still have had more.
    const last = await tap(max, base)
    expect(last.json).toMatchObject({ finished: true, crown: true, winnerName: 'Max' })

    const s = await state(admin, base)
    expect(s.cycle).toBe(2)
    expect(s.total).toBe(0)
    expect(s.crownHolderId).toBe(max.id)
    expect(of(s, max.id)).toMatchObject({ count: 0, crowns: 1, strikes: 0 })
    expect(of(s, admin.id)).toMatchObject({ count: 0, crowns: 0, strikes: 0 })
    expect(of(s, lisa.id)).toMatchObject({ count: 0, crowns: 0, strikes: 1 })
    expect(s.lastCycle.struck.map((u: { id: string }) => u.id)).toEqual([lisa.id])
  })

  it('does not crown whoever just happened to finish the round', async () => {
    const { admin, max, base } = await setup()
    await tap(admin, base)
    await tap(admin, base)
    const last = await tap(max, base)
    expect(last.json).toMatchObject({ finished: true, crown: false, winnerName: 'Admin' })
    expect((await state(admin, base)).crownHolderId).toBe(admin.id)
  })

  it('takes points away for a strike, from the total and this month', async () => {
    const { admin, max, lisa, base } = await setup(2)
    await admin.request('POST', `${base}/chores`, { title: 'x', startDate: berlinDate(), points: 30, rotation: [lisa.id] })
    const open = (await lisa.request('GET', `${base}/chore-occurrences`)).json.occurrences
    await lisa.request('POST', `${base}/chore-occurrences/${open[0].id}/complete`)
    await tap(admin, base)
    await tap(max, base)
    const board = (await admin.request('GET', `${base}/leaderboard`)).json.members
    const mine = board.find((m: { user: { id: string } }) => m.user.id === lisa.id)
    expect(mine.points).toBe(20)
    expect(mine.monthPoints).toBe(20)
  })

  it('can take someone below zero, and their level stays at the bottom', async () => {
    const { admin, max, lisa, base } = await setup(2)
    await tap(admin, base)
    await tap(max, base)
    const board = (await admin.request('GET', `${base}/leaderboard`)).json.members
    const mine = board.find((m: { user: { id: string } }) => m.user.id === lisa.id)
    expect(mine.points).toBe(-10)
    expect(mine.level).toBe(1)
    expect(board.at(-1).user.id).toBe(lisa.id)
  })

  it('does not strike someone frozen or who joined during the round', async () => {
    const { admin, lisa, base } = await setup(1)
    await admin.request('POST', `${base}/members/${lisa.id}/freeze`, { frozen: true })
    const invite = await admin.request('POST', `${base}/invitations`, { expiresInDays: 1 })
    const newbie = await registerUser(app)
    await newbie.request('POST', `/api/invitations/${invite.json.invitation.code}/accept`)
    await admin.request('POST', `${base}/dishwasher/entries`)
    const s = await state(admin, base)
    expect(of(s, lisa.id).strikes).toBe(0)
    expect(of(s, newbie.id).strikes).toBe(0)
  })

  it('lets you take back your own last magnet in the round', async () => {
    const { admin, max, base } = await setup()
    await admin.request('POST', `${base}/dishwasher/entries`)
    expect((await max.request('DELETE', `${base}/dishwasher/entries/last`)).status).toBe(409)
    expect((await admin.request('DELETE', `${base}/dishwasher/entries/last`)).status).toBe(204)
    expect(of(await state(admin, base), admin.id).count).toBe(0)
  })

  it('cannot undo a finished round', async () => {
    const { admin, base } = await setup(1)
    await tap(admin, base)
    expect((await admin.request('DELETE', `${base}/dishwasher/entries/last`)).status).toBe(409)
  })

  it('only admins switch it on', async () => {
    const { max, base } = await setup()
    expect((await max.request('PATCH', base, { dishwasherTarget: 5 })).status).toBe(403)
  })

  it('stays inside the WG', async () => {
    const a = await setup()
    const b = await createHousehold(app)
    expect((await b.admin.request('POST', `${a.base}/dishwasher/entries`)).status).toBe(404)
    expect((await b.admin.request('GET', `${a.base}/dishwasher`)).status).toBe(404)
  })
})

describe('notes', () => {
  it('enforces authorship and sorts pinned first', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const first = await max.request('POST', `${base}/notes`, { content: 'erste' })
    await admin.request('POST', `${base}/notes`, { content: 'zweite' })
    const id = first.json.note.id

    expect((await admin.request('PATCH', `${base}/notes/${id}`, { content: 'x' })).status).toBe(403)
    expect((await max.request('PATCH', `${base}/notes/${id}`, { content: 'neu' })).status).toBe(200)

    await admin.request('POST', `${base}/notes/${id}/pin`, { pinned: true })
    const list = await max.request('GET', `${base}/notes`)
    expect(list.json.notes[0].content).toBe('neu')

    await max.request('POST', `${base}/notes/${id}/archive`, { archived: true })
    expect((await max.request('GET', `${base}/notes`)).json.notes).toHaveLength(1)
    expect((await max.request('GET', `${base}/notes?archived=true`)).json.notes).toHaveLength(1)
  })

  it('stores markup as plain text', async () => {
    const { admin, base } = await createHousehold(app)
    const content = '<img src=x onerror=alert(1)>'
    const note = await admin.request('POST', `${base}/notes`, { content })
    expect(note.json.note.content).toBe(content)
  })
})

describe('calendar', () => {
  it('shows events and recurring chores together', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('POST', `${base}/chores`, {
      title: 'Müll',
      recurrence: 'WEEKLY',
      startDate: berlinDate(),
    })
    await admin.request('POST', `${base}/events`, {
      title: 'Party',
      startsAt: `${berlinDate(3)}T20:00`,
      participantIds: [admin.id],
    })
    const response = await admin.request(
      'GET',
      `${base}/calendar?from=${berlinDate()}&to=${berlinDate(14)}`,
    )
    expect(response.json.events.map((e: { title: string }) => e.title)).toEqual(['Party'])
    expect(response.json.events[0].participants[0].id).toBe(admin.id)
    expect(response.json.occurrences).toHaveLength(3)
  })

  it('generates chores far ahead on demand', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('POST', `${base}/chores`, {
      title: 'x',
      recurrence: 'WEEKLY',
      startDate: berlinDate(),
    })
    const from = berlinDate(200)
    const response = await admin.request('GET', `${base}/calendar?from=${from}&to=${berlinDate(214)}`)
    expect(response.json.occurrences.length).toBeGreaterThanOrEqual(2)
  })

  it('reads event times in the household time zone', async () => {
    const { admin, base } = await createHousehold(app)
    const event = await admin.request('POST', `${base}/events`, {
      title: 'x',
      startsAt: '2030-07-01T14:00',
    })
    expect(event.json.event.startsAt).toBe('2030-07-01T12:00:00.000Z')
  })

  it('rejects an end before the start', async () => {
    const { admin, base } = await createHousehold(app)
    const event = await admin.request('POST', `${base}/events`, {
      title: 'x',
      startsAt: '2030-07-01T14:00',
      endsAt: '2030-07-01T13:00',
    })
    expect(event.json.error).toBe('end_before_start')
  })

  it('includes an event that started before the range and is still running', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('POST', `${base}/events`, {
      title: 'Urlaub',
      startsAt: '2030-07-01T00:00',
      endsAt: '2030-07-10T00:00',
      allDay: true,
    })
    const response = await admin.request('GET', `${base}/calendar?from=2030-07-05&to=2030-07-06`)
    expect(response.json.events).toHaveLength(1)
  })
})

describe('expenses', () => {
  it('calculates the spec example end to end, and settles it', async () => {
    const { admin, members, base } = await createHousehold(app, ['Anna', 'Lisa'])
    const [anna, lisa] = members as [typeof admin, typeof admin]
    const created = await admin.request('POST', `${base}/expenses`, {
      description: 'Putzmittel',
      amountCents: 1800,
      paidById: admin.id,
      date: berlinDate(),
      participants: [{ userId: admin.id }, { userId: anna.id }, { userId: lisa.id }],
    })
    expect(created.json.expense.participants.map((p: { shareCents: number }) => p.shareCents)).toEqual([
      600, 600, 600,
    ])

    const balances = await admin.request('GET', `${base}/balances`)
    const byId = Object.fromEntries(
      balances.json.balances.map((b: { user: { id: string }; cents: number }) => [b.user.id, b.cents]),
    )
    expect(byId[admin.id]).toBe(1200)
    expect(byId[anna.id]).toBe(-600)
    expect(balances.json.suggestions).toHaveLength(2)

    await anna.request('POST', `${base}/settlements`, {
      fromUserId: anna.id,
      toUserId: admin.id,
      amountCents: 600,
      date: berlinDate(),
    })
    const after = await admin.request('GET', `${base}/balances`)
    expect(after.json.suggestions).toEqual([
      { fromUserId: lisa.id, toUserId: admin.id, amountCents: 600 },
    ])
  })

  it('validates custom shares and rejects fractional cents', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const payload = {
      description: 'x',
      amountCents: 1000,
      paidById: admin.id,
      date: berlinDate(),
      splitMode: 'CUSTOM',
    }
    const bad = await admin.request('POST', `${base}/expenses`, {
      ...payload,
      participants: [
        { userId: admin.id, shareCents: 700 },
        { userId: max.id, shareCents: 200 },
      ],
    })
    expect(bad.json.error).toBe('shares_do_not_add_up')
    const good = await admin.request('POST', `${base}/expenses`, {
      ...payload,
      participants: [
        { userId: admin.id, shareCents: 700 },
        { userId: max.id, shareCents: 300 },
      ],
    })
    expect(good.status).toBe(201)
    const fractional = await admin.request('POST', `${base}/expenses`, {
      ...payload,
      amountCents: 10.5,
      participants: [{ userId: admin.id, shareCents: 10.5 }],
    })
    expect(fractional.status).toBe(400)
  })

  it('editing an expense replaces its shares', async () => {
    const { admin, members, base } = await createHousehold(app, ['Max'])
    const max = members[0]!
    const created = await admin.request('POST', `${base}/expenses`, {
      description: 'x',
      amountCents: 1000,
      paidById: admin.id,
      date: berlinDate(),
      participants: [{ userId: admin.id }, { userId: max.id }],
    })
    const updated = await admin.request('PATCH', `${base}/expenses/${created.json.expense.id}`, {
      description: 'y',
      amountCents: 2000,
      paidById: max.id,
      date: berlinDate(),
      participants: [{ userId: admin.id }],
    })
    expect(updated.json.expense.participants).toHaveLength(1)
    const balances = await admin.request('GET', `${base}/balances`)
    const adminBalance = balances.json.balances.find(
      (b: { user: { id: string } }) => b.user.id === admin.id,
    )
    expect(adminBalance.cents).toBe(-2000)
  })
})

describe('dashboard', () => {
  it('summarises the day', async () => {
    const { admin, base } = await createHousehold(app)
    await admin.request('POST', `${base}/chores`, {
      title: 'Heute',
      startDate: berlinDate(),
      rotation: [admin.id],
    })
    await admin.request('POST', `${base}/chores`, {
      title: 'Überfällig',
      startDate: berlinDate(-3),
      rotation: [admin.id],
    })
    await admin.request('POST', `${base}/chores`, { title: 'Morgen', startDate: berlinDate(1) })
    await admin.request('POST', `${base}/shopping`, { name: 'Milch' })
    await admin.request('POST', `${base}/notes`, { content: 'Hallo' })
    await admin.request('POST', `${base}/events`, { title: 'Party', startsAt: `${berlinDate(2)}T20:00` })

    const { json } = await admin.request('GET', `${base}/dashboard`)
    expect(json.today).toBe(berlinDate())
    expect(json.tasks.map((t: { title: string }) => t.title)).toEqual(['Überfällig', 'Heute'])
    expect(json.upcomingTasks[0].title).toBe('Morgen')
    expect(json.shopping.pendingCount).toBe(1)
    expect(json.events[0].title).toBe('Party')
    expect(json.notes[0].content).toBe('Hallo')
  })
})

describe('infrastructure', () => {
  it('has a health endpoint and answers unknown routes with JSON', async () => {
    expect((await send(app, 'GET', '/api/health')).json.status).toBe('ok')
    const missing = await send(app, 'GET', '/api/nope')
    expect(missing.status).toBe(404)
    expect(missing.json.error).toBe('not_found')
  })
})
