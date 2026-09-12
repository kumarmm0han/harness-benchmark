import {defineConfig} from '@playwright/test';
export default defineConfig({testDir: './e2e', workers: 1, timeout: 60000, use: {baseURL: `http://localhost:${process.env.UI_PORT ?? '5173'}`, headless: true, trace: 'retain-on-failure'}, reporter: 'list'});
