import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { installBackButton } from './back'
import './styles.css'

installBackButton()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
