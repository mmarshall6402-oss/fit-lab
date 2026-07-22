import type { ItemDto } from '../types'
import { ColorTag, VibeTag } from './Tag'
import { CategoryIcon } from './CategoryIcon'
import { resolveUrl } from '../api/client'

interface Props {
  item: ItemDto
  selected: boolean
  score?: number
  onSelect: () => void
  onDelete: () => void
}

export function ItemCard({ item, selected, score, onSelect, onDelete }: Props) {
  return (
    <div
      className={`group relative flex flex-col overflow-hidden rounded-lg border transition-colors ${
        selected ? 'border-accent bg-accent/5' : 'border-white/10 bg-ink-2 hover:border-white/30'
      }`}
    >
      <button
        onClick={onSelect}
        className="flex flex-1 flex-col text-left cursor-pointer"
        aria-pressed={selected}
      >
        <div className="flex aspect-square items-center justify-center overflow-hidden bg-black/40">
          {item.imageUrl ? (
            <img src={resolveUrl(item.imageUrl)!} alt={item.name} className="h-full w-full object-cover" />
          ) : (
            <CategoryIcon category={item.category} className="h-10 w-10 text-neutral-600" />
          )}
        </div>
        <div className="flex flex-1 flex-col gap-2 p-3">
          <p className="text-sm font-semibold leading-tight">{item.name}</p>
          <div className="flex flex-wrap gap-1">
            {item.colors.map((c) => (
              <ColorTag key={c} color={c} />
            ))}
            {item.vibes.map((v) => (
              <VibeTag key={v} vibe={v} />
            ))}
          </div>
        </div>
      </button>

      {score !== undefined && (
        <div className="absolute left-2 top-2 rounded-full bg-black/70 px-2 py-0.5 font-mono text-[11px] text-accent">
          {score.toFixed(0)}
        </div>
      )}

      {selected && (
        <div className="absolute right-2 top-2 rounded-full bg-accent px-2 py-0.5 font-mono text-[10px] font-bold uppercase text-ink">
          in fit
        </div>
      )}

      <button
        onClick={(e) => {
          e.stopPropagation()
          onDelete()
        }}
        className="absolute bottom-2 right-2 hidden h-7 w-7 items-center justify-center rounded-full bg-black/70 text-neutral-400 hover:text-red-400 group-hover:flex cursor-pointer"
        title="Delete item"
        aria-label={`Delete ${item.name}`}
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
          <path d="M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m-8 0 1 13h8l1-13" strokeLinecap="round" />
        </svg>
      </button>
    </div>
  )
}
