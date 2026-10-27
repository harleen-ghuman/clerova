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
if (/^\/demo\/?$/.test(window.location.pathname)) {
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
