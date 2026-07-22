import { useEffect, useState } from 'react'
import type { SavedOutfitDto } from '../types'
import { api } from '../api/client'
import { matchesSavedOutfit } from '../search'
import { SearchInput } from './SearchInput'
import { SavedFitCard } from './SavedFitCard'
import { SavedFitDetail } from './SavedFitDetail'

export function GalleryPanel() {
  const [outfits, setOutfits] = useState<SavedOutfitDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [openId, setOpenId] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    api
      .listSavedOutfits()
      .then(setOutfits)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load saved fits'))
      .finally(() => setLoading(false))
  }, [])

  function handleLiked(updated: SavedOutfitDto) {
    setOutfits((prev) => prev.map((o) => (o.id === updated.id ? updated : o)))
  }

  const visible = outfits.filter((o) => matchesSavedOutfit(o, query))
  const openOutfit = outfits.find((o) => o.id === openId) ?? null

  return (
    <section className="flex flex-1 flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">
          Community Fits
        </h2>
        <div className="w-64">
          <SearchInput value={query} onChange={setQuery} placeholder="Search saved fits…" />
        </div>
      </div>

      {error && (
        <p className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">
          {error}
        </p>
      )}

      {loading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-40 animate-pulse rounded-lg bg-white/5" />
          ))}
        </div>
      ) : visible.length === 0 ? (
        <p className="rounded-lg border border-dashed border-white/15 py-10 text-center text-sm text-neutral-500">
          {outfits.length === 0
            ? 'No saved fits yet — build one and hit "Save to gallery".'
            : 'No fits match your search.'}
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {visible.map((outfit) => (
            <SavedFitCard key={outfit.id} outfit={outfit} onOpen={() => setOpenId(outfit.id)} />
          ))}
        </div>
      )}

      {openOutfit && (
        <SavedFitDetail outfit={openOutfit} onClose={() => setOpenId(null)} onLiked={handleLiked} />
      )}
    </section>
  )
}
