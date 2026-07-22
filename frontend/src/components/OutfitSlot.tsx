import type { Category, ItemDto } from '../types'
import { CategoryIcon } from './CategoryIcon'
import { ColorTag, VibeTag } from './Tag'
import { resolveUrl } from '../api/client'

interface Props {
  category: Category
  item: ItemDto | null
  isAnchor: boolean
  onClear: () => void
  onSetAnchor: () => void
}

export function OutfitSlot({ category, item, isAnchor, onClear, onSetAnchor }: Props) {
  if (!item) {
    return (
      <div className="flex items-center gap-3 rounded-lg border border-dashed border-white/15 p-3 text-neutral-500">
        <CategoryIcon category={category} className="h-6 w-6 shrink-0" />
        <div>
          <p className="text-xs font-bold uppercase tracking-wide">{category}</p>
          <p className="text-xs">Pick one from the catalog</p>
        </div>
      </div>
    )
  }

  return (
    <div
      className={`flex items-center gap-3 rounded-lg border p-3 ${
        isAnchor ? 'border-accent bg-accent/5' : 'border-white/10 bg-ink-2'
      }`}
    >
      <div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-md bg-black/40">
        {item.imageUrl ? (
          <img src={resolveUrl(item.imageUrl)!} alt={item.name} className="h-full w-full object-cover" />
        ) : (
          <CategoryIcon category={category} className="h-6 w-6 text-neutral-600" />
        )}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-xs font-bold uppercase tracking-wide text-neutral-400">{category}</p>
        <p className="truncate text-sm font-semibold">{item.name}</p>
        <div className="mt-1 flex flex-wrap gap-1">
          {item.colors.slice(0, 3).map((c) => (
            <ColorTag key={c} color={c} />
          ))}
          {item.vibes.slice(0, 2).map((v) => (
            <VibeTag key={v} vibe={v} />
          ))}
        </div>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        <button
          onClick={onSetAnchor}
          title="Keep this piece when generating a fit"
          className={`rounded-full px-2 py-1 text-[10px] font-bold uppercase tracking-wide cursor-pointer ${
            isAnchor ? 'bg-accent text-ink' : 'bg-white/10 text-neutral-400 hover:text-white'
          }`}
        >
          {isAnchor ? 'Anchor' : 'Keep'}
        </button>
        <button
          onClick={onClear}
          className="text-[11px] text-neutral-500 hover:text-red-400 cursor-pointer"
          aria-label={`Remove ${item.name} from outfit`}
        >
          remove
        </button>
      </div>
    </div>
  )
}
