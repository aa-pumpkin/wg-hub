import type { FastifyInstance } from 'fastify'
import { live } from '../../lib/live.js'
import { currentHousehold, currentUser } from '../../middleware/auth.js'

const HEARTBEAT_MS = 25_000

export async function liveRoutes(app: FastifyInstance) {
  app.get('/stream', (request, reply) => {
    reply.hijack()
    const stream = reply.raw
    stream.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
    })
    stream.write(': connected\n\n')

    const heartbeat = setInterval(() => stream.write(': ping\n\n'), HEARTBEAT_MS)
    const unsubscribe = live.subscribe(currentHousehold(request).id, {
      userId: currentUser(request).id,
      send: (module) => stream.write(`data: ${JSON.stringify({ module })}\n\n`),
      close: () => stream.end(),
    })
    request.raw.on('close', () => {
      clearInterval(heartbeat)
      unsubscribe()
    })
  })
}
