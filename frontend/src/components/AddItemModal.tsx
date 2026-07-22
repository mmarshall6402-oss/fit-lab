import { useRef, useState } from 'react'
import type { Category, CreateItemRequest, ItemDto } from '../types'
import { CATEGORIES } from '../types'
import { TagInput } from './TagInput'

interface Props {
  defaultCategory: Category
  onCreate: (request: CreateItemRequest) => Promise<ItemDto>
  onUploadImage: (id: string, file: File) => Promise<ItemDto>
  onClose: () => void
}

export function AddItemModal({ defaultCategory, onCreate, onUploadImage, onClose }: Props) {
  const [name, setName] = useState('')
  const [category, setCategory] = useState<Category>(defaultCategory)
  const [colors, setColors] = useState<string[]>([])
  const [vibes, setVibes] = useState<string[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<ItemDto | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Give it a name.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const item = await onCreate({ name: name.trim(), category, colors, vibes })
      setCreated(item)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create item.')
    } finally {
      setSaving(false)
    }
  }

  async function handleImagePick(file: File | undefined) {
    if (!file || !created) return
    setSaving(true)
    try {
      await onUploadImage(created.id, file)
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not upload image.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" onClick={onClose}>
      <div
        className="w-full max-w-md rounded-xl border border-white/10 bg-ink-2 p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {!created ? (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <h2 className="font-mono text-lg font-bold uppercase tracking-wide text-accent">Add Item</h2>

            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-semibold uppercase tracking-wide text-neutral-400">Name</span>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Oversized Flannel"
                autoFocus
                className="rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-accent"
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-semibold uppercase tracking-wide text-neutral-400">Category</span>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value as Category)}
                className="rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-accent"
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </label>

            <TagInput label="Colors" placeholder="black, red…" values={colors} onChange={setColors} />
            <TagInput label="Vibes" placeholder="street, loud…" values={vibes} onChange={setVibes} />

            {error && <p className="text-sm text-red-400">{error}</p>}

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={onClose}
                className="rounded-md px-4 py-2 text-sm text-neutral-400 hover:text-white cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={saving}
                className="rounded-md bg-accent px-4 py-2 text-sm font-bold uppercase text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
              >
                {saving ? 'Saving…' : 'Add'}
              </button>
            </div>
          </form>
        ) : (
          <div className="flex flex-col gap-4 text-center">
            <h2 className="font-mono text-lg font-bold uppercase tracking-wide text-accent">
              {created.name} added
            </h2>
            <p className="text-sm text-neutral-400">Add a photo now, or skip and do it later.</p>
            <input
              ref={fileInput}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => handleImagePick(e.target.files?.[0])}
            />
            {error && <p className="text-sm text-red-400">{error}</p>}
            <div className="flex justify-center gap-2">
              <button
                onClick={onClose}
                className="rounded-md px-4 py-2 text-sm text-neutral-400 hover:text-white cursor-pointer"
              >
                Skip
              </button>
              <button
                onClick={() => fileInput.current?.click()}
                disabled={saving}
                className="rounded-md bg-accent px-4 py-2 text-sm font-bold uppercase text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
              >
                {saving ? 'Uploading…' : 'Choose photo'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
