import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      // Each page is its own HTML file, served at /, /terms, /privacy and /guides/...
      input: {
        main: resolve(__dirname, 'index.html'),
        terms: resolve(__dirname, 'terms/index.html'),
        privacy: resolve(__dirname, 'privacy/index.html'),
        guides: resolve(__dirname, 'guides/index.html'),
        maintenanceRequestEmails: resolve(__dirname, 'guides/maintenance-request-emails/index.html'),
        tenantEmailTemplates: resolve(__dirname, 'guides/tenant-email-templates/index.html'),
        centralValley: resolve(__dirname, 'guides/central-valley-property-managers/index.html'),
      },
    },
  },
})
