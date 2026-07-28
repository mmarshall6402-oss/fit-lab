import { StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AdminPage } from './components/AdminPage.tsx'
import { StyleProfilePage } from './components/StyleProfilePage.tsx'

/**
 * Hash-based routing (no server-side rewrite needed on static S3 hosting):
 * #admin opens the admin dashboard, #style-profile opens the style profile.
 */
function Root() {
  const [hash, setHash] = useState(window.location.hash)

  useEffect(() => {
    const handler = () => setHash(window.location.hash)
    window.addEventListener('hashchange', handler)
    return () => window.removeEventListener('hashchange', handler)
  }, [])

  if (hash.startsWith('#admin')) return <AdminPage />
  if (hash.startsWith('#style-profile')) return <StyleProfilePage />
  return <App />
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
)
