import { StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AdminPage } from './components/AdminPage.tsx'
import { StyleProfilePage } from './components/StyleProfilePage.tsx'
import { SavedOutfitsPage } from './components/SavedOutfitsPage.tsx'
import { AuthPage } from './components/AuthPage.tsx'
import { AuthProvider, useAuth } from './auth.tsx'

/**
 * Hash-based routing (no server-side rewrite needed on static S3 hosting):
 * #admin opens the admin dashboard, #style-profile opens the style profile,
 * #saved-fits opens the saved-outfits history.
 */
function Routes() {
  const [hash, setHash] = useState(window.location.hash)

  useEffect(() => {
    const handler = () => setHash(window.location.hash)
    window.addEventListener('hashchange', handler)
    return () => window.removeEventListener('hashchange', handler)
  }, [])

  if (hash.startsWith('#admin')) return <AdminPage />
  if (hash.startsWith('#style-profile')) return <StyleProfilePage />
  if (hash.startsWith('#saved-fits')) return <SavedOutfitsPage />
  return <App />
}

/** Every route lives behind an account - a closet is only ever visible to the account that owns it. */
function Gate() {
  const { user, loading } = useAuth()

  if (loading) return null
  if (!user) return <AuthPage />
  return <Routes />
}

function Root() {
  return (
    <AuthProvider>
      <Gate />
    </AuthProvider>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
)
