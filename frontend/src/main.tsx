import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './App.css'
import { Root } from './Root.tsx'
import { ErrorBoundary } from './components/ErrorBoundary.tsx'
import { captureReferralSource } from './auth/referral.ts'

const container = document.getElementById('root')!

// /demo is usually served from demo/index.html; this covers hosts that send
// every path to the app instead
if (/^\/demo(\/trucking)?$/.test(window.location.pathname)) {
  // Without the slash this is the app's page (not indexed); the demo's own
  // page, with its title and description for search engines, is at the slash
  window.location.replace(`${window.location.pathname}/${window.location.search}${window.location.hash}`)
} else if (/^\/demo(\/trucking)?\/$/.test(window.location.pathname)) {
  void import('./demo/start.tsx').then(({ startDemo }) => startDemo(container))
} else {
  captureReferralSource()

  createRoot(container).render(
    <StrictMode>
      <ErrorBoundary>
        <Root />
      </ErrorBoundary>
    </StrictMode>,
  )
}
