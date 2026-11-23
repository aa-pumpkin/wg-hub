import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['test/**/*.test.ts'],
    // Integration tests share one test database, so files must not overlap.
    fileParallelism: false,
    env: {
      DATABASE_URL:
        process.env.TEST_DATABASE_URL ?? 'postgresql://wghub:wghub@localhost:5432/wghub_test',
      LOG_LEVEL: 'silent',
      RATE_LIMIT_AUTH_MAX: '10000',
    },
    globalSetup: ['test/global-setup.ts'],
  },
})
