import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { MockStateProvider } from './context/MockStateContext'
import './styles/tokens.css'
import './styles/globals.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter basename="/YoSnowMow">
      <MockStateProvider>
        <App />
      </MockStateProvider>
    </BrowserRouter>
  </StrictMode>,
)
