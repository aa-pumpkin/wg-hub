import { execSync } from 'node:child_process'

export default function setup() {
  execSync('npx prisma migrate deploy', {
    env: {
      ...process.env,
      DATABASE_URL:
        process.env.TEST_DATABASE_URL ?? 'postgresql://wghub:wghub@localhost:5432/wghub_test',
    },
    stdio: 'pipe',
  })
}
