import { defineConfig } from '@playwright/test'

const baseURL = process.env.CODEFORGE_GATE_BASE_URL
if (!baseURL) {
  throw new Error('Missing required environment variable: CODEFORGE_GATE_BASE_URL')
}

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 120_000,
  use: {
    baseURL,
    browserName: 'chromium',
    channel: 'msedge',
    headless: true,
  },
})
