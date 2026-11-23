# WG Hub

App for shared flats (WGs): rotating chores, shopping list, shared expenses, calendar and notes. Web app (PWA) and native Android app, available in German, English and Russian.

[Русская версия](README.ru.md) · [Architecture (Russian)](docs/ARCHITECTURE.md)

## Features

- **Chores** rotate around a circle of flatmates every week; a member can be "frozen" while away and keeps their place in the circle
- **Shopping and expenses:** ticking off an item creates the expense, debts are calculated in whole cents and settled with a minimal set of transfers
- **Points, levels and streaks** for chores done on time, plus an optional dishwasher counter
- **Calendar and notes**, with chores integrated into the calendar
- **Live updates** between devices via Server-Sent Events
- **No e-mail required:** username and password; a forgotten password is reset with a one-time link from the flat's admin
- **Android app** keeps showing the last data offline and queues actions until the connection is back

## Stack

| Part | Technologies |
|---|---|
| Frontend | Vue 3, TypeScript, Vite, Pinia, Vue Router, vue-i18n, Tailwind CSS 4, PWA |
| Backend | Node.js, TypeScript, Fastify 5, Zod, Prisma 6, Argon2id |
| Mobile | Kotlin, Compose Multiplatform |
| Database | PostgreSQL 17 |
| Tests | Vitest (unit and API integration tests on a real Postgres), Playwright |
| Deployment | Docker Compose, nginx |

## Run

Only Docker is needed:

```bash
docker compose up -d --build
```

The app is available at <http://localhost:8080>. Migrations run automatically. Optional demo data:

```bash
docker compose exec backend node dist/scripts/seed.js
```

This adds the users `anna` (admin), `max` and `lisa` with the password `demo12345`.

## Development

```bash
npm install
npm run db:up                    # Postgres on localhost:5432
cd backend && npx prisma migrate deploy && cd ..
npm run dev                      # API on :3000, Vite on :5173
npm test
```
