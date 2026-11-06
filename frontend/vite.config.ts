import react from '@vitejs/plugin-react'
import { cpSync, createReadStream, existsSync, statSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig, type Plugin } from 'vite'

// pdf.js (the in-app file viewer) loads decoders for scanned PDFs and the
// standard fonts on demand. They're served from /pdfjs/, straight from the
// package: copied into the build, and served from node_modules in dev.
const PDFJS = resolve(__dirname, 'node_modules/pdfjs-dist')
const PDFJS_DIRS = ['wasm', 'standard_fonts', 'cmaps', 'iccs']

function pdfjsAssets(): Plugin {
  return {
    name: 'pdfjs-assets',
    configureServer(server) {
      server.middlewares.use('/pdfjs', (req, res, next) => {
        const path = resolve(PDFJS, '.' + decodeURIComponent((req.url ?? '').split('?')[0]))

        if (!path.startsWith(PDFJS + '/') || !PDFJS_DIRS.some((dir) => path.startsWith(resolve(PDFJS, dir) + '/'))
          || !existsSync(path) || !statSync(path).isFile()) {
          next()
          return
        }

        if (path.endsWith('.wasm')) {
          res.setHeader('Content-Type', 'application/wasm')
        }

        createReadStream(path).pipe(res)
      })
    },
    writeBundle(options) {
      for (const dir of PDFJS_DIRS) {
        cpSync(resolve(PDFJS, dir), resolve(options.dir ?? 'dist', 'pdfjs', dir), { recursive: true })
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), pdfjsAssets()],
  build: {
    rollupOptions: {
      // The app at /, and the no-sign-up demo at /demo
      input: {
        main: resolve(__dirname, 'index.html'),
        demo: resolve(__dirname, 'demo/index.html'),
        demoTrucking: resolve(__dirname, 'demo/trucking/index.html'),
      },
    },
  },
})
