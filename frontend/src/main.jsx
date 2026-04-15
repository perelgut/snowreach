import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider }      from './context/AuthContext'
import { MockStateProvider } from './context/MockStateContext'
import './styles/tokens.css'
import './styles/globals.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter basename="/YoSnowMow">
      {/* AuthProvider must be outside MockStateProvider so auth state is
          available to all providers and components in the tree. */}
      <AuthProvider>
        <MockStateProvider>
          <App />
        </MockStateProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
