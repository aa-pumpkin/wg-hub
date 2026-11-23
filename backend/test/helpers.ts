import type { FastifyInstance } from 'fastify'
import { randomUUID } from 'node:crypto'
import type { InjectOptions } from 'fastify'

export interface Client {
  id: string
  username: string
  cookie: string
  request: (method: InjectOptions['method'], url: string, body?: unknown) => Promise<Response_>
}

export interface Response_ {
  status: number
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- tests read arbitrary API JSON
  json: any
}

export async function send(
  app: FastifyInstance,
  method: InjectOptions['method'],
  url: string,
  body?: unknown,
  headers: Record<string, string> = {},
) {
  const response = await app.inject({
    method,
    url,
    headers,
    ...(body === undefined ? {} : { payload: body as object }),
  })
  return {
    status: response.statusCode,
    json: response.body ? JSON.parse(response.body) : null,
    cookie: response.headers['set-cookie'],
  }
}

export async function registerUser(app: FastifyInstance, displayName = 'Test'): Promise<Client> {
  const username = `u${randomUUID().replaceAll('-', '').slice(0, 20)}`
  const response = await send(app, 'POST', '/api/auth/register', {
    username,
    displayName,
    password: 'correct horse battery',
  })
  if (response.status !== 201) throw new Error(`register failed: ${JSON.stringify(response.json)}`)
  const raw = response.cookie
  const cookie = String(Array.isArray(raw) ? raw[0] : raw).split(';')[0]!
  return {
    id: response.json.user.id,
    username,
    cookie,
    request: async (method, url, body) => {
      const result = await send(app, method, url, body, { cookie })
      return { status: result.status, json: result.json }
    },
  }
}

/** A household with an admin and members who joined through a real invitation. */
export async function createHousehold(app: FastifyInstance, memberNames: string[] = []) {
  const admin = await registerUser(app, 'Admin')
  const created = await admin.request('POST', '/api/households', { name: 'Test WG' })
  const householdId: string = created.json.household.id
  const invite = await admin.request('POST', `/api/households/${householdId}/invitations`, {
    expiresInDays: 7,
  })
  const members: Client[] = []
  for (const name of memberNames) {
    const member = await registerUser(app, name)
    await member.request('POST', `/api/invitations/${invite.json.invitation.code}/accept`)
    members.push(member)
  }
  return { admin, members, householdId, base: `/api/households/${householdId}` }
}
