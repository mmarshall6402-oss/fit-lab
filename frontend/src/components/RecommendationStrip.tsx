import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { Category, ItemDto, RecommendationDto } from '../types'
import { CategoryIcon } from './CategoryIcon'

interface Props {
  anchor: ItemDto
  category: Category
  onPick: (item: ItemDto) => void
}

/** Top picks for one empty slot, ranked against the current anchor via /recommend. */
export function RecommendationStrip({ anchor, category, onPick }: Props) {
  const [recs, setRecs] = useState<RecommendationDto[] | null>(null)

  useEffect(() => {
    let cancelled = false
    setRecs(null)
    api
      .recommend(anchor.id, category)
      .then((r) => !cancelled && setRecs(r))
      .catch(() => !cancelled && setRecs([]))
    return () => {
      cancelled = true
    }
  }, [anchor.id, category])

  if (recs === null) {
    return <p className="font-mono text-xs text-neutral-600">finding {category.toLowerCase()} matches…</p>
  }
  if (recs.length === 0) return null

  return (
    <div className="flex flex-col gap-2">
      <p className="font-mono text-[11px] uppercase tracking-widest text-neutral-500">
        Suggested {category.toLowerCase()}s for {anchor.name}
      </p>
      <div className="flex gap-2 overflow-x-auto pb-1">
        {recs.slice(0, 5).map(({ item, score }) => (
          <button
            key={item.id}
            onClick={() => onPick(item)}
            className="group flex w-24 shrink-0 flex-col items-center gap-1 rounded-md border border-white/10 bg-ink-2 p-2 text-center hover:border-accent cursor-pointer"
          >
            <div className="flex h-14 w-full items-center justify-center overflow-hidden rounded bg-black/40">
              {item.imageUrl ? (
                <img src={item.imageUrl} alt={item.name} className="h-full w-full object-cover" />
              ) : (
                <CategoryIcon category={category} className="h-6 w-6 text-neutral-600" />
              )}
            </div>
            <p className="w-full truncate text-[11px] text-neutral-300 group-hover:text-white">{item.name}</p>
            <p className="font-mono text-[10px] text-accent">{score.toFixed(0)}</p>
          </button>
        ))}
      </div>
    </div>
  )
}
