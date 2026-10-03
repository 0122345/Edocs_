// Requires `npm i -D vitest` (see docs/IMPLEMENTATION.md).
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: { environment: 'node', include: ['src/**/*.test.ts'] },
})
