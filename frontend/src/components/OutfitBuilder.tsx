import type { Category, ItemDto, OutfitDto, Slots } from '../types'
import { CATEGORIES } from '../types'
import { OutfitSlot } from './OutfitSlot'
import { ScorePanel } from './ScorePanel'
import { RecommendationStrip } from './RecommendationStrip'

interface Props {
  slots: Slots
  anchorCategory: Category | null
  outfit: OutfitDto | null
  scoring: boolean
  building: boolean
  error: string | null
  saving: boolean
  saved: boolean
  onClear: (category: Category) => void
  onSetAnchor: (category: Category) => void
  onPick: (item: ItemDto) => void
  onGenerate: () => void
  onSave: () => void
}

export function OutfitBuilder({
  slots,
  anchorCategory,
  outfit,
  scoring,
  building,
  error,
  saving,
  saved,
  onClear,
  onSetAnchor,
  onPick,
  onGenerate,
  onSave,
}: Props) {
  const filledCount = CATEGORIES.filter((c) => slots[c]).length
  const anchorItem = anchorCategory ? slots[anchorCategory] : null
  const emptyCategories = CATEGORIES.filter((c) => !slots[c])

  return (
    <section className="flex w-full flex-col gap-4 lg:w-96 lg:shrink-0">
      <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">Your Fit</h2>

      <div className="flex flex-col gap-2">
        {CATEGORIES.map((category) => (
          <OutfitSlot
            key={category}
            category={category}
            item={slots[category]}
            isAnchor={anchorCategory === category}
            onClear={() => onClear(category)}
            onSetAnchor={() => onSetAnchor(category)}
          />
        ))}
      </div>

      <button
        onClick={onGenerate}
        disabled={filledCount === 0 || building}
        className="rounded-md bg-accent py-2.5 text-sm font-bold uppercase tracking-widest text-ink hover:bg-accent-dim disabled:cursor-not-allowed disabled:opacity-40 cursor-pointer"
      >
        {building ? 'Building…' : filledCount === 0 ? 'Pick a piece to start' : 'Generate best fit'}
      </button>

      {error && (
        <p className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">
          {error}
        </p>
      )}

      <ScorePanel outfit={outfit} loading={scoring} />

      {outfit && (
        <button
          onClick={onSave}
          disabled={saving || saved}
          className="rounded-md border border-accent/40 py-2 text-sm font-bold uppercase tracking-widest text-accent hover:bg-accent/10 disabled:cursor-not-allowed disabled:opacity-60 cursor-pointer"
        >
          {saved ? 'Saved to gallery ✓' : saving ? 'Saving…' : 'Save to gallery'}
        </button>
      )}

      {anchorItem &&
        emptyCategories.map((category) => (
          <RecommendationStrip key={category} anchor={anchorItem} category={category} onPick={onPick} />
        ))}
    </section>
  )
}
