import { useRef, useState } from 'react'
import type { Category, CreateItemRequest, InputMethod, ItemDto, Slots, StyleFeedbackDto } from '../types'
import { CATEGORIES } from '../types'
import { ItemCard } from './ItemCard'
import { CategoryIcon } from './CategoryIcon'
import { AddItemModal } from './AddItemModal'
import { ItemAttachmentsModal } from './ItemAttachmentsModal'
import { ItemFeedbackModal } from './ItemFeedbackModal'

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
  onSubmitItemFeedback: (itemId: string, rawText: string, inputMethod: InputMethod) => Promise<StyleFeedbackDto>
}

/** Strips the extension and swaps separators for spaces, e.g. "aj1-fragment.png" -> "aj1 fragment". */
function nameFromFilename(filename: string): string {
  return filename.replace(/\.[^./]+$/, '').replace(/[-_]+/g, ' ').trim()
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
  onSubmitItemFeedback,
}: Props) {
  const [showAddModal, setShowAddModal] = useState(false)
  const [attachmentsItem, setAttachmentsItem] = useState<ItemDto | null>(null)
  const [feedbackItem, setFeedbackItem] = useState<ItemDto | null>(null)
  const [bulkUploading, setBulkUploading] = useState(false)
  const [bulkError, setBulkError] = useState<string | null>(null)
  const bulkInput = useRef<HTMLInputElement>(null)
  const visible = items.filter((i) => i.category === activeCategory)

  function handleDelete(item: ItemDto) {
    if (window.confirm(`Delete "${item.name}"? This can't be undone.`)) {
      onDelete(item.id)
    }
  }

  async function handleBulkUpload(files: FileList | null) {
    if (!files || files.length === 0) return
    setBulkUploading(true)
    setBulkError(null)
    try {
      for (const file of Array.from(files)) {
        const created = await onCreate({
          name: nameFromFilename(file.name),
          category: activeCategory,
          colors: [],
          vibes: [],
        })
        await onUploadImage(created.id, file)
      }
    } catch (e) {
      setBulkError(e instanceof Error ? e.message : 'Could not bulk upload photos.')
    } finally {
      setBulkUploading(false)
    }
  }

  return (
    <section className="flex flex-1 flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">Catalog</h2>
        <div className="flex items-center gap-2">
          <input
            ref={bulkInput}
            type="file"
            accept="image/*"
            multiple
            className="hidden"
            onChange={(e) => {
              handleBulkUpload(e.target.files)
              e.target.value = ''
            }}
          />
          <button
            onClick={() => bulkInput.current?.click()}
            disabled={bulkUploading}
            className="rounded-md border border-white/15 px-3 py-1.5 text-xs font-bold uppercase tracking-wide text-neutral-300 hover:border-accent hover:text-accent disabled:opacity-50 cursor-pointer"
          >
            {bulkUploading ? 'Uploading…' : '⇧ Bulk upload'}
          </button>
          <button
            onClick={() => setShowAddModal(true)}
            className="rounded-md bg-accent px-3 py-1.5 text-xs font-bold uppercase tracking-wide text-ink hover:bg-accent-dim cursor-pointer"
          >
            + Add item
          </button>
        </div>
      </div>

      {bulkError && (
        <p className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">
          {bulkError}
        </p>
      )}

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
          No {activeCategory.toLowerCase()}s yet — add one to get started.
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
              onUploadImage={(file) => {
                onUploadImage(item.id, file)
              }}
              onOpenAttachments={() => setAttachmentsItem(item)}
              onOpenFeedback={() => setFeedbackItem(item)}
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

      {attachmentsItem && <ItemAttachmentsModal item={attachmentsItem} onClose={() => setAttachmentsItem(null)} />}

      {feedbackItem && (
        <ItemFeedbackModal
          item={feedbackItem}
          onSubmit={(rawText, inputMethod) => onSubmitItemFeedback(feedbackItem.id, rawText, inputMethod)}
          onClose={() => setFeedbackItem(null)}
        />
      )}
    </section>
  )
}
