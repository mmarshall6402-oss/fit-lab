import { useState } from 'react'
import type { Category, CreateItemRequest, ItemDto, Slots } from '../types'
import { CATEGORIES } from '../types'
import { ItemCard } from './ItemCard'
import { CategoryIcon } from './CategoryIcon'
import { AddItemModal } from './AddItemModal'
import { SearchInput } from './SearchInput'
import { matchesItem } from '../search'

interface Props {
  items: ItemDto[]
  loading: boolean
  error: string | null
  slots: Slots
  activeCategory: Category
  onActiveCategoryChange: (category: Category) => void
  onSelect: (item: ItemDto) => void
  onDelete: (id: string) => void
  onCreate: (request: CreateItemRequest) => Promise<ItemDto>
  onUploadImage: (id: string, file: File) => Promise<ItemDto>
}

export function CatalogPanel({
  items,
  loading,
  error,
  slots,
  activeCategory,
  onActiveCategoryChange,
  onSelect,
  onDelete,
  onCreate,
  onUploadImage,
}: Props) {
  const [showAddModal, setShowAddModal] = useState(false)
  const [query, setQuery] = useState('')
  const visible = items.filter((i) => i.category === activeCategory && matchesItem(i, query))

  function handleDelete(item: ItemDto) {
    if (window.confirm(`Delete "${item.name}"? This can't be undone.`)) {
      onDelete(item.id)
    }
  }

  return (
    <section className="flex flex-1 flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">Catalog</h2>
        <button
          onClick={() => setShowAddModal(true)}
          className="rounded-md bg-accent px-3 py-1.5 text-xs font-bold uppercase tracking-wide text-ink hover:bg-accent-dim cursor-pointer"
        >
          + Add item
        </button>
      </div>

      <div className="flex gap-1 rounded-lg border border-white/10 bg-black/30 p-1">
        {CATEGORIES.map((c) => (
          <button
            key={c}
            onClick={() => onActiveCategoryChange(c)}
            className={`flex flex-1 items-center justify-center gap-2 rounded-md py-2 text-xs font-bold uppercase tracking-wide cursor-pointer ${
              activeCategory === c ? 'bg-accent text-ink' : 'text-neutral-400 hover:text-white'
            }`}
          >
            <CategoryIcon category={c} className="h-4 w-4" />
            {c}
            <span className="font-mono text-[10px] opacity-70">
              {items.filter((i) => i.category === c).length}
            </span>
          </button>
        ))}
      </div>

      <SearchInput value={query} onChange={setQuery} placeholder="Search by name, color, or vibe…" />

      {error && (
        <p className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">
          {error}
        </p>
      )}

      {loading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="aspect-[3/4] animate-pulse rounded-lg bg-white/5" />
          ))}
        </div>
      ) : visible.length === 0 ? (
        <p className="rounded-lg border border-dashed border-white/15 py-10 text-center text-sm text-neutral-500">
          {query
            ? `No ${activeCategory.toLowerCase()}s match "${query}".`
            : `No ${activeCategory.toLowerCase()}s yet — add one to get started.`}
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {visible.map((item) => (
            <ItemCard
              key={item.id}
              item={item}
              selected={slots[item.category]?.id === item.id}
              onSelect={() => onSelect(item)}
              onDelete={() => handleDelete(item)}
            />
          ))}
        </div>
      )}

      {showAddModal && (
        <AddItemModal
          defaultCategory={activeCategory}
          onCreate={onCreate}
          onUploadImage={onUploadImage}
          onClose={() => setShowAddModal(false)}
        />
      )}
    </section>
  )
}
