import { defineConfig } from 'vitest/config'

// Unit tests always run on mock data; *.live.test.ts runs the same client against a real backend (EDOCS_LIVE_API=http://localhost:8080/api).
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    env: { VITE_API_BASE_URL: '' },
  },
})
