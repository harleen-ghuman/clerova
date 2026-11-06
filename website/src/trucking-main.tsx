import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { TruckingPage } from './TruckingPage.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <TruckingPage />
  </StrictMode>,
)
