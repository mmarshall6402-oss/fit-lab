import { useCatalog } from '../hooks/useCatalog'
import { useSavedOutfits } from '../hooks/useSavedOutfits'
import type { ItemDto } from '../types'

function itemLabel(items: ItemDto[], id: string): string {
  return items.find((i) => i.id === id)?.name ?? 'removed from catalog'
}

export function SavedOutfitsPage() {
  const { items } = useCatalog()
  const { savedOutfits, loading, error, deleteSavedOutfit } = useSavedOutfits()

  async function handleDelete(id: string) {
    if (window.confirm("Delete this saved fit? This can't be undone.")) {
      await deleteSavedOutfit(id)
    }
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8 p-4 sm:p-8">
      <div className="flex items-center justify-between">
        <h1 className="font-mono text-lg font-bold uppercase tracking-wide text-accent">Saved Fits</h1>
        <a href="#" className="text-xs text-neutral-500 hover:text-neutral-300">
          ← Back to FIT//LAB
        </a>
      </div>

      {error && (
        <p className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">{error}</p>
      )}

      {loading ? (
        <p className="text-sm text-neutral-500">Loading…</p>
      ) : savedOutfits.length === 0 ? (
        <p className="rounded-lg border border-dashed border-white/15 py-10 text-center text-sm text-neutral-500">
          No saved fits yet — build one and hit Save.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {savedOutfits.map((outfit) => (
            <li
              key={outfit.id}
              className="flex items-center justify-between gap-3 rounded-md border border-white/10 px-4 py-3"
            >
              <div className="flex flex-col gap-1">
                <span className="text-sm">
                  {itemLabel(items, outfit.shirtId)} · {itemLabel(items, outfit.bottomId)} ·{' '}
                  {itemLabel(items, outfit.shoesId)}
                </span>
                <span className="font-mono text-xs text-neutral-500">
                  {outfit.score.toFixed(0)} / 100 · {new Date(outfit.createdAt).toLocaleString()}
                </span>
              </div>
              <button
                onClick={() => handleDelete(outfit.id)}
                className="shrink-0 text-xs text-neutral-400 hover:text-red-400 cursor-pointer"
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
