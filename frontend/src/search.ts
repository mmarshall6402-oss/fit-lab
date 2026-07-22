import type { ItemDto, SavedOutfitDto } from './types'

export function matchesItem(item: ItemDto, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return (
    item.name.toLowerCase().includes(q) ||
    item.colors.some((c) => c.includes(q)) ||
    item.vibes.some((v) => v.includes(q))
  )
}

export function matchesSavedOutfit(outfit: SavedOutfitDto, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return [outfit.shirtName, outfit.bottomName, outfit.shoesName].some((name) =>
    name.toLowerCase().includes(q),
  )
}
