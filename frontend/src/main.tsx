import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './App.css'
import { Root } from './Root.tsx'
import { ErrorBoundary } from './components/ErrorBoundary.tsx'
import { captureReferralSource } from './auth/referral.ts'

captureReferralSource()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <Root />
    </ErrorBoundary>
  </StrictMode>,
)
