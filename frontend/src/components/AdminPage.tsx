import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api/client'
import { useCatalog } from '../hooks/useCatalog'
import { CATEGORIES, type Category, type CreateItemRequest, type ItemDto, type ScoringConfigDto } from '../types'
import { TagInput } from './TagInput'

const TOKEN_STORAGE_KEY = 'fitlab_admin_token'

const REASON_TOGGLES = [
  ['sharedVibeReasonEnabled', 'Shared vibe across all three pieces'],
  ['sharedColorReasonEnabled', 'Shared color across all three pieces'],
  ['neutralCounterbalanceReasonEnabled', 'Neutral counterbalance explanation'],
] as const

export function AdminPage() {
  const [token, setToken] = useState<string | null>(() => sessionStorage.getItem(TOKEN_STORAGE_KEY))
  const [passwordInput, setPasswordInput] = useState('')
  const [authError, setAuthError] = useState<string | null>(null)
  const [authenticating, setAuthenticating] = useState(false)

  const [config, setConfig] = useState<ScoringConfigDto | null>(null)
  const [configLoading, setConfigLoading] = useState(false)
  const [configError, setConfigError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [savedAt, setSavedAt] = useState<number | null>(null)

  const { items, loading: itemsLoading, updateItem, deleteItem } = useCatalog()

  useEffect(() => {
    if (token) loadConfig(token)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  async function loadConfig(currentToken: string) {
    setConfigLoading(true)
    setConfigError(null)
    try {
      setConfig(await api.adminGetScoringConfig(currentToken))
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        sessionStorage.removeItem(TOKEN_STORAGE_KEY)
        setToken(null)
        setAuthError('Session expired — enter the admin token again.')
      } else {
        setConfigError(e instanceof Error ? e.message : 'Could not load scoring config.')
      }
    } finally {
      setConfigLoading(false)
    }
  }

  async function handleUnlock(e: FormEvent) {
    e.preventDefault()
    setAuthenticating(true)
    setAuthError(null)
    try {
      await api.adminGetScoringConfig(passwordInput)
      sessionStorage.setItem(TOKEN_STORAGE_KEY, passwordInput)
      setToken(passwordInput)
    } catch (e) {
      setAuthError(e instanceof ApiError && e.status === 401 ? 'Wrong admin token.' : 'Could not reach the server.')
    } finally {
      setAuthenticating(false)
    }
  }

  function handleLogout() {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY)
    setToken(null)
    setConfig(null)
  }

  async function handleSave() {
    if (!token || !config) return
    setSaving(true)
    setConfigError(null)
    try {
      setConfig(await api.adminUpdateScoringConfig(token, config))
      setSavedAt(Date.now())
    } catch (e) {
      setConfigError(e instanceof Error ? e.message : 'Could not save scoring config.')
    } finally {
      setSaving(false)
    }
  }

  async function handleReset() {
    if (!token) return
    setSaving(true)
    setConfigError(null)
    try {
      setConfig(await api.adminResetScoringConfig(token))
      setSavedAt(Date.now())
    } catch (e) {
      setConfigError(e instanceof Error ? e.message : 'Could not reset scoring config.')
    } finally {
      setSaving(false)
    }
  }

  if (!token) {
    return (
      <div className="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-4 p-8">
        <h1 className="font-mono text-lg font-bold uppercase tracking-wide text-accent">Admin</h1>
        <form onSubmit={handleUnlock} className="flex flex-col gap-3">
          <input
            type="password"
            value={passwordInput}
            onChange={(e) => setPasswordInput(e.target.value)}
            placeholder="Admin token"
            autoFocus
            className="rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-accent"
          />
          {authError && <p className="text-sm text-red-400">{authError}</p>}
          <button
            type="submit"
            disabled={authenticating || !passwordInput}
            className="rounded-md bg-accent px-4 py-2 text-sm font-bold uppercase text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
          >
            {authenticating ? 'Checking…' : 'Unlock'}
          </button>
        </form>
        <a href="#" className="text-xs text-neutral-500 hover:text-neutral-300">
          ← Back to FIT//LAB
        </a>
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8 p-4 sm:p-8">
      <div className="flex items-center justify-between">
        <h1 className="font-mono text-lg font-bold uppercase tracking-wide text-accent">Admin</h1>
        <div className="flex items-center gap-4">
          <a href="#" className="text-xs text-neutral-500 hover:text-neutral-300">
            ← Back to app
          </a>
          <button onClick={handleLogout} className="text-xs text-neutral-500 hover:text-red-400 cursor-pointer">
            Log out
          </button>
        </div>
      </div>

      <section className="flex flex-col gap-4 rounded-lg border border-white/10 p-5">
        <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">Scoring calibration</h2>
        {configLoading || !config ? (
          <p className="text-sm text-neutral-500">Loading…</p>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-4">
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-semibold uppercase tracking-wide text-neutral-400">Color weight</span>
                <input
                  type="number"
                  step="0.5"
                  min="0"
                  value={config.colorWeight}
                  onChange={(e) => setConfig({ ...config, colorWeight: Number(e.target.value) })}
                  className="rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-accent"
                />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-semibold uppercase tracking-wide text-neutral-400">Vibe weight</span>
                <input
                  type="number"
                  step="0.5"
                  min="0"
                  value={config.vibeWeight}
                  onChange={(e) => setConfig({ ...config, vibeWeight: Number(e.target.value) })}
                  className="rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-accent"
                />
              </label>
              <label className="col-span-2 flex flex-col gap-1.5">
                <span className="text-xs font-semibold uppercase tracking-wide text-neutral-400">
                  Neutral-counterbalance threshold (min colors on the loud piece)
                </span>
                <input
                  type="number"
                  step="1"
                  min="0"
                  value={config.neutralColorThreshold}
                  onChange={(e) => setConfig({ ...config, neutralColorThreshold: Number(e.target.value) })}
                  className="rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-accent"
                />
              </label>
            </div>

            <TagInput
              label="Neutral colors"
              placeholder="black, white…"
              values={config.neutralColors}
              onChange={(values) => setConfig({ ...config, neutralColors: values })}
            />

            <div className="flex flex-col gap-2">
              <span className="text-xs font-semibold uppercase tracking-wide text-neutral-400">Reason toggles</span>
              {REASON_TOGGLES.map(([key, label]) => (
                <label key={key} className="flex items-center gap-2 text-sm text-neutral-300">
                  <input
                    type="checkbox"
                    checked={config[key]}
                    onChange={(e) => setConfig({ ...config, [key]: e.target.checked })}
                  />
                  {label}
                </label>
              ))}
            </div>

            {configError && <p className="text-sm text-red-400">{configError}</p>}
            {savedAt && !configError && <p className="text-sm text-accent">Saved — live immediately, no redeploy.</p>}

            <div className="flex gap-2 pt-2">
              <button
                onClick={handleSave}
                disabled={saving}
                className="rounded-md bg-accent px-4 py-2 text-sm font-bold uppercase text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
              >
                {saving ? 'Saving…' : 'Save'}
              </button>
              <button
                onClick={handleReset}
                disabled={saving}
                className="rounded-md border border-white/15 px-4 py-2 text-sm font-bold uppercase text-neutral-300 hover:border-accent hover:text-accent disabled:opacity-50 cursor-pointer"
              >
                Reset to defaults
              </button>
            </div>
          </>
        )}
      </section>

      <section className="flex flex-col gap-4 rounded-lg border border-white/10 p-5">
        <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">
          Catalog moderation <span className="text-neutral-600">({items.length})</span>
        </h2>
        {itemsLoading ? (
          <p className="text-sm text-neutral-500">Loading…</p>
        ) : (
          <div className="flex flex-col gap-2">
            {items.map((item) => (
              <AdminItemRow key={item.id} item={item} onSave={updateItem} onDelete={deleteItem} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

interface RowProps {
  item: ItemDto
  onSave: (id: string, request: CreateItemRequest) => Promise<ItemDto>
  onDelete: (id: string) => Promise<void>
}

function AdminItemRow({ item, onSave, onDelete }: RowProps) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(item.name)
  const [category, setCategory] = useState<Category>(item.category)
  const [colors, setColors] = useState<string[]>(item.colors)
  const [vibes, setVibes] = useState<string[]>(item.vibes)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSave() {
    setSaving(true)
    setError(null)
    try {
      await onSave(item.id, { name, category, colors, vibes, imageUrl: item.imageUrl })
      setEditing(false)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save item.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (window.confirm(`Delete "${item.name}"? This can't be undone.`)) {
      await onDelete(item.id)
    }
  }

  if (!editing) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-md border border-white/10 px-3 py-2">
        <div className="flex flex-1 items-center gap-3 overflow-hidden">
          <span className="w-16 shrink-0 font-mono text-[10px] uppercase text-neutral-500">{item.category}</span>
          <span className="shrink-0 text-sm">{item.name}</span>
          <span className="truncate text-xs text-neutral-500">{[...item.colors, ...item.vibes].join(', ')}</span>
        </div>
        <div className="flex shrink-0 gap-3">
          <button onClick={() => setEditing(true)} className="text-xs text-neutral-400 hover:text-accent cursor-pointer">
            Edit
          </button>
          <button onClick={handleDelete} className="text-xs text-neutral-400 hover:text-red-400 cursor-pointer">
            Delete
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-2 rounded-md border border-accent/40 bg-black/20 p-3">
      <div className="flex gap-2">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="flex-1 rounded-md border border-white/15 bg-black/30 px-3 py-1.5 text-sm outline-none focus:border-accent"
        />
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as Category)}
          className="rounded-md border border-white/15 bg-black/30 px-2 py-1.5 text-sm outline-none focus:border-accent"
        >
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>
      <TagInput label="Colors" placeholder="black, red…" values={colors} onChange={setColors} />
      <TagInput label="Vibes" placeholder="street, loud…" values={vibes} onChange={setVibes} />
      {error && <p className="text-sm text-red-400">{error}</p>}
      <div className="flex justify-end gap-2">
        <button
          onClick={() => setEditing(false)}
          className="rounded-md px-3 py-1.5 text-xs text-neutral-400 hover:text-white cursor-pointer"
        >
          Cancel
        </button>
        <button
          onClick={handleSave}
          disabled={saving}
          className="rounded-md bg-accent px-3 py-1.5 text-xs font-bold uppercase text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </div>
  )
}
