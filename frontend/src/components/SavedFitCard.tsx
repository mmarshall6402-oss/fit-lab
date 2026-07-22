import type { Category, SavedOutfitDto } from '../types'
import { resolveUrl } from '../api/client'
import { CategoryIcon } from './CategoryIcon'

function Thumb({ url, alt, category }: { url: string | null; alt: string; category: Category }) {
  return (
    <div className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-md bg-black/40">
      {url ? (
        <img src={resolveUrl(url)!} alt={alt} className="h-full w-full object-cover" />
      ) : (
        <CategoryIcon category={category} className="h-6 w-6 text-neutral-600" />
      )}
    </div>
  )
}

export function SavedFitCard({ outfit, onOpen }: { outfit: SavedOutfitDto; onOpen: () => void }) {
  return (
    <button
      onClick={onOpen}
      className="flex flex-col gap-3 rounded-lg border border-white/10 bg-ink-2 p-3 text-left hover:border-accent cursor-pointer"
    >
      <div className="flex gap-2">
        <Thumb url={outfit.shirtImageUrl} alt={outfit.shirtName} category="SHIRT" />
        <Thumb url={outfit.bottomImageUrl} alt={outfit.bottomName} category="BOTTOM" />
        <Thumb url={outfit.shoesImageUrl} alt={outfit.shoesName} category="SHOES" />
      </div>
      <p className="truncate text-xs text-neutral-400">
        {outfit.shirtName} + {outfit.bottomName} + {outfit.shoesName}
      </p>
      <div className="flex items-center justify-between">
        <span className="font-mono text-lg font-bold text-accent">{outfit.score.toFixed(0)}</span>
        <div className="flex items-center gap-3 text-xs text-neutral-400">
          <span className="flex items-center gap-1">
            <svg viewBox="0 0 24 24" fill="currentColor" className="h-3.5 w-3.5">
              <path d="M12 21s-7.5-4.6-10-9.1C.5 8.4 2.4 5 6 5c2 0 3.4 1 4 2.3C10.6 6 12 5 14 5c3.6 0 5.5 3.4 4 6.9C19.5 16.4 12 21 12 21Z" />
            </svg>
            {outfit.likeCount}
          </span>
          <span className="flex items-center gap-1">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-3.5 w-3.5">
              <path d="M4 4h16v12H8l-4 4V4Z" strokeLinejoin="round" />
            </svg>
            {outfit.commentCount}
          </span>
        </div>
      </div>
    </button>
  )
}
