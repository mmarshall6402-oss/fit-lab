import { useEffect, useState } from 'react'
import type { CommentDto, SavedOutfitDto } from '../types'
import { api, resolveUrl } from '../api/client'
import { CategoryIcon } from './CategoryIcon'

interface Props {
  outfit: SavedOutfitDto
  onClose: () => void
  onLiked: (updated: SavedOutfitDto) => void
}

function Piece({
  label,
  name,
  imageUrl,
  category,
}: {
  label: string
  name: string
  imageUrl: string | null
  category: 'SHIRT' | 'BOTTOM' | 'SHOES'
}) {
  return (
    <div className="flex flex-col items-center gap-1">
      <div className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-md bg-black/40">
        {imageUrl ? (
          <img src={resolveUrl(imageUrl)!} alt={name} className="h-full w-full object-cover" />
        ) : (
          <CategoryIcon category={category} className="h-8 w-8 text-neutral-600" />
        )}
      </div>
      <p className="text-[10px] uppercase tracking-wide text-neutral-500">{label}</p>
      <p className="max-w-20 truncate text-xs">{name}</p>
    </div>
  )
}

export function SavedFitDetail({ outfit, onClose, onLiked }: Props) {
  const [comments, setComments] = useState<CommentDto[] | null>(null)
  const [author, setAuthor] = useState('')
  const [body, setBody] = useState('')
  const [posting, setPosting] = useState(false)
  const [liking, setLiking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .listComments(outfit.id)
      .then((c) => !cancelled && setComments(c))
      .catch(() => !cancelled && setComments([]))
    return () => {
      cancelled = true
    }
  }, [outfit.id])

  async function handleLike() {
    setLiking(true)
    try {
      onLiked(await api.likeSavedOutfit(outfit.id))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not like this fit.')
    } finally {
      setLiking(false)
    }
  }

  async function handleComment(e: React.FormEvent) {
    e.preventDefault()
    if (!body.trim()) return
    setPosting(true)
    setError(null)
    try {
      const comment = await api.addComment(outfit.id, { body: body.trim(), author: author.trim() || undefined })
      setComments((prev) => [...(prev ?? []), comment])
      setBody('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not post comment.')
    } finally {
      setPosting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" onClick={onClose}>
      <div
        className="flex max-h-[85vh] w-full max-w-lg flex-col gap-4 overflow-y-auto rounded-xl border border-white/10 bg-ink-2 p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between">
          <div className="flex gap-4">
            <Piece label="Shirt" name={outfit.shirtName} imageUrl={outfit.shirtImageUrl} category="SHIRT" />
            <Piece label="Bottom" name={outfit.bottomName} imageUrl={outfit.bottomImageUrl} category="BOTTOM" />
            <Piece label="Shoes" name={outfit.shoesName} imageUrl={outfit.shoesImageUrl} category="SHOES" />
          </div>
          <span className="font-mono text-3xl font-bold text-accent">{outfit.score.toFixed(0)}</span>
        </div>

        <ul className="flex flex-col gap-1.5">
          {outfit.reasons.map((reason) => (
            <li key={reason} className="flex gap-2 text-sm text-neutral-300">
              <span className="text-accent">»</span>
              {reason}
            </li>
          ))}
        </ul>

        <button
          onClick={handleLike}
          disabled={liking}
          aria-label="Like this fit"
          className="flex w-fit items-center gap-2 rounded-full bg-white/10 px-3 py-1.5 text-sm font-bold text-neutral-200 hover:bg-accent hover:text-ink disabled:opacity-50 cursor-pointer"
        >
          <svg viewBox="0 0 24 24" fill="currentColor" className="h-4 w-4">
            <path d="M12 21s-7.5-4.6-10-9.1C.5 8.4 2.4 5 6 5c2 0 3.4 1 4 2.3C10.6 6 12 5 14 5c3.6 0 5.5 3.4 4 6.9C19.5 16.4 12 21 12 21Z" />
          </svg>
          {outfit.likeCount}
        </button>

        <div className="flex flex-col gap-3 border-t border-white/10 pt-4">
          <p className="font-mono text-xs uppercase tracking-widest text-neutral-500">
            Comments {comments ? `(${comments.length})` : ''}
          </p>

          {comments === null ? (
            <p className="text-sm text-neutral-600">Loading…</p>
          ) : comments.length === 0 ? (
            <p className="text-sm text-neutral-600">No comments yet — be the first.</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {comments.map((c) => (
                <li key={c.id} className="text-sm">
                  <span className="font-semibold text-neutral-200">{c.author}</span>{' '}
                  <span className="text-neutral-300">{c.body}</span>
                </li>
              ))}
            </ul>
          )}

          <form onSubmit={handleComment} className="flex flex-col gap-2">
            <div className="flex gap-2">
              <input
                value={author}
                onChange={(e) => setAuthor(e.target.value)}
                placeholder="Name (optional)"
                className="w-32 rounded-md border border-white/15 bg-black/30 px-2 py-1.5 text-sm outline-none focus:border-accent"
              />
              <input
                value={body}
                onChange={(e) => setBody(e.target.value)}
                placeholder="Add a comment…"
                className="flex-1 rounded-md border border-white/15 bg-black/30 px-2 py-1.5 text-sm outline-none focus:border-accent"
              />
            </div>
            {error && <p className="text-sm text-red-400">{error}</p>}
            <button
              type="submit"
              disabled={posting || !body.trim()}
              className="self-end rounded-md bg-accent px-3 py-1.5 text-xs font-bold uppercase text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
            >
              {posting ? 'Posting…' : 'Post'}
            </button>
          </form>
        </div>

        <button onClick={onClose} className="self-center text-sm text-neutral-500 hover:text-white cursor-pointer">
          Close
        </button>
      </div>
    </div>
  )
}
