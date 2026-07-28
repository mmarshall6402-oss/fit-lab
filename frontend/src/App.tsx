import { useEffect, useMemo, useState } from 'react'
import { useCatalog } from './hooks/useCatalog'
import { useStyleProfile } from './hooks/useStyleProfile'
import { api } from './api/client'
import { CATEGORIES, type Category, type InputMethod, type ItemDto, type OutfitDto, type Slots } from './types'
import { Header } from './components/Header'
import { CatalogPanel } from './components/CatalogPanel'
import { OutfitBuilder } from './components/OutfitBuilder'

const EMPTY_SLOTS: Slots = { SHIRT: null, BOTTOM: null, SHOES: null }

function pickDefaultAnchor(slots: Slots): Category | null {
  return CATEGORIES.find((c) => slots[c]) ?? null
}

export default function App() {
  const { items, loading, error, createItem, deleteItem, uploadImage } = useCatalog()
  const { submitItemFeedback, submitOutfitFeedback } = useStyleProfile()

  const [activeCategory, setActiveCategory] = useState<Category>('SHIRT')
  const [slots, setSlots] = useState<Slots>(EMPTY_SLOTS)
  const [pinnedAnchor, setPinnedAnchor] = useState<Category | null>(null)
  const [outfit, setOutfit] = useState<OutfitDto | null>(null)
  const [scoring, setScoring] = useState(false)
  const [building, setBuilding] = useState(false)
  const [buildError, setBuildError] = useState<string | null>(null)

  const anchorCategory = useMemo(
    () => (pinnedAnchor && slots[pinnedAnchor] ? pinnedAnchor : pickDefaultAnchor(slots)),
    [pinnedAnchor, slots],
  )

  // Drop any slot whose item was deleted from the catalog.
  useEffect(() => {
    setSlots((prev) => {
      let changed = false
      const next = { ...prev }
      for (const c of CATEGORIES) {
        if (next[c] && !items.some((i) => i.id === next[c]!.id)) {
          next[c] = null
          changed = true
        }
      }
      return changed ? next : prev
    })
  }, [items])

  // Live-score whenever all three slots are filled, unless we already have that exact result (e.g. from Generate).
  useEffect(() => {
    const { SHIRT, BOTTOM, SHOES } = slots
    if (!SHIRT || !BOTTOM || !SHOES) {
      setOutfit(null)
      return
    }
    if (outfit && outfit.shirt.id === SHIRT.id && outfit.bottom.id === BOTTOM.id && outfit.shoes.id === SHOES.id) {
      return
    }
    let cancelled = false
    setScoring(true)
    api
      .scoreOutfit(SHIRT.id, BOTTOM.id, SHOES.id)
      .then((r) => !cancelled && setOutfit(r))
      .catch((e) => !cancelled && setBuildError(e instanceof Error ? e.message : 'Could not score this fit.'))
      .finally(() => !cancelled && setScoring(false))
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slots.SHIRT, slots.BOTTOM, slots.SHOES])

  function handlePick(item: ItemDto) {
    setSlots((prev) => ({ ...prev, [item.category]: item }))
    setBuildError(null)
  }

  function handleClear(category: Category) {
    setSlots((prev) => ({ ...prev, [category]: null }))
    if (pinnedAnchor === category) setPinnedAnchor(null)
    setBuildError(null)
  }

  function handleSetAnchor(category: Category) {
    if (slots[category]) setPinnedAnchor(category)
  }

  function handleSubmitOutfitFeedback(rawText: string, inputMethod: InputMethod) {
    if (!outfit) return Promise.reject(new Error('No scored outfit to give feedback on.'))
    return submitOutfitFeedback({
      shirtId: outfit.shirt.id,
      bottomId: outfit.bottom.id,
      shoesId: outfit.shoes.id,
      rawText,
      inputMethod,
    })
  }

  async function handleGenerate() {
    if (!anchorCategory) return
    const anchor = slots[anchorCategory]
    if (!anchor) return
    setBuilding(true)
    setBuildError(null)
    try {
      const result = await api.buildOutfit(anchor.id)
      setSlots({ SHIRT: result.shirt, BOTTOM: result.bottom, SHOES: result.shoes })
      setOutfit(result)
      setPinnedAnchor(anchorCategory)
    } catch (err) {
      setBuildError(err instanceof Error ? err.message : 'Could not build an outfit from the catalog.')
    } finally {
      setBuilding(false)
    }
  }

  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto flex max-w-6xl flex-col gap-8 p-4 sm:p-8 lg:flex-row">
        <CatalogPanel
          items={items}
          loading={loading}
          error={error}
          slots={slots}
          activeCategory={activeCategory}
          onActiveCategoryChange={setActiveCategory}
          onSelect={handlePick}
          onDelete={deleteItem}
          onCreate={createItem}
          onUploadImage={uploadImage}
          onSubmitItemFeedback={submitItemFeedback}
        />
        <OutfitBuilder
          slots={slots}
          anchorCategory={anchorCategory}
          outfit={outfit}
          scoring={scoring}
          building={building}
          error={buildError}
          onClear={handleClear}
          onSetAnchor={handleSetAnchor}
          onPick={handlePick}
          onGenerate={handleGenerate}
          onSubmitOutfitFeedback={handleSubmitOutfitFeedback}
        />
      </main>
    </div>
  )
}
