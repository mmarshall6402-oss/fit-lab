import { useRef } from 'react'
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
  onUploadImage: (file: File) => void
  onOpenAttachments: () => void
}

export function ItemCard({ item, selected, score, onSelect, onDelete, onUploadImage, onOpenAttachments }: Props) {
  const photoInput = useRef<HTMLInputElement>(null)

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

      <input
        ref={photoInput}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) onUploadImage(file)
          e.target.value = ''
        }}
      />

      <div className="absolute bottom-2 right-2 hidden gap-1.5 group-hover:flex">
        <button
          onClick={(e) => {
            e.stopPropagation()
            photoInput.current?.click()
          }}
          className="flex h-7 w-7 items-center justify-center rounded-full bg-black/70 text-neutral-400 hover:text-accent cursor-pointer"
          title="Upload photo"
          aria-label={`Upload photo for ${item.name}`}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
            <path
              d="M4 8h3l1.5-2h7L17 8h3v11H4V8Z"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
            <circle cx="12" cy="13.5" r="3" />
          </svg>
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onOpenAttachments()
          }}
          className="flex h-7 w-7 items-center justify-center rounded-full bg-black/70 text-neutral-400 hover:text-accent cursor-pointer"
          title="Files"
          aria-label={`Files for ${item.name}`}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
            <path
              d="M8 7V5.5a2.5 2.5 0 0 1 5 0V15a3.5 3.5 0 0 1-7 0V7a1 1 0 0 1 2 0v8"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onDelete()
          }}
          className="flex h-7 w-7 items-center justify-center rounded-full bg-black/70 text-neutral-400 hover:text-red-400 cursor-pointer"
          title="Delete item"
          aria-label={`Delete ${item.name}`}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
            <path d="M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m-8 0 1 13h8l1-13" strokeLinecap="round" />
          </svg>
        </button>
      </div>
    </div>
  )
}
