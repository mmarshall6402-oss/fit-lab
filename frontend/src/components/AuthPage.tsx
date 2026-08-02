import { useState } from 'react'
import { useAuth } from '../auth'

export function AuthPage() {
  const { login, register } = useAuth()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await register(email, password)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm rounded-lg border border-white/10 p-8">
        <h1 className="mb-1 font-mono text-xl font-bold tracking-tight">
          FIT<span className="text-accent">//</span>LAB
        </h1>
        <p className="mb-6 font-mono text-xs uppercase tracking-widest text-neutral-500">
          {mode === 'login' ? 'log in to your closet' : 'create an account'}
        </p>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <label className="flex flex-col gap-1 text-xs uppercase tracking-widest text-neutral-500">
            Email
            <input
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="rounded-md border border-white/15 bg-ink-2 px-3 py-2 text-sm text-neutral-100 outline-none focus:border-accent"
            />
          </label>
          <label className="flex flex-col gap-1 text-xs uppercase tracking-widest text-neutral-500">
            Password
            <input
              type="password"
              required
              minLength={8}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="rounded-md border border-white/15 bg-ink-2 px-3 py-2 text-sm text-neutral-100 outline-none focus:border-accent"
            />
          </label>

          {error && (
            <p className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">{error}</p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="mt-2 rounded-md bg-accent px-4 py-2 font-mono text-sm font-bold uppercase tracking-widest text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
          >
            {submitting ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}
          </button>
        </form>

        <button
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login')
            setError(null)
          }}
          className="mt-6 w-full text-center text-xs text-neutral-500 hover:text-accent cursor-pointer"
        >
          {mode === 'login' ? "Don't have an account? Sign up" : 'Already have an account? Log in'}
        </button>
      </div>
    </div>
  )
}
