import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { AuthProvider } from './lib/auth.jsx'
import ErrorBoundary from './components/ErrorBoundary.jsx'
import { startMonitoring } from './lib/monitoring'
import './index.css'
import { MotionConfig } from 'framer-motion'

startMonitoring()

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <MotionConfig reducedMotion="user"><App /></MotionConfig>
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  </React.StrictMode>,
)
