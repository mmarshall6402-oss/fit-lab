import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { CreateItemRequest, ItemDto } from '../types'

export function useCatalog() {
  const [items, setItems] = useState<ItemDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setItems(await api.getItems())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load catalog')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const createItem = useCallback(async (request: CreateItemRequest) => {
    const created = await api.createItem(request)
    setItems((prev) => [...prev, created])
    return created
  }, [])

  const deleteItem = useCallback(async (id: string) => {
    await api.deleteItem(id)
    setItems((prev) => prev.filter((item) => item.id !== id))
  }, [])

  const uploadImage = useCallback(async (id: string, file: File) => {
    const updated = await api.uploadImage(id, file)
    setItems((prev) => prev.map((item) => (item.id === id ? updated : item)))
    return updated
  }, [])

  return { items, loading, error, refresh, createItem, deleteItem, uploadImage }
}
