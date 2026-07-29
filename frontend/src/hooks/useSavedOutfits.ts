import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { SavedOutfitDto } from '../types'

export function useSavedOutfits() {
  const [savedOutfits, setSavedOutfits] = useState<SavedOutfitDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setSavedOutfits(await api.getSavedOutfits())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load saved fits')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const saveOutfit = useCallback(
    async (shirtId: string, bottomId: string, shoesId: string): Promise<SavedOutfitDto> => {
      const saved = await api.saveOutfit({ shirtId, bottomId, shoesId })
      await refresh()
      return saved
    },
    [refresh],
  )

  const deleteSavedOutfit = useCallback(
    async (id: string) => {
      await api.deleteSavedOutfit(id)
      setSavedOutfits((prev) => prev.filter((o) => o.id !== id))
    },
    [],
  )

  return { savedOutfits, loading, error, refresh, saveOutfit, deleteSavedOutfit }
}
